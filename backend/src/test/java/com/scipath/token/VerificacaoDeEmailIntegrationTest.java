package com.scipath.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.scipath.email.EmailSenderDeOutbox;
import com.scipath.identidade.TokensOpacos;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Verificação de e-mail de ponta a ponta (ADR-0003 (c), (g) e (h); contrato v0.4): o cadastro
 * emite o token no <b>outbox</b>, a confirmação o consome e ativa a conta, o reenvio respeita o
 * cooldown de 60 s e o token recusado responde sempre a mesma coisa.
 *
 * <p><b>Onde o token em claro existe.</b> Só no arquivo de outbox e na requisição de confirmação.
 * O teste o obtém lendo o outbox — <b>o mesmo caminho do desenvolvedor</b> ({@code make
 * email:outbox}) —, e o que imprime está mascarado ({@link ApoioDeTestesDeToken#mascararLink}).
 * A tabela é conferida pelo hash: {@code verification_token.token_hash} tem de ser o SHA-256 do
 * valor que viajou (RNF009).
 *
 * <p><b>Prazos sem espera.</b> A validade de 24 h e o cooldown de 60 s são os de produção: o
 * teste não os encurta em configuração — ele <b>envelhece a linha</b> ({@code criado_em} /
 * {@code expira_em}) quando precisa observar expiração ou janela vencida, como o E3.3 fez com o
 * limite absoluto da sessão. Assim a regra exercitada é a mesma que roda no produto, com o mesmo
 * número, e a suíte não dorme 24 h.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("verificação de e-mail: token de uso único, outbox e cooldown (E3.4)")
class VerificacaoDeEmailIntegrationTest extends ApoioDeTestesDeToken {

    @Test
    @DisplayName("fluxo completo: cadastro emite no outbox, confirmação consome o token e ativa a conta")
    void fluxoCompletoDeVerificacao() throws Exception {
        String email = PREFIXO + "confirma" + DOMINIO;
        Sessao sessao = criarConta(email, "Verificacao Confirma");

        // Estado inicial: a conta nasce PENDENTE_VERIFICACAO e sem marca de verificação.
        assertThat(estadoDaConta(email)).isEqualTo("PENDENTE_VERIFICACAO");
        assertThat(verificadoEmDaConta(email)).isNull();

        // O cadastro emitiu UMA mensagem, do tipo certo, com o link da página de confirmação.
        EmailSenderDeOutbox.LinhaDoOutbox mensagem = ultimaMensagemPara(email, TipoDeToken.CONFIRMACAO_EMAIL)
                .orElseThrow();
        assertThat(mensagem.tipo()).isEqualTo(TipoDeToken.CONFIRMACAO_EMAIL.name());
        assertThat(mensagem.assunto()).isEqualTo("Confirme seu e-mail no SciPath");
        assertThat(mensagem.link()).contains("/verificar-email#token=");
        assertThat(mensagem.link())
                .as("o token vai no fragmento da URL, nunca na query string (RNF009)")
                .doesNotContain("?token=");
        String token = tokenDoLink(mensagem.link());
        System.out.println("[E3.4] 201 cadastro -> outbox: " + mascararLink(mensagem.link()));

        // A tabela guarda o HASH e a validade de 24 h; o valor em claro não é persistido (RNF009).
        assertThat(linhasDeToken(email, TipoDeToken.CONFIRMACAO_EMAIL)).isEqualTo(1);
        assertThat(duracaoGravadaEmSegundos(token, TipoDeToken.CONFIRMACAO_EMAIL)).isEqualTo(24 * 3600);
        assertThat(consumidoEmDoToken(token, TipoDeToken.CONFIRMACAO_EMAIL)).isNull();

        // A confirmação vai SEM sessão e SEM X-CSRF-Token: o segredo é o token do corpo.
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_CONFIRMACAO_DE_EMAIL,
                corpoDoToken(token), null, null, null);
        assertThat(resposta.getStatusCode().value()).isEqualTo(204);
        assertThat(resposta.getBody()).isNullOrEmpty();
        assertThat(linhasDeCookie(resposta, "SCIPATH_SESSAO")).isEmpty();
        System.out.println("[E3.4] 204 confirmação (sem sessão, sem CSRF) -> corpo vazio");

        // Efeito no banco: conta ATIVA, instante de verificação gravado, token queimado.
        assertThat(estadoDaConta(email)).isEqualTo("ATIVO");
        assertThat(verificadoEmDaConta(email)).isNotNull();
        assertThat(consumidoEmDoToken(token, TipoDeToken.CONFIRMACAO_EMAIL)).isNotNull();

        // O estado novo aparece na sessão que o cadastro abriu (o contrato publica estadoConta).
        ResponseEntity<String> estado = autenticado(HttpMethod.GET, URL_ESTADO_DA_SESSAO, null, sessao);
        assertThat(estado.getStatusCode().value()).isEqualTo(200);
        assertThat(JSON.readTree(estado.getBody()).get("estadoConta").asText()).isEqualTo("ATIVO");
        System.out.println("[E3.4] GET /sessoes/atual com a sessão do cadastro -> " + estado.getBody());
    }

    @Test
    @DisplayName("token reusado e token inventado recebem o MESMO 422 do contrato")
    void tokenReusadoRecebeOMesmo422DoContrato() throws Exception {
        String email = PREFIXO + "reuso" + DOMINIO;
        criarConta(email, "Verificacao Reuso");
        String token = tokenDoLink(ultimaMensagemPara(email, TipoDeToken.CONFIRMACAO_EMAIL).orElseThrow().link());

        assertThat(confirmar(token)).as("a primeira confirmação vale").isEqualTo(204);

        ResponseEntity<String> reusado = confirmarComoResposta(token);
        ResponseEntity<String> inventado = confirmarComoResposta("token-que-nunca-foi-emitido-por-ninguem");

        assertThat(reusado.getStatusCode().value()).isEqualTo(422);
        assertThat(inventado.getStatusCode().value()).isEqualTo(422);
        assertThat(reusado.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        // Corpo IDÊNTICO: o reuso não se distingue de um valor que nunca existiu (RNF013).
        assertThat(reusado.getBody()).isEqualTo(inventado.getBody());
        assertThat(reusado.getBody())
                .contains("urn:scipath:erro:validacao_campo")
                .contains("Campo inválido")
                .contains("Um ou mais campos do corpo não foram aceitos.")
                .contains("\"campo\":\"token\"")
                .contains("token inválido ou expirado")
                .as("a recusa não repete o valor apresentado nem fala da conta")
                .doesNotContain(token);
        System.out.println("[E3.4] 422 token reusado -> " + reusado.getBody());

        // A recusa não desfaz o que já tinha valido.
        assertThat(estadoDaConta(email)).isEqualTo("ATIVO");
    }

    @Test
    @DisplayName("token expirado é recusado com o mesmo 422 e a conta continua PENDENTE_VERIFICACAO")
    void tokenExpiradoRecebeOMesmo422() throws Exception {
        String email = PREFIXO + "expirado" + DOMINIO;
        criarConta(email, "Verificacao Expirado");
        String token = tokenDoLink(ultimaMensagemPara(email, TipoDeToken.CONFIRMACAO_EMAIL).orElseThrow().link());

        // Envelhece a validade da linha: mesma regra de 24 h, relógio movido para trás.
        jdbc.update("UPDATE verification_token SET expira_em = now() - interval '1 minute'"
                + " WHERE token_hash = ?", TokensOpacos.hash(token));

        ResponseEntity<String> expirado = confirmarComoResposta(token);
        ResponseEntity<String> inventado = confirmarComoResposta("token-que-nunca-foi-emitido-por-ninguem");

        assertThat(expirado.getStatusCode().value()).isEqualTo(422);
        assertThat(expirado.getBody())
                .as("expirado e inválido respondem a MESMA coisa — nada distingue os casos (RNF013)")
                .isEqualTo(inventado.getBody());
        System.out.println("[E3.4] 422 token expirado -> " + expirado.getBody());

        assertThat(estadoDaConta(email)).isEqualTo("PENDENTE_VERIFICACAO");
        assertThat(verificadoEmDaConta(email)).isNull();
        assertThat(consumidoEmDoToken(token, TipoDeToken.CONFIRMACAO_EMAIL))
                .as("token vencido não é consumido: ele nunca chegou a valer")
                .isNull();
    }

    @Test
    @DisplayName("reenvio dentro do cooldown de 60 s responde 429 com Retry-After; passada a janela, 202")
    void reenvioRespeitaOCooldownDeSessentaSegundos() throws Exception {
        String email = PREFIXO + "cooldown" + DOMINIO;
        Sessao sessao = criarConta(email, "Verificacao Cooldown");
        String primeiroToken = tokenDoLink(ultimaMensagemPara(email).orElseThrow().link());
        assertThat(mensagensPara(email, TipoDeToken.CONFIRMACAO_EMAIL)).hasSize(1);

        // Logo após o cadastro, o reenvio está dentro do cooldown.
        ResponseEntity<String> cedo = autenticado(HttpMethod.POST, URL_REENVIOS, null, sessao);
        assertThat(cedo.getStatusCode().value()).isEqualTo(429);
        assertThat(cedo.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        String retryAfter = cedo.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).as("o 429 do contrato traz Retry-After em segundos").isNotNull();
        // Pedido imediatamente após o cadastro: resta o cooldown inteiro, e ele é informado como 60
        // (a contagem arredonda para cima, nunca promete menos do que falta).
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 60);
        assertThat(cedo.getBody())
                .contains("urn:scipath:erro:reenvio_em_cooldown")
                .contains("Reenvio em cooldown")
                .contains("Aguarde antes de pedir um novo e-mail de verificação.")
                .as("não é erro de campo: o pedido chegou cedo, não errado")
                .doesNotContain("errors");
        assertThat(mensagensPara(email, TipoDeToken.CONFIRMACAO_EMAIL))
                .as("a recusa não emite mensagem nenhuma")
                .hasSize(1);
        System.out.println("[E3.4] 429 reenvio dentro do cooldown -> Retry-After: " + retryAfter
                + " | " + cedo.getBody());

        // Passada a janela (emissão envelhecida), o mesmo pedido é aceito.
        jdbc.update("UPDATE verification_token SET criado_em = now() - interval '2 minutes'"
                + " WHERE account_id = ?", contaId(email));
        ResponseEntity<String> aceito = autenticado(HttpMethod.POST, URL_REENVIOS, null, sessao);
        assertThat(aceito.getStatusCode().value()).isEqualTo(202);
        JsonNode corpo = JSON.readTree(aceito.getBody());
        Instant reenviadoEm = Instant.parse(corpo.get("reenviadoEm").asText());
        Instant podeReenviarEm = Instant.parse(corpo.get("podeReenviarEm").asText());
        assertThat(Duration.between(reenviadoEm, podeReenviarEm))
                .as("podeReenviarEm é o cooldown configurado depois de reenviadoEm")
                .isEqualTo(Duration.ofSeconds(60));
        System.out.println("[E3.4] 202 reenvio aceito -> " + aceito.getBody());

        // E saiu uma mensagem NOVA, com token diferente do primeiro.
        assertThat(mensagensPara(email, TipoDeToken.CONFIRMACAO_EMAIL)).hasSize(2);
        String segundoToken = tokenDoLink(ultimaMensagemPara(email, TipoDeToken.CONFIRMACAO_EMAIL).orElseThrow().link());
        assertThat(segundoToken).isNotEqualTo(primeiroToken);
        assertThat(linhasDeToken(email, TipoDeToken.CONFIRMACAO_EMAIL)).isEqualTo(2);
    }

    @Test
    @DisplayName("reenvio sem sessão responde 401 e sem o token CSRF responde 403, sem emitir nada")
    void reenvioExigeSessaoEProtecaoCsrf() throws Exception {
        String email = PREFIXO + "autorizacao" + DOMINIO;
        Sessao sessao = criarConta(email, "Verificacao Autorizacao");

        // Sem sessão: 401 — a autenticação precede a checagem de CSRF (contrato v0.4).
        ResponseEntity<String> semSessao = chamar(HttpMethod.POST, URL_REENVIOS, null,
                null, csrfDeVisitante(), csrfDeVisitante());
        assertThat(semSessao.getStatusCode().value()).isEqualTo(401);
        assertThat(semSessao.getBody())
                .contains("urn:scipath:erro:nao_autenticado")
                .contains("É necessário entrar na conta para esta operação.");
        System.out.println("[E3.4] 401 reenvio sem sessão -> " + semSessao.getBody());

        // Com sessão e sem o cabeçalho: 403.
        ResponseEntity<String> semCabecalho = chamar(HttpMethod.POST, URL_REENVIOS, null,
                sessao, sessao.csrf(), null);
        assertThat(semCabecalho.getStatusCode().value()).isEqualTo(403);
        assertThat(semCabecalho.getBody()).contains("urn:scipath:erro:acesso_negado");
        System.out.println("[E3.4] 403 reenvio sem X-CSRF-Token -> " + semCabecalho.getBody());

        assertThat(mensagensPara(email, TipoDeToken.CONFIRMACAO_EMAIL))
                .as("nenhuma recusa de autorização emite mensagem")
                .hasSize(1);
    }

    @Test
    @DisplayName("o item do outbox leva só o essencial: sem hash do token e sem identificador de conta")
    void itemDoOutboxNaoCarregaHashNemIdentificadorDeConta() throws Exception {
        String email = PREFIXO + "outbox" + DOMINIO;
        criarConta(email, "Verificacao Outbox");

        List<String> minhasLinhas = linhasCruasDoOutbox().stream()
                .filter(linha -> linha.contains(email))
                .toList();
        assertThat(minhasLinhas).hasSize(1);
        String linhaCrua = minhasLinhas.get(minhasLinhas.size() - 1);
        JsonNode item = JSON.readTree(linhaCrua);

        List<String> campos = new java.util.ArrayList<>();
        item.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("destinatario", "tipo", "assunto", "link", "criadoEm");
        String token = tokenDoLink(item.get("link").asText());
        assertThat(linhaCrua)
                .as("o outbox carrega o link (ADR-0003 (f)) e nada além dele: sem SHA-256 do token")
                .doesNotContain(TokensOpacos.hash(token));
        assertThat(linhaCrua)
                .as("identificador interno de conta não sai do banco (RNF013)")
                .doesNotContain(contaId(email).toString());
        System.out.println("[E3.4] item do outbox -> "
                + linhaCrua.replace(token, "<valor opaco omitido>"));
    }

    @Test
    @DisplayName("confirmação em conta suspensa registra a verificação e NÃO desfaz a suspensão")
    void confirmacaoNaoDesfazSuspensao() {
        String email = PREFIXO + "suspensa" + DOMINIO;
        criarConta(email, "Verificacao Suspensa");
        String token = tokenDoLink(ultimaMensagemPara(email, TipoDeToken.CONFIRMACAO_EMAIL).orElseThrow().link());
        jdbc.update("UPDATE account SET estado = 'SUSPENSO' WHERE email = ?", email);

        assertThat(confirmar(token)).isEqualTo(204);

        assertThat(estadoDaConta(email))
                .as("suspensão é decisão de abuso (ADR-0003 (b)); confirmar e-mail não a revoga")
                .isEqualTo("SUSPENSO");
        assertThat(verificadoEmDaConta(email)).isNotNull();
        System.out.println("[E3.4] 204 confirmação em conta SUSPENSO -> estado segue SUSPENSO");
    }

    // -------------------------------------------------------------- utilidades

    /** Confirma pelo endpoint do contrato e devolve só o status. */
    private int confirmar(String token) {
        return confirmarComoResposta(token).getStatusCode().value();
    }

    /** A resposta completa do endpoint de confirmação — o corpo é o que os testes comparam. */
    private ResponseEntity<String> confirmarComoResposta(String token) {
        return chamar(HttpMethod.POST, URL_CONFIRMACAO_DE_EMAIL, corpoDoToken(token), null, null, null);
    }
}
