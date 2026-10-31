package com.scipath.taxonomia;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Os três endpoints de leitura da taxonomia (RF018), exercitados de verdade: contexto
 * completo, MVC completo e o PostgreSQL local com a seed da V2 — o mesmo banco que o
 * {@code make test} carrega do {@code .env}. Sem mock de repositório: o que o card exige
 * provar é que a contagem publicada é a do banco (13/6/7), e mock nenhum prova isso.
 *
 * <p>Mesma configuração de contexto de {@code ApiExceptionHandlerIntegrationTest}
 * ({@code RANDOM_PORT}) de propósito: o Spring reusa o contexto já subido, e a máquina
 * tem 954 MB — subir um segundo contexto só para estes testes é custo sem ganho
 * (AGENTS.md, higiene de recursos).
 *
 * <p>Um teste aqui <b>escreve</b>: {@code ativo=false} só tem o que mostrar se existir item
 * desativado, e a seed não traz nenhum (todos nascem ativos). O item é gravado e apagado
 * na mesma classe, com slug de teste reconhecível, para o banco ficar exatamente como
 * estava.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("leitura da taxonomia de referência (RF018)")
class TaxonomiaControllerIntegrationTest {

    /** Slug de teste: visível como tal se um dia sobrar no banco por falha no meio do teste. */
    private static final String SLUG_DESATIVADA = "categoria-desativada-de-teste-e1-9";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removerDadoDeTeste() {
        jdbc.update("DELETE FROM category WHERE slug = ?", SLUG_DESATIVADA);
    }

    // ------------------------------------------------------------------ conteúdo

    @Test
    @DisplayName("os três endpoints devolvem o envelope Pagina com as contagens da seed (13/6/7)")
    void osTresEndpointsDevolvemOEnvelopeDoContratoComAsContagensDaSeed() {
        JsonNode categorias = buscar("/api/categorias");
        assertThat(categorias.get("total").asInt()).as("categorias semeadas pela V2").isEqualTo(13);
        assertThat(categorias.get("items").size()).as("cabe uma página só").isEqualTo(13);
        assertThat(categorias.get("page").asInt()).isZero();
        assertThat(categorias.get("size").asInt()).isEqualTo(20);

        JsonNode tipos = buscar("/api/tipos-material");
        assertThat(tipos.get("total").asInt()).as("tipos de material semeados pela V2").isEqualTo(6);
        assertThat(tipos.get("items").size()).isEqualTo(6);

        JsonNode licencas = buscar("/api/licencas");
        assertThat(licencas.get("total").asInt()).as("licenças semeadas pela V2").isEqualTo(7);
        assertThat(licencas.get("items").size()).isEqualTo(7);

        System.out.println("[E1.9] GET /api/categorias -> " + categorias);
        System.out.println("[E1.9] GET /api/tipos-material -> " + tipos);
        System.out.println("[E1.9] GET /api/licencas -> " + licencas);
    }

    @Test
    @DisplayName("o item de cada lista publica exatamente os campos do schema do contrato")
    void oItemPublicaOsCamposDoSchema() {
        JsonNode categoria = buscar("/api/categorias").get("items").get(0);
        assertThat(campos(categoria)).containsExactlyInAnyOrder("id", "slug", "nome", "descricao", "ativo");
        assertThat(categoria.get("id").asText())
                .as("id do contrato é uuid textual")
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(categoria.get("descricao").isNull()).as("descricao é obrigatória no schema").isFalse();

        JsonNode tipo = buscar("/api/tipos-material").get("items").get(0);
        assertThat(campos(tipo)).containsExactlyInAnyOrder("id", "slug", "nome", "ativo");

        // A licença sem identificador público ("Todos os direitos reservados") tem os dois
        // campos AUSENTES — não nulos: o contrato diz "podem vir ausentes" (§ RN006), e a
        // ausência é o que o cliente testa antes de montar link.
        JsonNode semTextoPublico = itemComSlug(buscar("/api/licencas"), "todos-os-direitos-reservados");
        assertThat(campos(semTextoPublico)).containsExactlyInAnyOrder("id", "slug", "nome", "descricaoCurta", "ativo");
        assertThat(semTextoPublico.get("descricaoCurta").asText())
                .as("RN006: a licença explica o que permite, em linguagem simples")
                .isNotBlank();

        JsonNode comTextoPublico = itemComSlug(buscar("/api/licencas"), "cc-by-4.0");
        assertThat(campos(comTextoPublico))
                .containsExactlyInAnyOrder("id", "slug", "nome", "identificador", "urlReferencia",
                        "descricaoCurta", "ativo");
        assertThat(comTextoPublico.get("urlReferencia").asText()).startsWith("https://");

        System.out.println("[E1.9] licença sem identificador público -> " + semTextoPublico);
    }

    // ------------------------------------------------------------ filtro de estado

    @Test
    @DisplayName("sem o parâmetro só vêm ativos; ?ativo=false traz também a desativada; ?ativo=true é igual ao omitido")
    void categoriaDesativadaSoApareceQuandoPedidaExplicitamente() {
        jdbc.update("INSERT INTO category (nome, slug, descricao, ativo) VALUES (?, ?, ?, false)",
                "Categoria desativada de teste", SLUG_DESATIVADA, "Item gravado pelo teste E1.9; removido ao fim.");

        JsonNode semParametro = buscar("/api/categorias");
        assertThat(semParametro.get("total").asInt()).as("padrão do contrato é só ativos").isEqualTo(13);
        assertThat(slugsDe(semParametro)).doesNotContain(SLUG_DESATIVADA);

        JsonNode comAtivoTrue = buscar("/api/categorias?ativo=true");
        assertThat(comAtivoTrue.get("total").asInt())
                .as("quem manda o default do contrato não pode ver resultado diferente")
                .isEqualTo(13);

        JsonNode comAtivoFalse = buscar("/api/categorias?ativo=false");
        assertThat(comAtivoFalse.get("total").asInt()).as("13 ativas + 1 desativada").isEqualTo(14);
        assertThat(slugsDe(comAtivoFalse)).contains(SLUG_DESATIVADA);
        assertThat(itemComSlug(comAtivoFalse, SLUG_DESATIVADA).get("ativo").asBoolean())
                .as("o item desativado chega marcado, não escondido")
                .isFalse();

        System.out.println("[E1.9] /api/categorias (sem parâmetro) -> total=" + semParametro.get("total").asInt());
        System.out.println("[E1.9] /api/categorias?ativo=false -> total=" + comAtivoFalse.get("total").asInt()
                + " (inclui " + SLUG_DESATIVADA + ")");
    }

    // ------------------------------------------------------------------- 400

    @Test
    @DisplayName("?ativo=xyz responde 400 em application/problem+json com errors[] do parâmetro")
    void ativoNaoBooleanoResponde400() {
        ResponseEntity<String> resposta = rest.getForEntity("/api/categorias?ativo=xyz", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getHeaders().getContentType())
                .as("erro de campo tem de sair no formato único do contrato")
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resposta.getBody())
                .contains("\"type\":\"urn:scipath:erro:validacao\"")
                .contains("\"title\":\"Requisição inválida\"")
                .contains("\"status\":400")
                .contains("\"instance\":\"/api/categorias\"")
                .contains("\"campo\":\"ativo\"")
                .contains("deve ser true ou false");

        System.out.println("[E1.9] GET /api/categorias?ativo=xyz -> 400 " + resposta.getBody());
    }

    @Test
    @DisplayName("?size=1000 é recusado (400), não truncado em silêncio para o teto de 100")
    void tamanhoAcimaDoTetoResponde400() {
        ResponseEntity<String> resposta = rest.getForEntity("/api/categorias?size=1000", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody())
                .contains("\"campo\":\"size\"")
                .contains("deve ser 100 ou menos");

        System.out.println("[E1.9] GET /api/categorias?size=1000 -> 400 " + resposta.getBody());
    }

    // -------------------------------------------------------------- ordenação

    @Test
    @DisplayName("ordenação por nome é estável e a paginação não repete nem perde item")
    void ordenacaoPorNomeEEstavelEntreChamadasEPaginas() {
        JsonNode primeira = buscar("/api/categorias");
        JsonNode segunda = buscar("/api/categorias");
        assertThat(segunda)
                .as("duas chamadas seguidas devolvem a mesma ordem")
                .isEqualTo(primeira);

        // A ORDEM é a do banco com COLLATE "C" (ADR-0010): ordem de ponto de código, que é o
        // que a asserção natural do AssertJ confere aqui — e agora por decisão explícita da
        // consulta, não por acidente do datcollate da máquina. O desempate por slug garante que
        // nome repetido não embaralhe a página.
        for (String caminho : List.of("/api/categorias", "/api/tipos-material", "/api/licencas")) {
            List<String> nomes = nomesDe(buscar(caminho));
            assertThat(nomes).as("nomes de %s em ordem", caminho).isSorted().doesNotHaveDuplicates();
        }

        assertThat(slugsDe(primeira).get(0)).as("primeira categoria por nome").isEqualTo("artes");
        assertThat(slugsDe(primeira).get(12)).as("última categoria por nome").isEqualTo("saude");

        // Fatiar a mesma lista em páginas de 5 tem de reproduzir a lista inteira, sem
        // repetir e sem pular — é a razão de a ordenação precisa ser estável (RNF021).
        List<String> paginado = new ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            paginado.addAll(slugsDe(buscar("/api/categorias?size=5&page=" + pagina)));
        }
        assertThat(paginado).as("páginas 0,1,2 de tamanho 5").hasSize(13).doesNotHaveDuplicates().isSorted();
        assertThat(paginado).as("a fatia tem de ser a mesma lista").isEqualTo(slugsDe(primeira));

        System.out.println("[E1.9] ordem publicada -> " + paginado);
    }

    // -------------------------------------------------------------- auxiliares

    private JsonNode buscar(String caminho) {
        ResponseEntity<JsonNode> resposta = rest.getForEntity(caminho, JsonNode.class);
        assertThat(resposta.getStatusCode().value()).as("GET %s", caminho).isEqualTo(200);
        assertThat(resposta.getHeaders().getContentType()).isNotNull();
        assertThat(resposta.getBody()).as("corpo de GET %s", caminho).isNotNull();
        assertThat(resposta.getBody().get("items")).as("envelope Pagina de %s", caminho).isNotNull();
        return resposta.getBody();
    }

    private static List<String> campos(JsonNode item) {
        List<String> nomes = new ArrayList<>();
        item.fieldNames().forEachRemaining(nomes::add);
        return nomes;
    }

    private static List<String> slugsDe(JsonNode pagina) {
        return valoresDe(pagina, "slug");
    }

    private static List<String> nomesDe(JsonNode pagina) {
        return valoresDe(pagina, "nome");
    }

    private static List<String> valoresDe(JsonNode pagina, String campo) {
        List<String> valores = new ArrayList<>();
        pagina.get("items").forEach(item -> valores.add(item.get(campo).asText()));
        return valores;
    }

    private static JsonNode itemComSlug(JsonNode pagina, String slug) {
        for (JsonNode item : pagina.get("items")) {
            if (slug.equals(item.get("slug").asText())) {
                return item;
            }
        }
        throw new AssertionError("slug '" + slug + "' não está na página: " + pagina);
    }
}
