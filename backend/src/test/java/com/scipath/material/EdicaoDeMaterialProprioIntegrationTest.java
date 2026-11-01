package com.scipath.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scipath.identidade.CookiesDeSessao;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

/**
 * A edição dos metadados do material próprio (RF016, contrato v0.6, tag {@code gerenciamento}),
 * exercitada de verdade: contexto completo, MVC completo (o 401 e o 403 do interceptor, o 400, o
 * 404 e o 422 do tratamento único de erro — não um corpo montado à mão) e o PostgreSQL local de
 * {@code scipath_test}. Sem mock de repositório: o que o card exige provar é <b>de quem</b> é a
 * linha que a escrita alcança, <b>o que</b> ela muda no banco e <b>o que</b> ela recusa — e mock
 * nenhum prova isso.
 *
 * <p><b>Dados controlados, com dois donos.</b> A classe cria as contas <b>A</b> e <b>B</b> pelo
 * cadastro de verdade ({@code POST /api/contas}), ativa as duas por SQL — o que se mede aqui é a
 * edição, não o fluxo de confirmação de e-mail (E3.4) — e autentica cada uma ({@code POST
 * /api/sessoes}), porque é a cookie de sessão que a fronteira desta superfície usa: sem sessão
 * real, o 401, o 403 de CSRF e a propriedade "vem da sessão" não seriam medidos. A tem três
 * materiais — os dois estados editáveis ({@code PUBLICADO}, {@code RETIRADO}) e um {@code
 * EXCLUIDO}, que é terminal —; B tem um, e é ele que o acesso cruzado tenta editar. Tudo é limpo
 * antes e depois de cada teste, com o prefixo {@code teste-e5-3-} como marcador.
 *
 * <p><b>Por que os 404 são comparados por igualdade, e não por status.</b> O card pede o 404 de
 * material alheio <i>indistinguível</i> do de material inexistente (RN002, CA007, ADR-0011 §3).
 * Comparar só o status deixaria passar um detalhe diferente para "não é seu" e "não existe", que é
 * justamente o que revela a existência do material. Os corpos são comparados <b>byte a byte</b>,
 * normalizando apenas o {@code instance} — o endereço pedido, que é o único campo que pode diferir
 * entre duas requisições a caminhos diferentes (mesma decisão dos testes do detalhe público e da
 * leitura do dono).
 *
 * <p><b>"Nenhuma alteração no banco" é conferido no banco.</b> Os casos de recusa (404, 422, 400,
 * 401, 403) leem a linha <b>inteira</b> antes e depois — título, descrição, taxonomia, estado, as
 * duas datas e o vínculo de propriedade — e exigem igualdade. Sem isso, um 404 correto na resposta
 * conviveria com uma escrita indevida no banco, que é exatamente o defeito que o card pede para
 * descartar.
 *
 * <p>Mesma configuração de contexto das outras integrações ({@code RANDOM_PORT}): o Spring reusa o
 * contexto já subido e a máquina tem pouca RAM (AGENTS.md, higiene de recursos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("edição dos metadados do material próprio (RF016, contrato v0.6)")
class EdicaoDeMaterialProprioIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Marcador dos dados desta classe em slug, e-mail e chave de arquivo. */
    private static final String PREFIXO = "teste-e5-3-";

    private static final String DOMINIO = "@example.test";
    private static final String SENHA = "editar-material-2026";

    /** A rota da edição; o detalhe do dono é ela mais o {@code /{slug}} (contrato v0.6). */
    private static final String ROTA = "/api/perfil/atual/materiais";

    /** A rota pública do mesmo material — é ela que tem de refletir a edição (RF016). */
    private static final String ROTA_PUBLICA = "/api/materiais";

    /** Taxonomia da seed V2 (ADR-0006): a de origem dos materiais semeados. */
    private static final String CATEGORIA = "computacao-e-tecnologia";
    private static final String TIPO = "nota-de-aula";
    private static final String LICENCA = "cc-by-4.0";

    /** Taxonomia de destino da edição — outra categoria, outro tipo e outra licença, todos ativos. */
    private static final String OUTRA_CATEGORIA = "matematica-e-estatistica";
    private static final String OUTRO_TIPO = "slides";
    private static final String OUTRA_LICENCA = "cc-by-sa-4.0";

    private static final String TITULO_EDITADO = "Material revisado pelo dono";
    private static final String DESCRICAO_EDITADA =
            "Descrição revisada: o dono corrigiu o texto, a categoria, o tipo e a licença.";

    /** Base fixa das datas: não depende do relógio da máquina nem de fuso do ambiente. */
    private static final Instant PUBLICADO_EM = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant ATUALIZADO_EM = Instant.parse("2026-10-20T12:00:00Z");

    /** Os materiais de A: os dois estados vivos (editáveis) e o terminal (não editável). */
    private static final List<String> MATERIAIS_DE_A = List.of("a-publicado", "a-retirado", "a-excluido");

    /** O único material de B: é ele que A tenta editar no acesso cruzado (CA007). */
    private static final String MATERIAL_DE_B = "b-publicado";

    /** Conteúdo semeado: assinatura de PDF + corpo determinístico (a edição não abre o conteúdo). */
    private static final byte[] CONTEUDO = pdfComCorpo(512);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private Sessao sessaoDeA;
    private Sessao sessaoDeB;

    @BeforeEach
    void semearDadosControlados() {
        limparDadosDeTeste();

        sessaoDeA = cadastrarElogar("a", "Ana do Painel");
        sessaoDeB = cadastrarElogar("b", "Bruno do Painel");

        inserirMaterial(perfilId(emailDe("a")), "a-publicado", "PUBLICADO");
        inserirMaterial(perfilId(emailDe("a")), "a-retirado", "RETIRADO");
        inserirMaterial(perfilId(emailDe("a")), "a-excluido", "EXCLUIDO");
        inserirMaterial(perfilId(emailDe("b")), MATERIAL_DE_B, "PUBLICADO");
    }

    @AfterEach
    void removerDadosDeTeste() {
        limparDadosDeTeste();
    }

    // ------------------------------------------- critério 1: a edição válida persiste e responde

    @Test
    @DisplayName("edição válida substitui os cinco campos, preserva slug, dono, estado, arquivo e publicadoEm, e sobe atualizadoEm")
    void edicaoValidaPersisteEZeraOsImutaveis() {
        String slug = PREFIXO + "a-publicado";
        Map<String, Object> antes = linhaNoBanco(slug);

        ResponseEntity<String> resposta = editar(sessaoDeA, slug, corpoValido().toString(), true);

        assertThat(resposta.getStatusCode().value())
                .as("RF016: o dono edita o próprio material -> 200")
                .isEqualTo(200);
        assertThat(resposta.getHeaders().getContentType())
                .as("a resposta sai como JSON")
                .isNotNull()
                .satisfies(tipo -> assertThat(tipo.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());

        // A resposta é o schema MaterialProprio atualizado — o MESMO da leitura (escopo 5).
        JsonNode material = json(resposta.getBody());
        assertThat(camposDe(material))
                .as("exatamente o schema MaterialProprio do contrato v0.6, nem um campo a mais")
                .containsExactlyInAnyOrder("slug", "titulo", "descricao", "categoria", "tipo", "licenca",
                        "arquivo", "estado", "publicadoEm", "atualizadoEm");
        assertThat(material.get("slug").asText())
                .as("o slug é a URL canônica pública e NÃO muda (RNF025, RN009)")
                .isEqualTo(slug);
        assertThat(material.get("titulo").asText()).isEqualTo(TITULO_EDITADO);
        assertThat(material.get("descricao").asText()).isEqualTo(DESCRICAO_EDITADA);
        assertThat(material.get("categoria").get("slug").asText()).isEqualTo(OUTRA_CATEGORIA);
        assertThat(material.get("tipo").get("slug").asText()).isEqualTo(OUTRO_TIPO);
        assertThat(material.get("licenca").get("slug").asText()).isEqualTo(OUTRA_LICENCA);
        assertThat(material.get("estado").asText())
                .as("a edição não é o PATCH: o estado continua o que era (ADR-0011 §4)")
                .isEqualTo("PUBLICADO");
        assertThat(Instant.parse(material.get("publicadoEm").asText()))
                .as("publicadoEm não muda por edição — é a âncora da ordenação pública (ADR-0011 §4)")
                .isEqualTo(PUBLICADO_EM);
        assertThat(Instant.parse(material.get("atualizadoEm").asText()))
                .as("atualizadoEm sobe com a escrita")
                .isAfter(ATUALIZADO_EM);
        assertThat(material.get("arquivo").get("nomeOriginal").asText())
                .as("o arquivo e seus metadados são os do recebimento: trocar binário é outra operação")
                .isEqualTo(PREFIXO + "a-publicado.pdf");

        // ---- o banco, antes e depois: o que mudou e o que NÃO mudou (critério 1 do card)
        Map<String, Object> depois = linhaNoBanco(slug);
        assertThat(depois.get("slug")).as("o slug não muda no banco").isEqualTo(antes.get("slug"));
        assertThat(depois.get("profile_id"))
                .as("a propriedade não muda: a edição não troca o dono (RF016, RN001, CA007)")
                .isEqualTo(antes.get("profile_id"))
                .isEqualTo(perfilId(emailDe("a")));
        assertThat(depois.get("file_asset_id"))
                .as("o vínculo com o arquivo não muda")
                .isEqualTo(antes.get("file_asset_id"));
        assertThat(depois.get("estado_publicacao")).as("o estado não muda por esta rota").isEqualTo("PUBLICADO");
        assertThat(instanteNoBanco(slug, "publicado_em"))
                .as("publicado_em preservado no banco")
                .isEqualTo(PUBLICADO_EM)
                .isEqualTo(instanteDe(antes, "publicado_em"));
        assertThat(instanteNoBanco(slug, "atualizado_em"))
                .as("atualizado_em maior que o de antes da escrita")
                .isAfter(instanteDe(antes, "atualizado_em"))
                .isAfter(ATUALIZADO_EM);
        assertThat(depois.get("titulo")).isEqualTo(TITULO_EDITADO);
        assertThat(depois.get("descricao")).isEqualTo(DESCRICAO_EDITADA);
        assertThat(depois.get("category_id")).isEqualTo(idDaTaxonomia("category", OUTRA_CATEGORIA));
        assertThat(depois.get("material_type_id")).isEqualTo(idDaTaxonomia("material_type", OUTRO_TIPO));
        assertThat(depois.get("license_id")).isEqualTo(idDaTaxonomia("license", OUTRA_LICENCA));

        System.out.println("[E5.3] PUT " + ROTA + "/" + slug + " -> 200 " + resposta.getBody());
        System.out.println("[E5.3] banco: dono " + antes.get("profile_id") + " -> " + depois.get("profile_id")
                + " | publicado_em " + antes.get("publicado_em") + " -> " + depois.get("publicado_em")
                + " | atualizado_em " + antes.get("atualizado_em") + " -> " + depois.get("atualizado_em"));
    }

    // ------------------------------------- critério 4 (RF016): a página pública reflete a edição

    @Test
    @DisplayName("a alteração aparece no detalhe público do material (RF016), e a leitura do dono traz o mesmo registro")
    void alteracaoApareceNoDetalhePublico() {
        String slug = PREFIXO + "a-publicado";

        assertThat(editar(sessaoDeA, slug, corpoValido().toString(), true).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> publico = rest.getForEntity(ROTA_PUBLICA + "/" + slug, String.class);

        assertThat(publico.getStatusCode().value())
                .as("o material continua publicado: editar metadados não tira do público")
                .isEqualTo(200);
        JsonNode detalhe = json(publico.getBody());
        assertThat(detalhe.get("titulo").asText())
                .as("a página pública reflete a alteração (RF016)")
                .isEqualTo(TITULO_EDITADO);
        assertThat(detalhe.get("descricao").asText()).isEqualTo(DESCRICAO_EDITADA);
        assertThat(detalhe.get("categoria").get("slug").asText()).isEqualTo(OUTRA_CATEGORIA);
        assertThat(detalhe.get("tipo").get("slug").asText()).isEqualTo(OUTRO_TIPO);
        assertThat(detalhe.get("licenca").get("slug").asText()).isEqualTo(OUTRA_LICENCA);
        assertThat(detalhe.get("slug").asText())
                .as("a URL canônica continua a mesma — link e indexação não quebram (RNF025)")
                .isEqualTo(slug);
        assertThat(Instant.parse(detalhe.get("publicadoEm").asText()))
                .as("o detalhe público publica a mesma data de publicação de antes")
                .isEqualTo(PUBLICADO_EM);

        // A leitura do dono é o mesmo registro: o que a tela de edição pré-preenche é o que ficou.
        JsonNode dono = json(ler(sessaoDeA, ROTA + "/" + slug).getBody());
        assertThat(dono.get("titulo").asText()).isEqualTo(TITULO_EDITADO);
        assertThat(dono.get("categoria").get("slug").asText()).isEqualTo(OUTRA_CATEGORIA);
        assertThat(Instant.parse(dono.get("atualizadoEm").asText())).isAfter(ATUALIZADO_EM);

        System.out.println("[E5.3] GET " + ROTA_PUBLICA + "/" + slug + " -> 200 " + publico.getBody());
    }

    // ------------------------------------------------- escopo 3 (ADR-0011 §2): RETIRADO é editável

    @Test
    @DisplayName("material RETIRADO é editável e continua fora do público depois da edição")
    void retiradoEhEditavelEContinuaForaDoPublico() {
        String slug = PREFIXO + "a-retirado";

        ResponseEntity<String> resposta = editar(sessaoDeA, slug, corpoValido().toString(), true);

        assertThat(resposta.getStatusCode().value())
                .as("as ações do dono valem em PUBLICADO e RETIRADO (ADR-0011 §2)")
                .isEqualTo(200);
        assertThat(json(resposta.getBody()).get("estado").asText())
                .as("editar não republica: o estado continua RETIRADO")
                .isEqualTo("RETIRADO");
        assertThat(linhaNoBanco(slug).get("titulo")).isEqualTo(TITULO_EDITADO);

        ResponseEntity<String> publico = rest.getForEntity(ROTA_PUBLICA + "/" + slug, String.class);
        assertThat(publico.getStatusCode().value())
                .as("RETIRADO segue invisível no público (RN002) — a edição não o devolve à descoberta")
                .isEqualTo(404);

        System.out.println("[E5.3] PUT " + ROTA + "/" + slug + " (RETIRADO) -> 200; público -> 404");
    }

    // ---------------------- critério 1 (§17.4): identificador de outra pessoa no corpo é ignorado

    @Test
    @DisplayName("autorSlug, contaId, perfilId, slug e estado no corpo são ignorados: quem manda é a sessão (§17.4, RN001)")
    void camposDeOutraPessoaNoCorpoSaoIgnorados() {
        String slug = PREFIXO + "a-publicado";
        UUID perfilDeB = perfilId(emailDe("b"));
        Map<String, Object> antes = linhaNoBanco(slug);

        // O corpo carrega, além dos cinco campos, tudo o que o schema NÃO publica: um identificador
        // de conta e de perfil de outra pessoa, o slug do autor de B, um estado (o terminal!) e o
        // próprio slug. O contrato v0.6 diz que são ignorados — "não há campo para eles".
        ObjectNode corpo = corpoValido();
        corpo.put("autorSlug", "b-bruno");
        corpo.put("contaId", UUID.randomUUID().toString());
        corpo.put("perfilId", perfilDeB.toString());
        corpo.put("slug", "slug-que-eu-quero");
        corpo.put("estado", "EXCLUIDO");
        corpo.put("declaracaoDireito", false);

        ResponseEntity<String> resposta = editar(sessaoDeA, slug, corpo.toString(), true);

        assertThat(resposta.getStatusCode().value())
                .as("o corpo com campos fora do schema é aceito e os campos são descartados")
                .isEqualTo(200);
        JsonNode material = json(resposta.getBody());
        assertThat(material.get("slug").asText())
                .as("o slug pedido no corpo não tem efeito nenhum (RNF025)")
                .isEqualTo(slug);
        assertThat(material.get("estado").asText())
                .as("estado no corpo não exclui nada: o ato de excluir é o DELETE (E5.4)")
                .isEqualTo("PUBLICADO");
        assertThat(material.get("titulo").asText())
                .as("os cinco campos válidos, esses sim, foram aplicados")
                .isEqualTo(TITULO_EDITADO);

        Map<String, Object> depois = linhaNoBanco(slug);
        assertThat(depois.get("profile_id"))
                .as("a propriedade vem da sessão: o perfilId do corpo não escreve nada (RN001, CA007)")
                .isEqualTo(antes.get("profile_id"))
                .isNotEqualTo(perfilDeB);
        assertThat(depois.get("slug")).isEqualTo(slug);
        assertThat(depois.get("estado_publicacao")).isEqualTo("PUBLICADO");
        assertThat(depois.get("titulo")).isEqualTo(TITULO_EDITADO);

        System.out.println("[E5.3] PUT com autorSlug/contaId/perfilId/slug/estado no corpo -> 200 "
                + resposta.getBody());
        System.out.println("[E5.3] banco depois: dono " + depois.get("profile_id") + " | slug " + depois.get("slug")
                + " | estado " + depois.get("estado_publicacao"));
    }

    // ------------------------------------------------------ critério 2 (CA007): o acesso cruzado

    @Test
    @DisplayName("A editar material de B responde 404 idêntico ao do slug inexistente, e NÃO altera o banco (CA007)")
    void acessoCruzadoResponde404IdenticoENaoAlteraNada() {
        String alheio = PREFIXO + MATERIAL_DE_B;
        String inexistente = PREFIXO + "nao-existe";
        String corpo = corpoValido().toString();
        Map<String, Object> antes = linhaNoBanco(alheio);

        ResponseEntity<String> respostaAlheio = editar(sessaoDeA, alheio, corpo, true);
        ResponseEntity<String> respostaInexistente = editar(sessaoDeA, inexistente, corpo, true);

        for (Map.Entry<String, ResponseEntity<String>> caso : Map.of(
                "material de B", respostaAlheio,
                "slug inexistente", respostaInexistente).entrySet()) {
            assertThat(caso.getValue().getStatusCode().value())
                    .as("PUT no slug de %s -> 404", caso.getKey())
                    .isEqualTo(404);
            assertThat(caso.getValue().getHeaders().getContentType())
                    .as("o erro sai no formato único do contrato (RFC 7807)")
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(caso.getValue().getBody())
                    .contains("\"type\":\"urn:scipath:erro:nao_encontrado\"")
                    .contains("\"title\":\"Recurso não encontrado\"")
                    .contains("\"status\":404")
                    .contains("\"detail\":\"O recurso solicitado não foi encontrado.\"")
                    .doesNotContain("\"errors\"")
                    .as("nenhum rastro do material alheio: nem título, nem categoria, nem motivo")
                    .doesNotContain(TITULO_EDITADO)
                    .doesNotContain("Material " + MATERIAL_DE_B);
        }

        assertThat(semInstance(respostaAlheio.getBody()))
                .as("material alheio e material inexistente respondem o MESMO corpo (ADR-0011 §3)")
                .isEqualTo(semInstance(respostaInexistente.getBody()));

        // A metade que mais importa: a recusa não deixou rastro nenhum no banco.
        assertThat(linhaNoBanco(alheio))
                .as("o material de B ficou exatamente como estava — nenhuma escrita aconteceu")
                .isEqualTo(antes);

        System.out.println("[E5.3] PUT (A) no material de B -> 404 " + respostaAlheio.getBody());
        System.out.println("[E5.3] material de B no banco, antes e depois: inalterado");
    }

    // ------------------------------------------------------- critério 3: EXCLUIDO é terminal

    @Test
    @DisplayName("material EXCLUIDO responde 404 (o mesmo do inexistente) e não é alterado")
    void materialExcluidoResponde404SemAlteracao() {
        String excluido = PREFIXO + "a-excluido";
        String inexistente = PREFIXO + "nao-existe";
        Map<String, Object> antes = linhaNoBanco(excluido);

        ResponseEntity<String> resposta = editar(sessaoDeA, excluido, corpoValido().toString(), true);
        ResponseEntity<String> doInexistente = editar(sessaoDeA, inexistente, corpoValido().toString(), true);

        assertThat(resposta.getStatusCode().value())
                .as("EXCLUIDO é terminal no MVP: não há ação nenhuma nele (ADR-0011 §2, §6)")
                .isEqualTo(404);
        assertThat(semInstance(resposta.getBody()))
                .as("o 404 de EXCLUIDO é o mesmo do inexistente — sem oráculo de estado")
                .isEqualTo(semInstance(doInexistente.getBody()));
        assertThat(linhaNoBanco(excluido))
                .as("nenhuma escrita em material excluído")
                .isEqualTo(antes);

        System.out.println("[E5.3] PUT em material EXCLUIDO -> 404 " + resposta.getBody());
    }

    // ---------------------------------------------- critério 3: 401 sem sessão, 403 sem CSRF

    @Test
    @DisplayName("sem sessão é 401; com sessão e sem X-CSRF-Token é 403; e nenhuma das três recusas escreve")
    void semSessaoEh401ESemCsrfEh403() {
        String slug = PREFIXO + "a-publicado";
        Map<String, Object> antes = linhaNoBanco(slug);
        String corpo = corpoValido().toString();

        // Sem cookie de sessão nenhuma: 401 (e não 403 de CSRF) — a autenticação precede o CSRF.
        ResponseEntity<String> semSessao = editar(null, slug, corpo, true);
        assertThat(semSessao.getStatusCode().value())
                .as("escrita sem sessão -> 401")
                .isEqualTo(401);
        assertThat(semSessao.getBody())
                .contains("\"type\":\"urn:scipath:erro:nao_autenticado\"")
                .contains("\"status\":401")
                .contains("\"detail\":\"É necessário entrar na conta para esta operação.\"")
                .doesNotContain("\"errors\"");

        // Sessão de verdade, mas sem o cabeçalho: 403 do interceptor.
        ResponseEntity<String> semCsrf = editar(sessaoDeA, slug, corpo, false);
        assertThat(semCsrf.getStatusCode().value())
                .as("com sessão e sem X-CSRF-Token -> 403")
                .isEqualTo(403);
        assertThat(semCsrf.getBody())
                .contains("\"type\":\"urn:scipath:erro:acesso_negado\"")
                .contains("\"status\":403")
                .contains("X-CSRF-Token");

        // Cabeçalho presente, mas de outra sessão: o token duplo não fecha contra a linha da sessão.
        ResponseEntity<String> csrfDeOutro = editar(sessaoDeA, slug, corpo, sessaoDeB.csrf());
        assertThat(csrfDeOutro.getStatusCode().value())
                .as("token de CSRF de outra sessão -> 403")
                .isEqualTo(403);

        assertThat(linhaNoBanco(slug))
                .as("nenhuma das três recusas escreveu nada no banco")
                .isEqualTo(antes);

        System.out.println("[E5.3] sem sessão -> 401 " + semSessao.getBody());
        System.out.println("[E5.3] sem X-CSRF-Token -> 403 " + semCsrf.getBody());
    }

    // ---------------------------------------------------------------- escopo 4: não exige ATIVO

    @Test
    @DisplayName("conta PENDENTE_VERIFICACAO edita o próprio material: editar não é publicar (ADR-0003 (b))")
    void contaPendenteEditaOProprioMaterial() {
        // A conta é criada e autenticada, mas NÃO é ativada: quem publica precisa de e-mail
        // confirmado; quem mantém no público o que já está lá, não (ADR-0003 (b)).
        String email = cadastrar("pendente", "Paula do Painel");
        Sessao pendente = autenticar(email);
        String slug = PREFIXO + "pendente-publicado";
        inserirMaterial(perfilId(email), "pendente-publicado", "PUBLICADO");

        ResponseEntity<String> resposta = editar(pendente, slug, corpoValido().toString(), true);

        assertThat(resposta.getStatusCode().value())
                .as("editar não exige conta ATIVO: a verificação é de quem põe no público, não de quem mantém")
                .isEqualTo(200);
        assertThat(linhaNoBanco(slug).get("titulo")).isEqualTo(TITULO_EDITADO);

        System.out.println("[E5.3] PUT com conta PENDENTE_VERIFICACAO -> 200 " + resposta.getBody());
    }

    // ------------------------------------------------------------------ 422: valor inválido

    @Test
    @DisplayName("taxonomia inexistente ou inativa é 422 no campo, com o mesmo vocabulário da publicação (RF018)")
    void taxonomiaInexistenteOuInativaEh422NoCampo() {
        String slug = PREFIXO + "a-publicado";
        Map<String, Object> antes = linhaNoBanco(slug);

        Map<String, String> casos = new LinkedHashMap<>();
        casos.put("categoriaSlug", "nao-existe-categoria");
        casos.put("tipoSlug", "nao-existe-tipo");
        casos.put("licencaSlug", "nao-existe-licenca");

        for (Map.Entry<String, String> caso : casos.entrySet()) {
            ObjectNode corpo = corpoValido();
            corpo.put(caso.getKey(), caso.getValue());
            ResponseEntity<String> resposta = editar(sessaoDeA, slug, corpo.toString(), true);

            assertThat(resposta.getStatusCode().value())
                    .as("%s inexistente -> 422 (não é 400: o corpo veio e o valor não passou)", caso.getKey())
                    .isEqualTo(422);
            assertThat(json(resposta.getBody()).get("type").asText())
                    .isEqualTo("urn:scipath:erro:validacao_campo");
            assertThat(erroDoCampo(resposta, caso.getKey()).get("mensagem").asText())
                    .as("a mensagem nomeia o que faltou no campo (RF018)")
                    .contains("inexistente ou inativ");
            assertThat(linhaNoBanco(slug))
                    .as("a recusa de taxonomia não escreve nada")
                    .isEqualTo(antes);

            System.out.println("[E5.3] PUT com " + caso.getKey() + " inexistente -> 422 " + resposta.getBody());
        }

        // Slug que EXISTE mas está inativo: mesma recusa, mesmo campo (ADR-0006, RF018).
        jdbc.update("UPDATE material_type SET ativo = false WHERE slug = ?", OUTRO_TIPO);
        try {
            ResponseEntity<String> inativa = editar(sessaoDeA, slug, corpoValido().toString(), true);

            assertThat(inativa.getStatusCode().value())
                    .as("tipo de material inativo -> 422: a lista pública de referência só mostra ativos")
                    .isEqualTo(422);
            assertThat(erroDoCampo(inativa, "tipoSlug").get("mensagem").asText()).contains("inexistente ou inativ");
            assertThat(linhaNoBanco(slug)).as("recusa não escreve").isEqualTo(antes);

            System.out.println("[E5.3] PUT com tipoSlug inativo -> 422 " + inativa.getBody());
        } finally {
            jdbc.update("UPDATE material_type SET ativo = true WHERE slug = ?", OUTRO_TIPO);
        }
    }

    @Test
    @DisplayName("campo presente com valor inválido é 422 no campo — título vazio, texto acima do teto e slug malformado")
    void campoComValorInvalidoEh422() {
        String slug = PREFIXO + "a-publicado";
        Map<String, Object> antes = linhaNoBanco(slug);

        Map<String, String[]> casos = new LinkedHashMap<>();
        casos.put("titulo em branco", new String[] {"titulo", " "});
        casos.put("titulo acima de 200", new String[] {"titulo", "t".repeat(201)});
        casos.put("descricao acima de 2000", new String[] {"descricao", "d".repeat(2001)});
        casos.put("categoriaSlug fora do formato", new String[] {"categoriaSlug", "Matemática!e estatística"});
        casos.put("tipoSlug fora do formato", new String[] {"tipoSlug", "slides_antigos"});
        casos.put("licencaSlug fora do formato", new String[] {"licencaSlug", "CC BY 4.0"});

        for (Map.Entry<String, String[]> caso : casos.entrySet()) {
            ObjectNode corpo = corpoValido();
            corpo.put(caso.getValue()[0], caso.getValue()[1]);
            ResponseEntity<String> resposta = editar(sessaoDeA, slug, corpo.toString(), true);

            assertThat(resposta.getStatusCode().value())
                    .as("%s -> 422", caso.getKey())
                    .isEqualTo(422);
            assertThat(json(resposta.getBody()).get("type").asText()).isEqualTo("urn:scipath:erro:validacao_campo");
            assertThat(erroDoCampo(resposta, caso.getValue()[0]).get("mensagem").asText()).isNotBlank();
            assertThat(linhaNoBanco(slug)).as("recusa de valor não escreve").isEqualTo(antes);

            System.out.println("[E5.3] PUT " + caso.getKey() + " -> 422 " + resposta.getBody());
        }
    }

    // ------------------------------------------------------------------ 400: campo ausente

    @Test
    @DisplayName("campo obrigatório ausente no corpo é 400 com o campo em errors[], e nenhum deles escreve")
    void campoAusenteEh400() {
        String slug = PREFIXO + "a-publicado";
        Map<String, Object> antes = linhaNoBanco(slug);

        for (String campo : List.of("titulo", "descricao", "categoriaSlug", "tipoSlug", "licencaSlug")) {
            ObjectNode corpo = corpoValido();
            corpo.remove(campo);
            ResponseEntity<String> resposta = editar(sessaoDeA, slug, corpo.toString(), true);

            assertThat(resposta.getStatusCode().value())
                    .as("corpo sem %s -> 400 (o servidor não leu o pedido como veio; não é 422)", campo)
                    .isEqualTo(400);
            assertThat(json(resposta.getBody()).get("type").asText()).isEqualTo("urn:scipath:erro:validacao");
            assertThat(erroDoCampo(resposta, campo).get("mensagem").asText()).isEqualTo("é obrigatório");
            assertThat(resposta.getBody())
                    .as("o detalhe diz onde o campo faltou: aqui o corpo é JSON, não formulário (RNF004)")
                    .contains("Falta o campo obrigatório '" + campo + "' no corpo da requisição.")
                    .doesNotContain("no formulário");
            assertThat(linhaNoBanco(slug)).as("recusa de presença não escreve").isEqualTo(antes);

            System.out.println("[E5.3] PUT sem " + campo + " -> 400 " + resposta.getBody());
        }

        // Corpo vazio e corpo que não é objeto: o mesmo 400 do primeiro campo obrigatório da ordem.
        for (String corpo : List.of("{}", "[]")) {
            ResponseEntity<String> resposta = editar(sessaoDeA, slug, corpo, true);
            assertThat(resposta.getStatusCode().value())
                    .as("corpo %s -> 400", corpo)
                    .isEqualTo(400);
            System.out.println("[E5.3] PUT corpo " + corpo + " -> 400 " + resposta.getBody());
        }
        assertThat(linhaNoBanco(slug)).isEqualTo(antes);
    }

    // ------------------------------------------------------------------ 400: slug malformado

    @Test
    @DisplayName("slug fora do formato responde 400 no ProblemDetail padrão, sem tocar em material nenhum")
    void slugForaDoFormatoEh400() {
        for (String slug : List.of("Material-Invalido", "material_com_underscore", "a".repeat(121))) {
            ResponseEntity<String> resposta = editar(sessaoDeA, slug, corpoValido().toString(), true);

            assertThat(resposta.getStatusCode().value())
                    .as("PUT em '%s' -> 400 (o endereço está malformado; não é o recurso que falta)", slug)
                    .isEqualTo(400);
            assertThat(resposta.getBody())
                    .contains("\"type\":\"urn:scipath:erro:validacao\"")
                    .contains("\"campo\":\"slug\"");

            System.out.println("[E5.3] PUT slug " + resumido(slug) + " -> 400 " + resposta.getBody());
        }

        assertThat(jdbc.queryForList("SELECT slug FROM material WHERE slug LIKE ?", String.class, PREFIXO + "%"))
                .as("nenhum slug malformado virou linha — o pedido nem chegou ao serviço")
                .containsExactlyInAnyOrder(PREFIXO + "a-publicado", PREFIXO + "a-retirado",
                        PREFIXO + "a-excluido", PREFIXO + MATERIAL_DE_B);
    }

    // ---------------------------------------------------------------------------- sessão

    /** Sessão como o cliente a tem: o valor da cookie de sessão e o token CSRF que ela carrega. */
    private record Sessao(String token, String csrf) {
    }

    /** Cadastra a conta pelo fluxo de verdade e devolve o e-mail dela. */
    private String cadastrar(String identificador, String nomePublico) {
        String email = emailDe(identificador);
        String csrfDoVisitante = csrfDeVisitante();

        ResponseEntity<String> cadastro = rest.exchange("/api/contas", HttpMethod.POST,
                new HttpEntity<>(JSON.createObjectNode().put("email", email).put("senha", SENHA)
                        .put("nomePublico", nomePublico).toString(), cabecalhosDeJson(csrfDoVisitante)),
                String.class);
        assertThat(cadastro.getStatusCode().value())
                .as("cadastro de apoio falhou: %s", cadastro.getBody())
                .isEqualTo(201);
        return email;
    }

    /**
     * Conta <b>ativa</b> e autenticada. A ativação é por SQL, e não pelo fluxo de confirmação de
     * e-mail (E3.4): o que esta suíte mede é a edição, e passar pelo token faria cada caso depender
     * de outro card.
     */
    private Sessao cadastrarElogar(String identificador, String nomePublico) {
        String email = cadastrar(identificador, nomePublico);
        assertThat(jdbc.update("UPDATE account SET estado = 'ATIVO', email_verificado_em = now()"
                + " WHERE email = ?", email)).isEqualTo(1);
        return autenticar(email);
    }

    /** Login de verdade, sem mexer no estado da conta — é o caminho da conta pendente. */
    private Sessao autenticar(String email) {
        String csrfDoVisitante = csrfDeVisitante();
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

    /** {@code PUT} da edição com o CSRF da própria sessão (ou nenhum, quando {@code comCsrf} é falso). */
    private ResponseEntity<String> editar(Sessao sessao, String slug, String corpo, boolean comCsrf) {
        return editar(sessao, slug, corpo, comCsrf && sessao != null ? sessao.csrf() : null);
    }

    /**
     * {@code PUT} da edição, como o cliente o manda: cookie de sessão, cookie de CSRF,
     * {@code Content-Type} JSON e — quando não é nulo — o cabeçalho {@code X-CSRF-Token}. O token do
     * cabeçalho é parâmetro para poder mandar o de <b>outra</b> sessão e medir a recusa.
     */
    private ResponseEntity<String> editar(Sessao sessao, String slug, String corpo, String csrf) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        if (sessao != null) {
            cabecalhos.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_SESSAO + "=" + sessao.token());
            cabecalhos.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_CSRF + "=" + sessao.csrf());
        }
        if (csrf != null) {
            cabecalhos.add("X-CSRF-Token", csrf);
        }
        return rest.exchange(ROTA + "/" + slug, HttpMethod.PUT, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    /**
     * O conjunto editável inteiro, válido, apontando para <b>outra</b> taxonomia ativa: é o corpo
     * que os casos de sucesso usam, e a partir dele cada caso de recusa estraga um campo só.
     */
    private static ObjectNode corpoValido() {
        ObjectNode corpo = JSON.createObjectNode();
        corpo.put("titulo", TITULO_EDITADO);
        corpo.put("descricao", DESCRICAO_EDITADA);
        corpo.put("categoriaSlug", OUTRA_CATEGORIA);
        corpo.put("tipoSlug", OUTRO_TIPO);
        corpo.put("licencaSlug", OUTRA_LICENCA);
        return corpo;
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

    private UUID idDaTaxonomia(String tabela, String slug) {
        return jdbc.queryForObject("SELECT id FROM " + tabela + " WHERE slug = ?", UUID.class, slug);
    }

    /**
     * A linha do material como o banco a guarda — <b>todos</b> os campos que a edição pode mudar e
     * todos os que ela não pode. É esta leitura que dá conteúdo a "nenhuma alteração": comparar a
     * linha inteira antes e depois é o que descarta uma escrita indevida que não aparecesse na
     * resposta. {@code SELECT *} de propósito: coluna nova na migração aparece aqui sozinha.
     */
    private Map<String, Object> linhaNoBanco(String slug) {
        return jdbc.queryForMap("SELECT * FROM material WHERE slug = ?", slug);
    }

    /** Instante de uma coluna de tempo, lido como {@link Instant} para comparar sem fuso no meio. */
    private Instant instanteNoBanco(String slug, String coluna) {
        return jdbc.queryForObject("SELECT " + coluna + " FROM material WHERE slug = ?", Instant.class, slug);
    }

    /** O mesmo instante, a partir da linha já lida — evita uma segunda ida ao banco. */
    private static Instant instanteDe(Map<String, Object> linha, String coluna) {
        Object valor = linha.get(coluna);
        if (valor instanceof Instant instante) {
            return instante;
        }
        if (valor instanceof OffsetDateTime dataHora) {
            return dataHora.toInstant();
        }
        // `SELECT *` pelo JdbcTemplate devolve `timestamptz` como `java.sql.Timestamp` (o
        // `OffsetDateTime` acima aparece quando a coluna é pedida pelo nome, com o tipo declarado).
        if (valor instanceof java.sql.Timestamp carimbo) {
            return carimbo.toInstant();
        }
        throw new AssertionError("coluna '" + coluna + "' não é data-hora: " + valor);
    }

    /**
     * Semeia {@code file_asset} + {@code material} com o estado pedido. O arquivo nasce
     * {@code VALIDADO} e <b>sem binário</b> no armazenamento: a edição não toca no arquivo e este
     * card não mede disponibilidade — o que ele mede é metadado, dono e data.
     */
    private void inserirMaterial(UUID perfil, String identificador, String estadoPublicacao) {
        String chave = PREFIXO + "chave-" + identificador;
        String nomeOriginal = PREFIXO + identificador + ".pdf";

        jdbc.update("""
                INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,
                                        tamanho_bytes, checksum, estado_processamento)
                VALUES (?, ?, 'application/pdf', ?, repeat('c', 64), 'VALIDADO')
                """, chave, nomeOriginal, (long) CONTEUDO.length);

        jdbc.update("""
                INSERT INTO material (slug, titulo, descricao, profile_id, category_id, material_type_id,
                                      license_id, file_asset_id, estado_publicacao, publicado_em, atualizado_em)
                SELECT ?, ?, ?, ?,
                       (SELECT id FROM category WHERE slug = ?),
                       (SELECT id FROM material_type WHERE slug = ?),
                       (SELECT id FROM license WHERE slug = ?),
                       (SELECT id FROM file_asset WHERE chave_interna = ?),
                       ?, ?, ?
                """, PREFIXO + identificador, "Material " + identificador,
                "Descrição de apoio do material " + identificador + ".",
                perfil, CATEGORIA, TIPO, LICENCA, chave,
                estadoPublicacao, emUtc(PUBLICADO_EM), emUtc(ATUALIZADO_EM));
    }

    /**
     * Remove só o que esta classe criou, na ordem que as chaves estrangeiras exigem
     * ({@code material} → {@code file_asset} → {@code account}).
     */
    private void limparDadosDeTeste() {
        jdbc.update("DELETE FROM material WHERE slug LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM file_asset WHERE chave_interna LIKE ?", PREFIXO + "%");
        // Apagar a conta remove o perfil junto (ON DELETE CASCADE da V1).
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
    }

    // -------------------------------------------------------------------------- auxiliares

    /** Corpo do 404 sem o {@code instance} — o único campo que pode diferir entre dois 404. */
    private static String semInstance(String corpo) {
        return corpo.replaceFirst("\"instance\":\"[^\"]*\"", "\"instance\":\"\"");
    }

    /** O erro de {@code errors[]} que aponta o campo pedido, ou falha dizendo o corpo recebido. */
    private static JsonNode erroDoCampo(ResponseEntity<String> resposta, String campo) {
        for (JsonNode erro : json(resposta.getBody()).get("errors")) {
            if (campo.equals(erro.get("campo").asText())) {
                return erro;
            }
        }
        throw new AssertionError("errors[] não aponta o campo '" + campo + "': " + resposta.getBody());
    }

    private static String resumido(String slug) {
        return slug.length() > 40 ? slug.substring(0, 12) + "...(" + slug.length() + " caracteres)" : slug;
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
