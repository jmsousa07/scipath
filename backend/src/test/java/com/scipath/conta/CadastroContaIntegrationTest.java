package com.scipath.conta;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cadastro de conta (RF008, RN009, CA004) exercitado de ponta a ponta: contexto completo,
 * MVC completo (o 422/409 do {@code ProblemDetail} padrão, não um corpo montado à mão) e o
 * PostgreSQL local de {@code scipath_test}. Sem mock — o que o card exige provar é o que o
 * <b>SQL</b> guardou e o que a <b>resposta HTTP</b> mostra.
 *
 * <p><b>Dados controlados.</b> E-mails e nomes carregam o prefixo {@code teste-e3-2-} e o
 * banco é limpo antes e depois de cada teste: a suíte não depende da ordem das classes nem
 * deixa linha para trás. O prefixo também deixa o {@code \\d account} da evidência legível.
 *
 * <p><b>O que este teste não faz:</b> não verifica o valor da cookie de sessão nem a
 * expiração — isso é o card E3.3, em {@code LoginSessaoIntegrationTest}. O que ele faz desde
 * o E3.3 é mandar o {@code X-CSRF-Token} que a escrita exige: sem o cabeçalho, o cadastro
 * responde <b>403</b> (contrato v0.4 — a escrita é protegida mesmo antes de existir sessão).
 *
 * <p>Mesma configuração de contexto das outras integrações ({@code RANDOM_PORT}): o Spring
 * reusa o contexto já subido e a máquina tem 954 MB (AGENTS.md, higiene de recursos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("cadastro de conta (RF008, RN009)")
class CadastroContaIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Marcador dos dados desta classe em e-mail (e, por consequência, nas linhas que ela cria). */
    private static final String PREFIXO = "teste-e3-2-";
    private static final String DOMINIO = "@example.test";

    private static final String SENHA = "nota-de-aula-2026";
    private static final String NOME = "Maria Silva da Evidencia";

    /** Conteúdo de {@code account.senha_hash} no formato que o bcrypt grava o custo. */
    private static final String BCRYPT_CUSTO_12 = "^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @BeforeEach
    void limparAntes() {
        limparDadosDeTeste();
    }

    @AfterEach
    void limparDepois() {
        limparDadosDeTeste();
    }

    @Test
    @DisplayName("cadastro válido grava conta PENDENTE_VERIFICACAO e perfil com slug do nome")
    void cadastroValidoCriaContaPendenteEPerfil() throws Exception {
        String email = PREFIXO + "maria" + DOMINIO;

        ResponseEntity<String> resposta = cadastrar(email, SENHA, NOME);

        assertThat(resposta.getStatusCode().value()).as("201 é o status publicado no contrato").isEqualTo(201);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        System.out.println("[E3.2] 201 cadastro válido -> " + resposta.getBody());
        // Fronteira do E3.2 com o E3.3: o 201 já traz o Set-Cookie da sessão (contrato v0.4 —
        // o cadastro sai autenticado). O valor não é verificado aqui; quem o exercita é o
        // LoginSessaoIntegrationTest. A linha fica registrada para o atributo não voltar a
        // desaparecer em silêncio.
        System.out.println("[E3.2] cabeçalho Set-Cookie do 201 -> "
                + resposta.getHeaders().getOrDefault("Set-Cookie", List.of()).stream()
                        .map(linha -> linha.substring(0, linha.indexOf('=')) + "=<valor opaco omitido>"
                                + linha.substring(linha.indexOf(';')))
                        .toList());

        assertThat(corpo.get("autenticado").asBoolean()).isTrue();
        assertThat(corpo.get("estadoConta").asText()).isEqualTo("PENDENTE_VERIFICACAO");
        assertThat(corpo.get("perfil").get("slug").asText()).isEqualTo("maria-silva-da-evidencia");
        assertThat(corpo.get("perfil").get("nomePublico").asText()).isEqualTo(NOME);
        assertThat(corpo.get("perfil").get("areasInteresse").isArray()).isTrue();
        assertThat(corpo.get("perfil").get("areasInteresse").size()).as("lista vazia, sempre presente")
                .isZero();
        assertThat(corpo.get("perfil").get("linksExternos").size()).isZero();
        assertThat(corpo.get("perfil").has("biografia")).as("não informado não sai no JSON")
                .isFalse();

        Map<String, Object> conta = jdbc.queryForMap(
                "SELECT estado, email_verificado_em FROM account WHERE email = ?", email);
        assertThat(conta.get("estado")).as("a conta nasce pendente de verificação (ADR-0003 (b))")
                .isEqualTo("PENDENTE_VERIFICACAO");
        assertThat(conta.get("email_verificado_em")).as("nada é verificado no cadastro (E3.4)")
                .isNull();

        List<String> slugs = jdbc.queryForList(
                "SELECT p.slug FROM profile p JOIN account a ON a.id = p.account_id WHERE a.email = ?",
                String.class, email);
        assertThat(slugs).as("o perfil inicial existe e é um só (profile_conta_unica)")
                .containsExactly("maria-silva-da-evidencia");
    }

    @Test
    @DisplayName("e-mail já cadastrado responde 409 e não cria segunda conta (caixa e espaço não importam)")
    void emailDuplicadoResponde409() throws Exception {
        String email = PREFIXO + "maria" + DOMINIO;
        assertThat(cadastrar(email, SENHA, NOME).getStatusCode().value()).isEqualTo(201);

        // Mesmo e-mail em outra caixa e com espaços nas pontas: é o MESMO identificador (RN009).
        ResponseEntity<String> resposta = cadastrar("  " + email.toUpperCase() + "  ", SENHA, "Maria de Novo");

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        System.out.println("[E3.2] 409 e-mail duplicado -> " + resposta.getBody());

        assertThat(corpo.get("type").asText()).isEqualTo("urn:scipath:erro:conflito_de_unicidade");
        assertThat(corpo.get("status").asInt()).isEqualTo(409);
        assertThat(corpo.get("errors").get(0).get("campo").asText()).isEqualTo("email");

        Integer contas = jdbc.queryForObject(
                "SELECT count(*) FROM account WHERE email = ?", Integer.class, email);
        assertThat(contas).as("o 409 não deixa conta parcial no banco").isEqualTo(1);
    }

    @Test
    @DisplayName("a senha gravada é hash bcrypt custo 12, nunca o texto enviado")
    void senhaGravadaEhHashBcryptCusto12() throws Exception {
        String email = PREFIXO + "maria" + DOMINIO;
        assertThat(cadastrar(email, SENHA, NOME).getStatusCode().value()).isEqualTo(201);

        String senhaHash = jdbc.queryForObject(
                "SELECT senha_hash FROM account WHERE email = ?", String.class, email);

        assertThat(senhaHash).as("RNF009: a coluna não guarda o texto puro").isNotEqualTo(SENHA);
        assertThat(senhaHash).as("ADR-0003 (a): bcrypt com custo 12").matches(BCRYPT_CUSTO_12);
        assertThat(codificador.matches(SENHA, senhaHash))
                .as("o hash gravado corresponde à senha enviada")
                .isTrue();
        System.out.println("[E3.2] senha_hash -> " + senhaHash.substring(0, 7) + "... (resto omitido)");
    }

    @Test
    @DisplayName("e-mail fora do formato e senha curta respondem 422 no ProblemDetail padrão")
    void campoInvalidoResponde422() throws Exception {
        ResponseEntity<String> resposta = cadastrar(PREFIXO + "sem-arroba", "curta", NOME);

        assertThat(resposta.getStatusCode().value()).isEqualTo(422);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        System.out.println("[E3.2] 422 campos inválidos -> " + resposta.getBody());

        assertThat(corpo.get("type").asText()).isEqualTo("urn:scipath:erro:validacao_campo");
        assertThat(corpo.get("title").asText()).isEqualTo("Campo inválido");
        assertThat(corpo.get("instance").asText()).isEqualTo("/api/contas");

        List<String> campos = corpo.get("errors").findValues("campo").stream()
                .map(JsonNode::asText).toList();
        assertThat(campos).contains("email", "senha");
        assertThat(corpo.get("errors").findValues("mensagem").stream().map(JsonNode::asText))
                .contains("deve ser um endereço de e-mail válido", "deve ter entre 8 e 72 caracteres");
    }

    @Test
    @DisplayName("senha de 72 bytes UTF-8 é aceita e 74 bytes é recusada sem persistir")
    void limiteDeBytesUtf8DaSenhaNoCadastro() throws Exception {
        String senhaAsciiNoLimite = "a".repeat(72);
        assertThat(senhaAsciiNoLimite.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        String emailAscii = PREFIXO + "limite-ascii" + DOMINIO;
        ResponseEntity<String> asciiAceita = cadastrar(emailAscii, senhaAsciiNoLimite, NOME);
        assertThat(asciiAceita.getStatusCode().value()).isEqualTo(201);
        String hashAscii = jdbc.queryForObject("SELECT senha_hash FROM account WHERE email = ?", String.class, emailAscii);
        assertThat(hashAscii).matches(BCRYPT_CUSTO_12);
        assertThat(codificador.matches(senhaAsciiNoLimite, hashAscii)).isTrue();

        String senhaNoLimite = "é".repeat(36);
        String senhaAcimaDoLimite = "é".repeat(37);
        assertThat(senhaNoLimite.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(senhaAcimaDoLimite.getBytes(StandardCharsets.UTF_8)).hasSize(74);

        String emailValido = PREFIXO + "limite-bytes" + DOMINIO;
        ResponseEntity<String> aceita = cadastrar(emailValido, senhaNoLimite, NOME);
        assertThat(aceita.getStatusCode().value()).isEqualTo(201);
        String hash = jdbc.queryForObject("SELECT senha_hash FROM account WHERE email = ?", String.class, emailValido);
        assertThat(hash).matches(BCRYPT_CUSTO_12);
        assertThat(codificador.matches(senhaNoLimite, hash)).isTrue();

        String emailInvalido = PREFIXO + "acima-bytes" + DOMINIO;
        ResponseEntity<String> recusa = cadastrar(emailInvalido, senhaAcimaDoLimite, NOME);
        assertThat(recusa.getStatusCode().value()).isEqualTo(422);
        JsonNode corpo = JSON.readTree(recusa.getBody());
        assertThat(corpo.get("errors").findValues("campo").stream().map(JsonNode::asText))
                .contains("senha");
        assertThat(corpo.get("errors").findValues("mensagem").stream().map(JsonNode::asText))
                .anyMatch(mensagem -> mensagem.contains("72 bytes"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE email = ?", Integer.class, emailInvalido))
                .isZero();
    }

    @Test
    @DisplayName("nada é persistido quando a validação recusa o cadastro")
    void validacaoRecusadaNaoPersisteNada() throws Exception {
        String email = PREFIXO + "sem-arroba";
        assertThat(cadastrar(email, SENHA, NOME).getStatusCode().value()).isEqualTo(422);

        Integer contas = jdbc.queryForObject(
                "SELECT count(*) FROM account WHERE email = ?", Integer.class, email);
        assertThat(contas).as("RNF011: validação antes de qualquer escrita").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM account WHERE email LIKE ?", Integer.class, PREFIXO + "%"))
                .as("nenhuma conta parcial do cadastro recusado")
                .isZero();
    }

    @Test
    @DisplayName("a resposta do cadastro não carrega e-mail, senha, hash nem token")
    void respostaNaoTrazDadoPrivado() throws Exception {
        String email = PREFIXO + "maria" + DOMINIO;
        ResponseEntity<String> resposta = cadastrar(email, SENHA, NOME);

        String corpo = resposta.getBody();
        assertThat(corpo)
                .as("RNF013: a fronteira pública não devolve dado de conta")
                .doesNotContain(email)
                .doesNotContain(SENHA)
                .doesNotContain("senha")
                .doesNotContain("hash")
                .doesNotContain("token")
                .doesNotContain("email");
        System.out.println("[E3.2] corpo do 201 sem dado privado -> " + corpo);
    }

    @Test
    @DisplayName("dois perfis com o mesmo nome público recebem slugs distintos")
    void slugRepetidoRecebeSufixo() throws Exception {
        ResponseEntity<String> primeiro = cadastrar(PREFIXO + "maria" + DOMINIO, SENHA, "Maria Silva");
        ResponseEntity<String> segundo = cadastrar(PREFIXO + "maria2" + DOMINIO, SENHA, "Maria Silva");

        String slugPrimeiro = JSON.readTree(primeiro.getBody()).get("perfil").get("slug").asText();
        String slugSegundo = JSON.readTree(segundo.getBody()).get("perfil").get("slug").asText();

        assertThat(slugPrimeiro).isEqualTo("maria-silva");
        assertThat(slugSegundo).as("RN009: o slug é único e determinístico").isEqualTo("maria-silva-2");

        Integer perfis = jdbc.queryForObject(
                "SELECT count(*) FROM profile WHERE slug LIKE 'maria-silva%'", Integer.class);
        assertThat(perfis).isEqualTo(2);
        System.out.println("[E3.2] slugs derivados -> " + slugPrimeiro + ", " + slugSegundo);
    }

    /** Cadastro pelo endpoint do contrato, devolvendo a resposta crua (status, corpo e cabeçalhos). */
    private ResponseEntity<String> cadastrar(String email, String senha, String nomePublico) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        // Toda escrita exige o X-CSRF-Token (contrato v0.4) — inclusive o cadastro, que
        // responde sem sessão. O token vem da cookie XSRF-TOKEN que a primeira leitura do app
        // emite; o visitante o devolve no cabeçalho, e a cookie acompanha a requisição.
        String csrf = csrfDeVisitante();
        cabecalhos.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        cabecalhos.add("X-CSRF-Token", csrf);

        String corpo = JSON.createObjectNode()
                .put("email", email)
                .put("senha", senha)
                .put("nomePublico", nomePublico)
                .toString();

        ResponseEntity<String> resposta = rest.exchange("/api/contas", HttpMethod.POST,
                new HttpEntity<>(corpo, cabecalhos), String.class);
        return resposta;
    }

    /** Token CSRF que o frontend lê na cookie XSRF-TOKEN da primeira leitura do app. */
    private String csrfDeVisitante() {
        ResponseEntity<String> estado = rest.getForEntity("/api/sessoes/atual", String.class);
        return estado.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).stream()
                .filter(linha -> linha.startsWith("XSRF-TOKEN="))
                .map(linha -> linha.substring("XSRF-TOKEN=".length(), linha.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("GET /api/sessoes/atual não emitiu a cookie XSRF-TOKEN"));
    }

    /** Remove só as linhas criadas por esta classe (o perfil sai antes, por causa da FK). */
    private void limparDadosDeTeste() {
        jdbc.update("DELETE FROM profile WHERE account_id IN"
                + " (SELECT id FROM account WHERE email LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
    }
}
