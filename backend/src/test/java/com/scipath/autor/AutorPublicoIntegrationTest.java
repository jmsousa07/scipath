package com.scipath.autor;

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
import java.util.stream.StreamSupport;
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
 * Perfil público do autor (RF006) exercitado de verdade: contexto completo, MVC completo e o
 * PostgreSQL local de {@code scipath_test}. Sem mock — o que o card exige provar é o que o
 * <b>SQL</b> devolve para o visitante, e como o <b>404</b> sai quando a conta não é ativa.
 *
 * <p><b>Dados controlados.</b> A classe semeia cinco perfis, um por situação que o card
 * distingue:
 *
 * <ul>
 *   <li>{@code ana} (conta ATIVA) — identidade completa, três materiais PUBLICADOS, um
 *       RETIRADO e um EXCLUIDO;</li>
 *   <li>{@code bruno} (conta ATIVA) — nenhum material: é o critério 2 do card (200 com
 *       {@code items: []}, e não 404);</li>
 *   <li>{@code carla} (conta ATIVA) — um material PUBLICADO, que <b>não</b> pode aparecer na
 *       página da ana: a página é do autor, não do acervo;</li>
 *   <li>{@code dora} (conta PENDENTE_VERIFICACAO) e {@code edu} (conta SUSPENSO) — 404
 *       indistinguível de slug inexistente (RN002, CA008).</li>
 * </ul>
 *
 * <p>O banco é limpo antes e depois de cada teste, com o prefixo {@code teste-e2-5-} como
 * marcador: não fica sujo nem depende da ordem das classes da suíte.
 *
 * <p>Mesma configuração de contexto das outras integrações ({@code RANDOM_PORT}): o Spring
 * reusa o contexto já subido e a máquina tem 954 MB (AGENTS.md, higiene de recursos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("perfil público do autor (RF006)")
class AutorPublicoIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Marcador dos dados desta classe em slug, e-mail e chave de arquivo. */
    private static final String PREFIXO = "teste-e2-5-";

    /** Categorias e tipos da seed V2 (ADR-0006): o teste usa as listas de referência reais. */
    private static final String CATEGORIA = "computacao-e-tecnologia";
    private static final String TIPO = "nota-de-aula";
    private static final String LICENCA = "cc-by-4.0";

    private static final String ANA = PREFIXO + "ana";
    private static final String BRUNO = PREFIXO + "bruno";
    private static final String CARLA = PREFIXO + "carla";
    private static final String DORA = PREFIXO + "dora";
    private static final String EDU = PREFIXO + "edu";

    private static final String NOME_ANA = "Ana de Perfil";
    private static final String BIO_ANA = "Professora de programacao; escreve material introdutorio.";
    private static final String AFILIACAO_ANA =
            "Declaro vinculo com o Instituto de Exemplo (declaracao propria do autor).";
    private static final String LINK_ANA = "https://github.com/ana-perfil";
    private static final String AREA_1 = "algoritmos";
    private static final String AREA_2 = "estruturas de dados";

    private static final String TITULO = "Material de perfil";
    private static final String DESCRICAO = "Material de evidencia do E2.5; nao e conteudo do produto.";

    /** Base fixa das datas: não depende do relógio da máquina nem de fuso do ambiente. */
    private static final Instant PUBLICADO_EM = Instant.parse("2026-10-14T15:10:00Z");

    /** Material RETIRADO/EXCLUIDO mais recente que os publicados: se vazasse, viria primeiro. */
    private static final Instant FORA_DO_PUBLICO_EM = Instant.parse("2026-10-16T12:00:00Z");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void semearDadosControlados() {
        limparDadosDeTeste();

        UUID perfilAna = inserirPerfil(inserirConta("ana", "ATIVO"), NOME_ANA, ANA,
                BIO_ANA, AFILIACAO_ANA, AREA_1 + "," + AREA_2, LINK_ANA);
        UUID perfilBruno = inserirPerfil(inserirConta("bruno", "ATIVO"), "Bruno Sem Publicacao", BRUNO,
                null, null, null, null);
        UUID perfilCarla = inserirPerfil(inserirConta("carla", "ATIVO"), "Carla de Outro Autor", CARLA,
                null, null, null, null);
        UUID perfilDora = inserirPerfil(inserirConta("dora", "PENDENTE_VERIFICACAO"), "Dora Nao Verificada", DORA,
                null, null, null, null);
        inserirPerfil(inserirConta("edu", "SUSPENSO"), "Edu Suspenso", EDU, null, null, null, null);

        // Ordens distintas de propósito: a página do autor sai na ordem padrão do contrato (§12.2).
        inserirMaterial(perfilAna, "ana-material-1", "PUBLICADO", PUBLICADO_EM);
        inserirMaterial(perfilAna, "ana-material-2", "PUBLICADO", PUBLICADO_EM.minusSeconds(86400));
        inserirMaterial(perfilAna, "ana-material-3", "PUBLICADO", PUBLICADO_EM.minusSeconds(2 * 86400));
        inserirMaterial(perfilAna, "ana-retirado", "RETIRADO", FORA_DO_PUBLICO_EM);
        inserirMaterial(perfilAna, "ana-excluido", "EXCLUIDO", FORA_DO_PUBLICO_EM);
        inserirMaterial(perfilCarla, "carla-material-1", "PUBLICADO", FORA_DO_PUBLICO_EM);
        inserirMaterial(perfilDora, "dora-material-1", "PUBLICADO", FORA_DO_PUBLICO_EM);

        assertThat(perfilBruno).as("perfil sem material é o que o critério 2 exige").isNotNull();
    }

    @AfterEach
    void removerDadosDeTeste() {
        limparDadosDeTeste();
    }

    // --------------------------------------------------- critério 1: identidade + materiais

    @Test
    @DisplayName("perfil de conta ativa responde 200 com o schema AutorPublico, a identidade completa e a página dos materiais do autor")
    void perfilPublicoResponde200ComIdentidadeEMateriais() {
        ResponseEntity<String> resposta = rest.getForEntity("/api/autores/" + ANA, String.class);

        assertThat(resposta.getStatusCode().value()).as("perfil de conta ativa é público").isEqualTo(200);
        assertThat(resposta.getHeaders().getContentType())
                .as("resposta do perfil sai como JSON")
                .isNotNull()
                .satisfies(tipo -> assertThat(tipo.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());

        JsonNode corpo = json(resposta.getBody());
        assertThat(camposDe(corpo))
                .as("exatamente o schema AutorPublico do contrato v0.3, nem um campo a mais")
                .containsExactlyInAnyOrder("slug", "nomePublico", "biografia", "afiliacaoDeclarada",
                        "areasInteresse", "linksExternos", "materiais");

        // Identidade (RF006, §11.1.2). O nome do campo de afiliação já diz o que o dado é.
        assertThat(corpo.get("slug").asText()).isEqualTo(ANA);
        assertThat(corpo.get("nomePublico").asText()).isEqualTo(NOME_ANA);
        assertThat(corpo.get("biografia").asText()).isEqualTo(BIO_ANA);
        assertThat(corpo.get("afiliacaoDeclarada").asText())
                .as("afiliação DECLARADA pelo autor, nunca vínculo verificado (RN011, §13.1)")
                .isEqualTo(AFILIACAO_ANA);
        assertThat(textos(corpo.get("areasInteresse"))).containsExactly(AREA_1, AREA_2);
        assertThat(textos(corpo.get("linksExternos"))).containsExactly(LINK_ANA);

        // Envelope padrão (RNF021): o mesmo das outras listagens, e só ele.
        JsonNode materiais = corpo.get("materiais");
        assertThat(camposDe(materiais))
                .as("envelope de paginação do contrato (schema Pagina), nada além dos quatro campos")
                .containsExactlyInAnyOrder("items", "page", "size", "total");
        assertThat(materiais.get("page").asInt()).isZero();
        assertThat(materiais.get("size").asInt()).isEqualTo(20);
        assertThat(materiais.get("total").asLong())
                .as("só os três PUBLICADOS entram na contagem (RN002)")
                .isEqualTo(3);

        // Ordem padrão do contrato: publicação decrescente (§12.2). O RETIRADO, que é o mais
        // recente, não aparece — se aparecesse, seria o primeiro da lista.
        JsonNode itens = materiais.get("items");
        assertThat(itens).hasSize(3);
        assertThat(textos(itens, "slug")).containsExactly(
                ANA + "-material-1", ANA + "-material-2", ANA + "-material-3");

        // Item de material: o MESMO schema MaterialResumo da listagem (inclusive a licença da
        // listagem, sem o identificador e a URL que só o detalhe publica).
        JsonNode item = itens.get(0);
        assertThat(camposDe(item)).containsExactlyInAnyOrder("slug", "titulo", "descricao", "autor",
                "categoria", "tipo", "licenca", "publicadoEm");
        assertThat(item.get("titulo").asText()).isEqualTo(TITULO);
        assertThat(item.get("descricao").asText()).isEqualTo(DESCRICAO);
        assertThat(camposDe(item.get("autor"))).containsExactlyInAnyOrder("slug", "nomePublico");
        assertThat(item.get("autor").get("slug").asText().equals(ANA)).isTrue();
        assertThat(item.get("autor").get("nomePublico").asText()).isEqualTo(NOME_ANA);
        assertThat(camposDe(item.get("categoria"))).containsExactlyInAnyOrder("slug", "nome");
        assertThat(item.get("categoria").get("slug").asText()).isEqualTo(CATEGORIA);
        assertThat(camposDe(item.get("tipo"))).containsExactlyInAnyOrder("slug", "nome");
        assertThat(camposDe(item.get("licenca"))).containsExactlyInAnyOrder("slug", "nome", "descricaoCurta");
        assertThat(item.get("licenca").get("slug").asText()).isEqualTo(LICENCA);
        assertThat(item.get("publicadoEm").asText()).isEqualTo("2026-10-14T15:10:00Z");

        System.out.println("[E2.5] GET /api/autores/" + ANA + " -> 200 " + corpo);
    }

    @Test
    @DisplayName("perfil sem material publicado responde 200 com items vazio e total 0 (não 404)")
    void autorSemMaterialResponde200ComListaVazia() {
        ResponseEntity<String> resposta = rest.getForEntity("/api/autores/" + BRUNO, String.class);

        assertThat(resposta.getStatusCode().value())
                .as("ausência de material não é perfil inexistente (card E2.5, critério 2)")
                .isEqualTo(200);

        JsonNode corpo = json(resposta.getBody());
        assertThat(corpo.get("slug").asText()).isEqualTo(BRUNO);
        assertThat(corpo.get("nomePublico").asText()).isEqualTo("Bruno Sem Publicacao");
        assertThat(camposDe(corpo.get("materiais")))
                .as("o envelope continua o mesmo, mesmo vazio")
                .containsExactlyInAnyOrder("items", "page", "size", "total");
        assertThat(corpo.get("materiais").get("items")).isEmpty();
        assertThat(corpo.get("materiais").get("total").asLong()).isZero();
        assertThat(corpo.get("materiais").get("page").asInt()).isZero();
        assertThat(corpo.get("materiais").get("size").asInt()).isEqualTo(20);

        // Autor sem bio, sem áreas e sem links: os dois campos obrigatórios saem como lista
        // vazia e os dois opcionais NÃO saem (§13.2) — ausente é informação.
        assertThat(corpo.has("biografia")).as("biografia não informada não vira campo vazio").isFalse();
        assertThat(corpo.has("afiliacaoDeclarada")).isFalse();
        assertThat(corpo.get("areasInteresse")).isEmpty();
        assertThat(corpo.get("linksExternos")).isEmpty();

        System.out.println("[E2.5] GET /api/autores/" + BRUNO + " -> 200 " + corpo);
    }

    @Test
    @DisplayName("a página do autor traz só os materiais públicos DELE: nem retirado, nem excluído, nem de outro autor")
    void paginaTrazSoOsMateriaisPublicosDoAutor() {
        JsonNode corpo = json(rest.getForEntity("/api/autores/" + ANA, String.class).getBody());

        List<String> slugs = textos(corpo.get("materiais").get("items"), "slug");
        assertThat(slugs).containsExactly(ANA + "-material-1", ANA + "-material-2", ANA + "-material-3");
        assertThat(slugs)
                .as("material fora do público não aparece, mesmo sendo do autor (RN002)")
                .doesNotContain(ANA + "-retirado", ANA + "-excluido")
                .as("material de outro autor não aparece (a página é do autor, não do acervo)")
                .doesNotContain(CARLA + "-material-1", DORA + "-material-1");

        // O material fora do público existe na tabela: o que o exclui é a elegibilidade, não a ausência.
        Long existem = jdbc.queryForObject(
                "SELECT count(*) FROM material WHERE slug IN (?, ?)",
                Long.class, ANA + "-retirado", ANA + "-excluido");
        assertThat(existem).as("retirado e excluído existem no banco").isEqualTo(2L);

        System.out.println("[E2.5] materiais publicados devolvidos em /api/autores/" + ANA + ": " + slugs);
    }

    @Test
    @DisplayName("page e size paginam os materiais do autor, e a identidade vem completa em qualquer página")
    void paginacaoDoPerfilPaginaSoOsMateriais() {
        JsonNode primeira = json(rest.getForEntity(
                "/api/autores/" + ANA + "?page=0&size=2", String.class).getBody());
        JsonNode segunda = json(rest.getForEntity(
                "/api/autores/" + ANA + "?page=1&size=2", String.class).getBody());

        assertThat(textos(primeira.get("materiais").get("items"), "slug"))
                .containsExactly(ANA + "-material-1", ANA + "-material-2");
        assertThat(textos(segunda.get("materiais").get("items"), "slug"))
                .as("a segunda página não repete a primeira nem omite o terceiro item (RN013)")
                .containsExactly(ANA + "-material-3");
        assertThat(segunda.get("materiais").get("total").asLong()).isEqualTo(3);
        assertThat(segunda.get("materiais").get("page").asInt()).isEqualTo(1);
        assertThat(segunda.get("materiais").get("size").asInt()).isEqualTo(2);

        // A identidade não é paginada: vem inteira em qualquer página.
        for (JsonNode pagina : List.of(primeira, segunda)) {
            assertThat(pagina.get("nomePublico").asText()).isEqualTo(NOME_ANA);
            assertThat(pagina.get("biografia").asText()).isEqualTo(BIO_ANA);
            assertThat(pagina.get("afiliacaoDeclarada").asText()).isEqualTo(AFILIACAO_ANA);
            assertThat(textos(pagina.get("areasInteresse"))).containsExactly(AREA_1, AREA_2);
        }

        System.out.println("[E2.5] paginacao size=2 -> page 0: "
                + textos(primeira.get("materiais").get("items"), "slug") + " · page 1: "
                + textos(segunda.get("materiais").get("items"), "slug") + " · total 3");
    }

    // ------------------------------------------ critério 3: navegação material → autor → categoria

    @Test
    @DisplayName("navegação RN013: o slug de categoria da página do autor é o mesmo que a listagem aceita como filtro, e o material volta para o autor")
    void navegacaoMaterialAutorCategoriaUsaOsMesmosSlugs() {
        JsonNode perfil = json(rest.getForEntity("/api/autores/" + ANA, String.class).getBody());
        JsonNode material = perfil.get("materiais").get("items").get(0);

        String autorSlug = material.get("autor").get("slug").asText();
        String categoriaSlug = material.get("categoria").get("slug").asText();
        String materialSlug = material.get("slug").asText();

        assertThat(autorSlug).as("material → autor: o slug é o da rota do perfil").isEqualTo(ANA);
        assertThat(rest.getForEntity("/api/autores/" + autorSlug, String.class).getStatusCode().value())
                .as("o slug devolvido no material é uma rota de perfil que responde (RF007)")
                .isEqualTo(200);

        // material → categoria: o slug é aceito como filtro pela listagem, sem 400, e devolve
        // o material com o MESMO slug de categoria — a consistência que a RN013 exige.
        ResponseEntity<String> listagem = rest.getForEntity(
                "/api/materiais?categoria=" + categoriaSlug + "&size=100", String.class);
        assertThat(listagem.getStatusCode().value())
                .as("o slug de categoria do material é um filtro válido para a listagem (RN013)")
                .isEqualTo(200);

        JsonNode filtrados = json(listagem.getBody()).get("items");
        JsonNode proprio = StreamSupport.stream(filtrados.spliterator(), false)
                .filter(item -> item.get("slug").asText().equals(materialSlug))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "o material " + materialSlug + " não aparece na listagem filtrada por " + categoriaSlug));
        assertThat(proprio.get("categoria").get("slug").asText()).isEqualTo(categoriaSlug);
        assertThat(proprio.get("autor").get("slug").asText()).isEqualTo(autorSlug);

        // categoria → material: a terceira perna, o detalhe, devolve o mesmo autor pública e não
        // um id de conta (é o que faz o link "voltar ao autor" funcionar sem segundo mapeamento).
        JsonNode detalhe = json(rest.getForEntity("/api/materiais/" + materialSlug, String.class).getBody());
        assertThat(detalhe.get("autor").get("slug").asText()).isEqualTo(autorSlug);
        assertThat(detalhe.get("categoria").get("slug").asText()).isEqualTo(categoriaSlug);

        System.out.println("[E2.5] navegacao: perfil " + autorSlug + " -> material " + materialSlug
                + " -> categoria " + categoriaSlug + " -> listagem com "
                + json(listagem.getBody()).get("total").asLong() + " material(is), o mesmo slug aceito como filtro");
    }

    // ----------------------------------------- critério 2: conta não ativa e inexistente → 404

    @Test
    @DisplayName("perfil de conta pendente, de conta suspensa e slug inexistente respondem 404 indistinguível")
    void perfilForaDoPublicoResponde404Indistinguivel() {
        Map<String, String> enderecos = new LinkedHashMap<>();
        enderecos.put("conta pendente de verificacao", DORA);
        enderecos.put("conta suspensa", EDU);
        enderecos.put("inexistente", PREFIXO + "nunca-existiu");

        Map<String, String> corposNormalizados = new LinkedHashMap<>();
        for (Map.Entry<String, String> endereco : enderecos.entrySet()) {
            ResponseEntity<String> resposta = rest.getForEntity("/api/autores/" + endereco.getValue(), String.class);

            assertThat(resposta.getStatusCode().value())
                    .as("perfil %s -> 404 (RN002, CA008)", endereco.getKey())
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
                    .contains("\"instance\":\"/api/autores/" + endereco.getValue() + "\"")
                    .doesNotContain("\"errors\"")
                    .as("nenhum rastro de quem é o autor nem do motivo de o perfil não ser público")
                    .doesNotContain("Dora", "Edu", PREFIXO + "dora@example.test", PREFIXO + "edu@example.test");

            corposNormalizados.put(endereco.getKey(), semInstance(corpo));
            System.out.println("[E2.5] GET /api/autores/" + endereco.getValue() + " -> 404 " + corpo);
        }

        // A prova de indistinguibilidade: os três corpos são IGUAIS fora o endereço pedido. Um
        // detalhe diferente para "conta suspensa" e "inexistente" revelaria que o perfil existe.
        List<String> distintos = new ArrayList<>(new LinkedHashSet<>(corposNormalizados.values()));
        assertThat(distintos)
                .as("os três 404 têm de ser o mesmo corpo, tirando o instance: %s", corposNormalizados)
                .hasSize(1);
    }

    @Test
    @DisplayName("slug fora do formato, page negativo e size fora de 1..100 respondem 400 no ProblemDetail padrão")
    void entradaInvalidaResponde400() {
        Map<String, String[]> casos = new LinkedHashMap<>();
        casos.put("/api/autores/Maria-Silva", new String[]{"slug", "deve ser um slug"});
        casos.put("/api/autores/maria_silva", new String[]{"slug", "deve ser um slug"});
        casos.put("/api/autores/maria--silva", new String[]{"slug", "deve ser um slug"});
        casos.put("/api/autores/" + "a".repeat(121), new String[]{"slug", "deve ter no máximo 120 caracteres"});
        casos.put("/api/autores/" + ANA + "?page=-1", new String[]{"page", "deve ser 0 ou mais"});
        casos.put("/api/autores/" + ANA + "?size=0", new String[]{"size", "deve ser 1 ou mais"});
        casos.put("/api/autores/" + ANA + "?size=101", new String[]{"size", "deve ser 100 ou menos"});

        for (Map.Entry<String, String[]> caso : casos.entrySet()) {
            ResponseEntity<String> resposta = rest.getForEntity(caso.getKey(), String.class);

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
                    .contains("\"campo\":\"" + caso.getValue()[0] + "\"")
                    .contains(caso.getValue()[1]);

            System.out.println("[E2.5] GET " + resumido(caso.getKey()) + " -> 400 " + resposta.getBody());
        }
    }

    // ------------------------------------------------- critério 1: ausência de dado privado

    @Test
    @DisplayName("o payload do perfil não carrega e-mail, hash, token nem id de conta, e nenhuma marca de verificação (RNF013, RN011, CA012)")
    void payloadNaoTrazDadoPrivadoNemMarcaDeVerificacao() {
        String corpo = rest.getForEntity("/api/autores/" + ANA, String.class).getBody();

        // Ausência, não só presença: o payload inteiro é varrido. Nada de e-mail, hash de senha,
        // token, checksum, chave de storage, id de conta ou id interno de tabela.
        for (String proibido : List.of("email", "senha", "hash", "token", "checksum", "chave", "account",
                "profile_id", "profileId", "file_asset", "fileAsset", "\"id\"")) {
            assertThat(corpo)
                    .as("campo privado '%s' no perfil público (RNF013)", proibido)
                    .doesNotContain(proibido);
        }

        // Nem campo, nem VALOR: o e-mail de login do autor não existe em nenhuma forma na resposta.
        assertThat(corpo)
                .as("o e-mail de login do autor não pode aparecer em nenhuma forma (RNF013)")
                .doesNotContain(PREFIXO + "ana@example.test")
                .doesNotContain("@example.test");

        // A varredura de aval científico não se aplica ao perfil do jeito que se aplica ao
        // material, mas o perfil é tão público quanto: nada de campo ou rótulo que sugira
        // revisão, curadoria, certificação ou verificação institucional (RN011, CA012). A
        // afiliação sai como DECLARAÇÃO, e o teste de dados acima garante que o texto de teste
        // não contém nenhum desses termos por acidente.
        for (String proibido : List.of("revis", "curad", "certific", "verifica", "verificado",
                "selo", "aprovad", "parecer", "peer", "avaliad", "instituicao verificada")) {
            assertThat(corpo.toLowerCase())
                    .as("marca de aval ou de vínculo verificado '%s' no perfil (RN011, CA012)", proibido)
                    .doesNotContain(proibido);
        }

        System.out.println("[E2.5] varredura do payload do perfil (privado + aval/verificacao): sem ocorrencia");
    }

    // ---------------------------------------------------------------------- auxiliares

    /** Corpo do 404 sem o {@code instance} — o único campo que pode diferir entre dois 404. */
    private static String semInstance(String corpo) {
        return corpo.replaceFirst("\"instance\":\"[^\"]*\"", "\"instance\":\"\"");
    }

    private static String resumido(String caminho) {
        return caminho.length() > 60 ? caminho.substring(0, 40) + "...(" + caminho.length() + " caracteres)" : caminho;
    }

    /** {@code areasInteresse}/{@code linksExternos}: os dois só têm valores de texto. */
    private static List<String> textos(JsonNode array) {
        List<String> valores = new ArrayList<>();
        array.forEach(no -> valores.add(no.asText()));
        return valores;
    }

    /** O campo {@code campo} de cada item de uma lista de objetos. */
    private static List<String> textos(JsonNode lista, String campo) {
        List<String> valores = new ArrayList<>();
        lista.forEach(item -> valores.add(item.get(campo).asText()));
        return valores;
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

    /**
     * Perfil do autor. As duas colunas {@code text[]} são montadas por {@code string_to_array}:
     * os valores de teste não contêm vírgula, e escrever o array assim evita depender da
     * conversão de {@code String[]} do driver. Coluna sem valor fica {@code NULL} — é o caso do
     * autor que não informou nada.
     */
    private UUID inserirPerfil(UUID conta, String nomePublico, String slug, String biografia,
            String afiliacao, String areas, String links) {
        return jdbc.queryForObject("""
                INSERT INTO profile (account_id, nome_publico, slug, biografia, afiliacao_declarada,
                                     areas_interesse, links_externos)
                VALUES (?, ?, ?, ?, ?, string_to_array(?, ','), string_to_array(?, ','))
                RETURNING id
                """, UUID.class, conta, nomePublico, slug, biografia, afiliacao, areas, links);
    }

    /** Material do autor com o arquivo e a taxonomia da seed; atualização igual à publicação. */
    private void inserirMaterial(UUID perfil, String identificador, String estadoPublicacao, Instant publicadoEm) {
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
                """, PREFIXO + identificador, TITULO, DESCRICAO, perfil, CATEGORIA, TIPO, LICENCA,
                arquivo, estadoPublicacao, emUtc(publicadoEm), emUtc(publicadoEm));
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
