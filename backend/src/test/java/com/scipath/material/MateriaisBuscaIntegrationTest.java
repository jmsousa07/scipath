package com.scipath.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A busca por texto da listagem pública (RF003, §12.1, ADR-0008), exercitada de verdade:
 * contexto completo, MVC completo e o PostgreSQL local de {@code scipath_test} — com a
 * configuração e o índice criados pela migração {@code V3__busca_texto_material.sql}. Sem mock
 * de repositório: o que o card exige provar é que <b>o banco</b> casa "matemática" e
 * "matematica" no mesmo conjunto, e nenhum mock prova isso.
 *
 * <p><b>Por que uma classe separada da listagem (E2.2).</b> Estes testes precisam de dados com
 * <b>acento no título e na descrição</b>, e as asserções de total da classe E2.2 são exatas
 * (o total do envelope é o da consulta, não o do teste). Semear acento naquele conjunto mudaria
 * a contagem que o outro card asseriu; reaproveitar o arquivo exigiria reescrever os testes
 * dele, o que é mudança não pedida. Duas classes, dois prefixos de dado
 * ({@code teste-e2-2-} e {@code teste-e2-3-}), cada uma limpando o seu antes e depois de cada
 * teste — o banco fica igual ao que estava.
 *
 * <p><b>Sobre o que é asserido aqui.</b> O critério do card é uma igualdade de conjuntos entre
 * o termo acentuado e o não acentuado (RN013). O teste também fixa o que o contrato e o ADR-0008
 * decidem para os casos de borda: termo vazio é listagem padrão, termo sem resultado é 200 com
 * lista vazia, termo só de stopwords é lista vazia, caracteres especiais são texto (nunca
 * operador) e termo acima do teto é 400. Asserção sem caso de borda é asserção que passa em
 * vazio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("busca por texto da listagem pública (RF003, RN013)")
class MateriaisBuscaIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Marcador dos dados desta classe em slug, e-mail e chave de arquivo. */
    private static final String PREFIXO = "teste-e2-3-";

    /** Taxonomia real da seed V2 (ADR-0006). */
    private static final String CATEGORIA = "computacao-e-tecnologia";
    private static final String CATEGORIA_OUTRA = "quimica";
    private static final String TIPO = "nota-de-aula";
    private static final String TIPO_OUTRO = "slides";

    private static final Instant T0 = Instant.parse("2026-10-16T12:00:00Z");

    /**
     * Materiais publicados de conta ativa. Dois deles são o núcleo do critério de aceite: o
     * mesmo tema escrito <b>com</b> e <b>sem</b> acento no título ("Matemática discreta" e
     * "Logica matematica aplicada"). Os outros dois isolam a busca por descrição e servem de
     * controle negativo.
     */
    private static final List<Semeado> PUBLICADOS = List.of(
            new Semeado("matematica-acento", "Matemática discreta",
                    "Contagem e princípios de combinatória.", CATEGORIA, TIPO),
            new Semeado("matematica-sem-acento", "Logica matematica aplicada",
                    "Proposições, conectivos e tabelas verdade.", CATEGORIA, TIPO_OUTRO),
            new Semeado("ordenacao", "Ordenação de vetores",
                    "Algoritmos de ordenação por comparação e complexidade.", CATEGORIA, TIPO),
            new Semeado("quimica", "Ligacoes quimicas",
                    "Ligações iônicas e covalentes.", CATEGORIA_OUTRA, "artigo"));

    /** Slugs que a busca por "matematica" tem de devolver, e nenhum outro. */
    private static final List<String> COM_MATEMATICA =
            List.of(PREFIXO + "matematica-acento", PREFIXO + "matematica-sem-acento");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int porta;

    @BeforeEach
    void semearDadosControlados() {
        limparDadosDeTeste();

        UUID contaAtiva = inserirConta("ativo", "ATIVO");
        UUID contaPendente = inserirConta("pendente", "PENDENTE_VERIFICACAO");
        UUID perfilAtivo = inserirPerfil(contaAtiva, "Ana de Teste", PREFIXO + "ana");
        UUID perfilPendente = inserirPerfil(contaPendente, "Bruno de Teste", PREFIXO + "bruno");

        int i = 0;
        for (Semeado material : PUBLICADOS) {
            inserirMaterial(perfilAtivo, material, "PUBLICADO", T0.minus(i++, ChronoUnit.HOURS));
        }
        // Fora do público, com o termo no título de propósito: se a busca não respeitasse a
        // elegibilidade (RN002, CA008, RN013), estes dois apareceriam no resultado.
        inserirMaterial(perfilAtivo, new Semeado("retirado", "Matemática retirada do ar",
                "Material retirado pelo autor.", CATEGORIA, TIPO), "RETIRADO",
                T0.minus(30, ChronoUnit.MINUTES));
        inserirMaterial(perfilPendente, new Semeado("pendente", "Matemática de conta pendente",
                "Material de conta ainda não verificada.", CATEGORIA, TIPO), "PUBLICADO",
                T0.minus(10, ChronoUnit.MINUTES));
    }

    @AfterEach
    void removerDadosDeTeste() {
        limparDadosDeTeste();
    }

    // ------------------------------------------------------------------ acento e caixa

    @Test
    @DisplayName("termo com e sem acento (e em caixa alta) devolve o MESMO conjunto — RN013")
    void acentoECaixaNaoMudamOConjunto() {
        List<String> comAcento = slugsDe(buscar("q", "matemática"));
        List<String> semAcento = slugsDe(buscar("q", "matematica"));
        List<String> caixaAlta = slugsDe(buscar("q", "MATEMÁTICA"));
        List<String> caixaMista = slugsDe(buscar("q", "Matemática"));

        // Sem estas duas guardas o teste passaria com a busca devolvendo vazio dos dois lados:
        // igualdade de conjunto vazio é igualdade.
        assertThat(comAcento)
                .as("o dado de teste tem de casar: dois materiais sobre matemática")
                .hasSize(2)
                .containsExactlyInAnyOrderElementsOf(COM_MATEMATICA);
        assertThat(semAcento).as("matematica (sem acento)").isEqualTo(comAcento);
        assertThat(caixaAlta).as("MATEMÁTICA (caixa alta)").isEqualTo(comAcento);
        assertThat(caixaMista).as("Matemática (caixa mista)").isEqualTo(comAcento);

        // O que o critério realmente exige: o acento do DADO não separa resultado. Um material
        // tem "Matemática" no título e o outro "matematica"; os dois saem nas duas consultas.
        assertThat(comAcento).contains(PREFIXO + "matematica-acento", PREFIXO + "matematica-sem-acento");

        JsonNode pagina = buscar("q", "matematica");
        assertThat(pagina.get("total").asInt()).as("total é da consulta, não da página").isEqualTo(2);
        assertThat(pagina.get("items").size()).isEqualTo(2);
        // RN013: a busca opera só sobre material elegível para o contexto público.
        assertThat(comAcento)
                .as("material RETIRADO e material de conta não verificada não entram na busca")
                .doesNotContain(PREFIXO + "retirado", PREFIXO + "pendente");

        System.out.println("[E2.3] q=matemática -> " + comAcento);
        System.out.println("[E2.3] q=matematica -> " + semAcento);
        System.out.println("[E2.3] q=MATEMÁTICA -> " + caixaAlta);
    }

    @Test
    @DisplayName("busca alcança título E descrição, e o termo casa também em palavra acentuada da descrição")
    void buscaAlcancaTituloEDescricao() {
        assertThat(slugsDe(buscar("q", "discreta")))
                .as("palavra do título, sem acento no dado")
                .containsExactly(PREFIXO + "matematica-acento");

        assertThat(slugsDe(buscar("q", "combinatória")))
                .as("palavra acentuada da DESCRIÇÃO")
                .containsExactly(PREFIXO + "matematica-acento");
        assertThat(slugsDe(buscar("q", "combinatoria")))
                .as("a mesma palavra da descrição, sem acento na consulta")
                .containsExactly(PREFIXO + "matematica-acento");

        assertThat(slugsDe(buscar("q", "ionicas")))
                .as("descrição com acento ('Ligações iônicas') consultada sem acento")
                .containsExactly(PREFIXO + "quimica");

        assertThat(slugsDe(buscar("q", "ordenacao")))
                .as("título 'Ordenação de vetores' consultado sem acento")
                .containsExactly(PREFIXO + "ordenacao");

        System.out.println("[E2.3] q=combinatoria -> " + slugsDe(buscar("q", "combinatoria")));
        System.out.println("[E2.3] q=ionicas -> " + slugsDe(buscar("q", "ionicas")));
    }

    @Test
    @DisplayName("busca combinada com acento e caixa preserva o resultado e a paginação (RN013)")
    void acentoNaoQuebraPaginacao() {
        List<String> completa = slugsDe(buscar("q", "matemática", "size", "100"));

        List<String> paginado = new ArrayList<>();
        for (int pagina = 0; pagina < 2; pagina++) {
            JsonNode bloco = buscar("q", "matemática", "size", "1", "page", String.valueOf(pagina));
            assertThat(bloco.get("size").asInt()).isEqualTo(1);
            assertThat(bloco.get("total").asInt())
                    .as("total da consulta, igual em toda página")
                    .isEqualTo(2);
            paginado.addAll(slugsDe(bloco));
        }

        assertThat(paginado)
                .as("páginas 0 e 1 de tamanho 1 = lista completa, sem duplicata nem omissão")
                .hasSize(2)
                .doesNotHaveDuplicates()
                .isEqualTo(completa);

        System.out.println("[E2.3] q=matemática página a página (size=1) -> " + paginado);
    }

    // ------------------------------------------------------------------- combinação

    @Test
    @DisplayName("busca combina com os filtros de categoria e tipo em E, e não em OU")
    void buscaCombinaComOsFiltros() {
        assertThat(slugsDe(buscar("q", "matematica", "categoria", CATEGORIA)))
                .as("categoria restringe a busca")
                .containsExactlyInAnyOrderElementsOf(COM_MATEMATICA);

        assertThat(slugsDe(buscar("q", "matematica", "tipo", TIPO_OUTRO)))
                .as("tipo=slides deixa só o material de slides")
                .containsExactly(PREFIXO + "matematica-sem-acento");
        assertThat(slugsDe(buscar("q", "matematica", "tipo", TIPO)))
                .as("tipo=nota-de-aula deixa só a nota de aula")
                .containsExactly(PREFIXO + "matematica-acento");

        JsonNode contraditorio = buscar("q", "matematica", "categoria", CATEGORIA_OUTRA);
        assertThat(contraditorio.get("items").size())
                .as("busca e filtro são combinados em E: material de matemática não está em química")
                .isZero();
        assertThat(contraditorio.get("total").asInt()).isZero();

        assertThat(slugsDe(buscar("q", "ionicas", "categoria", CATEGORIA_OUTRA)))
                .as("a combinação continua funcionando com o termo do outro extremo")
                .containsExactly(PREFIXO + "quimica");

        System.out.println("[E2.3] q=matematica&tipo=" + TIPO_OUTRO + " -> "
                + slugsDe(buscar("q", "matematica", "tipo", TIPO_OUTRO)));
        System.out.println("[E2.3] q=matematica&categoria=" + CATEGORIA_OUTRA + " -> "
                + contraditorio.get("items").size() + " item(ns)");
    }

    // -------------------------------------------------------------- termo vazio e ausente

    @Test
    @DisplayName("q ausente, vazio ou só espaço devolve a listagem padrão — busca vazia não é erro (RF003)")
    void termoVazioOuAusenteNaoFiltra() {
        List<String> semBusca = slugsDe(buscar());
        assertThat(semBusca).as("o acervo publicado do teste").hasSize(PUBLICADOS.size());
        assertThat(semBusca).contains(PREFIXO + "quimica");
        assertThat(semBusca).doesNotContain(PREFIXO + "retirado", PREFIXO + "pendente");

        for (String sufixo : List.of("", "?q=", "?q=%20%20%20")) {
            String caminho = "/api/materiais" + sufixo;
            ResponseEntity<String> resposta = rest.getForEntity(url(caminho), String.class);

            assertThat(resposta.getStatusCode().value())
                    .as("GET %s: termo vazio é ausência de busca, não erro", caminho)
                    .isEqualTo(200);
            assertThat(resposta.getBody()).as("GET %s", caminho).isNotNull();
            assertThat(slugsDe(ler(resposta)))
                    .as("GET %s: a listagem padrão, idêntica à sem busca", caminho)
                    .isEqualTo(semBusca);
            assertThat(resposta.getBody()).contains("\"total\":" + PUBLICADOS.size());

            System.out.println("[E2.3] GET " + caminho + " -> 200 total=" + PUBLICADOS.size());
        }
    }

    // ---------------------------------------------------------------- sem resultado

    @Test
    @DisplayName("termo que não encontra nada é 200 com lista vazia e total 0 — nunca 404")
    void termoSemResultadoResponde200Vazio() {
        for (String termo : List.of("termodinamica estatistica", "matematica astronomica",
                "quimica quantica relativistica")) {
            ResponseEntity<String> resposta = rest.getForEntity(url("/api/materiais?q=" + codificar(termo)),
                    String.class);

            assertThat(resposta.getStatusCode().value()).as("q=%s", termo).isEqualTo(200);
            assertThat(resposta.getHeaders().getContentType())
                    .as("200 de conjunto vazio é resposta normal do endpoint")
                    .isNotNull();
            assertThat(resposta.getBody())
                    .as("envelope vazio, nunca erro nem 404: q=%s", termo)
                    .contains("\"items\":[]")
                    .contains("\"total\":0");

            System.out.println("[E2.3] GET /api/materiais?q=" + termo + " -> 200 " + resposta.getBody());
        }
    }

    @Test
    @DisplayName("termo só de palavras de ligação não tem lexema e devolve lista vazia (consequência registrada no ADR-0008)")
    void termoSoComStopwordsDevolveVazio() {
        // "de" aparece nas descrições semeadas, mas não é lexema: a configuração da busca o
        // remove, o tsquery sai vazio e `@@` com consulta vazia é falso. Está no ADR-0008 como
        // consequência aceita: não há exceção, e o estado "nenhum resultado" já é o que o
        // contrato define (com mensagem clara e ajuste da consulta no frontend, RF003).
        JsonNode pagina = buscar("q", "de");
        assertThat(pagina.get("total").asInt()).isZero();
        assertThat(pagina.get("items").size()).isZero();

        System.out.println("[E2.3] q=de (stopword) -> total=" + pagina.get("total").asInt());
    }

    // ------------------------------------------------------------ caracteres especiais

    @Test
    @DisplayName("caracteres especiais são texto, nunca operador de busca (§12.1)")
    void caracteresEspeciaisSaoTexto() {
        // Se `|` virasse operador, "zzz | matematica" seria OU e devolveria os dois materiais
        // de matemática. É texto: as duas palavras são procuradas juntas e nada casa.
        assertThat(slugsDe(buscar("q", "zzz | matematica")))
                .as("`|` não é OR: a expressão inteira é texto")
                .isEmpty();
        assertThat(slugsDe(buscar("q", "zzz & matematica")))
                .as("`&` não é AND de operador — é texto junto de 'matematica'")
                .isEmpty();
        // Se `!` virasse negação, "!matematica" devolveria tudo MENOS os dois. É texto: o
        // lexema procurado é "matemat", o mesmo de "matematica".
        assertThat(slugsDe(buscar("q", "!matematica")))
                .as("`!` não é negação")
                .containsExactlyInAnyOrderElementsOf(COM_MATEMATICA);

        // Nem parênteses, nem aspas, nem apóstrofo derrubam a consulta — e o termo entra como
        // parâmetro ligado, então nada do que está abaixo é interpretado como SQL.
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM material", Integer.class);
        for (String termo : List.of("ordenação (teste)", "matematica OR quimica", "'; drop table material; --",
                "matematica''")) {
            ResponseEntity<String> resposta = rest.getForEntity(url("/api/materiais?q=" + codificar(termo)),
                    String.class);

            assertThat(resposta.getStatusCode().value()).as("q=%s não pode falhar", termo).isEqualTo(200);
            assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(resposta.getBody()).contains("\"items\":");

            System.out.println("[E2.3] GET /api/materiais?q=" + termo + " -> 200 "
                    + slugsDe(ler(resposta)).size() + " item(ns)");
        }

        // A tabela continua exatamente onde estava: termo com aspas e ponto e vírgula não é SQL.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM material", Integer.class))
                .as("o banco sobrevive ao termo com aspas e ponto e vírgula")
                .isEqualTo(antes);

        System.out.println("[E2.3] q='zzz | matematica' -> " + slugsDe(buscar("q", "zzz | matematica")).size()
                + " item(ns) · q='!matematica' -> " + slugsDe(buscar("q", "!matematica")));
    }

    // ------------------------------------------------------------- termo acima do teto

    @Test
    @DisplayName("q acima do teto de 200 caracteres do contrato responde 400, sem cortar o termo em silêncio")
    void termoAcimaDoTetoResponde400() {
        String longo = "a".repeat(MateriaisController.BUSCA_MAXIMA + 1);
        ResponseEntity<String> resposta = rest.getForEntity(url("/api/materiais?q=" + longo), String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resposta.getBody())
                .contains("\"type\":\"urn:scipath:erro:validacao\"")
                .contains("\"campo\":\"q\"")
                .contains("deve ter " + MateriaisController.BUSCA_MAXIMA + " caracteres ou menos");

        // Exatamente no teto é aceito: o limite do contrato é 200, não 199.
        String noLimite = "a".repeat(MateriaisController.BUSCA_MAXIMA);
        assertThat(rest.getForEntity(url("/api/materiais?q=" + noLimite), String.class).getStatusCode().value())
                .as("termo no limite do contrato é atendido")
                .isEqualTo(200);

        System.out.println("[E2.3] GET /api/materiais?q=<" + longo.length() + " caracteres> -> 400 "
                + resposta.getBody());
    }

    // ---------------------------------------------------------------------- auxiliares

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

    private void inserirMaterial(UUID perfil, Semeado material, String estadoPublicacao, Instant publicadoEm) {
        UUID arquivo = jdbc.queryForObject("""
                INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,
                                        tamanho_bytes, checksum, estado_processamento)
                VALUES (?, 'teste.pdf', 'application/pdf', 1024, repeat('b', 64), 'VALIDADO')
                RETURNING id
                """, UUID.class, PREFIXO + material.slug() + ".pdf");

        jdbc.update("""
                INSERT INTO material (slug, titulo, descricao, profile_id, category_id, material_type_id,
                                      license_id, file_asset_id, estado_publicacao, publicado_em, atualizado_em)
                SELECT ?, ?, ?, ?,
                       (SELECT id FROM category WHERE slug = ?),
                       (SELECT id FROM material_type WHERE slug = ?),
                       (SELECT id FROM license WHERE slug = 'cc-by-4.0'),
                       ?, ?, ?, ?
                """, PREFIXO + material.slug(), material.titulo(), material.descricao(), perfil,
                material.categoria(), material.tipo(), arquivo, estadoPublicacao,
                emUtc(publicadoEm), emUtc(publicadoEm));
    }

    private static OffsetDateTime emUtc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    /** Listagem sem parâmetro nenhum. */
    private JsonNode buscar() {
        return ler(rest.getForEntity(url("/api/materiais"), String.class));
    }

    /** Listagem com pares nome/valor de query, codificados como na URL de um navegador. */
    private JsonNode buscar(String... parametros) {
        assertThat(parametros.length % 2).as("pares nome/valor").isZero();
        StringBuilder caminho = new StringBuilder("/api/materiais");
        for (int i = 0; i < parametros.length; i += 2) {
            caminho.append(i == 0 ? '?' : '&')
                    .append(parametros[i]).append('=').append(codificar(parametros[i + 1]));
        }
        return ler(rest.getForEntity(url(caminho.toString()), String.class));
    }

    /**
     * URL absoluta e já codificada. Passar o caminho como {@code String} faria o cliente
     * interpretá-lo como <i>template</i> de URI e reescapar o que já está escapado (o
     * {@code %} do acento viraria {@code %25} e a busca receberia o texto errado) — por isso o
     * teste monta a URI e usa a porta sorteada do contexto.
     */
    private URI url(String caminho) {
        return URI.create("http://localhost:" + porta + caminho);
    }

    /** Codificação de valor de query: acento virá percentual e espaço vira {@code %20}. */
    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static JsonNode ler(ResponseEntity<String> resposta) {
        assertThat(resposta.getStatusCode().value()).as("resposta do endpoint").isEqualTo(200);
        assertThat(resposta.getHeaders().getContentType())
                .as("resposta de listagem sai como JSON")
                .isNotNull();
        try {
            JsonNode corpo = JSON.readTree(resposta.getBody());
            assertThat(corpo.get("items")).as("envelope Pagina").isNotNull();
            return corpo;
        } catch (Exception e) {
            throw new AssertionError("corpo não é JSON: " + resposta.getBody(), e);
        }
    }

    private static List<String> slugsDe(JsonNode pagina) {
        List<String> slugs = new ArrayList<>();
        pagina.get("items").forEach(item -> slugs.add(item.get("slug").asText()));
        return slugs;
    }

    /** Material controlado desta classe. */
    private record Semeado(String slug, String titulo, String descricao, String categoria, String tipo) {
    }
}
