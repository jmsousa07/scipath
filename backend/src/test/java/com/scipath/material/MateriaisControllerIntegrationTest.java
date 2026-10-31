package com.scipath.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
 * A listagem pública de material (RF002, RF004), exercitada de verdade: contexto completo, MVC
 * completo e o PostgreSQL local de {@code scipath_test}. Sem mock de repositório — o que o card
 * exige provar é a <b>ordem</b> e a <b>paginação</b> que saem do SQL, e mock nenhum prova isso.
 *
 * <p><b>Dados controlados.</b> A classe semeia sete materiais publicados (um deles de outra
 * categoria/tipo, para o filtro discriminar, e um com atualização distinta da publicação) e três
 * que <b>não</b> podem aparecer: RETIRADO, EXCLUIDO e material de conta ainda não verificada
 * (RN002, CA008). O banco é limpo antes e depois de cada teste, com o prefixo
 * {@code teste-e2-2-} como marcador: ele não fica sujo nem depende da ordem das classes da suíte.
 *
 * <p><b>Por que o total exato é uma asserção legítima.</b> A tabela {@code material} do
 * {@code scipath_test} é populada só pelos próprios testes: a V2 semeia taxonomia, não material, e
 * a {@code MigracaoIntegracaoTest} reverte o que grava. Se sobrar material de outra origem, este
 * teste falha — o que é desejado, porque a contagem do envelope tem de ser a do banco.
 *
 * <p>Mesma configuração de contexto das outras integrações ({@code RANDOM_PORT}): o Spring reusa o
 * contexto já subido e a máquina tem 954 MB (AGENTS.md, higiene de recursos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("listagem pública de materiais (RF002, RF004)")
class MateriaisControllerIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Marcador dos dados desta classe em slug, e-mail e chave de arquivo. */
    private static final String PREFIXO = "teste-e2-2-";

    /** Categorias e tipos da seed V2 (ADR-0006): o teste usa as listas de referência reais. */
    private static final String CATEGORIA = "computacao-e-tecnologia";
    private static final String CATEGORIA_OUTRA = "matematica-e-estatistica";
    private static final String TIPO = "nota-de-aula";
    private static final String TIPO_OUTRO = "slides";

    /** Base fixa das datas: não depende do relógio da máquina nem de fuso do ambiente. */
    private static final Instant T0 = Instant.parse("2026-10-16T12:00:00Z");

    /** Instante compartilhado por dois materiais publicados — força o desempate por id. */
    private static final Instant EMPATE = T0.minus(6, ChronoUnit.HOURS);

    private static final List<Semeado> PUBLICADOS = List.of(
            new Semeado("pub-01", "Alfa ordenacao por insercao", T0.minus(1, ChronoUnit.HOURS), null, CATEGORIA, TIPO),
            new Semeado("pub-02", "Beta complexidade assintotica", T0.minus(2, ChronoUnit.HOURS), null, CATEGORIA, TIPO),
            new Semeado("pub-03", "Gama arvores balanceadas", T0.minus(3, ChronoUnit.HOURS), null, CATEGORIA, TIPO),
            new Semeado("pub-04", "Delta filas de prioridade", T0.minus(4, ChronoUnit.HOURS), null, CATEGORIA, TIPO),
            // Estes dois compartilham o instante: sem desempate por id a ordem entre eles seria a
            // que o PostgreSQL quisesse, e a paginação poderia repetir ou perder um dos dois.
            new Semeado("pub-05", "Eta busca binaria", EMPATE, null, CATEGORIA, TIPO),
            new Semeado("pub-06", "Zeta grafos e caminhos", EMPATE, null, CATEGORIA, TIPO),
            // Único de outra categoria/tipo, e único com atualização distinta da publicação.
            new Semeado("pub-07", "Zulu compressao de dados", T0.minus(7, ChronoUnit.HOURS),
                    T0.minus(5, ChronoUnit.HOURS), CATEGORIA_OUTRA, TIPO_OUTRO));

    /**
     * Títulos que <b>separam</b> a collation do banco da ordem de ponto de código (ADR-0010) — e é
     * a única razão de existirem. Os títulos de {@link #PUBLICADOS} não servem para isso: todos
     * diferem na primeira letra, onde {@code en_US.UTF-8} e {@code C} concordam, então ordem de
     * ponto de código e ordem da collation são a mesma lista e o teste passa mesmo sem o
     * {@code COLLATE "C"} (defeito de cobertura do E1.13-f1).
     *
     * <p>Estes exercitam as duas divergências reais:
     * <ul>
     *   <li><b>espaço/pontuação:</b> a collation {@code en_US.UTF-8} ignora espaço no primeiro
     *       nível e põe {@code "CC0 1.0 (dominio publico)"} antes de {@code "CC BY 4.0"}; a ordem
     *       de ponto de código compara {@code " "} (0x20) com {@code "0"} (0x30) e põe
     *       {@code "CC BY 4.0"} primeiro.</li>
     *   <li><b>caixa:</b> {@code en_US.UTF-8} ordena maiúscula e minúscula no mesmo nível, então
     *       {@code "eclipse..."} sobe na frente de {@code "Eta..."}; em ponto de código toda
     *       maiúscula (0x41–0x5A) vem antes de qualquer minúscula (0x61–0x7A).</li>
     * </ul>
     */
    private static final List<Semeado> TITULOS_SENSIVEIS_A_COLLATION = List.of(
            new Semeado("col-01", "CC BY 4.0", T0.minus(11, ChronoUnit.HOURS), null, CATEGORIA, TIPO),
            new Semeado("col-02", "CC0 1.0 (dominio publico)", T0.minus(12, ChronoUnit.HOURS), null,
                    CATEGORIA, TIPO),
            new Semeado("col-03", "eclipse de teste", T0.minus(13, ChronoUnit.HOURS), null, CATEGORIA, TIPO));

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void semearDadosControlados() {
        limparDadosDeTeste();

        UUID contaAtiva = inserirConta("ativo", "ATIVO");
        UUID contaPendente = inserirConta("pendente", "PENDENTE_VERIFICACAO");
        UUID perfilAtivo = inserirPerfil(contaAtiva, "Ana de Teste", PREFIXO + "ana");
        UUID perfilPendente = inserirPerfil(contaPendente, "Bruno de Teste", PREFIXO + "bruno");

        for (Semeado material : PUBLICADOS) {
            inserirMaterial(perfilAtivo, material, "PUBLICADO");
        }
        inserirMaterial(perfilAtivo,
                new Semeado("retirado-01", "Retirado de teste", T0.minus(30, ChronoUnit.MINUTES), null,
                        CATEGORIA, TIPO), "RETIRADO");
        inserirMaterial(perfilAtivo,
                new Semeado("excluido-01", "Excluido de teste", T0.minus(20, ChronoUnit.MINUTES), null,
                        CATEGORIA, TIPO), "EXCLUIDO");
        inserirMaterial(perfilPendente,
                new Semeado("pendente-01", "De conta pendente", T0.minus(10, ChronoUnit.MINUTES), null,
                        CATEGORIA, TIPO), "PUBLICADO");
    }

    @AfterEach
    void removerDadosDeTeste() {
        limparDadosDeTeste();
    }

    // ------------------------------------------------------------------- elegibilidade

    @Test
    @DisplayName("só material PUBLICADO de conta ATIVA aparece; RETIRADO, EXCLUIDO e conta não verificada ficam fora")
    void soMaterialPublicadoDeContaAtiva() {
        JsonNode pagina = buscar("/api/materiais");

        assertThat(pagina.get("total").asInt())
                .as("total do envelope é o da consulta, não o tamanho da página")
                .isEqualTo(PUBLICADOS.size());
        assertThat(pagina.get("items").size()).isEqualTo(PUBLICADOS.size());
        assertThat(pagina.get("page").asInt()).isZero();
        assertThat(pagina.get("size").asInt()).isEqualTo(20);

        List<String> slugs = slugsDe(pagina);
        assertThat(slugs).containsExactlyInAnyOrderElementsOf(slugsEsperados(PUBLICADOS));
        assertThat(slugs)
                .as("RN002/CA008: material fora do público não aparece")
                .doesNotContain(PREFIXO + "retirado-01", PREFIXO + "excluido-01", PREFIXO + "pendente-01");

        System.out.println("[E2.2] GET /api/materiais -> total=" + pagina.get("total").asInt() + " slugs=" + slugs);
    }

    // -------------------------------------------------------------------- ordenação

    @Test
    @DisplayName("ordem padrão (recentes) é publicado_em decrescente com desempate por id, e nenhuma página duplica ou omite item")
    void ordemRecentesDeterministicaEPaginacaoSemDuplicataNemOmissao() {
        // O empate é real, não presumido: os dois materiais têm o MESMO instante de publicação.
        Integer instantesDistintos = jdbc.queryForObject(
                "SELECT count(DISTINCT publicado_em) FROM material WHERE slug IN (?, ?)",
                Integer.class, PREFIXO + "pub-05", PREFIXO + "pub-06");
        assertThat(instantesDistintos)
                .as("o teste precisa de empate de data para exercitar o desempate por id")
                .isEqualTo(1);

        List<String> esperado = ordemRecentesEsperada();

        JsonNode completa = buscar("/api/materiais?size=100");
        assertThat(slugsDe(completa)).as("ordem padrão do contrato").isEqualTo(esperado);

        JsonNode denovo = buscar("/api/materiais?size=100");
        assertThat(slugsDe(denovo)).as("duas chamadas seguidas devolvem a mesma ordem").isEqualTo(esperado);

        // Paginar em blocos de 3 tem de reproduzir a lista inteira, na mesma ordem: é a prova de
        // que a ordem total é estável (RN013) — sem ela, um item apareceria em duas páginas ou
        // não apareceria em nenhuma.
        List<String> paginado = new ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            JsonNode bloco = buscar("/api/materiais?size=3&page=" + pagina);
            assertThat(bloco.get("page").asInt()).isEqualTo(pagina);
            assertThat(bloco.get("size").asInt()).isEqualTo(3);
            assertThat(bloco.get("total").asInt()).as("total é da consulta, igual em toda página").isEqualTo(7);
            paginado.addAll(slugsDe(bloco));
        }
        assertThat(paginado).as("páginas 0,1,2 de tamanho 3").hasSize(7).doesNotHaveDuplicates();
        assertThat(paginado).as("a fatia tem de ser a mesma lista").isEqualTo(esperado);

        System.out.println("[E2.2] ordem recentes -> " + esperado);
        System.out.println("[E2.2] página a página (size=3) -> " + paginado);
    }

    @Test
    @DisplayName("ordem=titulo ordena por título crescente e também não duplica nem omite item entre páginas")
    void ordemPorTituloDeterministicaEPaginacaoSemDuplicataNemOmissao() {
        List<String> esperado = PUBLICADOS.stream()
                .sorted(Comparator.comparing(Semeado::titulo))
                .map(semeado -> PREFIXO + semeado.slug())
                .toList();

        JsonNode completa = buscar("/api/materiais?ordem=titulo&size=100");
        assertThat(slugsDe(completa)).isEqualTo(esperado);
        assertThat(titulosDe(completa)).as("títulos em ordem crescente").isSorted().doesNotHaveDuplicates();

        List<String> paginado = new ArrayList<>();
        for (int pagina = 0; pagina < 4; pagina++) {
            paginado.addAll(slugsDe(buscar("/api/materiais?ordem=titulo&size=2&page=" + pagina)));
        }
        assertThat(paginado).as("páginas 0..3 de tamanho 2").hasSize(7).doesNotHaveDuplicates().isEqualTo(esperado);

        System.out.println("[E2.2] ordem titulo -> " + titulosDe(completa));
    }

    @Test
    @DisplayName("ordem=titulo é ordem de PONTO DE CÓDIGO: espaço/pontuação e caixa decidem, não a collation do banco")
    void ordemPorTituloSeguePontoDeCodigoENaoACollationDoBanco() {
        // Semeia, além dos sete comuns, os títulos que a collation do banco reordenaria. Fica
        // dentro deste teste (e não em PUBLICADOS) para não deslocar as contagens exatas dos
        // outros testes da classe, que contam sete materiais publicados.
        UUID perfil = jdbc.queryForObject("SELECT id FROM profile WHERE slug = ?", UUID.class, PREFIXO + "ana");
        for (Semeado material : TITULOS_SENSIVEIS_A_COLLATION) {
            inserirMaterial(perfil, material, "PUBLICADO");
        }

        List<String> esperado = new ArrayList<>();
        PUBLICADOS.forEach(material -> esperado.add(material.titulo()));
        TITULOS_SENSIVEIS_A_COLLATION.forEach(material -> esperado.add(material.titulo()));
        esperado.sort(Comparator.naturalOrder()); // String.compareTo = ponto de código

        JsonNode completa = buscar("/api/materiais?ordem=titulo&size=100");
        List<String> titulos = titulosDe(completa);

        assertThat(titulos)
                .as("ordem=titulo é a de ponto de código (COLLATE \"C\", ADR-0010), não a da collation do banco")
                .isEqualTo(esperado);
        assertThat(completa.get("total").asInt()).isEqualTo(esperado.size());
        assertThat(titulos)
                .as("espaço (0x20) antes de '0' (0x30): en_US.UTF-8 inverteria os dois")
                .containsSubsequence("CC BY 4.0", "CC0 1.0 (dominio publico)");
        assertThat(titulos)
                .as("maiúscula (0x41–0x5A) antes de minúscula (0x61–0x7A): en_US.UTF-8 inverteria os dois")
                .containsSubsequence("Eta busca binaria", "eclipse de teste");

        // Mesma lista página a página: o COLLATE não pode custar o determinismo da ordem (RN013).
        List<String> paginado = new ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            paginado.addAll(slugsDe(buscar("/api/materiais?ordem=titulo&size=4&page=" + pagina)));
        }
        assertThat(paginado).as("páginas 0..2 de tamanho 4").hasSize(esperado.size()).doesNotHaveDuplicates();
        assertThat(titulosDe(completa)).as("a fatia tem de ser a mesma lista").isEqualTo(esperado);

        // Poder de mutação declarado, não presumido: a consulta SEM collate explícito, no banco
        // desta execução, tem mesmo de dar lista diferente — senão este teste não distingue a
        // linha do COLLATE de lugar nenhum. Em banco C.UTF-8/C as duas ordens coincidem (é o
        // caso da VPS) e a checagem se cala de propósito, em vez de reprovar por ambiente.
        String datcollate = jdbc.queryForObject(
                "SELECT datcollate FROM pg_database WHERE datname = current_database()", String.class);
        System.out.println("[E2.2] datcollate do banco de teste -> " + datcollate);
        if (datcollate != null && !datcollate.strip().toUpperCase(java.util.Locale.ROOT).startsWith("C")) {
            List<String> semCollate = jdbc.queryForList("""
                    SELECT m.titulo FROM material m
                    JOIN profile p ON p.id = m.profile_id
                    JOIN account a ON a.id = p.account_id
                    WHERE m.slug LIKE ? AND m.estado_publicacao = 'PUBLICADO' AND a.estado = 'ATIVO'
                    ORDER BY m.titulo ASC, m.id DESC
                    """, String.class, PREFIXO + "%");
            assertThat(semCollate)
                    .as("com datcollate=%s a ordem sem COLLATE \"C\" difere da de ponto de código — "
                            + "é isso que dá poder de mutação a este teste; se coincidirem, o teste "
                            + "não está exercitando a cláusula", datcollate)
                    .isNotEqualTo(titulos);
            System.out.println("[E2.2] ordem sem COLLATE (collation do banco) -> " + semCollate);
        }

        System.out.println("[E2.2] ordem titulo (ponto de código) -> " + titulos);
    }

    // ---------------------------------------------------------------------- filtros

    @Test
    @DisplayName("filtro por categoria e por tipo recorta o conjunto, e os dois combinam em E")
    void filtrosPorCategoriaETipo() {
        JsonNode porCategoria = buscar("/api/materiais?categoria=" + CATEGORIA_OUTRA);
        assertThat(slugsDe(porCategoria)).containsExactly(PREFIXO + "pub-07");
        assertThat(porCategoria.get("total").asInt()).isEqualTo(1);
        assertThat(porCategoria.get("items").get(0).get("categoria").get("slug").asText()).isEqualTo(CATEGORIA_OUTRA);

        JsonNode porTipo = buscar("/api/materiais?tipo=" + TIPO);
        assertThat(slugsDe(porTipo)).hasSize(6).doesNotContain(PREFIXO + "pub-07");
        assertThat(valoresDe(porTipo, "tipo", "slug"))
                .allSatisfy(slugDoTipo -> assertThat(slugDoTipo).isEqualTo(TIPO));

        JsonNode combinado = buscar("/api/materiais?categoria=" + CATEGORIA_OUTRA + "&tipo=" + TIPO_OUTRO);
        assertThat(slugsDe(combinado)).containsExactly(PREFIXO + "pub-07");

        JsonNode contraditorio = buscar("/api/materiais?categoria=" + CATEGORIA_OUTRA + "&tipo=" + TIPO);
        assertThat(contraditorio.get("items").size()).as("categoria e tipo são combinados em E").isZero();

        System.out.println("[E2.2] GET /api/materiais?categoria=" + CATEGORIA_OUTRA + " -> " + slugsDe(porCategoria));
        System.out.println("[E2.2] GET /api/materiais?tipo=" + TIPO + " -> total=" + porTipo.get("total").asInt());
    }

    @Test
    @DisplayName("slug bem formado e inexistente responde 200 com items: [] — vazio não é erro (RNF004)")
    void filtroSemResultadoResponde200Vazio() {
        for (String caminho : List.of("/api/materiais?categoria=area-que-nao-existe",
                "/api/materiais?tipo=tipo-que-nao-existe",
                "/api/materiais?categoria=area-que-nao-existe&tipo=tipo-que-nao-existe")) {
            ResponseEntity<String> resposta = rest.getForEntity(caminho, String.class);

            assertThat(resposta.getStatusCode().value()).as("GET %s", caminho).isEqualTo(200);
            assertThat(resposta.getHeaders().getContentType())
                    .as("200 de conjunto vazio é resposta normal do endpoint")
                    .isNotNull();
            assertThat(resposta.getBody())
                    .as("envelope vazio, nunca erro: %s", caminho)
                    .contains("\"items\":[]")
                    .contains("\"total\":0");

            System.out.println("[E2.2] GET " + caminho + " -> 200 " + resposta.getBody());
        }
    }

    // ---------------------------------------------------------------- parâmetro inválido

    @Test
    @DisplayName("parâmetro inválido responde 400 no ProblemDetail padrão, com o parâmetro culpado em errors[]")
    void parametroInvalidoResponde400() {
        Map<String, String[]> casos = new LinkedHashMap<>();
        casos.put("?ordem=relevancia", new String[]{"ordem", "deve ser 'recentes' ou 'titulo'"});
        casos.put("?ordem=TITULO", new String[]{"ordem", "deve ser 'recentes' ou 'titulo'"});
        casos.put("?categoria=Computacao!e%20tecnologia", new String[]{"categoria", "deve ser um slug de categoria"});
        casos.put("?tipo=Nota%20de%20Aula", new String[]{"tipo", "deve ser um slug de tipo de material"});
        casos.put("?categoria=", new String[]{"categoria", "deve ser um slug de categoria"});
        casos.put("?size=1000", new String[]{"size", "deve ser 100 ou menos"});
        casos.put("?page=-1", new String[]{"page", "deve ser 0 ou mais"});

        for (Map.Entry<String, String[]> caso : casos.entrySet()) {
            String caminho = "/api/materiais" + caso.getKey();
            String campo = caso.getValue()[0];
            ResponseEntity<String> resposta = rest.getForEntity(caminho, String.class);

            assertThat(resposta.getStatusCode().value()).as("GET %s", caminho).isEqualTo(400);
            assertThat(resposta.getHeaders().getContentType())
                    .as("erro de campo sai no formato único do contrato")
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(resposta.getBody())
                    .contains("\"type\":\"urn:scipath:erro:validacao\"")
                    .contains("\"title\":\"Requisição inválida\"")
                    .contains("\"status\":400")
                    .contains("\"instance\":\"/api/materiais\"")
                    .contains("\"campo\":\"" + campo + "\"")
                    .contains(caso.getValue()[1]);

            System.out.println("[E2.2] GET " + caminho + " -> 400 " + resposta.getBody());
        }
    }

    // ------------------------------------------------------- forma do payload (contrato)

    @Test
    @DisplayName("o item publica exatamente o schema MaterialResumo, com licença explicada e sem campo privado (RNF013)")
    void itemPublicaOSchemaEMnadaPrivado() {
        ResponseEntity<String> resposta = rest.getForEntity("/api/materiais?size=100", String.class);
        JsonNode pagina = ler(resposta);

        JsonNode semAtualizacao = itemComSlug(pagina, PREFIXO + "pub-01");
        assertThat(camposDe(semAtualizacao))
                .as("atualizadoEm é ausente quando a atualização coincide com a publicação")
                .containsExactlyInAnyOrder("slug", "titulo", "descricao", "autor", "categoria", "tipo",
                        "licenca", "publicadoEm");
        assertThat(semAtualizacao.get("titulo").asText()).isEqualTo("Alfa ordenacao por insercao");
        assertThat(semAtualizacao.get("publicadoEm").asText())
                .as("date-time do contrato")
                .isEqualTo("2026-10-16T11:00:00Z");

        JsonNode comAtualizacao = itemComSlug(pagina, PREFIXO + "pub-07");
        assertThat(camposDe(comAtualizacao))
                .as("atualizadoEm aparece quando difere da publicação")
                .containsExactlyInAnyOrder("slug", "titulo", "descricao", "autor", "categoria", "tipo",
                        "licenca", "publicadoEm", "atualizadoEm");
        assertThat(comAtualizacao.get("atualizadoEm").asText()).isEqualTo("2026-10-16T07:00:00Z");

        assertThat(camposDe(semAtualizacao.get("autor"))).containsExactlyInAnyOrder("slug", "nomePublico");
        assertThat(semAtualizacao.get("autor").get("nomePublico").asText()).isEqualTo("Ana de Teste");
        assertThat(semAtualizacao.get("autor").get("slug").asText()).isEqualTo(PREFIXO + "ana");
        assertThat(camposDe(semAtualizacao.get("categoria"))).containsExactlyInAnyOrder("slug", "nome");
        assertThat(camposDe(semAtualizacao.get("tipo"))).containsExactlyInAnyOrder("slug", "nome");
        assertThat(camposDe(semAtualizacao.get("licenca")))
                .containsExactlyInAnyOrder("slug", "nome", "descricaoCurta");
        assertThat(semAtualizacao.get("licenca").get("descricaoCurta").asText())
                .as("RN006: a licença explica o que permite, em linguagem simples")
                .isNotBlank();

        // Ausência dos dados privados, e não só presença dos públicos: o payload inteiro é
        // varrido. Nada de e-mail, hash de senha, token, chave de arquivo ou id interno.
        String corpo = resposta.getBody();
        for (String proibido : List.of("email", "senha", "hash", "token", "checksum", "chave",
                "account", "profile_id", "profileId", "file_asset", "fileAsset", "\"id\"")) {
            assertThat(corpo).as("campo privado '%s' na resposta pública (RNF013)", proibido).doesNotContain(proibido);
        }

        System.out.println("[E2.2] item do contrato -> " + semAtualizacao);
        System.out.println("[E2.2] item com atualizadoEm -> " + comAtualizacao);
    }

    // ---------------------------------------------------------------------- auxiliares

    /** Ordem esperada de {@code recentes}: data decrescente, empate resolvido por id no banco. */
    private List<String> ordemRecentesEsperada() {
        Map<Instant, List<String>> porInstante = new LinkedHashMap<>();
        for (Semeado material : PUBLICADOS) {
            porInstante.computeIfAbsent(material.publicadoEm(), instante -> new ArrayList<>())
                    .add(PREFIXO + material.slug());
        }

        List<String> esperado = new ArrayList<>();
        List<Instant> instantes = new ArrayList<>(porInstante.keySet());
        instantes.sort(Comparator.reverseOrder());
        for (Instant instante : instantes) {
            List<String> bloco = porInstante.get(instante);
            if (bloco.size() > 1) {
                // O bloco empatado é ordenado pelo próprio banco, na regra do contrato (id) — e o
                // endpoint é comparado com essa regra, não com a ordem de inserção das linhas.
                String marcadores = String.join(", ", bloco.stream().map(slug -> "?").toList());
                bloco = jdbc.queryForList(
                        "SELECT slug FROM material WHERE slug IN (" + marcadores + ") ORDER BY id DESC",
                        String.class, bloco.toArray());
            }
            esperado.addAll(bloco);
        }
        return esperado;
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

    private void inserirMaterial(UUID perfil, Semeado material, String estadoPublicacao) {
        UUID arquivo = jdbc.queryForObject("""
                INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,
                                        tamanho_bytes, checksum, estado_processamento)
                VALUES (?, 'teste.pdf', 'application/pdf', 1024, repeat('b', 64), 'VALIDADO')
                RETURNING id
                """, UUID.class, PREFIXO + material.slug() + ".pdf");

        Instant atualizadoEm = material.atualizadoEm() == null ? material.publicadoEm() : material.atualizadoEm();
        jdbc.update("""
                INSERT INTO material (slug, titulo, descricao, profile_id, category_id, material_type_id,
                                      license_id, file_asset_id, estado_publicacao, publicado_em, atualizado_em)
                SELECT ?, ?, ?, ?,
                       (SELECT id FROM category WHERE slug = ?),
                       (SELECT id FROM material_type WHERE slug = ?),
                       (SELECT id FROM license WHERE slug = 'cc-by-4.0'),
                       ?, ?, ?, ?
                """, PREFIXO + material.slug(), material.titulo(), "Descrição de teste de " + material.titulo(),
                perfil, material.categoria(), material.tipo(), arquivo, estadoPublicacao,
                emUtc(material.publicadoEm()), emUtc(atualizadoEm));
    }

    private static OffsetDateTime emUtc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private JsonNode buscar(String caminho) {
        return ler(rest.getForEntity(caminho, String.class));
    }

    private JsonNode ler(ResponseEntity<String> resposta) {
        assertThat(resposta.getStatusCode().value()).as("resposta do endpoint").isEqualTo(200);
        assertThat(resposta.getHeaders().getContentType())
                .as("resposta de listagem sai como JSON")
                .isNotNull();
        assertThat(resposta.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode corpo = textoParaJson(resposta.getBody());
        assertThat(corpo.get("items")).as("envelope Pagina").isNotNull();
        return corpo;
    }

    private static JsonNode textoParaJson(String corpo) {
        try {
            return JSON.readTree(corpo);
        } catch (Exception e) {
            throw new AssertionError("corpo não é JSON: " + corpo, e);
        }
    }

    private static List<String> slugsDe(JsonNode pagina) {
        return valoresDe(pagina, "slug");
    }

    private static List<String> titulosDe(JsonNode pagina) {
        return valoresDe(pagina, "titulo");
    }

    private static List<String> valoresDe(JsonNode pagina, String... caminho) {
        List<String> valores = new ArrayList<>();
        pagina.get("items").forEach(item -> {
            JsonNode no = item;
            for (String campo : caminho) {
                no = no.get(campo);
            }
            valores.add(no.asText());
        });
        return valores;
    }

    private static List<String> slugsEsperados(List<Semeado> materiais) {
        return materiais.stream().map(material -> PREFIXO + material.slug()).toList();
    }

    private static List<String> camposDe(JsonNode objeto) {
        List<String> campos = new ArrayList<>();
        objeto.fieldNames().forEachRemaining(campos::add);
        return campos;
    }

    private static JsonNode itemComSlug(JsonNode pagina, String slug) {
        for (JsonNode item : pagina.get("items")) {
            if (slug.equals(item.get("slug").asText())) {
                return item;
            }
        }
        throw new AssertionError("slug '" + slug + "' não está na página: " + pagina);
    }

    /** Material controlado desta classe; {@code null} em {@code atualizadoEm} = igual à publicação. */
    private record Semeado(String slug, String titulo, Instant publicadoEm, Instant atualizadoEm,
            String categoria, String tipo) {
    }
}
