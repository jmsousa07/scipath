package com.scipath.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scipath.identidade.CookiesDeSessao;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Integração real do PATCH/DELETE do material próprio, incluindo posse, banco e descoberta pública. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("retirada, republicação e exclusão lógica do material próprio (RF017/RF019)")
class GerenciamentoDeEstadoDeMaterialProprioIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PREFIXO = "teste-e5-4-";
    private static final String SENHA = "gerenciar-material-2026";
    private static final String DOMINIO = "@example.test";
    private static final String ROTA = "/api/perfil/atual/materiais";
    private static final String MARCADOR_BUSCA = "marcadore54unico";
    private static final Instant PUBLICADO_EM = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant ATUALIZADO_EM = Instant.parse("2026-10-20T12:00:00Z");
    private static final String PUBLICADO = "a-publicado";
    private static final String RETIRADO = "a-retirado";
    private static final String EXCLUIDO = "a-excluido";
    private static final String ALHEIO = "b-publicado";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private Sessao sessaoA;
    private Sessao sessaoB;
    private String perfilSlugA;

    @BeforeEach
    void preparar() {
        limpar();
        sessaoA = criarSessao("a", "Ana E54");
        sessaoB = criarSessao("b", "Bruno E54");
        perfilSlugA = perfilSlug(email("a"));
        inserirMaterial(perfilId(email("a")), PUBLICADO, "PUBLICADO");
        inserirMaterial(perfilId(email("a")), RETIRADO, "RETIRADO");
        inserirMaterial(perfilId(email("a")), EXCLUIDO, "EXCLUIDO");
        inserirMaterial(perfilId(email("b")), ALHEIO, "PUBLICADO");
    }

    @AfterEach
    void limparDepois() {
        limpar();
    }

    @Test
    @DisplayName("retirar atualiza estado e datas e remove o material imediatamente das cinco superfícies públicas")
    void retiradaTemEfeitoPublicoImediato() {
        String slug = slug(PUBLICADO);
        Map<String, Object> antes = linha(slug);

        ResponseEntity<String> resposta = patch(sessaoA, slug, "RETIRADO");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resposta.getBody()).get("estado").asText()).isEqualTo("RETIRADO");
        assertThat(instante(json(resposta.getBody()).get("publicadoEm").asText())).isEqualTo(PUBLICADO_EM);
        Map<String, Object> depois = linha(slug);
        assertThat(depois.get("estado_publicacao")).isEqualTo("RETIRADO");
        assertThat(depois.get("excluido_em")).isNull();
        assertThat(instanteDe(depois, "publicado_em")).isEqualTo(instanteDe(antes, "publicado_em"));
        assertThat(instanteDe(depois, "atualizado_em")).isAfter(instanteDe(antes, "atualizado_em"));
        assertaForaDoPublico(slug(PUBLICADO));
    }

    @Test
    @DisplayName("republicar preserva publicadoEm, sobe atualizadoEm e restaura a descoberta")
    void republicacaoPreservaDataOriginal() {
        String slug = slug(RETIRADO);
        Map<String, Object> antes = linha(slug);

        ResponseEntity<String> resposta = patch(sessaoA, slug, "PUBLICADO");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resposta.getBody()).get("estado").asText()).isEqualTo("PUBLICADO");
        assertThat(instante(json(resposta.getBody()).get("publicadoEm").asText())).isEqualTo(PUBLICADO_EM);
        Map<String, Object> depois = linha(slug);
        assertThat(depois.get("estado_publicacao")).isEqualTo("PUBLICADO");
        assertThat(instanteDe(depois, "publicado_em")).isEqualTo(instanteDe(antes, "publicado_em"));
        assertThat(instanteDe(depois, "atualizado_em")).isAfter(instanteDe(antes, "atualizado_em"));
        assertThat(rest.getForEntity("/api/materiais/" + slug, String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(json(rest.getForEntity("/api/autores/" + perfilSlugA, String.class).getBody())
                .path("materiais").path("items").toString()).contains("\"slug\":\"" + slug + "\"");
    }

    @Test
    @DisplayName("pedir o estado atual responde 200 sem alterar qualquer coluna, inclusive atualizadoEm")
    void mesmoEstadoEhIdempotenteSemEscrita() {
        String slug = slug(PUBLICADO);
        Map<String, Object> antes = linha(slug);

        ResponseEntity<String> resposta = patch(sessaoA, slug, "PUBLICADO");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resposta.getBody()).get("estado").asText()).isEqualTo("PUBLICADO");
        assertThat(linha(slug)).isEqualTo(antes);
    }

    @Test
    @DisplayName("DELETE marca EXCLUIDO e excluidoEm sem apagar arquivo, e retira o material das superfícies públicas")
    void exclusaoLogicaTemEfeitoPublicoImediato() {
        String slug = slug(PUBLICADO);
        String chave = PREFIXO + "chave-" + PUBLICADO;
        Map<String, Object> antes = linha(slug);

        ResponseEntity<String> resposta = excluir(sessaoA, slug);

        assertThat(resposta.getStatusCode().value()).isEqualTo(204);
        assertThat(resposta.getBody()).isNull();
        Map<String, Object> depois = linha(slug);
        assertThat(depois.get("estado_publicacao")).isEqualTo("EXCLUIDO");
        assertThat(depois.get("excluido_em")).isNotNull();
        assertThat(instanteDe(depois, "publicado_em")).isEqualTo(instanteDe(antes, "publicado_em"));
        assertThat(instanteDe(depois, "atualizado_em")).isAfter(instanteDe(antes, "atualizado_em"));
        assertThat(jdbc.queryForObject("SELECT estado_processamento FROM file_asset WHERE chave_interna = ?",
                String.class, chave)).isEqualTo("VALIDADO");
        assertaForaDoPublico(slug);
    }

    @Test
    @DisplayName("EXCLUIDO é terminal: repetir DELETE ou PATCH responde o mesmo 404 do inexistente sem nova escrita")
    void exclusaoEhTerminalEIndistinguivelDoInexistente() {
        String excluido = slug(PUBLICADO);
        String inexistente = slug("nao-existe");
        assertThat(excluir(sessaoA, excluido).getStatusCode().value()).isEqualTo(204);
        Map<String, Object> aposExclusao = linha(excluido);

        ResponseEntity<String> segundoDelete = excluir(sessaoA, excluido);
        ResponseEntity<String> deleteInexistente = excluir(sessaoA, inexistente);
        ResponseEntity<String> patchExcluido = patch(sessaoA, excluido, "RETIRADO");
        ResponseEntity<String> patchInexistente = patch(sessaoA, inexistente, "RETIRADO");

        assertThat(segundoDelete.getStatusCode().value()).isEqualTo(404);
        assertThat(patchExcluido.getStatusCode().value()).isEqualTo(404);
        assertThat(semInstance(patchExcluido.getBody())).isEqualTo(semInstance(patchInexistente.getBody()));
        assertThat(semInstance(segundoDelete.getBody())).isEqualTo(semInstance(deleteInexistente.getBody()));
        assertThat(linha(excluido)).isEqualTo(aposExclusao);
    }

    @Test
    @DisplayName("estado ausente é 400 e EXCLUIDO, RASCUNHO ou valor arbitrário são 422 no campo")
    void validaEstadoDoCorpo() {
        String slug = slug(PUBLICADO);
        Map<String, Object> antes = linha(slug);

        ResponseEntity<String> ausente = patchJson(sessaoA, slug, "{}");
        assertThat(ausente.getStatusCode().value()).isEqualTo(400);
        assertThat(erro(ausente, "estado")).isEqualTo("é obrigatório");
        for (String estado : List.of("EXCLUIDO", "RASCUNHO", "QUALQUER")) {
            ResponseEntity<String> resposta = patch(sessaoA, slug, estado);
            assertThat(resposta.getStatusCode().value()).as("estado %s", estado).isEqualTo(422);
            assertThat(erro(resposta, "estado")).contains("DELETE");
            assertThat(linha(slug)).isEqualTo(antes);
        }
    }

    @Test
    @DisplayName("conta pendente não republica, mas pode retirar; 403 não altera o material")
    void verificacaoSoEhExigidaParaRepublicar() {
        String slug = slug(RETIRADO);
        jdbc.update("UPDATE account SET estado = 'PENDENTE_VERIFICACAO', email_verificado_em = NULL WHERE email = ?",
                email("a"));
        Map<String, Object> antes = linha(slug);

        ResponseEntity<String> republicacao = patch(sessaoA, slug, "PUBLICADO");

        assertThat(republicacao.getStatusCode().value()).isEqualTo(403);
        assertThat(republicacao.getBody()).contains("Confirme seu e-mail para republicar o material.");
        assertThat(linha(slug)).isEqualTo(antes);
        assertThat(patch(sessaoA, slug(PUBLICADO), "RETIRADO").getStatusCode().value()).isEqualTo(200);
        assertThat(excluir(sessaoA, slug(RETIRADO)).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    @DisplayName("A não pode retirar ou excluir material de B: 404 idêntico ao inexistente e banco de B intacto")
    void recusaAcessoCruzadoNasDuasRotas() {
        String alheio = slug(ALHEIO);
        String inexistente = slug("nao-existe");
        Map<String, Object> antes = linha(alheio);

        ResponseEntity<String> patchAlheio = patch(sessaoA, alheio, "RETIRADO");
        ResponseEntity<String> patchInexistente = patch(sessaoA, inexistente, "RETIRADO");
        ResponseEntity<String> deleteAlheio = excluir(sessaoA, alheio);
        ResponseEntity<String> deleteInexistente = excluir(sessaoA, inexistente);

        assertThat(patchAlheio.getStatusCode().value()).isEqualTo(404);
        assertThat(deleteAlheio.getStatusCode().value()).isEqualTo(404);
        assertThat(semInstance(patchAlheio.getBody())).isEqualTo(semInstance(patchInexistente.getBody()));
        assertThat(semInstance(deleteAlheio.getBody())).isEqualTo(semInstance(deleteInexistente.getBody()));
        assertThat(linha(alheio)).isEqualTo(antes);
    }

    @Test
    @DisplayName("ambas as rotas exigem sessão e CSRF; slug malformado é 400")
    void autenticacaoCsrfESlug() {
        String slug = slug(PUBLICADO);
        assertThat(patch(null, slug, "RETIRADO").getStatusCode().value()).isEqualTo(401);
        assertThat(excluir(null, slug).getStatusCode().value()).isEqualTo(401);
        assertThat(patchSemCsrf(sessaoA, slug, "RETIRADO").getStatusCode().value()).isEqualTo(403);
        assertThat(excluirSemCsrf(sessaoA, slug).getStatusCode().value()).isEqualTo(403);
        assertThat(patch(sessaoA, "Slug_Invalido", "RETIRADO").getStatusCode().value()).isEqualTo(400);
        assertThat(excluir(sessaoA, "Slug_Invalido").getStatusCode().value()).isEqualTo(400);
    }

    private void assertaForaDoPublico(String slug) {
        ResponseEntity<String> listagem = rest.getForEntity("/api/materiais", String.class);
        assertThat(listagem.getStatusCode().value()).isEqualTo(200);
        assertThat(json(listagem.getBody()).path("items").toString()).doesNotContain("\"slug\":\"" + slug + "\"");

        String termo = MARCADOR_BUSCA + slug.substring(PREFIXO.length()).replace("-", "");
        ResponseEntity<String> busca = rest.getForEntity("/api/materiais?q=" + termo, String.class);
        assertThat(busca.getStatusCode().value()).isEqualTo(200);
        assertThat(json(busca.getBody()).path("items")).isEmpty();
        assertThat(json(busca.getBody()).path("total").asInt()).isZero();

        ResponseEntity<String> detalhe = rest.getForEntity("/api/materiais/" + slug, String.class);
        ResponseEntity<String> arquivo = rest.getForEntity("/api/materiais/" + slug + "/arquivo", String.class);
        ResponseEntity<String> ausente = rest.getForEntity("/api/materiais/" + slug("nao-existe"), String.class);
        assertThat(detalhe.getStatusCode().value()).isEqualTo(404);
        assertThat(arquivo.getStatusCode().value()).isEqualTo(404);
        assertThat(semInstance(detalhe.getBody())).isEqualTo(semInstance(ausente.getBody()));
        assertThat(semInstance(arquivo.getBody())).isEqualTo(semInstance(ausente.getBody()));

        JsonNode perfil = json(rest.getForEntity("/api/autores/" + perfilSlugA, String.class).getBody());
        assertThat(perfil.path("materiais").path("items")).isEmpty();
        assertThat(perfil.path("materiais").path("total").asInt()).isZero();
    }

    private ResponseEntity<String> patch(Sessao sessao, String slug, String estado) {
        return patchJson(sessao, slug, "{\"estado\":\"" + estado + "\"}");
    }

    private ResponseEntity<String> patchJson(Sessao sessao, String slug, String corpo) {
        return rest.exchange(ROTA + "/" + slug, HttpMethod.PATCH,
                new HttpEntity<>(corpo, cabecalhos(sessao, true)), String.class);
    }

    private ResponseEntity<String> patchSemCsrf(Sessao sessao, String slug, String estado) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_SESSAO + "=" + sessao.token());
        headers.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_CSRF + "=" + sessao.csrf());
        return rest.exchange(ROTA + "/" + slug, HttpMethod.PATCH,
                new HttpEntity<>("{\"estado\":\"" + estado + "\"}", headers), String.class);
    }

    private ResponseEntity<String> excluir(Sessao sessao, String slug) {
        return rest.exchange(ROTA + "/" + slug, HttpMethod.DELETE,
                new HttpEntity<>(cabecalhos(sessao, true)), String.class);
    }

    private ResponseEntity<String> excluirSemCsrf(Sessao sessao, String slug) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_SESSAO + "=" + sessao.token());
        headers.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_CSRF + "=" + sessao.csrf());
        return rest.exchange(ROTA + "/" + slug, HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);
    }

    private HttpHeaders cabecalhos(Sessao sessao, boolean comCsrf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessao != null) {
            headers.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_SESSAO + "=" + sessao.token());
            headers.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_CSRF + "=" + sessao.csrf());
            if (comCsrf) {
                headers.add("X-CSRF-Token", sessao.csrf());
            }
        }
        return headers;
    }

    private Sessao criarSessao(String id, String nome) {
        String email = email(id);
        String visitante = csrfVisitante();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_CSRF + "=" + visitante);
        headers.add("X-CSRF-Token", visitante);
        ResponseEntity<String> cadastro = rest.postForEntity("/api/contas",
                new HttpEntity<>("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA
                        + "\",\"nomePublico\":\"" + nome + "\"}", headers), String.class);
        assertThat(cadastro.getStatusCode().value()).isEqualTo(201);
        jdbc.update("UPDATE account SET estado = 'ATIVO', email_verificado_em = now() WHERE email = ?", email);

        String csrf = csrfVisitante();
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_CSRF + "=" + csrf);
        headers.add("X-CSRF-Token", csrf);
        ResponseEntity<String> login = rest.postForEntity("/api/sessoes",
                new HttpEntity<>("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}", headers),
                String.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        return new Sessao(cookie(login, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow(),
                cookie(login, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow());
    }

    private String csrfVisitante() {
        return cookie(rest.getForEntity("/api/sessoes/atual", String.class), CookiesDeSessao.COOKIE_DE_CSRF)
                .orElseThrow();
    }

    private void inserirMaterial(UUID perfilId, String identificador, String estado) {
        String chave = PREFIXO + "chave-" + identificador;
        jdbc.update("INSERT INTO file_asset (chave_interna,nome_original_seguro,tipo_detectado,tamanho_bytes,"
                + "checksum,estado_processamento) VALUES (?,?, 'application/pdf', 512, repeat('c',64), 'VALIDADO')",
                chave, identificador + ".pdf");
        jdbc.update("""
                INSERT INTO material (slug,titulo,descricao,profile_id,category_id,material_type_id,license_id,
                                      file_asset_id,estado_publicacao,publicado_em,atualizado_em)
                SELECT ?, ?, 'Descrição do material de teste', ?,
                       (SELECT id FROM category WHERE slug='computacao-e-tecnologia'),
                       (SELECT id FROM material_type WHERE slug='nota-de-aula'),
                       (SELECT id FROM license WHERE slug='cc-by-4.0'),
                       (SELECT id FROM file_asset WHERE chave_interna=?), ?, ?, ?
                """, slug(identificador), MARCADOR_BUSCA + identificador.replace("-", ""), perfilId, chave, estado,
                OffsetDateTime.ofInstant(PUBLICADO_EM, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(ATUALIZADO_EM, ZoneOffset.UTC));
    }

    private Map<String, Object> linha(String slug) {
        return jdbc.queryForMap("SELECT * FROM material WHERE slug = ?", slug);
    }

    private UUID perfilId(String email) {
        return jdbc.queryForObject("SELECT p.id FROM profile p JOIN account a ON a.id=p.account_id WHERE a.email=?",
                UUID.class, email);
    }

    private String perfilSlug(String email) {
        return jdbc.queryForObject("SELECT p.slug FROM profile p JOIN account a ON a.id=p.account_id WHERE a.email=?",
                String.class, email);
    }

    private void limpar() {
        jdbc.update("DELETE FROM material WHERE profile_id IN (SELECT p.id FROM profile p JOIN account a"
                + " ON a.id=p.account_id WHERE a.email LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM file_asset WHERE chave_interna LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
    }

    private static String email(String id) {
        return PREFIXO + id + DOMINIO;
    }

    private static String slug(String id) {
        return PREFIXO + id;
    }

    private static JsonNode json(String body) {
        try {
            return JSON.readTree(body);
        } catch (Exception falha) {
            throw new AssertionError("resposta não é JSON: " + body, falha);
        }
    }

    private static String erro(ResponseEntity<String> resposta, String campo) {
        for (JsonNode item : json(resposta.getBody()).path("errors")) {
            if (campo.equals(item.path("campo").asText())) {
                return item.path("mensagem").asText();
            }
        }
        throw new AssertionError("errors[] não contém " + campo + ": " + resposta.getBody());
    }

    private static String semInstance(String body) {
        return body == null ? null : body.replaceFirst("\\\"instance\\\":\\\"[^\\\"]*\\\"", "\\\"instance\\\":\\\"\\\"");
    }

    private static Optional<String> cookie(ResponseEntity<?> resposta, String nome) {
        return resposta.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).stream()
                .filter(linha -> linha.startsWith(nome + "="))
                .map(linha -> {
                    int inicio = linha.indexOf('=') + 1;
                    int fim = linha.indexOf(';', inicio);
                    return fim < 0 ? linha.substring(inicio) : linha.substring(inicio, fim);
                })
                .filter(valor -> !valor.isBlank())
                .findFirst();
    }

    private static Instant instante(String valor) {
        return Instant.parse(valor);
    }

    private static Instant instanteDe(Map<String, Object> linha, String campo) {
        Object valor = linha.get(campo);
        if (valor instanceof Instant instante) return instante;
        if (valor instanceof OffsetDateTime dataHora) return dataHora.toInstant();
        if (valor instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new AssertionError("valor não é timestamp: " + campo + "=" + valor);
    }

    private record Sessao(String token, String csrf) {
    }
}
