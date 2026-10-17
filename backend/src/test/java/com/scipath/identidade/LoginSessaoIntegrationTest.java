package com.scipath.identidade;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scipath.autor.PerfilPublico;
import com.scipath.error.NaoEncontradoException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, logout e sessão (RF009, CA004, RNF009, RNF010, RNF013) exercitados de ponta a ponta:
 * contexto completo, MVC completo, o PostgreSQL local de {@code scipath_test} e — o que este
 * card tem de mais importante — a <b>cookie</b> como o cliente a vê, com os atributos que o
 * ADR-0003 (e) fixa.
 *
 * <p><b>Por que o teste não usa um cookie jar.</b> {@link TestRestTemplate} não guarda cookie
 * nenhum: cada requisição declara o que manda. É exatamente o que a evidência precisa — o
 * valor devolvido no {@code Set-Cookie} do login é reapresentado à mão nas requisições
 * seguintes, então cada caso prova o que diz provar, e não o que um cliente HTTP completinho
 * fez por trás.
 *
 * <p><b>A rota privada é de teste, e é dito.</b> A primeira rota privada de produção
 * ({@code PUT /perfil/atual}) é o card E3.5; o que existe hoje é o <b>mecanismo</b> —
 * interceptor que resolve a sessão, anotações e checagem de CSRF. Para provar 401 sem sessão,
 * 403 de CSRF, 404 sobre recurso alheio e 200 sobre o próprio, o contexto deste teste ganha
 * {@link ControladorPrivadoDeTeste} (registrado só aqui, em {@code src/test/java}). Sem ele, o
 * mecanismo existiria sem teste de ponta a ponta, e o E3.5 herdaria um guard não exercitado.
 *
 * <p><b>Prazos.</b> O perfil de teste encurta a inatividade para 5 s
 * ({@code application-test.yml}): o caso de expiração espera o prazo configurado e observa a
 * recusa — a mesma regra de produção, com outro número. O limite absoluto (30 s no perfil de
 * teste) é exercitado envelhecendo a linha da sessão no banco, e o teste confere antes que o
 * <i>último uso</i> continua recente: assim a recusa só pode ser do limite absoluto.
 *
 * <p>Dados controlados pelo prefixo {@code teste-e3-3-} no e-mail e limpeza antes e depois de
 * cada caso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(LoginSessaoIntegrationTest.ControladorPrivadoDeTeste.class)
@DisplayName("login, logout e sessão (RF009, RNF010)")
class LoginSessaoIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String PREFIXO = "teste-e3-3-";
    private static final String DOMINIO = "@example.test";
    private static final String SENHA = "nota-de-aula-2026";

    /** Senha de corpo ilegível: o valor só existe para o grep de segredo na evidência (RNF009). */
    private static final String SENHA_DO_CORPO_ILEGIVEL = "senha-ilegivel-nao-pode-ir-para-o-log-3e3";

    private static final String URL_DO_LOGIN = "/api/sessoes";
    private static final String URL_DO_ESTADO = "/api/sessoes/atual";
    private static final String URL_DO_CADASTRO = "/api/contas";
    private static final String ROTA_PRIVADA = "/api/teste/privado/perfis/";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PropriedadesDeSessao propriedades;

    @BeforeEach
    void limparAntes() {
        limparDadosDeTeste();
    }

    @AfterEach
    void limparDepois() {
        limparDadosDeTeste();
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("login válido responde 200 com o estado da sessão e a cookie do ADR-0003 (e)")
    void loginValidoAbreSessaoComCookieDoAdr() throws Exception {
        criarConta(PREFIXO + "um" + DOMINIO, "Sessao Um");

        String csrf = csrfDeVisitante();
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_DO_LOGIN,
                credenciais(PREFIXO + "um" + DOMINIO, SENHA), null, csrf, csrf);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        assertThat(corpo.get("autenticado").asBoolean()).isTrue();
        assertThat(corpo.get("estadoConta").asText()).isEqualTo("PENDENTE_VERIFICACAO");
        assertThat(corpo.get("perfil").get("slug").asText()).isEqualTo("sessao-um");
        assertThat(corpo.get("perfil").get("nomePublico").asText()).isEqualTo("Sessao Um");

        String cookieDaSessao = atributosDaCookie(resposta, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow();
        System.out.println("[E3.3] 200 login -> Set-Cookie " + mascarar(cookieDaSessao));
        assertThat(cookieDaSessao)
                .as("ADR-0003 (e): HttpOnly, Secure, SameSite=Lax, Path=/")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/");
        assertThat(atributosDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow())
                .as("o token CSRF é legível pelo frontend da mesma origem — sem HttpOnly")
                .doesNotContain("HttpOnly")
                .contains("SameSite=Lax").contains("Path=/");

        // O prazo anunciado na cookie é o limite ABSOLUTO configurado (o único que não se estende).
        assertThat(cookieDaSessao)
                .contains("Max-Age=" + propriedades.getDuracaoAbsoluta().toSeconds());

        // O banco guarda o hash, não o valor que viajou na cookie; e a duração gravada é a configurada.
        // A consulta é pelo hash da cookie do login: a conta já tem a sessão do cadastro (que o
        // 201 abriu), e é a linha DESTA sessão que o caso verifica.
        String token = valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow();
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT token_hash, EXTRACT(EPOCH FROM (expira_em - criada_em)) AS duracao FROM sessao"
                        + " WHERE token_hash = ?",
                TokensOpacos.hash(token));
        assertThat(linha.get("token_hash")).as("RNF009: a tabela não guarda o token em claro")
                .isEqualTo(TokensOpacos.hash(token));
        assertThat(((Number) linha.get("duracao")).doubleValue())
                .as("o limite absoluto gravado é o de scipath.sessao.duracao-absoluta")
                .isEqualTo((double) propriedades.getDuracaoAbsoluta().toSeconds());
    }

    @Test
    @DisplayName("conta ATIVO entra e o estado publicado é o da conta no banco")
    void contaAtivaPublicaEstadoAtivo() throws Exception {
        criarConta(PREFIXO + "ativa" + DOMINIO, "Sessao Ativa");
        jdbc.update("UPDATE account SET estado = 'ATIVO', email_verificado_em = now() WHERE email = ?",
                PREFIXO + "ativa" + DOMINIO);

        String csrf = csrfDeVisitante();
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_DO_LOGIN,
                credenciais(PREFIXO + "ativa" + DOMINIO, SENHA), null, csrf, csrf);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(JSON.readTree(resposta.getBody()).get("estadoConta").asText()).isEqualTo("ATIVO");
        System.out.println("[E3.3] 200 login conta ATIVO -> " + resposta.getBody());
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente respondem o MESMO 401 (RNF013)")
    void credenciaisInvalidasRespondemCorpoUnico() throws Exception {
        criarConta(PREFIXO + "dois" + DOMINIO, "Sessao Dois");

        String csrf = csrfDeVisitante();
        ResponseEntity<String> senhaErrada = chamar(HttpMethod.POST, URL_DO_LOGIN,
                credenciais(PREFIXO + "dois" + DOMINIO, "senha-que-nao-e-a-dela"), null, csrf, csrf);
        ResponseEntity<String> emailInexistente = chamar(HttpMethod.POST, URL_DO_LOGIN,
                credenciais(PREFIXO + "nao-existe" + DOMINIO, SENHA), null, csrf, csrf);

        assertThat(senhaErrada.getStatusCode().value()).isEqualTo(401);
        assertThat(emailInexistente.getStatusCode().value()).isEqualTo(401);
        System.out.println("[E3.3] 401 senha errada      -> " + senhaErrada.getBody());
        System.out.println("[E3.3] 401 e-mail inexistente -> " + emailInexistente.getBody());

        assertThat(senhaErrada.getBody())
                .as("o corpo do 401 não diz qual campo falhou nem se a conta existe")
                .isEqualTo(emailInexistente.getBody())
                .contains("urn:scipath:erro:nao_autenticado")
                .contains("E-mail ou senha inválidos.")
                .doesNotContain(PREFIXO + "dois");
        assertThat(senhaErrada.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("conta SUSPENSO: 403 com a senha certa e 401 genérico com a senha errada")
    void contaSuspensaNaoEntraEDeixaDeSerOraculo() throws Exception {
        criarConta(PREFIXO + "suspensa" + DOMINIO, "Sessao Suspensa");
        jdbc.update("UPDATE account SET estado = 'SUSPENSO' WHERE email = ?", PREFIXO + "suspensa" + DOMINIO);

        String csrf = csrfDeVisitante();
        ResponseEntity<String> comSenhaCerta = chamar(HttpMethod.POST, URL_DO_LOGIN,
                credenciais(PREFIXO + "suspensa" + DOMINIO, SENHA), null, csrf, csrf);
        ResponseEntity<String> comSenhaErrada = chamar(HttpMethod.POST, URL_DO_LOGIN,
                credenciais(PREFIXO + "suspensa" + DOMINIO, "senha-errada-mas-nao-digo-qual"), null, csrf, csrf);

        assertThat(comSenhaCerta.getStatusCode().value())
                .as("quem provou ser o dono tem o direito de saber por que não entra (RNF004)")
                .isEqualTo(403);
        assertThat(comSenhaCerta.getBody())
                .contains("urn:scipath:erro:acesso_negado")
                .contains("Esta conta está suspensa e não pode entrar.");
        assertThat(comSenhaCerta.getHeaders().get(HttpHeaders.SET_COOKIE)).as("não abre sessão").isNull();
        System.out.println("[E3.3] 403 conta suspensa -> " + comSenhaCerta.getBody());

        assertThat(comSenhaErrada.getStatusCode().value())
                .as("a suspensão não se transforma em oráculo de existência de conta")
                .isEqualTo(401);
        assertThat(comSenhaErrada.getBody()).contains("E-mail ou senha inválidos.");
    }

    @Test
    @DisplayName("login rotaciona a sessão anterior: o cookie antigo deixa de autenticar")
    void loginRotacionaASessaoAnterior() {
        String email = PREFIXO + "tres" + DOMINIO;
        criarConta(email, "Sessao Tres");

        Sessao primeira = logar(email, SENHA);
        assertThat(rotaPrivada(primeira, "sessao-tres").getStatusCode().value()).isEqualTo(200);

        // O segundo login apresenta a cookie que o cliente já tinha — é o que um navegador faz.
        Sessao segunda = logar(email, SENHA, primeira);

        assertThat(rotaPrivada(primeira, "sessao-tres").getStatusCode().value())
                .as("sessão apresentada antes do login não sobrevive a ele (session fixation)")
                .isEqualTo(401);
        assertThat(rotaPrivada(segunda, "sessao-tres").getStatusCode().value()).isEqualTo(200);
        assertThat(sessoesDoToken(primeira.token())).as("a linha da sessão anterior foi apagada").isZero();
        assertThat(sessoesDoToken(segunda.token())).isEqualTo(1);
    }

    // ------------------------------------------------------- estado da sessão

    @Test
    @DisplayName("visitante recebe 200 com autenticado:false e a cookie XSRF-TOKEN na primeira leitura")
    void visitanteRecebeEstadoAnonimoECookieDeCsrf() throws Exception {
        ResponseEntity<String> resposta = chamar(HttpMethod.GET, URL_DO_ESTADO, null, null, null, null);

        assertThat(resposta.getStatusCode().value())
                .as("não estar autenticado não é erro, é o estado normal do visitante (RNF004)")
                .isEqualTo(200);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        assertThat(corpo.get("autenticado").asBoolean()).isFalse();
        assertThat(corpo.has("estadoConta")).isFalse();
        assertThat(corpo.has("perfil")).isFalse();

        String cookieDeCsrf = atributosDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow(
                () -> new AssertionError("o contrato promete a cookie XSRF-TOKEN já na primeira leitura do app"));
        System.out.println("[E3.3] 200 sessão anônima -> " + resposta.getBody()
                + " | Set-Cookie " + mascarar(cookieDeCsrf));
        assertThat(cookieDeCsrf).contains("Path=/").contains("SameSite=Lax").doesNotContain("HttpOnly");
    }

    @Test
    @DisplayName("estado da sessão autenticada devolve o perfil público e nenhum dado de conta (RNF013)")
    void estadoAutenticadoNaoTrazDadoPrivado() throws Exception {
        String email = PREFIXO + "quatro" + DOMINIO;
        criarConta(email, "Sessao Quatro");
        Sessao sessao = logar(email, SENHA);

        ResponseEntity<String> resposta = autenticado(HttpMethod.GET, URL_DO_ESTADO, null, sessao);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        assertThat(corpo.get("autenticado").asBoolean()).isTrue();
        assertThat(corpo.get("estadoConta").asText()).isEqualTo("PENDENTE_VERIFICACAO");
        assertThat(corpo.get("perfil").get("slug").asText()).isEqualTo("sessao-quatro");
        System.out.println("[E3.3] 200 estado autenticado -> " + resposta.getBody());

        assertThat(resposta.getBody())
                .as("nem para o dono: e-mail, hash, token e id de conta não existem na fronteira")
                .doesNotContain(email)
                .doesNotContain("senha")
                .doesNotContain("hash")
                .doesNotContain("token")
                .doesNotContain(contaId(email).toString());
    }

    // ----------------------------------------------------------------- logout

    @Test
    @DisplayName("logout revoga a sessão no servidor: o cookie reapresentado não autentica mais")
    void logoutRevogaASessaoNoServidor() throws Exception {
        String email = PREFIXO + "cinco" + DOMINIO;
        criarConta(email, "Sessao Cinco");
        Sessao sessao = logar(email, SENHA);

        ResponseEntity<String> saida = autenticado(HttpMethod.DELETE, URL_DO_LOGIN, null, sessao);
        assertThat(saida.getStatusCode().value()).isEqualTo(204);
        assertThat(saida.getBody()).as("204 não tem corpo").isNull();
        System.out.println("[E3.3] 204 logout -> Set-Cookie "
                + mascarar(atributosDaCookie(saida, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow()));

        assertThat(atributosDaCookie(saida, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow())
                .contains("Max-Age=0").contains("HttpOnly").contains("Path=/");
        assertThat(sessoesDoToken(sessao.token()))
                .as("a linha da sessão foi apagada, não só a cookie expirada")
                .isZero();

        // O cliente guarda o valor e o reapresenta: não há mais sessão que ele identifique.
        assertThat(rotaPrivada(sessao, "sessao-cinco").getStatusCode().value()).isEqualTo(401);
        ResponseEntity<String> estado = autenticado(HttpMethod.GET, URL_DO_ESTADO, null, sessao);
        assertThat(JSON.readTree(estado.getBody()).get("autenticado").asBoolean()).isFalse();
        System.out.println("[E3.3] 200 estado com cookie pós-logout -> " + estado.getBody());

        // Idempotente: sair de novo, com a mesma cookie, continua 204. E sem sessão nenhuma
        // também: o cliente manda o token CSRF que ele tem (o visitante recebe um na primeira
        // leitura do app) e o servidor não tem o que revogar.
        assertThat(autenticado(HttpMethod.DELETE, URL_DO_LOGIN, null, sessao).getStatusCode().value()).isEqualTo(204);
        String csrfDeVisitante = csrfDeVisitante();
        assertThat(chamar(HttpMethod.DELETE, URL_DO_LOGIN, null, null, csrfDeVisitante, csrfDeVisitante)
                .getStatusCode().value())
                .as("sem sessão nenhuma também é 204 — logout não vira estado de erro em tela (§15.3)")
                .isEqualTo(204);
    }

    // ------------------------------------------------------------- autorização

    @Test
    @DisplayName("rota privada sem cookie responde 401 com o código do contrato")
    void rotaPrivadaSemSessaoResponde401() throws Exception {
        ResponseEntity<String> resposta = chamar(HttpMethod.GET, ROTA_PRIVADA + "sessao-seis", null, null, null, null);

        assertThat(resposta.getStatusCode().value()).isEqualTo(401);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        assertThat(corpo.get("type").asText()).isEqualTo("urn:scipath:erro:nao_autenticado");
        assertThat(corpo.get("title").asText()).isEqualTo("Não autenticado");
        assertThat(corpo.get("instance").asText()).isEqualTo(ROTA_PRIVADA + "sessao-seis");
        System.out.println("[E3.3] 401 rota privada sem sessão -> " + resposta.getBody());
    }

    @Test
    @DisplayName("sessão de outra conta não alcança recurso alheio: 404, e o perfil alheio não muda (CA007)")
    void sessaoDeOutraContaNaoAlcancaRecursoAlheio() throws Exception {
        criarConta(PREFIXO + "seis" + DOMINIO, "Sessao Seis");
        criarConta(PREFIXO + "sete" + DOMINIO, "Sessao Sete");
        Sessao daSeis = logar(PREFIXO + "seis" + DOMINIO, SENHA);

        assertThat(rotaPrivada(daSeis, "sessao-seis").getStatusCode().value())
                .as("o próprio recurso, com a própria sessão, é 200")
                .isEqualTo(200);

        ResponseEntity<String> alheio = rotaPrivada(daSeis, "sessao-sete");
        assertThat(alheio.getStatusCode().value())
                .as("a propriedade vem da sessão, nunca do caminho (ADR-0003 (j))")
                .isEqualTo(404);
        assertThat(JSON.readTree(alheio.getBody()).get("type").asText()).isEqualTo("urn:scipath:erro:nao_encontrado");
        System.out.println("[E3.3] 404 recurso de outra conta -> " + alheio.getBody());

        // A escrita sobre recurso alheio também é recusada, e nada muda no banco.
        ResponseEntity<String> escrita = autenticado(HttpMethod.PUT, ROTA_PRIVADA + "sessao-sete", "{}", daSeis);
        assertThat(escrita.getStatusCode().value()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT nome_publico FROM profile WHERE slug = 'sessao-sete'", String.class))
                .as("o perfil da outra conta continua intacto")
                .isEqualTo("Sessao Sete");
    }

    // ------------------------------------------------------------ expiração

    @Test
    @DisplayName("sessão expirada por inatividade responde 401 com o código do contrato")
    void sessaoExpiradaPorInatividadeResponde401() throws Exception {
        String email = PREFIXO + "oito" + DOMINIO;
        criarConta(email, "Sessao Oito");
        Sessao sessao = logar(email, SENHA);

        assertThat(rotaPrivada(sessao, "sessao-oito").getStatusCode().value())
                .as("antes do prazo, a mesma cookie autentica")
                .isEqualTo(200);

        Duration inatividade = propriedades.getDuracaoInatividade();
        esperar(inatividade.plusMillis(600));

        ResponseEntity<String> resposta = rotaPrivada(sessao, "sessao-oito");
        System.out.println("[E3.3] 401 após " + inatividade.toSeconds()
                + "s de inatividade (prazo de scipath.sessao.duracao-inatividade) -> " + resposta.getBody());
        assertThat(resposta.getStatusCode().value()).isEqualTo(401);
        assertThat(JSON.readTree(resposta.getBody()).get("type").asText())
                .isEqualTo("urn:scipath:erro:nao_autenticado");
    }

    @Test
    @DisplayName("limite absoluto recusa mesmo com uso recente (não se estende com o uso)")
    void limiteAbsolutoRecusaMesmoComUsoRecente() throws Exception {
        String email = PREFIXO + "nove" + DOMINIO;
        criarConta(email, "Sessao Nove");
        Sessao sessao = logar(email, SENHA);

        // Envelhece SÓ o limite absoluto da sessão do login: o último uso continua agora, então
        // a recusa não pode ser confundida com inatividade (o prazo de inatividade do perfil de
        // teste é 5 s). `criada_em + 1 microssegundo` respeita o CHECK
        // sessao_expira_depois_de_criar e já é passado — a linha nasceu alguns milissegundos
        // antes desta atualização.
        jdbc.update("UPDATE sessao SET expira_em = criada_em + interval '1 microsecond' WHERE token_hash = ?",
                TokensOpacos.hash(sessao.token()));
        Instant ultimoUso = jdbc.queryForObject("SELECT ultimo_uso_em FROM sessao WHERE token_hash = ?",
                (rs, linha) -> rs.getObject(1, java.time.OffsetDateTime.class).toInstant(),
                TokensOpacos.hash(sessao.token()));
        assertThat(Duration.between(ultimoUso, Instant.now()))
                .as("o último uso é recente: a única razão possível para a recusa é o limite absoluto")
                .isLessThan(propriedades.getDuracaoInatividade());

        ResponseEntity<String> resposta = rotaPrivada(sessao, "sessao-nove");
        System.out.println("[E3.3] 401 com expira_em no passado e uso recente -> " + resposta.getBody());
        assertThat(resposta.getStatusCode().value()).isEqualTo(401);
        assertThat(JSON.readTree(resposta.getBody()).get("type").asText())
                .isEqualTo("urn:scipath:erro:nao_autenticado");
    }

    @Test
    @DisplayName("conta suspensa no meio da sessão não mantém sessão (contrato: SUSPENSO não aparece em SessaoAtual)")
    void contaSuspensaNoMeioDaSessaoPerdeASessao() throws Exception {
        String email = PREFIXO + "dez" + DOMINIO;
        criarConta(email, "Sessao Dez");
        Sessao sessao = logar(email, SENHA);
        assertThat(rotaPrivada(sessao, "sessao-dez").getStatusCode().value()).isEqualTo(200);

        jdbc.update("UPDATE account SET estado = 'SUSPENSO' WHERE email = ?", email);

        assertThat(rotaPrivada(sessao, "sessao-dez").getStatusCode().value()).isEqualTo(401);
        assertThat(JSON.readTree(autenticado(HttpMethod.GET, URL_DO_ESTADO, null, sessao).getBody())
                .get("autenticado").asBoolean()).isFalse();
    }

    // ----------------------------------------------------------- cookie forjada

    @Test
    @DisplayName("cookie forjada ou alterada não autentica: não existe sessão montável por valor inventado")
    void cookieForjadaNaoAutentica() throws Exception {
        String email = PREFIXO + "onze" + DOMINIO;
        criarConta(email, "Sessao Onze");
        Sessao verdadeira = logar(email, SENHA);
        assertThat(rotaPrivada(verdadeira, "sessao-onze").getStatusCode().value()).isEqualTo(200);

        String valor = verdadeira.token();
        String alterado = valor.substring(0, valor.length() - 1)
                + (valor.endsWith("A") ? "B" : "A");
        List<String> forjados = List.of(
                alterado,
                "valor-inventado-sem-sessao-nenhuma",
                valor.substring(0, valor.length() / 2),
                TokensOpacos.hash(valor));   // o hash gravado na tabela não vale como cookie

        for (String forjado : forjados) {
            Sessao impostora = new Sessao(forjado, verdadeira.csrf());
            ResponseEntity<String> resposta = rotaPrivada(impostora, "sessao-onze");
            assertThat(resposta.getStatusCode().value())
                    .as("cookie forjada/alterada (%d caracteres) não pode autenticar", forjado.length())
                    .isEqualTo(401);
            assertThat(JSON.readTree(autenticado(HttpMethod.GET, URL_DO_ESTADO, null, impostora).getBody())
                    .get("autenticado").asBoolean()).isFalse();
        }
        System.out.println("[E3.3] 401 para " + forjados.size()
                + " cookies forjadas/alteradas (valor alterado, inventado, truncado e o hash da tabela)");
    }

    // ------------------------------------------------------------------ CSRF

    @Test
    @DisplayName("escrita sem X-CSRF-Token responde 403 acesso_negado, mesmo sem sessão")
    void escritaSemTokenCsrfResponde403() throws Exception {
        String email = PREFIXO + "doze" + DOMINIO;
        criarConta(email, "Sessao Doze");
        Sessao sessao = logar(email, SENHA);
        String csrf = sessao.csrf();

        ResponseEntity<String> cadastroSemCabecalho = chamar(HttpMethod.POST, URL_DO_CADASTRO,
                corpoDoCadastro(PREFIXO + "treze" + DOMINIO, "Sessao Treze"), null, csrf, null);
        assertThat(cadastroSemCabecalho.getStatusCode().value())
                .as("o cadastro é protegido mesmo antes de existir sessão (contrato v0.4)")
                .isEqualTo(403);

        ResponseEntity<String> loginSemCabecalho = chamar(HttpMethod.POST, URL_DO_LOGIN,
                credenciais(email, SENHA), null, csrf, null);
        assertThat(loginSemCabecalho.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> logoutSemCabecalho = chamar(HttpMethod.DELETE, URL_DO_LOGIN, null, sessao, csrf, null);
        assertThat(logoutSemCabecalho.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> cabecalhoQueNaoCasa = chamar(HttpMethod.DELETE, URL_DO_LOGIN, null, sessao, csrf,
                "outro-valor-qualquer");
        assertThat(cabecalhoQueNaoCasa.getStatusCode().value()).isEqualTo(403);

        assertThat(cadastroSemCabecalho.getBody())
                .contains("urn:scipath:erro:acesso_negado")
                .contains("Falta o cabeçalho 'X-CSRF-Token' ou ele não corresponde à sessão corrente.");
        System.out.println("[E3.3] 403 escrita sem CSRF -> " + cadastroSemCabecalho.getBody());

        // A sessão continua viva: a recusa de CSRF não derruba a sessão de quem está logado.
        assertThat(rotaPrivada(sessao, "sessao-doze").getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE email = ?", Integer.class,
                PREFIXO + "treze" + DOMINIO)).as("nada foi criado pelo cadastro recusado").isZero();
    }

    @Test
    @DisplayName("escrita que exige identidade e chega sem sessão responde 401, não 403 (autenticação antes do CSRF)")
    void escritaSemSessaoResponde401AntesDoCsrf() throws Exception {
        ResponseEntity<String> resposta = chamar(HttpMethod.PUT, ROTA_PRIVADA + "sessao-quatorze", "{}",
                null, null, null);

        assertThat(resposta.getStatusCode().value())
                .as("o contrato fixa a ordem: sem sessão é 401, e o 403 de CSRF aparece quando há sessão")
                .isEqualTo(401);
        assertThat(JSON.readTree(resposta.getBody()).get("type").asText())
                .isEqualTo("urn:scipath:erro:nao_autenticado");
        System.out.println("[E3.3] 401 escrita privada sem sessão (sem CSRF também) -> " + resposta.getBody());
    }

    @Test
    @DisplayName("método não aceito continua 405: o interceptor não troca o código de rota inexistente")
    void metodoNaoAceitoContinua405() throws Exception {
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, "/api/health", "{}", null, null, null);

        assertThat(resposta.getStatusCode().value())
                .as("POST em rota que só aceita GET é 405 — e não 403 de CSRF, que seria resposta de escrita válida")
                .isEqualTo(405);
        assertThat(JSON.readTree(resposta.getBody()).get("type").asText())
                .isEqualTo("urn:scipath:erro:metodo_nao_permitido");
    }

    // ------------------------------------------------------------ cadastro

    @Test
    @DisplayName("cadastro abre sessão no 201 e a cookie já autentica (contrato: sai autenticado)")
    void cadastroAbreSessaoNo201() throws Exception {
        String email = PREFIXO + "quatorze" + DOMINIO;
        String csrf = csrfDeVisitante();

        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_DO_CADASTRO,
                corpoDoCadastro(email, "Sessao Quatorze"), null, csrf, csrf);

        assertThat(resposta.getStatusCode().value()).isEqualTo(201);
        Sessao sessao = new Sessao(
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow(),
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow());
        System.out.println("[E3.3] 201 cadastro -> Set-Cookie "
                + mascarar(atributosDaCookie(resposta, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow()));

        assertThat(atributosDaCookie(resposta, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow())
                .contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/");
        assertThat(rotaPrivada(sessao, "sessao-quatorze").getStatusCode().value())
                .as("a sessão do cadastro é utilizável, não só anunciada")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("corpo ilegível com senha não devolve a senha na resposta (RNF009)")
    void corpoIlegivelNaoDevolveASenha() throws Exception {
        String csrf = csrfDeVisitante();
        String corpo = "{\"email\": \"" + PREFIXO + "quinze" + DOMINIO + "\", \"senha\": \""
                + SENHA_DO_CORPO_ILEGIVEL + "\",}";

        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_DO_LOGIN, corpo, null, csrf, csrf);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(JSON.readTree(resposta.getBody()).get("type").asText()).isEqualTo("urn:scipath:erro:validacao");
        assertThat(resposta.getBody())
                .as("nem a resposta nem o log podem repetir a senha enviada (RNF009)")
                .doesNotContain(SENHA_DO_CORPO_ILEGIVEL)
                .doesNotContain(SENHA);
        System.out.println("[E3.3] 400 corpo ilegível -> " + resposta.getBody());
    }

    // -------------------------------------------------------------- utilidades

    /** Sessão como o cliente a tem: o valor da cookie e o token CSRF que ela carrega. */
    private record Sessao(String token, String csrf) {
    }

    private Sessao logar(String email, String senha) {
        return logar(email, senha, null);
    }

    /**
     * Login pelo endpoint do contrato. {@code anterior}, quando informada, é a cookie que o
     * cliente já tinha: é assim que o navegador chega ao login — a sessão <b>e</b> o token
     * CSRF dela, os dois que o login anterior deixou — e é o que faz a rotação de sessão
     * acontecer (o valor apresentado é revogado antes de a nova sessão nascer).
     */
    private Sessao logar(String email, String senha, Sessao anterior) {
        String csrf = anterior == null ? csrfDeVisitante() : anterior.csrf();
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_DO_LOGIN, credenciais(email, senha),
                anterior, csrf, csrf);
        assertThat(resposta.getStatusCode().value()).as("login de apoio falhou: " + resposta.getBody())
                .isEqualTo(200);
        return new Sessao(
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow(),
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow());
    }

    /** O token que o frontend lê na cookie XSRF-TOKEN da primeira leitura do app. */
    private String csrfDeVisitante() {
        ResponseEntity<String> resposta = rest.getForEntity(URL_DO_ESTADO, String.class);
        return valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow(
                () -> new AssertionError("GET " + URL_DO_ESTADO + " não emitiu a cookie XSRF-TOKEN"));
    }

    private ResponseEntity<String> rotaPrivada(Sessao sessao, String slug) {
        return autenticado(HttpMethod.GET, ROTA_PRIVADA + slug, null, sessao);
    }

    private ResponseEntity<String> autenticado(HttpMethod metodo, String caminho, String corpo, Sessao sessao) {
        return chamar(metodo, caminho, corpo, sessao, sessao == null ? null : sessao.csrf(),
                sessao == null ? null : sessao.csrf());
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String caminho, String corpo,
            Sessao sessao, String cookieDeCsrf, String cabecalhoDeCsrf) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (corpo != null) {
            cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        }
        StringBuilder cookies = new StringBuilder();
        if (sessao != null) {
            cookies.append(CookiesDeSessao.COOKIE_DE_SESSAO).append('=').append(sessao.token());
        }
        if (cookieDeCsrf != null) {
            if (!cookies.isEmpty()) {
                cookies.append("; ");
            }
            cookies.append(CookiesDeSessao.COOKIE_DE_CSRF).append('=').append(cookieDeCsrf);
        }
        if (!cookies.isEmpty()) {
            cabecalhos.add(HttpHeaders.COOKIE, cookies.toString());
        }
        if (cabecalhoDeCsrf != null) {
            cabecalhos.add("X-CSRF-Token", cabecalhoDeCsrf);
        }
        return rest.exchange(caminho, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private ResponseEntity<String> criarConta(String email, String nomePublico) {
        String csrf = csrfDeVisitante();
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_DO_CADASTRO,
                corpoDoCadastro(email, nomePublico), null, csrf, csrf);
        assertThat(resposta.getStatusCode().value()).as("cadastro de apoio falhou: " + resposta.getBody())
                .isEqualTo(201);
        return resposta;
    }

    private static String corpoDoCadastro(String email, String nomePublico) {
        return JSON.createObjectNode().put("email", email).put("senha", SENHA).put("nomePublico", nomePublico)
                .toString();
    }

    private static String credenciais(String email, String senha) {
        return JSON.createObjectNode().put("email", email).put("senha", senha).toString();
    }

    /** Valor da cookie pelo nome, se a resposta a emitiu. */
    private static Optional<String> valorDaCookie(ResponseEntity<?> resposta, String nome) {
        return linhasDeCookie(resposta, nome)
                .map(linha -> {
                    int igual = linha.indexOf('=');
                    int pontoEVirgula = linha.indexOf(';');
                    return pontoEVirgula < 0 ? linha.substring(igual + 1) : linha.substring(igual + 1, pontoEVirgula);
                })
                .filter(valor -> !valor.isBlank())
                .findFirst();
    }

    /** A linha inteira do {@code Set-Cookie} — é ela que a evidência cola (com o valor mascarado). */
    private static Optional<String> atributosDaCookie(ResponseEntity<?> resposta, String nome) {
        return linhasDeCookie(resposta, nome).findFirst();
    }

    private static java.util.stream.Stream<String> linhasDeCookie(ResponseEntity<?> resposta, String nome) {
        return resposta.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).stream()
                .filter(linha -> linha.startsWith(nome + "="));
    }

    /**
     * Troca o valor da cookie por um marcador: a evidência mostra os atributos e não entrega o
     * valor de sessão (RNF009 — o token em claro existe só entre o servidor e o cliente).
     */
    private static String mascarar(String linhaDeCookie) {
        int igual = linhaDeCookie.indexOf('=');
        int pontoEVirgula = linhaDeCookie.indexOf(';');
        return linhaDeCookie.substring(0, igual) + "=<valor opaco omitido>"
                + (pontoEVirgula < 0 ? "" : linhaDeCookie.substring(pontoEVirgula));
    }

    private UUID contaId(String email) {
        return jdbc.queryForObject("SELECT id FROM account WHERE email = ?", UUID.class, email);
    }

    /**
     * Linhas de sessão que a cookie apresentada identifica. É a consulta certa para "esta
     * sessão existe?": a conta pode ter outras sessões legítimas (a do cadastro, a de outro
     * dispositivo), e contá-las todas mediria outra coisa.
     */
    private Integer sessoesDoToken(String token) {
        return jdbc.queryForObject("SELECT count(*) FROM sessao WHERE token_hash = ?", Integer.class,
                TokensOpacos.hash(token));
    }

    private static void esperar(Duration prazo) throws InterruptedException {
        Thread.sleep(prazo.toMillis());
    }

    /** Remove só as linhas criadas por esta classe (perfil antes da conta, por causa da FK). */
    private void limparDadosDeTeste() {
        jdbc.update("DELETE FROM profile WHERE account_id IN"
                + " (SELECT id FROM account WHERE email LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
    }

    /**
     * Rota privada <b>de teste</b> — a única superfície privada que existe antes do E3.5.
     *
     * <p>Ela faz o mínimo que uma rota privada de verdade faz: recebe a identidade por
     * {@link IdentidadeAutenticada} (nunca por caminho ou corpo) e recusa o recurso de outra
     * conta com 404 — a mesma escolha que o contrato publica para recurso que não é do
     * solicitante (não revelar que ele existe). É o que permite provar 401, 403, 404 e 200
     * ponta a ponta, com o servidor de verdade, antes de a rota de produção existir.
     */
    @RestController
    static class ControladorPrivadoDeTeste {

        @GetMapping("/api/teste/privado/perfis/{slug}")
        PerfilPublico perfil(@PathVariable String slug, @IdentidadeAutenticada SessaoAutenticada sessao) {
            exigirPropriedade(slug, sessao);
            return sessao.perfil();
        }

        @PutMapping("/api/teste/privado/perfis/{slug}")
        void editar(@PathVariable String slug, @IdentidadeAutenticada SessaoAutenticada sessao) {
            exigirPropriedade(slug, sessao);
        }

        private static void exigirPropriedade(String slug, SessaoAutenticada sessao) {
            if (!sessao.perfil().slug().equals(slug)) {
                throw new NaoEncontradoException("O recurso solicitado não foi encontrado.");
            }
        }
    }
}
