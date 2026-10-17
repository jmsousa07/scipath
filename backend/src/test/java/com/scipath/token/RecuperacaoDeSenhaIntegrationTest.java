package com.scipath.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.scipath.email.EmailSenderDeOutbox;
import com.scipath.identidade.TokensOpacos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Recuperação de acesso de ponta a ponta (RF010, ADR-0003 (d); contrato v0.4). O que este teste
 * tem de mais importante é o que ele exige que <b>não</b> aconteça:
 *
 * <ul>
 *   <li>a solicitação responde <b>igual</b> para e-mail que existe e para e-mail que não existe —
 *       mesmo status, mesmo corpo, mesma ausência de mensagem do outro lado (RNF013);</li>
 *   <li>o token de recuperação é de <b>uso único</b> e <b>não</b> vale na rota de confirmação de
 *       e-mail (e vice-versa): os dois tipos não se misturam;</li>
 *   <li>senha nova fora do intervalo para em 422 <b>sem consumir o token</b> — o link do e-mail
 *       continua servindo depois de o formulário corrigir a senha;</li>
 *   <li>confirmar a recuperação <b>derruba as sessões abertas</b> da conta (RNF012): a cookie
 *       obtida antes da troca deixa de autenticar, e o teste prova isso reapresentando-a a uma
 *       rota que exige identidade.</li>
 * </ul>
 *
 * <p>Dados com o prefixo {@code teste-e3-4-} e limpeza antes e depois de cada caso; o token vem do
 * outbox de teste (o mesmo caminho de {@code make email:outbox}) e nunca é impresso em claro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("recuperação de acesso: token de uso único, sem enumeração, e sessões derrubadas (E3.4)")
class RecuperacaoDeSenhaIntegrationTest extends ApoioDeTestesDeToken {

    @Autowired
    private PasswordEncoder codificador;

    @Test
    @DisplayName("solicitação responde 202 idêntico com conta e sem conta, e só envia para quem tem conta")
    void solicitacaoRespondeIgualComESemConta() {
        String comConta = PREFIXO + "recupera" + DOMINIO;
        String semConta = PREFIXO + "nao-existe" + DOMINIO;
        criarConta(comConta, "Recuperacao Com Conta");

        // Sem sessão e sem X-CSRF-Token: o segredo aqui é o token, não a cookie (contrato v0.4).
        ResponseEntity<String> respostaComConta = chamar(HttpMethod.POST, URL_SOLICITACAO_DE_RECUPERACAO,
                corpoDaSolicitacaoDeRecuperacao(comConta), null, null, null);
        ResponseEntity<String> respostaSemConta = chamar(HttpMethod.POST, URL_SOLICITACAO_DE_RECUPERACAO,
                corpoDaSolicitacaoDeRecuperacao(semConta), null, null, null);

        assertThat(respostaComConta.getStatusCode().value()).isEqualTo(202);
        assertThat(respostaSemConta.getStatusCode().value()).isEqualTo(202);
        assertThat(respostaComConta.getHeaders().getContentType())
                .isEqualTo(respostaSemConta.getHeaders().getContentType());
        assertThat(respostaComConta.getBody())
                .as("o corpo do 202 não distingue conta existente de inexistente (RNF013)")
                .isEqualTo(respostaSemConta.getBody())
                .isNullOrEmpty();

        // O que muda é só o que acontece do lado de dentro: mensagem para quem tem conta, nada para quem não tem.
        assertThat(mensagensPara(comConta, TipoDeToken.RECUPERACAO_SENHA)).hasSize(1);
        assertThat(mensagensPara(semConta, TipoDeToken.RECUPERACAO_SENHA)).isEmpty();
        assertThat(linhasDeToken(comConta, TipoDeToken.RECUPERACAO_SENHA)).isEqualTo(1);
        assertThat(linhasDeToken(semConta, TipoDeToken.RECUPERACAO_SENHA)).isZero();
        System.out.println("[E3.4] 202 solicitação -> conta: " + respostaComConta.getStatusCode()
                + " | sem conta: " + respostaSemConta.getStatusCode() + " | corpos iguais (vazios)");
    }

    @Test
    @DisplayName("dentro da janela de reenvio a solicitação continua 202 e NÃO gera mensagem nova")
    void solicitacaoRepetidaNaoGeraMensagemNovaESegue202() throws Exception {
        String email = PREFIXO + "janela" + DOMINIO;
        criarConta(email, "Recuperacao Janela");

        for (int tentativa = 1; tentativa <= 2; tentativa++) {
            ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_SOLICITACAO_DE_RECUPERACAO,
                    corpoDaSolicitacaoDeRecuperacao(email), null, null, null);
            assertThat(resposta.getStatusCode().value())
                    .as("a solicitação de recuperação não usa 429 — um cooldown só para conta existente "
                            + "seria o oráculo que a resposta evita (RNF013)")
                    .isEqualTo(202);
        }

        assertThat(mensagensPara(email, TipoDeToken.RECUPERACAO_SENHA))
                .as("a segunda tentativa está dentro da janela de 60 s: nenhuma mensagem nova")
                .hasSize(1);
        System.out.println("[E3.4] 202 solicitação repetida -> 1 mensagem no outbox (janela de reenvio)");
    }

    @Test
    @DisplayName("confirmação troca a senha (bcrypt 12) e invalida a sessão anterior (cookie antigo → 401)")
    void confirmacaoTrocaASenhaEDerrubaAsSessoesAbertas() throws Exception {
        String email = PREFIXO + "troca" + DOMINIO;
        Sessao doCadastro = criarConta(email, "Recuperacao Troca");
        Sessao doLogin = logar(email, SENHA, doCadastro);
        assertThat(sessoesDoToken(doLogin.token())).isEqualTo(1);

        // Pede a recuperação e pega o token no outbox — sem sessão, sem CSRF.
        ResponseEntity<String> solicitacao = chamar(HttpMethod.POST, URL_SOLICITACAO_DE_RECUPERACAO,
                corpoDaSolicitacaoDeRecuperacao(email), null, null, null);
        assertThat(solicitacao.getStatusCode().value()).isEqualTo(202);

        EmailSenderDeOutbox.LinhaDoOutbox mensagem = ultimaMensagemPara(email, TipoDeToken.RECUPERACAO_SENHA)
                .orElseThrow();
        assertThat(mensagem.tipo()).isEqualTo(TipoDeToken.RECUPERACAO_SENHA.name());
        assertThat(mensagem.link()).contains("/recuperar-senha#token=");
        String token = tokenDoLink(mensagem.link());
        System.out.println("[E3.4] mensagem de recuperação -> " + mascararLink(mensagem.link()));
        assertThat(duracaoGravadaEmSegundos(token, TipoDeToken.RECUPERACAO_SENHA))
                .as("validade curta de 30 min (ADR-0003 (d))")
                .isEqualTo(30 * 60);

        // Conclui a recuperação: 204, sem sessão e sem CSRF (o segredo é o token).
        ResponseEntity<String> confirmacao = chamar(HttpMethod.POST, URL_CONFIRMACAO_DE_RECUPERACAO,
                corpoDaConfirmacaoDeRecuperacao(token, SENHA_NOVA), null, null, null);
        assertThat(confirmacao.getStatusCode().value()).isEqualTo(204);
        assertThat(confirmacao.getBody()).isNullOrEmpty();
        System.out.println("[E3.4] 204 confirmação da recuperação -> corpo vazio");

        // Critério 5: a cookie obtida ANTES da troca não autentica mais. A rota escolhida exige
        // identidade (reenvio de verificação), e a recusa tem de ser 401 — sessão inexistente —,
        // não 403 de CSRF: o servidor apagou a linha, não apenas ignorou o cabeçalho.
        ResponseEntity<String> comCookieAntigo = autenticado(HttpMethod.POST, URL_REENVIOS, null, doLogin);
        assertThat(comCookieAntigo.getStatusCode().value())
                .as("sessão anterior derrubada pela recuperação (RNF012)")
                .isEqualTo(401);
        assertThat(comCookieAntigo.getBody()).contains("urn:scipath:erro:nao_autenticado");
        assertThat(sessoesDoToken(doLogin.token())).isZero();
        System.out.println("[E3.4] 401 rota privada com a cookie anterior -> " + comCookieAntigo.getBody());

        // E a senha realmente mudou: a antiga não entra, a nova entra; o hash é bcrypt custo 12.
        assertThat(statusDoLogin(email, SENHA)).isEqualTo(401);
        Sessao nova = logar(email, SENHA_NOVA);
        assertThat(nova.token()).isNotBlank();
        String hash = senhaHashDaConta(email);
        assertThat(hash).matches("^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$");
        assertThat(codificador.matches(SENHA_NOVA, hash)).isTrue();
        assertThat(codificador.matches(SENHA, hash)).isFalse();
        assertThat(consumidoEmDoToken(token, TipoDeToken.RECUPERACAO_SENHA)).isNotNull();
        assertThat(sessoesDaConta(email))
                .as("a sessão anterior foi apagada e só a nova (do login com a senha nova) existe")
                .isEqualTo(1);
        System.out.println("[E3.4] login com a senha antiga -> 401 | com a nova -> 200 | hash bcrypt 12");
    }

    @Test
    @DisplayName("token de recuperação reusado ou expirado responde o mesmo 422")
    void tokenDeRecuperacaoReusadoOuExpiradoRecebe422() {
        String reuso = PREFIXO + "recuperacao-reuso" + DOMINIO;
        criarConta(reuso, "Recuperacao Reuso");
        chamar(HttpMethod.POST, URL_SOLICITACAO_DE_RECUPERACAO, corpoDaSolicitacaoDeRecuperacao(reuso), null, null, null);
        String token = tokenDoLink(ultimaMensagemPara(reuso, TipoDeToken.RECUPERACAO_SENHA).orElseThrow().link());

        assertThat(confirmarRecuperacao(token, SENHA_NOVA)).isEqualTo(204);
        ResponseEntity<String> reusado = confirmarRecuperacaoComoResposta(token, SENHA_NOVA);
        assertThat(reusado.getStatusCode().value()).isEqualTo(422);
        assertThat(reusado.getBody())
                .contains("urn:scipath:erro:validacao_campo")
                .contains("\"campo\":\"token\"")
                .contains("token inválido ou expirado");
        System.out.println("[E3.4] 422 token de recuperação reusado -> " + reusado.getBody());

        // Expirado: mesma regra de 30 min, linha envelhecida.
        String vencido = PREFIXO + "recuperacao-expirado" + DOMINIO;
        criarConta(vencido, "Recuperacao Expirado");
        chamar(HttpMethod.POST, URL_SOLICITACAO_DE_RECUPERACAO, corpoDaSolicitacaoDeRecuperacao(vencido), null, null, null);
        String tokenVencido = tokenDoLink(ultimaMensagemPara(vencido, TipoDeToken.RECUPERACAO_SENHA).orElseThrow().link());
        jdbc.update("UPDATE verification_token SET expira_em = now() - interval '1 minute' WHERE token_hash = ?",
                TokensOpacos.hash(tokenVencido));

        ResponseEntity<String> expirado = confirmarRecuperacaoComoResposta(tokenVencido, SENHA_NOVA);
        ResponseEntity<String> inventado = confirmarRecuperacaoComoResposta("token-que-nunca-existiu", SENHA_NOVA);
        assertThat(expirado.getStatusCode().value()).isEqualTo(422);
        assertThat(expirado.getBody()).isEqualTo(inventado.getBody());
        assertThat(codificador.matches(SENHA, senhaHashDaConta(vencido)))
                .as("a senha da conta continua a de antes — token vencido não troca nada")
                .isTrue();
    }

    @Test
    @DisplayName("token de confirmação de e-mail não vale na recuperação (e não troca a senha)")
    void tokenDeOutroTipoNaoValeNaRecuperacao() {
        String email = PREFIXO + "tipo-trocado" + DOMINIO;
        criarConta(email, "Recuperacao Tipo Trocado");
        String tokenDeConfirmacao = tokenDoLink(ultimaMensagemPara(email, TipoDeToken.CONFIRMACAO_EMAIL).orElseThrow().link());

        ResponseEntity<String> resposta = confirmarRecuperacaoComoResposta(tokenDeConfirmacao, SENHA_NOVA);

        assertThat(resposta.getStatusCode().value()).isEqualTo(422);
        assertThat(resposta.getBody())
                .contains("urn:scipath:erro:validacao_campo")
                .contains("\"campo\":\"token\"")
                .contains("token inválido ou expirado");
        assertThat(codificador.matches(SENHA, senhaHashDaConta(email)))
                .as("a senha não muda por um token de outro tipo")
                .isTrue();
        assertThat(consumidoEmDoToken(tokenDeConfirmacao, TipoDeToken.CONFIRMACAO_EMAIL))
                .as("e o token de confirmação continua válido para o que ele é")
                .isNull();
        System.out.println("[E3.4] 422 token de confirmação apresentado na recuperação -> " + resposta.getBody());
    }

    @Test
    @DisplayName("senha nova fora do intervalo responde 422 no campo novaSenha e NÃO consome o token")
    void senhaForaDoIntervaloNaoConsomeOToken() {
        String email = PREFIXO + "senha-curta" + DOMINIO;
        criarConta(email, "Recuperacao Senha Curta");
        chamar(HttpMethod.POST, URL_SOLICITACAO_DE_RECUPERACAO, corpoDaSolicitacaoDeRecuperacao(email), null, null, null);
        String token = tokenDoLink(ultimaMensagemPara(email, TipoDeToken.RECUPERACAO_SENHA).orElseThrow().link());

        ResponseEntity<String> resposta = confirmarRecuperacaoComoResposta(token, "curta");

        assertThat(resposta.getStatusCode().value()).isEqualTo(422);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resposta.getBody())
                .contains("urn:scipath:erro:validacao_campo")
                .contains("\"campo\":\"novaSenha\"")
                .contains("deve ter entre 8 e 72 caracteres")
                .doesNotContain("curta");
        assertThat(consumidoEmDoToken(token, TipoDeToken.RECUPERACAO_SENHA))
                .as("o formulário pode corrigir a senha e usar o MESMO link")
                .isNull();
        System.out.println("[E3.4] 422 senha fora do intervalo -> " + resposta.getBody());

        // O link do e-mail continua servindo: a senha válida conclui a recuperação.
        assertThat(confirmarRecuperacao(token, SENHA_NOVA)).isEqualTo(204);
        assertThat(codificador.matches(SENHA_NOVA, senhaHashDaConta(email))).isTrue();
    }

    // -------------------------------------------------------------- utilidades

    private int confirmarRecuperacao(String token, String novaSenha) {
        return confirmarRecuperacaoComoResposta(token, novaSenha).getStatusCode().value();
    }

    private ResponseEntity<String> confirmarRecuperacaoComoResposta(String token, String novaSenha) {
        return chamar(HttpMethod.POST, URL_CONFIRMACAO_DE_RECUPERACAO,
                corpoDaConfirmacaoDeRecuperacao(token, novaSenha), null, null, null);
    }
}
