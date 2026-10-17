package com.scipath.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * O detalhe público de material (RF005), exercitado de verdade: contexto completo, MVC completo e
 * o PostgreSQL local de {@code scipath_test}. Sem mock de repositório — o que o card exige provar
 * é o que o <b>SQL</b> devolve para o visitante e como o <b>404</b> sai quando o material não é
 * público, e mock nenhum prova isso.
 *
 * <p><b>Dados controlados.</b> A classe semeia quatro materiais: dois publicados (com licenças
 * diferentes — uma com identificador e URL de referência, outra sem, para exercitar a ausência
 * dos dois campos), um RETIRADO, um EXCLUIDO e um publicado de conta PENDENTE_VERIFICACAO. O
 * banco é limpo antes e depois de cada teste, com o prefixo {@code teste-e2-4-} como marcador: ele
 * não fica sujo nem depende da ordem das classes da suíte.
 *
 * <p><b>As três respostas de erro são asseridas por igualdade, não por semelhança.</b> O card fala
 * em 404 <i>indistinguível</i> (RN002, CA008); comparar só o status deixaria passar um detalhe
 * diferente para "retirado" e "inexistente", que é justamente o que revela a existência do
 * material. Por isso os corpos dos quatro 404 são comparados entre si, campo a campo, depois de
 * normalizar apenas o {@code instance} (o endereço pedido, que é o único que pode diferir).
 *
 * <p>Mesma configuração de contexto das outras integrações ({@code RANDOM_PORT}): o Spring reusa
 * o contexto já subido e a máquina tem 954 MB (AGENTS.md, higiene de recursos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("detalhe público de material (RF005)")
class MateriaisDetalheIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Marcador dos dados desta classe em slug, e-mail e chave de arquivo. */
    private static final String PREFIXO = "teste-e2-4-";

    /** Categorias e tipos da seed V2 (ADR-0006): o teste usa as listas de referência reais. */
    private static final String CATEGORIA = "computacao-e-tecnologia";
    private static final String TIPO = "nota-de-aula";

    /** Licença com identificador e URL de referência preenchidos na seed V2. */
    private static final String LICENCA_COMPLETA = "cc-by-4.0";
    private static final String LICENCA_COMPLETA_URL =
            "https://creativecommons.org/licenses/by/4.0/deed.pt-br";

    /** Licença com {@code identificador} e {@code url_referencia} NULOS na seed V2. */
    private static final String LICENCA_SEM_REFERENCIA = "todos-os-direitos-reservados";

    private static final String TITULO = "Ordenacao por comparacao";
    private static final String DESCRICAO = "Nota de aula sobre ordenacao por comparacao, com analise de complexidade.";

    /** Base fixa das datas: não depende do relógio da máquina nem de fuso do ambiente. */
    private static final Instant PUBLICADO_EM = Instant.parse("2026-10-14T15:10:00Z");

    /** Distinta da publicação, de propósito: o detalhe publica a data de atualização sempre. */
    private static final Instant ATUALIZADO_EM = Instant.parse("2026-10-15T09:00:00Z");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void semearDadosControlados() {
        limparDadosDeTeste();

        UUID contaAtiva = inserirConta("ativo", "ATIVO");
        UUID contaPendente = inserirConta("pendente", "PENDENTE_VERIFICACAO");
        UUID perfilAtivo = inserirPerfil(contaAtiva, "Ana de Detalhe", PREFIXO + "ana");
        UUID perfilPendente = inserirPerfil(contaPendente, "Bruno de Detalhe", PREFIXO + "bruno");

        inserirMaterial(perfilAtivo, "pub-licenca-completa", LICENCA_COMPLETA, "PUBLICADO");
        inserirMaterial(perfilAtivo, "pub-licenca-sem-referencia", LICENCA_SEM_REFERENCIA, "PUBLICADO");
        inserirMaterial(perfilAtivo, "retirado-01", LICENCA_COMPLETA, "RETIRADO");
        inserirMaterial(perfilAtivo, "excluido-01", LICENCA_COMPLETA, "EXCLUIDO");
        inserirMaterial(perfilPendente, "pendente-01", LICENCA_COMPLETA, "PUBLICADO");
    }

    @AfterEach
    void removerDadosDeTeste() {
        limparDadosDeTeste();
    }

    // ------------------------------------------------------------------ critério 1: 200

    @Test
    @DisplayName("material publicado responde 200 com o schema MaterialDetalhe, a licença explicada e o autor sem dado privado")
    void materialPublicadoResponde200ComLicencaExplicada() {
        ResponseEntity<String> resposta = rest.getForEntity(
                "/api/materiais/" + PREFIXO + "pub-licenca-completa", String.class);

        assertThat(resposta.getStatusCode().value()).as("material publicado é público").isEqualTo(200);
        assertThat(resposta.getHeaders().getContentType())
                .as("resposta do detalhe sai como JSON")
                .isNotNull()
                .satisfies(tipo -> assertThat(tipo.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());

        JsonNode corpo = json(resposta.getBody());
        assertThat(camposDe(corpo))
                .as("exatamente o schema MaterialDetalhe do contrato v0.3, nem um campo a mais")
                .containsExactlyInAnyOrder("slug", "titulo", "descricao", "autor", "categoria", "tipo",
                        "licenca", "estado", "publicadoEm", "atualizadoEm");

        assertThat(corpo.get("slug").asText()).isEqualTo(PREFIXO + "pub-licenca-completa");
        assertThat(corpo.get("titulo").asText()).isEqualTo(TITULO);
        assertThat(corpo.get("descricao").asText()).isEqualTo(DESCRICAO);
        assertThat(corpo.get("estado").asText())
                .as("nesta superfície o estado é sempre PUBLICADO (RN002/CA008)")
                .isEqualTo("PUBLICADO");
        assertThat(corpo.get("publicadoEm").asText()).as("date-time do contrato").isEqualTo("2026-10-14T15:10:00Z");
        assertThat(corpo.get("atualizadoEm").asText())
                .as("o detalhe publica a atualização sempre (é campo required do schema)")
                .isEqualTo("2026-10-15T09:00:00Z");

        // Autor público: nome e slug, para creditar e linkar o perfil (RF007).
        assertThat(camposDe(corpo.get("autor"))).containsExactlyInAnyOrder("slug", "nomePublico");
        assertThat(corpo.get("autor").get("slug").asText()).isEqualTo(PREFIXO + "ana");
        assertThat(corpo.get("autor").get("nomePublico").asText()).isEqualTo("Ana de Detalhe");

        assertThat(camposDe(corpo.get("categoria"))).containsExactlyInAnyOrder("slug", "nome");
        assertThat(camposDe(corpo.get("tipo"))).containsExactlyInAnyOrder("slug", "nome");
        assertThat(corpo.get("categoria").get("slug").asText()).isEqualTo(CATEGORIA);
        assertThat(corpo.get("tipo").get("slug").asText()).isEqualTo(TIPO);

        // Licença com identificador e URL: RN006 — vem explicada, não só nomeada.
        JsonNode licenca = corpo.get("licenca");
        assertThat(camposDe(licenca)).containsExactlyInAnyOrder("slug", "nome", "identificador",
                "urlReferencia", "descricaoCurta");
        assertThat(licenca.get("slug").asText()).isEqualTo(LICENCA_COMPLETA);
        assertThat(licenca.get("identificador").asText()).isEqualTo("CC-BY-4.0");
        assertThat(licenca.get("urlReferencia").asText()).isEqualTo(LICENCA_COMPLETA_URL);
        assertThat(licenca.get("descricaoCurta").asText())
                .as("a explicação publicada é a da licença no banco, não um texto de tela")
                .isEqualTo(textoDaDescricaoCurta(LICENCA_COMPLETA))
                .isNotBlank();

        System.out.println("[E2.4] GET /api/materiais/" + PREFIXO + "pub-licenca-completa -> 200 " + corpo);
        System.out.println("[E2.4] licenca explicada -> " + licenca.get("descricaoCurta").asText());
    }

    @Test
    @DisplayName("licença sem identificador nem texto público responde 200 omitindo os dois campos (não vêm vazios)")
    void licencaSemReferenciaOmiteIdentificadorEUrl() {
        JsonNode corpo = json(rest.getForEntity(
                "/api/materiais/" + PREFIXO + "pub-licenca-sem-referencia", String.class).getBody());

        JsonNode licenca = corpo.get("licenca");
        assertThat(licenca.get("slug").asText()).isEqualTo(LICENCA_SEM_REFERENCIA);
        assertThat(camposDe(licenca))
                .as("ausente é informação: não existe identificador nem URL para exibir (§13.2)")
                .containsExactlyInAnyOrder("slug", "nome", "descricaoCurta");
        assertThat(licenca.get("descricaoCurta").asText())
                .as("RN006: explica também o que a licença NÃO permite")
                .isEqualTo(textoDaDescricaoCurta(LICENCA_SEM_REFERENCIA));

        String explicacao = licenca.get("descricaoCurta").asText().toLowerCase();
        assertThat(explicacao)
                .as("a explicação diz o que a licença não permite, não só o rótulo")
                .contains("exige autorização expressa")
                .contains("não coloca a obra em domínio público");

        System.out.println("[E2.4] GET /api/materiais/" + PREFIXO + "pub-licenca-sem-referencia -> 200 " + corpo);
    }

    // ------------------------------------------- critério 1 (2ª metade) e 3: 404 indistinguível

    @Test
    @DisplayName("material retirado, excluído, de conta não ativa e inexistente respondem 404 indistinguível")
    void materialForaDoPublicoResponde404Indistinguivel() {
        Map<String, String> enderecos = new LinkedHashMap<>();
        enderecos.put("retirado", PREFIXO + "retirado-01");
        enderecos.put("excluido", PREFIXO + "excluido-01");
        enderecos.put("de conta nao ativa", PREFIXO + "pendente-01");
        enderecos.put("inexistente", PREFIXO + "nunca-existiu");

        Map<String, String> corposNormalizados = new LinkedHashMap<>();
        for (Map.Entry<String, String> endereco : enderecos.entrySet()) {
            ResponseEntity<String> resposta = rest.getForEntity("/api/materiais/" + endereco.getValue(), String.class);

            assertThat(resposta.getStatusCode().value())
                    .as("material %s -> 404 (RN002, CA008)", endereco.getKey())
                    .isEqualTo(404);
            assertThat(resposta.getHeaders().getContentType())
                    .as("erro sai no formato único do contrato (RFC 7807)")
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

            String corpo = resposta.getBody();
            assertThat(corpo)
                    .contains("\"type\":\"urn:scipath:erro:nao_encontrado\"")
                    .contains("\"title\":\"Recurso não encontrado\"")
                    .contains("\"status\":404")
                    .contains("\"detail\":\"O recurso solicitado não foi encontrado.\"")
                    .contains("\"instance\":\"/api/materiais/" + endereco.getValue() + "\"")
                    .doesNotContain("\"errors\"")
                    .as("nenhum rastro do material, do autor ou do motivo de estar fora do público")
                    .doesNotContain(PREFIXO + "ana")
                    .doesNotContain(TITULO);

            corposNormalizados.put(endereco.getKey(), semInstance(corpo));
            System.out.println("[E2.4] GET /api/materiais/" + endereco.getValue() + " -> 404 " + corpo);
        }

        // A prova de indistinguibilidade: os quatro corpos são IGUAIS fora o endereço pedido.
        // Um detalhe diferente para "retirado" e "inexistente" revelaria que o material existe.
        List<String> distintos = new ArrayList<>(new LinkedHashSet<>(corposNormalizados.values()));
        assertThat(distintos)
                .as("os quatro 404 têm de ser o mesmo corpo, tirando o instance: %s", corposNormalizados)
                .hasSize(1);
    }

    // ------------------------------------------------------------------ critério 3: 400

    @Test
    @DisplayName("slug fora do formato do contrato responde 400 no ProblemDetail padrão, com o slug culpado em errors[]")
    void slugInvalidoResponde400() {
        Map<String, String> casos = new LinkedHashMap<>();
        casos.put("Material-Invalido", "deve ser um slug (minúsculas, dígitos e hífen)");
        casos.put("material_com_underscore", "deve ser um slug (minúsculas, dígitos e hífen)");
        casos.put("material--com-hifen-duplo", "deve ser um slug (minúsculas, dígitos e hífen)");
        casos.put("a".repeat(121), "deve ter no máximo 120 caracteres");

        for (Map.Entry<String, String> caso : casos.entrySet()) {
            String caminho = "/api/materiais/" + caso.getKey();
            ResponseEntity<String> resposta = rest.getForEntity(caminho, String.class);

            assertThat(resposta.getStatusCode().value())
                    .as("GET %s -> 400 (entrada malformada é recusa, não recurso ausente)", caso.getKey())
                    .isEqualTo(400);
            assertThat(resposta.getHeaders().getContentType())
                    .as("erro de entrada sai no ProblemDetail padrão")
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(resposta.getBody())
                    .contains("\"type\":\"urn:scipath:erro:validacao\"")
                    .contains("\"title\":\"Requisição inválida\"")
                    .contains("\"status\":400")
                    .contains("\"campo\":\"slug\"")
                    .contains(caso.getValue());

            System.out.println("[E2.4] GET /api/materiais/" + resumido(caso.getKey()) + " -> 400 " + resposta.getBody());
        }
    }

    // ------------------------------------------------- critério 2: ausência de dado privado

    @Test
    @DisplayName("o payload não carrega dado privado nem marca de revisão, certificação ou curadoria (RNF013, RN010/RN011, CA012)")
    void payloadNaoTrazDadoPrivadoNemMarcaDeVerificacao() {
        String corpo = rest.getForEntity("/api/materiais/" + PREFIXO + "pub-licenca-completa", String.class)
                .getBody();

        // Ausência, não só presença: o payload inteiro é varrido. Nada de e-mail, hash de senha,
        // token, checksum, chave de storage, id de conta ou id interno de tabela.
        for (String proibido : List.of("email", "senha", "hash", "token", "checksum", "chave", "account",
                "profile_id", "profileId", "file_asset", "fileAsset", "\"id\"")) {
            assertThat(corpo)
                    .as("campo privado '%s' na resposta pública (RNF013)", proibido)
                    .doesNotContain(proibido);
        }

        // Nem campo, nem valor, nem rótulo que sugira revisão, certificação, curadoria ou
        // verificação científica (RN010, RN011, CA012): o que o visitante lê é a declaração do
        // autor, e a plataforma não avaliza o conteúdo.
        for (String proibido : List.of("revis", "curad", "certific", "certificacao", "verifica", "verificado",
                "selo", "aprovad", "parecer", "peer", "avaliad")) {
            assertThat(corpo.toLowerCase())
                    .as("marca de aval científico '%s' no detalhe (RN010, RN011, CA012)", proibido)
                    .doesNotContain(proibido);
        }

        System.out.println("[E2.4] varredura do payload (privado + aval científico): sem ocorrência");
    }

    // ---------------------------------------------------------------------- auxiliares

    /** Corpo do 404 sem o {@code instance} — o único campo que pode diferir entre dois 404. */
    private static String semInstance(String corpo) {
        return corpo.replaceFirst("\"instance\":\"[^\"]*\"", "\"instance\":\"\"");
    }

    private static String resumido(String slug) {
        return slug.length() > 40 ? slug.substring(0, 12) + "...(" + slug.length() + " caracteres)" : slug;
    }

    private void limparDadosDeTeste() {
        jdbc.update("DELETE FROM material WHERE slug LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM file_asset WHERE chave_interna LIKE ?", PREFIXO + "%");
        // Apagar a conta remove o perfil junto (ON DELETE CASCADE da V1).
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
    }

    private UUID inserirConta(String sufixo, String estado) {
        return jdbc.queryForObject("""
                INSERT INTO account (email, senha_hash, estado, email_verificado_em)
                VALUES (?, 'hash-de-teste-nao-e-segredo', ?, now())
                RETURNING id
                """, UUID.class, PREFIXO + sufixo + "@example.test", estado);
    }

    private UUID inserirPerfil(UUID conta, String nomePublico, String slug) {
        return jdbc.queryForObject(
                "INSERT INTO profile (account_id, nome_publico, slug) VALUES (?, ?, ?) RETURNING id",
                UUID.class, conta, nomePublico, slug);
    }

    private void inserirMaterial(UUID perfil, String identificador, String licenca, String estadoPublicacao) {
        UUID arquivo = jdbc.queryForObject("""
                INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,
                                        tamanho_bytes, checksum, estado_processamento)
                VALUES (?, 'teste.pdf', 'application/pdf', 1024, repeat('b', 64), 'VALIDADO')
                RETURNING id
                """, UUID.class, PREFIXO + identificador + ".pdf");

        jdbc.update("""
                INSERT INTO material (slug, titulo, descricao, profile_id, category_id, material_type_id,
                                      license_id, file_asset_id, estado_publicacao, publicado_em, atualizado_em)
                SELECT ?, ?, ?, ?,
                       (SELECT id FROM category WHERE slug = ?),
                       (SELECT id FROM material_type WHERE slug = ?),
                       (SELECT id FROM license WHERE slug = ?),
                       ?, ?, ?, ?
                """, PREFIXO + identificador, TITULO, DESCRICAO, perfil, CATEGORIA, TIPO, licenca,
                arquivo, estadoPublicacao, emUtc(PUBLICADO_EM), emUtc(ATUALIZADO_EM));
    }

    /** Texto da licença na própria seed: o que o endpoint publica é o dado do banco, não de tela. */
    private String textoDaDescricaoCurta(String licenca) {
        return jdbc.queryForObject("SELECT descricao_curta FROM license WHERE slug = ?", String.class, licenca);
    }

    private static OffsetDateTime emUtc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static JsonNode json(String corpo) {
        try {
            return JSON.readTree(corpo);
        } catch (Exception e) {
            throw new AssertionError("corpo não é JSON: " + corpo, e);
        }
    }

    private static List<String> camposDe(JsonNode objeto) {
        List<String> campos = new ArrayList<>();
        objeto.fieldNames().forEachRemaining(campos::add);
        return campos;
    }
}
