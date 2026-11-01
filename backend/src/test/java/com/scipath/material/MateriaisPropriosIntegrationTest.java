package com.scipath.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scipath.arquivo.PropriedadesDeArmazenamento;
import com.scipath.arquivo.StorageService;
import com.scipath.identidade.CookiesDeSessao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Os materiais do próprio dono (RF015, contrato v0.6, tag {@code gerenciamento}), exercitados de
 * verdade: contexto completo, MVC completo (o 401 do interceptor, o 400 do {@code ProblemDetail} e o
 * 404 do handler — não um corpo montado à mão) e o PostgreSQL local de {@code scipath_test}. Sem mock
 * de repositório: o que o card exige provar é <b>de quem</b> é a linha que o SQL devolve, a
 * <b>ordem</b> da lista e o que a <b>sessão</b> autoriza — e mock nenhum prova isso.
 *
 * <p><b>Dados controlados, com dois donos.</b> A classe cria as contas <b>A</b> e <b>B</b> pelo
 * cadastro de verdade ({@code POST /api/contas}), ativa as duas por SQL — o que se mede aqui é a
 * leitura, não o fluxo de confirmação de e-mail (E3.4) — e autentica cada uma ({@code POST
 * /api/sessoes}), porque é a cookie de sessão que a fronteira inteira desta superfície usa: sem
 * sessão real, o 401 e a propriedade "vem da sessão" não seriam medidos. A tem doze materiais — um
 * em cada estado, um publicado com o registro do arquivo já {@code PURGADO}, e <b>oito no mesmo
 * {@code atualizado_em}</b>, que é o que dá conteúdo ao desempate —; B tem um, e é ele que prova que
 * a lista de A não contém material de B (e vice-versa). Tudo é limpo antes e depois de cada teste,
 * com o prefixo {@code teste-e5-2-} como marcador.
 *
 * <p><b>Por que os 404 são comparados por igualdade, e não por status.</b> O card pede o 404 de
 * material alheio <i>indistinguível</i> do de material inexistente (RN002, CA007, ADR-0011 §3).
 * Comparar só o status deixaria passar um detalhe diferente para "não é seu" e "não existe", que é
 * justamente o que revela a existência do material. Os corpos são comparados <b>byte a byte</b>,
 * normalizando apenas o {@code instance} — o endereço pedido, que é o único campo que pode diferir
 * entre duas requisições a caminhos diferentes (mesma decisão do teste do detalhe público, E2.4).
 *
 * <p>Mesma configuração de contexto das outras integrações ({@code RANDOM_PORT}): o Spring reusa o
 * contexto já subido e a máquina tem 954 MB (AGENTS.md, higiene de recursos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("materiais próprios do dono (RF015, contrato v0.6)")
class MateriaisPropriosIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Marcador dos dados desta classe em slug, e-mail e chave de arquivo. */
    private static final String PREFIXO = "teste-e5-2-";

    private static final String DOMINIO = "@example.test";
    private static final String SENHA = "materiais-proprios-2026";

    /** A rota da lista; o detalhe é ela mais o {@code /{slug}} (contrato v0.6). */
    private static final String ROTA = "/api/perfil/atual/materiais";

    /** A rota pública correspondente — usada para contrastar o 404 e a visibilidade (RN002). */
    private static final String ROTA_PUBLICA = "/api/materiais";

    /** Taxonomia da seed V2 (ADR-0006): o teste usa as listas de referência reais. */
    private static final String CATEGORIA = "computacao-e-tecnologia";
    private static final String TIPO = "nota-de-aula";
    private static final String LICENCA = "cc-by-4.0";

    /** Base fixa das datas: não depende do relógio da máquina nem de fuso do ambiente. */
    private static final Instant PUBLICADO_EM = Instant.parse("2026-10-01T10:00:00Z");

    /** Instante do material mais recente; os demais recuam em horas a partir dele. */
    private static final Instant MAIS_RECENTE_EM = Instant.parse("2026-10-20T12:00:00Z");

    /**
     * Quantos materiais de A compartilham o MESMO {@code atualizado_em} — o empate que o desempate
     * por {@code id} existe para resolver.
     *
     * <p>São oito, e o número não é decorativo: com poucos empatados, a ordem que o PostgreSQL
     * devolve sem desempate pode <b>coincidir</b> com a ordem por {@code id} (o {@code id} é um
     * UUID, então a coincidência é sorte), e a suíte ficaria verde com o desempate removido — o
     * teste de mutação provou exatamente isso com três. Com oito, a chance de a ordem "sem
     * desempate" cair justamente na ordem por {@code id} é 1/8!, e o que o teste exige passa a ser a
     * afirmação do contrato: o bloco empatado sai na ordem dos {@code id}.
     */
    private static final int EMPATES = 8;

    /** Quantos materiais de A têm {@code atualizado_em} distinto (ordem determinística só pela data). */
    private static final int DISTINTOS = 4;

    /**
     * Os materiais de A, na ordem em que a lista do dono os publica — {@code atualizadoEm} DESC, com
     * desempate por {@code id} ASC.
     *
     * <p>Os {@value #EMPATES} últimos compartilham o instante de propósito: sem desempate, a ordem
     * entre eles seria a que o PostgreSQL quisesse, e a promessa do contrato ("paginar não repete nem
     * omite item", RNF021) não teria como valer. Dois deles publicam {@code disponivel: false} por
     * motivos <b>diferentes</b> — registro {@code PURGADO} sem binário no disco, e registro
     * {@code PURGADO} com o binário ainda lá —, porque a resposta tem de ser a mesma nos dois casos e
     * por nenhum dos dois revelar o motivo (RF014, RNF014).
     */
    private static final List<Semeado> MATERIAIS_DE_A = materiaisDeA();

    private static List<Semeado> materiaisDeA() {
        List<Semeado> lista = new ArrayList<>(List.of(
                new Semeado("a-publicado", "PUBLICADO", 0, "VALIDADO", true),
                new Semeado("a-retirado", "RETIRADO", 1, "VALIDADO", true),
                new Semeado("a-excluido", "EXCLUIDO", 2, "PURGADO", true),
                new Semeado("a-publicado-purgado", "PUBLICADO", 3, "PURGADO", false)));
        for (int i = 1; i <= EMPATES; i++) {
            lista.add(new Semeado("a-empate-" + i, "PUBLICADO", 4, "VALIDADO", true));
        }
        return List.copyOf(lista);
    }

    /** O único material de B: é ele que a lista de A não pode conter. */
    private static final Semeado MATERIAL_DE_B = new Semeado("b-unico", "PUBLICADO", 5, "VALIDADO", true);

    /** Conteúdo semeado: assinatura de PDF + corpo determinístico (a leitura não abre o conteúdo). */
    private static final byte[] CONTEUDO = pdfComCorpo(1024);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PropriedadesDeArmazenamento propriedades;

    @Autowired
    private StorageService armazenamento;

    /** Origem dos binários: o armazenamento é alimentado pela porta, não pelo teste. */
    @TempDir
    Path areaDeApoio;

    private Sessao sessaoDeA;
    private Sessao sessaoDeB;

    @BeforeEach
    void semearDadosControlados() {
        limparDadosDeTeste();

        sessaoDeA = cadastrarElogar("a", "Ana do Painel");
        sessaoDeB = cadastrarElogar("b", "Bruno do Painel");

        UUID perfilDeA = perfilId(emailDe("a"));
        UUID perfilDeB = perfilId(emailDe("b"));

        for (Semeado material : MATERIAIS_DE_A) {
            inserirMaterial(perfilDeA, material);
        }
        inserirMaterial(perfilDeB, MATERIAL_DE_B);
    }

    @AfterEach
    void removerDadosDeTeste() {
        limparDadosDeTeste();
    }

    // ------------------------------------------------------- critério 1: a lista do dono

    @Test
    @DisplayName("a lista traz os três estados, na ordem atualizadoEm DESC com desempate por id, e exatamente o schema MaterialProprio")
    void listaDoDonoTrazOsTresEstadosNaOrdemDoContrato() {
        ResponseEntity<String> resposta = ler(sessaoDeA, ROTA + "?size=100");

        assertThat(resposta.getStatusCode().value()).as("lista do dono com sessão").isEqualTo(200);
        assertThat(resposta.getHeaders().getContentType())
                .as("resposta da lista sai como JSON")
                .isNotNull()
                .satisfies(tipo -> assertThat(tipo.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());

        JsonNode pagina = json(resposta.getBody());
        assertThat(camposDe(pagina))
                .as("exatamente o envelope Pagina do contrato, nem um campo a mais")
                .containsExactlyInAnyOrder("items", "page", "size", "total");
        assertThat(pagina.get("page").asInt()).isEqualTo(0);
        assertThat(pagina.get("size").asInt()).isEqualTo(100);
        assertThat(pagina.get("total").asLong())
                .as("o total é o da consulta do dono: todos os materiais de A, em qualquer estado")
                .isEqualTo(MATERIAIS_DE_A.size());
        assertThat(pagina.get("items")).hasSize(MATERIAIS_DE_A.size());

        List<String> slugs = slugsDe(pagina);
        List<String> distintos = slugs.subList(0, DISTINTOS);
        List<String> empatados = slugs.subList(DISTINTOS, slugs.size());
        assertThat(distintos)
                .as("ordem da tela de gerenciamento: o que mexi por último primeiro (ADR-0011 §2)")
                .containsExactly(PREFIXO + "a-publicado", PREFIXO + "a-retirado", PREFIXO + "a-excluido",
                        PREFIXO + "a-publicado-purgado");
        assertThat(new LinkedHashSet<>(slugs))
                .as("a ordem total não repete linha — todo material de A aparece uma vez")
                .hasSize(MATERIAIS_DE_A.size());

        // O DESEMPATE, afirmado diretamente: os empatados saem na ordem dos `id`, que é a única
        // autoridade sobre o que o contrato chama de "desempate por id crescente". Sem este
        // confronto, uma lista sem desempate passaria: o PostgreSQL devolveria os empatados numa
        // ordem qualquer, e a comparação da lista com as páginas (que saem da MESMA consulta)
        // continuaria verde — foi o que o teste de mutação do card mostrou com três empatados.
        List<String> empatesPorId = jdbc.queryForList("""
                SELECT m.slug FROM material m
                JOIN profile p ON p.id = m.profile_id
                JOIN account a ON a.id = p.account_id
                WHERE a.email = ? AND m.atualizado_em = ?
                ORDER BY m.id ASC
                """, String.class, emailDe("a"), emUtc(MAIS_RECENTE_EM.minus(4, ChronoUnit.HOURS)));
        assertThat(empatesPorId).as("o fixture tem de produzir o empate").hasSize(EMPATES);
        assertThat(empatados)
                .as("os empatados saem na ordem dos `id` (desempate por id crescente, contrato v0.6)")
                .containsExactlyElementsOf(empatesPorId);

        // Os três estados aparecem: é a visão do DONO, não a do público (RF015, ADR-0011 §2). O
        // conjunto distinto é o que importa — PUBLICADO aparece mais de uma vez, e isso é correto.
        assertThat(new LinkedHashSet<>(estadosDe(pagina)))
                .as("PUBLICADO, RETIRADO e EXCLUIDO na mesma lista — o filtro do público (RN002) não vale aqui")
                .containsExactlyInAnyOrder("PUBLICADO", "RETIRADO", "EXCLUIDO");

        // O item é EXATAMENTE o schema MaterialProprio do contrato — sem autor, sem id.
        JsonNode publicado = itemComSlug(pagina, PREFIXO + "a-publicado");
        assertThat(camposDe(publicado))
                .as("exatamente o schema MaterialProprio do contrato v0.6, nem um campo a mais")
                .containsExactlyInAnyOrder("slug", "titulo", "descricao", "categoria", "tipo", "licenca",
                        "arquivo", "estado", "publicadoEm", "atualizadoEm");
        assertThat(camposDe(publicado.get("categoria"))).containsExactlyInAnyOrder("slug", "nome");
        assertThat(camposDe(publicado.get("tipo"))).containsExactlyInAnyOrder("slug", "nome");
        assertThat(camposDe(publicado.get("licenca")))
                .as("a licença continua explicada na visão do dono (RN006)")
                .containsExactlyInAnyOrder("slug", "nome", "descricaoCurta");
        assertThat(publicado.get("categoria").get("slug").asText()).isEqualTo(CATEGORIA);
        assertThat(publicado.get("tipo").get("slug").asText()).isEqualTo(TIPO);
        assertThat(publicado.get("licenca").get("slug").asText()).isEqualTo(LICENCA);
        assertThat(publicado.get("licenca").get("descricaoCurta").asText()).isNotBlank();

        // atualizadoEm é campo required do schema: sai sempre, mesmo quando coincide com a publicação.
        assertThat(publicado.get("publicadoEm").asText()).as("date-time do contrato").isEqualTo("2026-10-01T10:00:00Z");
        assertThat(publicado.get("atualizadoEm").asText()).isEqualTo("2026-10-20T12:00:00Z");

        // Arquivo: metadado do recebimento + disponibilidade decidida no servidor (E4.9, RF014).
        assertThat(camposDe(publicado.get("arquivo")))
                .as("exatamente o schema ArquivoMaterial do contrato")
                .containsExactlyInAnyOrder("nomeOriginal", "tamanhoBytes", "disponivel");
        assertThat(publicado.get("arquivo").get("nomeOriginal").asText())
                .isEqualTo(PREFIXO + "a-publicado.pdf");
        assertThat(publicado.get("arquivo").get("tamanhoBytes").asLong()).isEqualTo(CONTEUDO.length);
        assertThat(publicado.get("arquivo").get("disponivel").asBoolean())
                .as("registro VALIDADO e binário no armazenamento: pode ser servido agora")
                .isTrue();

        System.out.println("[E5.2] GET " + ROTA + "?size=100 (A) -> 200 " + resposta.getBody());
    }

    // ------------------------------------- critério 1 (continuação): vazio, donos e paginação

    @Test
    @DisplayName("dono sem material responde 200 com items: [] — vazio não é erro (RNF004)")
    void donoSemMaterialResponde200ComItemsVazio() {
        Sessao semMateriais = cadastrarElogar("c", "Carla do Painel");

        ResponseEntity<String> resposta = ler(semMateriais, ROTA);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        JsonNode pagina = json(resposta.getBody());
        assertThat(camposDe(pagina)).containsExactlyInAnyOrder("items", "page", "size", "total");
        assertThat(pagina.get("items")).as("lista vazia, não 404").isEmpty();
        assertThat(pagina.get("total").asLong()).isZero();
        assertThat(pagina.get("page").asInt()).isZero();
        assertThat(pagina.get("size").asInt()).as("padrão do contrato quando size não é informado").isEqualTo(20);

        System.out.println("[E5.2] GET " + ROTA + " (dono sem material) -> 200 " + resposta.getBody());
    }

    @Test
    @DisplayName("a lista de A não contém material de B, e a de B não contém material de A (RN001, RNF010)")
    void listaDeANaoContemMaterialDeB() {
        JsonNode paginaDeA = json(ler(sessaoDeA, ROTA + "?size=100").getBody());
        JsonNode paginaDeB = json(ler(sessaoDeB, ROTA + "?size=100").getBody());

        assertThat(slugsDe(paginaDeA))
                .as("só o que é do perfil da sessão A")
                .allMatch(slug -> slug.startsWith(PREFIXO + "a-"))
                .doesNotContain(PREFIXO + MATERIAL_DE_B.identificador());
        assertThat(paginaDeA.get("total").asLong()).isEqualTo(MATERIAIS_DE_A.size());

        assertThat(slugsDe(paginaDeB))
                .as("B vê o material dele, e só ele")
                .containsExactly(PREFIXO + MATERIAL_DE_B.identificador());
        assertThat(paginaDeB.get("total").asLong()).isEqualTo(1);

        System.out.println("[E5.2] A vê " + slugsDe(paginaDeA));
        System.out.println("[E5.2] B vê " + slugsDe(paginaDeB));
    }

    @Test
    @DisplayName("paginar não repete nem omite item, inclusive com oito materiais no mesmo atualizadoEm (RN013, RNF021)")
    void paginacaoNaoRepeteNemOmiteItem() {
        // A ordem de referência é a da lista completa; as páginas têm de reproduzi-la, na ordem.
        JsonNode completa = json(ler(sessaoDeA, ROTA + "?size=100").getBody());

        List<String> acumulado = new ArrayList<>();
        int tamanhoDaPagina = 2;
        int paginas = 0;
        for (int pagina = 0; pagina * tamanhoDaPagina < MATERIAIS_DE_A.size(); pagina++) {
            JsonNode corpo = json(ler(sessaoDeA, ROTA + "?page=" + pagina + "&size=" + tamanhoDaPagina).getBody());
            assertThat(corpo.get("page").asInt()).isEqualTo(pagina);
            assertThat(corpo.get("size").asInt()).isEqualTo(tamanhoDaPagina);
            assertThat(corpo.get("total").asLong())
                    .as("o total não depende da página pedida")
                    .isEqualTo(MATERIAIS_DE_A.size());
            acumulado.addAll(slugsDe(corpo));
            paginas++;
        }

        assertThat(paginas)
                .as("%d materiais em páginas de 2 dão %d páginas", MATERIAIS_DE_A.size(),
                        MATERIAIS_DE_A.size() / tamanhoDaPagina)
                .isEqualTo(MATERIAIS_DE_A.size() / tamanhoDaPagina);
        assertThat(acumulado)
                .as("a união das páginas é a lista completa, na mesma ordem — sem repetir nem omitir")
                .containsExactlyElementsOf(slugsDe(completa));
        assertThat(new LinkedHashSet<>(acumulado))
                .as("nenhum item aparece em duas páginas")
                .hasSize(MATERIAIS_DE_A.size());

        System.out.println("[E5.2] páginas de 2 -> " + acumulado + " (igual à lista completa)");
    }

    // ------------------------------------------------- critério 1: leitura de um próprio

    @Test
    @DisplayName("a leitura de um material próprio vale nos três estados, e o detalhe público continua escondendo o que saiu do público")
    void leituraDoProprioValeNosTresEstados() {
        Map<String, String> estadoPorSlug = new LinkedHashMap<>();
        estadoPorSlug.put("a-publicado", "PUBLICADO");
        estadoPorSlug.put("a-retirado", "RETIRADO");
        estadoPorSlug.put("a-excluido", "EXCLUIDO");

        for (Map.Entry<String, String> caso : estadoPorSlug.entrySet()) {
            String slug = PREFIXO + caso.getKey();
            ResponseEntity<String> resposta = ler(sessaoDeA, ROTA + "/" + slug);

            assertThat(resposta.getStatusCode().value())
                    .as("o dono lê o próprio material em %s (RF015, ADR-0011 §2)", caso.getValue())
                    .isEqualTo(200);

            JsonNode material = json(resposta.getBody());
            assertThat(camposDe(material))
                    .as("o detalhe do dono é o MESMO schema do item da lista")
                    .containsExactlyInAnyOrder("slug", "titulo", "descricao", "categoria", "tipo", "licenca",
                            "arquivo", "estado", "publicadoEm", "atualizadoEm");
            assertThat(material.get("slug").asText()).isEqualTo(slug);
            assertThat(material.get("estado").asText()).isEqualTo(caso.getValue());
            assertThat(material.toString())
                    .as("sem autor: é sempre o dono (RNF013)")
                    .doesNotContain("\"autor\"");

            System.out.println("[E5.2] GET " + ROTA + "/" + slug + " -> 200 " + resposta.getBody());
        }

        // O contraste que dá sentido à rota: o MESMO slug responde 404 no detalhe público.
        assertThat(rest.getForEntity(ROTA_PUBLICA + "/" + PREFIXO + "a-retirado", String.class).getStatusCode().value())
                .as("material retirado não é público (RN002) — é o dono que precisa da leitura")
                .isEqualTo(404);
        assertThat(rest.getForEntity(ROTA_PUBLICA + "/" + PREFIXO + "a-excluido", String.class).getStatusCode().value())
                .isEqualTo(404);
    }

    // ------------------------------- critério 1: 404 indistinguível (material alheio × inexistente)

    @Test
    @DisplayName("o slug de B responde, para A, um 404 idêntico ao de slug inexistente — byte a byte fora o instance (RN002, CA007)")
    void materialAlheioResponde404IdenticoASlugInexistente() {
        String alheio = ROTA + "/" + PREFIXO + MATERIAL_DE_B.identificador();
        String inexistente = ROTA + "/" + PREFIXO + "nunca-existiu";

        ResponseEntity<String> respostaAlheio = ler(sessaoDeA, alheio);
        ResponseEntity<String> respostaInexistente = ler(sessaoDeA, inexistente);
        // O 404 público do MESMO material do dono em estado fora do público (a-retirado): é a
        // referência de "404 do contrato" contra a qual a leitura do dono tem de coincidir.
        ResponseEntity<String> respostaPublica = rest.getForEntity(
                ROTA_PUBLICA + "/" + PREFIXO + "a-retirado", String.class);

        for (Map.Entry<String, ResponseEntity<String>> caso : Map.of(
                "material de B", respostaAlheio,
                "slug inexistente", respostaInexistente,
                "404 público de material retirado", respostaPublica).entrySet()) {
            assertThat(caso.getValue().getStatusCode().value())
                    .as("%s -> 404", caso.getKey())
                    .isEqualTo(404);
            assertThat(caso.getValue().getHeaders().getContentType())
                    .as("erro sai no formato único do contrato (RFC 7807)")
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(caso.getValue().getBody())
                    .contains("\"type\":\"urn:scipath:erro:nao_encontrado\"")
                    .contains("\"title\":\"Recurso não encontrado\"")
                    .contains("\"status\":404")
                    .contains("\"detail\":\"O recurso solicitado não foi encontrado.\"")
                    .doesNotContain("\"errors\"")
                    .as("nenhum rastro do material alheio: nem título, nem descrição, nem motivo")
                    .doesNotContain("Material " + MATERIAL_DE_B.identificador())
                    .doesNotContain("Descrição de apoio");

            System.out.println("[E5.2] GET " + caso.getKey() + " -> 404 " + caso.getValue().getBody());
        }

        // A prova: os corpos são IGUAIS fora o instance (o endereço pedido, único campo que pode
        // diferir entre duas requisições a caminhos diferentes). Um detalhe próprio para "não é seu"
        // revelaria que o material existe — é exatamente o oráculo de enumeração que CA008 evita.
        List<String> distintos = new ArrayList<>(new LinkedHashSet<>(List.of(
                semInstance(respostaAlheio.getBody()),
                semInstance(respostaInexistente.getBody()),
                semInstance(respostaPublica.getBody()))));
        assertThat(distintos)
                .as("material alheio, inexistente e fora do público respondem o mesmo corpo")
                .hasSize(1);
    }

    // ------------------------------------------------ critério 1: arquivo indisponível (escopo 6)

    @Test
    @DisplayName("arquivo purgado publica disponivel: false sem revelar o motivo, e o material continua legível")
    void arquivoPurgadoNaoEhDisponivel() {
        Map<String, String> casos = new LinkedHashMap<>();
        casos.put("EXCLUIDO com o binário já purgado", "a-excluido");
        casos.put("PUBLICADO com o registro PURGADO e sem binário", "a-publicado-purgado");

        for (Map.Entry<String, String> caso : casos.entrySet()) {
            String slug = PREFIXO + caso.getValue();
            ResponseEntity<String> resposta = ler(sessaoDeA, ROTA + "/" + slug);

            assertThat(resposta.getStatusCode().value())
                    .as("%s: o dono continua lendo o material (o registro dele não some)", caso.getKey())
                    .isEqualTo(200);

            JsonNode material = json(resposta.getBody());
            assertThat(material.get("arquivo").get("disponivel").asBoolean())
                    .as("%s: a tela não oferece um link que responderia 404 (RF014)", caso.getKey())
                    .isFalse();
            assertThat(material.get("arquivo").get("nomeOriginal").asText())
                    .as("o metadado do arquivo é registro do recebimento e permanece")
                    .isEqualTo(slug + ".pdf");
            assertThat(material.get("arquivo").get("tamanhoBytes").asLong()).isEqualTo(CONTEUDO.length);

            String texto = resposta.getBody();
            assertThat(texto)
                    .as("o indisponível não revela o motivo: nem estado do arquivo, nem chave, nem caminho")
                    .doesNotContain("PURGADO")
                    .doesNotContain("VALIDADO")
                    .doesNotContain(PREFIXO + "chave-")
                    .doesNotContain("checksum")
                    .doesNotContain(propriedades.diretorio().toString());

            System.out.println("[E5.2] GET " + ROTA + "/" + slug + " (" + caso.getKey() + ") -> 200 " + resposta.getBody());
        }
    }

    // --------------------------------------------------------------- critério 1: 400 (faixa)

    @Test
    @DisplayName("page e size fora da faixa respondem 400 com o parâmetro culpado em errors[], e size=100 não é truncado (RNF021)")
    void paginaOuTamanhoForaDaFaixaResponde400() {
        Map<String, String[]> casos = new LinkedHashMap<>();
        casos.put("page=-1", new String[] {"page=-1", "page"});
        casos.put("size=0", new String[] {"size=0", "size"});
        casos.put("size=101", new String[] {"size=101", "size"});
        casos.put("size=5000", new String[] {"size=5000", "size"});

        for (Map.Entry<String, String[]> caso : casos.entrySet()) {
            String caminho = ROTA + "?" + caso.getValue()[0];
            ResponseEntity<String> resposta = ler(sessaoDeA, caminho);

            assertThat(resposta.getStatusCode().value())
                    .as("GET %s -> 400 (o teto é recusado, nunca truncado em silêncio)", caso.getKey())
                    .isEqualTo(400);
            assertThat(resposta.getHeaders().getContentType())
                    .as("erro de parâmetro sai no ProblemDetail padrão")
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(resposta.getBody())
                    .contains("\"type\":\"urn:scipath:erro:validacao\"")
                    .contains("\"title\":\"Requisição inválida\"")
                    .contains("\"status\":400")
                    .contains("\"campo\":\"" + caso.getValue()[1] + "\"");

            System.out.println("[E5.2] GET " + caminho + " -> 400 " + resposta.getBody());
        }

        // O limite é aceito, e devolve o conjunto inteiro: pedir 100 itens não pode devolver menos.
        JsonNode noLimite = json(ler(sessaoDeA, ROTA + "?size=100").getBody());
        assertThat(noLimite.get("size").asInt()).isEqualTo(100);
        assertThat(noLimite.get("items")).hasSize(MATERIAIS_DE_A.size());
        assertThat(noLimite.get("total").asLong()).isEqualTo(MATERIAIS_DE_A.size());
    }

    // ------------------------------------------------------------ critério 2: 401 sem sessão

    @Test
    @DisplayName("as duas rotas respondem 401 sem sessão, e a leitura não exige X-CSRF-Token")
    void semSessaoResponde401ENenhumaLeituraExigeCsrf() {
        for (String caminho : List.of(ROTA, ROTA + "/" + PREFIXO + "a-publicado")) {
            ResponseEntity<String> semSessao = ler(null, caminho);

            assertThat(semSessao.getStatusCode().value())
                    .as("GET %s sem cookie de sessão -> 401", caminho)
                    .isEqualTo(401);
            assertThat(semSessao.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(semSessao.getBody())
                    .contains("\"type\":\"urn:scipath:erro:nao_autenticado\"")
                    .contains("\"status\":401")
                    .contains("\"detail\":\"É necessário entrar na conta para esta operação.\"")
                    .doesNotContain("\"errors\"")
                    .as("o 401 não revela nada do material nem da conta")
                    .doesNotContain(PREFIXO + "a-publicado.pdf");

            System.out.println("[E5.2] GET " + caminho + " sem sessão -> 401 " + semSessao.getBody());
        }

        // Com sessão e SEM o cabeçalho X-CSRF-Token: 200. É leitura — exigir CSRF aqui quebraria a
        // tela que só abre a página (contrato v0.6).
        for (String caminho : List.of(ROTA, ROTA + "/" + PREFIXO + "a-publicado")) {
            ResponseEntity<String> semCsrf = ler(sessaoDeA, caminho);
            assertThat(semCsrf.getStatusCode().value())
                    .as("GET %s com sessão e sem X-CSRF-Token -> 200", caminho)
                    .isEqualTo(200);
        }
    }

    // --------------------------------------------------------------- critério 3: sem dado privado

    @Test
    @DisplayName("a resposta não carrega dado privado, nem identificador de conta, nem autor (RNF013, RNF014)")
    void respostaSemDadoPrivado() {
        String lista = ler(sessaoDeA, ROTA + "?size=100").getBody();
        String detalhe = ler(sessaoDeA, ROTA + "/" + PREFIXO + "a-publicado").getBody();

        for (String corpo : List.of(lista, detalhe)) {
            for (String proibido : List.of("\"autor\"", "email", "senha", "hash", "token", "checksum",
                    "chave", "account", "profile_id", "profileId", "file_asset", "fileAsset", "\"id\"",
                    "storage", propriedades.diretorio().toString())) {
                assertThat(corpo)
                        .as("campo privado ou de infraestrutura '%s' na resposta do dono (RNF013, RNF014)", proibido)
                        .doesNotContain(proibido);
            }
        }

        System.out.println("[E5.2] varredura privado/infra na lista e no detalhe: sem ocorrência");
    }

    // --------------------------------------------------------------- escopo 5: slug malformado

    @Test
    @DisplayName("slug fora do formato do contrato responde 400 no ProblemDetail padrão, com o slug culpado em errors[]")
    void slugForaDoFormatoResponde400() {
        Map<String, String> casos = new LinkedHashMap<>();
        casos.put("Material-Invalido", "deve ser um slug (minúsculas, dígitos e hífen)");
        casos.put("material_com_underscore", "deve ser um slug (minúsculas, dígitos e hífen)");
        casos.put("material--com-hifen-duplo", "deve ser um slug (minúsculas, dígitos e hífen)");
        casos.put("a".repeat(121), "deve ter no máximo 120 caracteres");

        for (Map.Entry<String, String> caso : casos.entrySet()) {
            String caminho = ROTA + "/" + caso.getKey();
            ResponseEntity<String> resposta = ler(sessaoDeA, caminho);

            assertThat(resposta.getStatusCode().value())
                    .as("GET %s -> 400 (o endereço está malformado; não é o recurso que falta)", caso.getKey())
                    .isEqualTo(400);
            assertThat(resposta.getBody())
                    .contains("\"type\":\"urn:scipath:erro:validacao\"")
                    .contains("\"status\":400")
                    .contains("\"campo\":\"slug\"")
                    .contains(caso.getValue());

            System.out.println("[E5.2] GET " + ROTA + "/" + resumido(caso.getKey()) + " -> 400 " + resposta.getBody());
        }
    }

    // ---------------------------------------------------------------------------- sessão

    /** Sessão como o cliente a tem: o valor da cookie de sessão e o token CSRF que ela carrega. */
    private record Sessao(String token, String csrf) {
    }

    /** Cadastra a conta pelo fluxo de verdade, ativa por SQL e autentica — a cookie é a fronteira. */
    private Sessao cadastrarElogar(String identificador, String nomePublico) {
        String email = emailDe(identificador);
        String csrfDoVisitante = csrfDeVisitante();

        ResponseEntity<String> cadastro = rest.exchange("/api/contas", HttpMethod.POST,
                new HttpEntity<>(JSON.createObjectNode().put("email", email).put("senha", SENHA)
                        .put("nomePublico", nomePublico).toString(), cabecalhosDeJson(csrfDoVisitante)),
                String.class);
        assertThat(cadastro.getStatusCode().value())
                .as("cadastro de apoio falhou: %s", cadastro.getBody())
                .isEqualTo(201);

        // Ativação por SQL: o que se mede aqui é a leitura do dono, não o fluxo de confirmação (E3.4).
        assertThat(jdbc.update("UPDATE account SET estado = 'ATIVO', email_verificado_em = now()"
                + " WHERE email = ?", email)).isEqualTo(1);

        ResponseEntity<String> login = rest.exchange("/api/sessoes", HttpMethod.POST,
                new HttpEntity<>(JSON.createObjectNode().put("email", email).put("senha", SENHA).toString(),
                        cabecalhosDeJson(csrfDoVisitante)),
                String.class);
        assertThat(login.getStatusCode().value()).as("login de apoio falhou: %s", login.getBody()).isEqualTo(200);

        return new Sessao(
                valorDaCookie(login, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow(),
                valorDaCookie(login, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow());
    }

    /** Cookie de CSRF de visitante — o token que o frontend lê na primeira leitura do app. */
    private String csrfDeVisitante() {
        ResponseEntity<String> resposta = rest.getForEntity("/api/sessoes/atual", String.class);
        return valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF)
                .orElseThrow(() -> new AssertionError("GET /api/sessoes/atual não emitiu a cookie de CSRF"));
    }

    /** GET autenticado (só a cookie de sessão, sem CSRF) — {@code sessao} nula é o visitante. */
    private ResponseEntity<String> ler(Sessao sessao, String caminho) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (sessao != null) {
            cabecalhos.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_SESSAO + "=" + sessao.token());
        }
        return rest.exchange(caminho, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }

    private HttpHeaders cabecalhosDeJson(String csrf) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_CSRF + "=" + csrf);
        cabecalhos.add("X-CSRF-Token", csrf);
        return cabecalhos;
    }

    private static Optional<String> valorDaCookie(ResponseEntity<?> resposta, String nome) {
        return resposta.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).stream()
                .filter(linha -> linha.startsWith(nome + "="))
                .map(linha -> {
                    int igual = linha.indexOf('=');
                    int pontoEVirgula = linha.indexOf(';');
                    return pontoEVirgula < 0 ? linha.substring(igual + 1) : linha.substring(igual + 1, pontoEVirgula);
                })
                .filter(valor -> !valor.isBlank())
                .findFirst();
    }

    // ------------------------------------------------------------------------------ banco

    private static String emailDe(String identificador) {
        return PREFIXO + identificador + DOMINIO;
    }

    private UUID perfilId(String email) {
        return jdbc.queryForObject("SELECT p.id FROM profile p JOIN account a ON a.id = p.account_id"
                + " WHERE a.email = ?", UUID.class, email);
    }

    /**
     * Semeia {@code file_asset} + {@code material} com o estado e o instante pedidos. O binário vai
     * para o armazenamento pela <b>mesma porta</b> que a aplicação usa (E4.3) quando
     * {@code comBinario} — é o que separa "arquivo disponível" de "registro que não permite servir".
     */
    private void inserirMaterial(UUID perfil, Semeado material) {
        String chave = PREFIXO + "chave-" + material.identificador();
        String nomeOriginal = PREFIXO + material.identificador() + ".pdf";
        Instant atualizadoEm = MAIS_RECENTE_EM.minus(material.horasAntes(), ChronoUnit.HOURS);

        jdbc.update("""
                INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,
                                        tamanho_bytes, checksum, estado_processamento)
                VALUES (?, ?, 'application/pdf', ?, repeat('b', 64), ?)
                """, chave, nomeOriginal, (long) CONTEUDO.length, material.estadoDoArquivo());

        jdbc.update("""
                INSERT INTO material (slug, titulo, descricao, profile_id, category_id, material_type_id,
                                      license_id, file_asset_id, estado_publicacao, publicado_em, atualizado_em)
                SELECT ?, ?, ?, ?,
                       (SELECT id FROM category WHERE slug = ?),
                       (SELECT id FROM material_type WHERE slug = ?),
                       (SELECT id FROM license WHERE slug = ?),
                       (SELECT id FROM file_asset WHERE chave_interna = ?),
                       ?, ?, ?
                """, PREFIXO + material.identificador(), "Material " + material.identificador(),
                "Descrição de apoio do material " + material.identificador() + ".",
                perfil, CATEGORIA, TIPO, LICENCA, chave,
                material.estadoPublicacao(), emUtc(PUBLICADO_EM), emUtc(atualizadoEm));

        if (material.comBinario()) {
            gravarNoArmazenamento(chave);
        }
    }

    /** Promove o conteúdo ao armazenamento pela MESMA porta que a aplicação usa (E4.3). */
    private void gravarNoArmazenamento(String chave) {
        try {
            Path origem = areaDeApoio.resolve(chave + ".pdf");
            Files.write(origem, CONTEUDO);
            armazenamento.guardar(chave, origem);
        } catch (IOException falha) {
            throw new UncheckedIOException("não foi possível preparar o arquivo de apoio " + chave, falha);
        }
    }

    /**
     * Remove só o que esta classe criou, na ordem que as chaves estrangeiras exigem
     * ({@code material} → {@code file_asset} → {@code account}), e apaga os binários promovidos —
     * o diretório é o mesmo das outras integrações e a suíte não pode deixar lixo atrás de si (RN012).
     */
    private void limparDadosDeTeste() {
        jdbc.update("DELETE FROM material WHERE slug LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM file_asset WHERE chave_interna LIKE ?", PREFIXO + "%");
        // Apagar a conta remove o perfil junto (ON DELETE CASCADE da V1).
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
        for (Path arquivo : arquivosNoArmazenamento()) {
            try {
                Files.deleteIfExists(arquivo);
            } catch (IOException falha) {
                throw new UncheckedIOException("não foi possível limpar " + arquivo, falha);
            }
        }
    }

    /** Só os arquivos desta classe — o diretório é o mesmo das outras integrações de backend. */
    private List<Path> arquivosNoArmazenamento() {
        Path diretorio = propriedades.diretorio();
        if (!Files.isDirectory(diretorio)) {
            return List.of();
        }
        try (Stream<Path> entradas = Files.list(diretorio)) {
            return entradas.filter(caminho -> caminho.getFileName().toString().startsWith(PREFIXO)).toList();
        } catch (IOException falha) {
            throw new UncheckedIOException("não foi possível listar " + diretorio, falha);
        }
    }

    // -------------------------------------------------------------------------- auxiliares

    /** Material semeado: identificador, estado, idade do {@code atualizado_em}, arquivo e binário. */
    private record Semeado(String identificador, String estadoPublicacao, int horasAntes,
            String estadoDoArquivo, boolean comBinario) {
    }

    /** Corpo do 404 sem o {@code instance} — o único campo que pode diferir entre dois 404. */
    private static String semInstance(String corpo) {
        return corpo.replaceFirst("\"instance\":\"[^\"]*\"", "\"instance\":\"\"");
    }

    private static String resumido(String slug) {
        return slug.length() > 40 ? slug.substring(0, 12) + "...(" + slug.length() + " caracteres)" : slug;
    }

    private static List<String> slugsDe(JsonNode pagina) {
        List<String> slugs = new ArrayList<>();
        pagina.get("items").forEach(item -> slugs.add(item.get("slug").asText()));
        return slugs;
    }

    private static List<String> estadosDe(JsonNode pagina) {
        List<String> estados = new ArrayList<>();
        pagina.get("items").forEach(item -> estados.add(item.get("estado").asText()));
        return estados;
    }

    private static JsonNode itemComSlug(JsonNode pagina, String slug) {
        for (JsonNode item : pagina.get("items")) {
            if (slug.equals(item.get("slug").asText())) {
                return item;
            }
        }
        throw new AssertionError("item '" + slug + "' não está na página: " + pagina);
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

    /** PDF de apoio: assinatura de verdade + corpo determinístico, do tamanho pedido. */
    private static byte[] pdfComCorpo(int tamanho) {
        byte[] cabecalho = "%PDF-1.7\n".getBytes(StandardCharsets.UTF_8);
        byte[] conteudo = new byte[Math.max(tamanho, cabecalho.length)];
        System.arraycopy(cabecalho, 0, conteudo, 0, cabecalho.length);
        for (int i = cabecalho.length; i < conteudo.length; i++) {
            conteudo[i] = (byte) ('a' + (i % 26));
        }
        return conteudo;
    }
}
