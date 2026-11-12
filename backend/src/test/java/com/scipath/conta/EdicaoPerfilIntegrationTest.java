package com.scipath.conta;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scipath.identidade.CookiesDeSessao;
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
 * Edição do próprio perfil (RF011, RN009, RN011, CA007, CA012) exercitada de ponta a ponta:
 * contexto completo, MVC completo (o 401/403 do interceptor e o 422/409 do
 * {@code ProblemDetail} padrão, não um corpo montado à mão) e o PostgreSQL local de
 * {@code scipath_test}. Sem mock — o que o card exige provar é o que o <b>SQL</b> guardou, o
 * que a <b>resposta HTTP</b> mostra e o que o <b>perfil público da Etapa 2</b> passa a
 * devolver depois da edição.
 *
 * <p><b>A sessão é a do login</b> ({@code POST /api/sessoes}), reapresentada à mão nas
 * requisições seguintes — o valor da cookie {@code SCIPATH_SESSAO} e o token CSRF que ela
 * carrega. É o mesmo caminho de um navegador, e é o que faz a propriedade vir da sessão
 * (ADR-0003 (j)) em vez de um parâmetro de teste. O login é refeito antes de cada escrita de
 * propósito: o perfil de teste encurta a inatividade da sessão para <b>segundos</b> (E3.3, para
 * o teste de expiração caber numa execução) e a sessão do cadastro expiraria no meio de uma
 * sequência longa de requisições — prazo de sessão não é o assunto deste card, e um teste que
 * falha por relógio não prova nada sobre autorização.
 *
 * <p><b>Dados controlados.</b> E-mails carregam o prefixo {@code teste-e3-5-} e o banco é
 * limpo antes e depois de cada teste (apagar a conta leva perfil e sessões junto, por
 * {@code ON DELETE CASCADE}): a suíte não depende da ordem das classes nem deixa linha para
 * trás.
 *
 * <p><b>O que este teste não faz:</b> não verifica expiração de sessão, rotação nem logout —
 * isso é o card E3.3, em {@code LoginSessaoIntegrationTest}. Aqui a sessão é só o meio de
 * provar quem edita.
 *
 * <p>Mesma configuração de contexto das outras integrações ({@code RANDOM_PORT}): o Spring
 * reusa o contexto já subido e a máquina tem 954 MB (AGENTS.md, higiene de recursos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("edição do próprio perfil (RF011, RN009)")
class EdicaoPerfilIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Marcador dos dados desta classe em e-mail (e, por consequência, nas linhas que ela cria). */
    private static final String PREFIXO = "teste-e3-5-";
    private static final String DOMINIO = "@example.test";
    private static final String SENHA = "nota-de-aula-2026";

    private static final String URL_DA_EDICAO = "/api/perfil/atual";
    private static final String URL_DO_CADASTRO = "/api/contas";
    private static final String URL_DO_LOGIN = "/api/sessoes";
    private static final String URL_DO_ESTADO = "/api/sessoes/atual";

    /** Afiliação de teste: declaração do autor, sem nenhum termo de aval ou verificação. */
    private static final String AFILIACAO =
            "Declaro vínculo com o Instituto de Exemplo (declaração própria do autor).";
    private static final String BIOGRAFIA =
            "Professora de programação; escreve material introdutório de algoritmos.";
    private static final String LINK = "https://github.com/ana-perfil";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limparAntes() {
        limparDadosDeTeste();
    }

    @AfterEach
    void limparDepois() {
        limparDadosDeTeste();
    }

    // ------------------------------------------ critério 1: dono edita e o público reflete

    @Test
    @DisplayName("edição pelo dono responde 200 e o perfil público da Etapa 2 passa a mostrar o valor novo")
    void edicaoPeloDonoRefleteNoPerfilPublico() throws Exception {
        String email = PREFIXO + "ana" + DOMINIO;
        cadastrar(email, "Ana de Perfil");
        ativar(email);

        // Antes: a página pública do autor (Etapa 2) mostra o perfil derivado do cadastro.
        JsonNode antes = JSON.readTree(publico("ana-de-perfil").getBody());
        System.out.println("[E3.5] perfil público ANTES -> " + antes);

        ObjectNode corpoDaEdicao = JSON.createObjectNode();
        corpoDaEdicao.put("nomePublico", "Ana Maria Silva");
        corpoDaEdicao.put("slug", "ana-maria-silva");
        corpoDaEdicao.put("biografia", BIOGRAFIA);
        corpoDaEdicao.put("afiliacaoDeclarada", AFILIACAO);
        corpoDaEdicao.putArray("areasInteresse").add("algoritmos").add("estruturas de dados");
        corpoDaEdicao.putArray("linksExternos").add(LINK);

        ResponseEntity<String> resposta = editar(logar(email), corpoDaEdicao);

        assertThat(resposta.getStatusCode().value()).as("200 é o status publicado no contrato").isEqualTo(200);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        System.out.println("[E3.5] PUT /api/perfil/atual -> 200 " + corpo);

        assertThat(camposDe(corpo))
                .as("a resposta é o schema PerfilPublico, o mesmo do perfil público do autor")
                .containsExactlyInAnyOrder("slug", "nomePublico", "biografia", "afiliacaoDeclarada",
                        "areasInteresse", "linksExternos");
        assertThat(corpo.get("slug").asText()).isEqualTo("ana-maria-silva");
        assertThat(corpo.get("nomePublico").asText()).isEqualTo("Ana Maria Silva");
        assertThat(corpo.get("biografia").asText()).isEqualTo(BIOGRAFIA);
        assertThat(corpo.get("afiliacaoDeclarada").asText())
                .as("afiliação DECLARADA pelo autor, nunca vínculo verificado (RN011)")
                .isEqualTo(AFILIACAO);
        assertThat(textos(corpo.get("areasInteresse"))).containsExactly("algoritmos", "estruturas de dados");
        assertThat(textos(corpo.get("linksExternos"))).containsExactly(LINK);

        // O que ficou gravado é o que a resposta afirmou (RN013).
        Map<String, Object> linha = perfilNoBanco(email);
        assertThat(linha.get("nome_publico")).isEqualTo("Ana Maria Silva");
        assertThat(linha.get("slug")).isEqualTo("ana-maria-silva");
        assertThat(linha.get("biografia")).isEqualTo(BIOGRAFIA);
        assertThat(linha.get("afiliacao_declarada")).isEqualTo(AFILIACAO);
        assertThat(jdbc.queryForList(
                "SELECT unnest(areas_interesse) FROM profile"
                        + " WHERE account_id = (SELECT id FROM account WHERE email = ?)",
                String.class, email)).containsExactly("algoritmos", "estruturas de dados");

        // Depois: a MESMA rota da Etapa 2, sem rota nova, agora com o valor editado.
        ResponseEntity<String> depois = publico("ana-maria-silva");
        assertThat(depois.getStatusCode().value()).as("perfil de conta ativa é público").isEqualTo(200);
        JsonNode publico = JSON.readTree(depois.getBody());
        System.out.println("[E3.5] perfil público DEPOIS -> " + publico);

        assertThat(publico.get("slug").asText()).isEqualTo("ana-maria-silva");
        assertThat(publico.get("nomePublico").asText()).isEqualTo("Ana Maria Silva");
        assertThat(publico.get("biografia").asText()).isEqualTo(BIOGRAFIA);
        assertThat(publico.get("afiliacaoDeclarada").asText()).isEqualTo(AFILIACAO);
        assertThat(textos(publico.get("areasInteresse"))).containsExactly("algoritmos", "estruturas de dados");
        assertThat(textos(publico.get("linksExternos"))).containsExactly(LINK);
        assertThat(publico.get("materiais").get("items")).as("a página do autor continua no envelope padrão")
                .isEmpty();

        assertThat(publico("ana-de-perfil").getStatusCode().value())
                .as("o slug antigo deixa de existir: o perfil tem um identificador só (RN009)")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("editar o próprio perfil não exige e-mail verificado; sem verificação o perfil segue fora do público")
    void editarNaoExigeEmailVerificado() throws Exception {
        String email = PREFIXO + "pendente" + DOMINIO;
        cadastrar(email, "Pendente de Perfil");

        ResponseEntity<String> resposta = editar(logar(email), JSON.createObjectNode()
                .put("nomePublico", "Pendente Editado")
                .put("slug", "pendente-editado")
                .put("biografia", "Edita antes de confirmar o e-mail."));

        assertThat(resposta.getStatusCode().value())
                .as("ADR-0003 (b): navegar e editar o perfil não exigem verificação")
                .isEqualTo(200);
        assertThat(JSON.readTree(resposta.getBody()).get("nomePublico").asText()).isEqualTo("Pendente Editado");
        assertThat(perfilNoBanco(email).get("slug")).isEqualTo("pendente-editado");

        assertThat(jdbc.queryForObject("SELECT estado FROM account WHERE email = ?", String.class, email))
                .as("a edição não muda o estado da conta")
                .isEqualTo("PENDENTE_VERIFICACAO");
        assertThat(publico("pendente-editado").getStatusCode().value())
                .as("quem publica exige ATIVO; o perfil de conta pendente não é público")
                .isEqualTo(404);
    }

    // ------------------------------------------------ critério 2: propriedade vem da sessão

    @Test
    @DisplayName("identificador no corpo não alcança o perfil alheio: recusa a requisição e não muda a linha de ninguém (CA007)")
    void identificadorNoCorpoNaoAlcancaPerfilAlheio() throws Exception {
        String emailA = PREFIXO + "ana" + DOMINIO;
        String emailB = PREFIXO + "bruno" + DOMINIO;
        cadastrar(emailA, "Ana de Perfil");
        cadastrar(emailB, "Bruno de Outro Autor");

        UUID contaB = contaDe(emailB);
        UUID perfilB = jdbc.queryForObject("SELECT id FROM profile WHERE account_id = ?", UUID.class, contaB);
        String antesDeB = perfilComoTexto(emailB);
        String antesDeA = perfilComoTexto(emailA);

        // A tentativa: sessão de A e um corpo que NOMEIA a conta/perfil de B. O schema
        // EdicaoPerfil não tem esses campos e a desserialização é estrita — o identificador é
        // recusado como corpo que o servidor não processa, em vez de ignorado em silêncio.
        ObjectNode tentativa = JSON.createObjectNode()
                .put("nomePublico", "Ana Tomou a Conta do Bruno")
                .put("slug", "ana-tomou-a-conta-do-bruno")
                .put("biografia", "tentativa de escrita no perfil alheio")
                .put("contaId", contaB.toString())
                .put("perfilId", perfilB.toString())
                .put("email", emailB);

        ResponseEntity<String> resposta = editar(logar(emailA), tentativa);
        System.out.println("[E3.5] PUT com identificador alheio -> " + resposta.getStatusCode().value()
                + " " + resposta.getBody());

        assertThat(resposta.getStatusCode().value())
                .as("CA007: o identificador não existe no schema — a requisição é recusada")
                .isEqualTo(400);
        assertThat(JSON.readTree(resposta.getBody()).get("type").asText())
                .as("erro no formato único do contrato (RFC 7807)")
                .isEqualTo("urn:scipath:erro:validacao");

        assertThat(perfilComoTexto(emailB))
                .as("o perfil da outra conta NÃO muda no banco")
                .isEqualTo(antesDeB);
        assertThat(perfilComoTexto(emailA))
                .as("e a requisição recusada também não edita o perfil de quem a enviou")
                .isEqualTo(antesDeA);

        // Controle: a MESMA conta edita o próprio perfil — a recusa acima é do identificador,
        // não de a escrita ser impossível.
        assertThat(editar(logar(emailA), JSON.createObjectNode()
                .put("nomePublico", "Ana Editada")
                .put("slug", "ana-editada")).getStatusCode().value())
                .as("sem identificador no corpo, a escrita do próprio perfil é aceita")
                .isEqualTo(200);

        assertThat(perfilNoBanco(emailA).get("slug")).isEqualTo("ana-editada");
        assertThat(perfilComoTexto(emailB))
                .as("a edição legítima de A continua sem tocar a linha de B")
                .isEqualTo(antesDeB);
    }

    // ------------------------------------------------- critério 3: slug único e validado

    @Test
    @DisplayName("slug de outra conta responde 409; slug fora do formato, curto ou reservado responde 422")
    void slugDuplicado409ESlugInvalido422() throws Exception {
        String emailA = PREFIXO + "ana" + DOMINIO;
        String emailB = PREFIXO + "bruno" + DOMINIO;
        cadastrar(emailA, "Ana de Perfil");
        cadastrar(emailB, "Maria Silva E3.5");

        // 409: o slug pertence a outra conta (RN009), com o campo culpado em errors[].
        ResponseEntity<String> conflito = editar(logar(emailA), corpoDeEdicao("Ana de Perfil", "maria-silva-e3-5"));
        assertThat(conflito.getStatusCode().value()).isEqualTo(409);
        JsonNode corpoDoConflito = JSON.readTree(conflito.getBody());
        System.out.println("[E3.5] slug de outra conta -> 409 " + conflito.getBody());
        assertThat(corpoDoConflito.get("type").asText()).isEqualTo("urn:scipath:erro:conflito_de_unicidade");
        assertThat(corpoDoConflito.get("detail").asText()).isEqualTo("Este identificador público já está em uso.");
        assertThat(corpoDoConflito.get("instance").asText()).isEqualTo(URL_DA_EDICAO);
        assertThat(corpoDoConflito.get("errors").get(0).get("campo").asText()).isEqualTo("slug");
        assertThat(perfilNoBanco(emailA).get("slug")).as("o 409 não grava nada").isEqualTo("ana-de-perfil");
        assertThat(perfilNoBanco(emailB).get("slug")).as("nem mexe no perfil dono do slug").isEqualTo("maria-silva-e3-5");

        // 422: formato, tamanho e reserva — cada um com o campo slug apontado.
        Map<String, String> invalidos = new java.util.LinkedHashMap<>();
        invalidos.put("Maria_Silva", "deve ser um slug");
        invalidos.put("maria--silva", "deve ser um slug");
        invalidos.put("ab", "deve ter entre 3 e 120 caracteres");
        invalidos.put("api", "reservado pela aplicação");

        for (Map.Entry<String, String> caso : invalidos.entrySet()) {
            ResponseEntity<String> resposta = editar(logar(emailA), corpoDeEdicao("Ana de Perfil", caso.getKey()));
            assertThat(resposta.getStatusCode().value())
                    .as("slug '%s' -> 422", caso.getKey())
                    .isEqualTo(422);
            JsonNode corpo = JSON.readTree(resposta.getBody());
            assertThat(corpo.get("type").asText()).isEqualTo("urn:scipath:erro:validacao_campo");
            assertThat(corpo.get("errors").get(0).get("campo").asText()).isEqualTo("slug");
            assertThat(corpo.get("errors").findValues("mensagem").stream().map(JsonNode::asText).toList())
                    .as("mensagem acionável para '%s'", caso.getKey())
                    .anyMatch(mensagem -> mensagem.contains(caso.getValue()));
            System.out.println("[E3.5] slug inválido '" + caso.getKey() + "' -> 422 " + resposta.getBody());
        }
        assertThat(perfilNoBanco(emailA).get("slug")).as("nenhum 422 grava").isEqualTo("ana-de-perfil");

        // Repetir o próprio slug não é colisão: a edição de bio não pode exigir trocar o slug.
        assertThat(editar(logar(emailA), JSON.createObjectNode()
                .put("nomePublico", "Ana de Perfil")
                .put("slug", "ana-de-perfil")
                .put("biografia", "Nova biografia, mesmo slug.")).getStatusCode().value())
                .as("o próprio slug não colide consigo mesmo")
                .isEqualTo(200);
        assertThat(perfilNoBanco(emailA).get("biografia")).isEqualTo("Nova biografia, mesmo slug.");
    }

    @Test
    @DisplayName("o cadastro nunca gera um slug reservado: nome 'Api' vira api-2")
    void cadastroNaoGeraSlugReservado() throws Exception {
        String email = PREFIXO + "reservado" + DOMINIO;
        cadastrar(email, "Api");

        assertThat(perfilNoBanco(email).get("slug"))
                .as("a derivação desvia da palavra reservada; sem isso o dono não conseguiria salvar o perfil")
                .isEqualTo("api-2");
        assertThat(editar(logar(email), corpoDeEdicao("Api", "api")).getStatusCode().value())
                .as("e o slug reservado continua recusado na edição")
                .isEqualTo(422);
    }

    // ---------------------------------------------------- critério 4: opcionais vazios

    @Test
    @DisplayName("campos opcionais vazios ou ausentes respondem 200 e a página pública não sai com valor quebrado")
    void camposOpcionaisVaziosNaoQuebramAPagina() throws Exception {
        String email = PREFIXO + "vazios" + DOMINIO;
        cadastrar(email, "João Souza");
        ativar(email);

        // Vazios explícitos (string vazia, lista vazia, string só de espaços) e um ausente.
        ObjectNode corpoDosVazios = JSON.createObjectNode();
        corpoDosVazios.put("nomePublico", "João Souza");
        corpoDosVazios.put("slug", "joao-souza");
        corpoDosVazios.put("biografia", "");
        corpoDosVazios.put("afiliacaoDeclarada", "   ");
        corpoDosVazios.putArray("areasInteresse");
        corpoDosVazios.putArray("linksExternos").add("").add("   ");

        ResponseEntity<String> resposta = editar(logar(email), corpoDosVazios);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        System.out.println("[E3.5] PUT com opcionais vazios -> 200 " + corpo);

        assertThat(corpo.has("biografia")).as("vazio não vira string vazia na resposta (§13.2)").isFalse();
        assertThat(corpo.has("afiliacaoDeclarada")).isFalse();
        assertThat(corpo.get("areasInteresse")).isEmpty();
        assertThat(corpo.get("linksExternos")).isEmpty();
        assertThat(corpo.toString()).as("nem null nem vazio em nenhum campo").doesNotContain("null", "\"\"");

        // No banco, "não informado" é NULL — o mesmo estado de um perfil que nunca foi editado.
        Map<String, Object> linha = perfilNoBanco(email);
        assertThat(linha.get("biografia")).isNull();
        assertThat(linha.get("afiliacao_declarada")).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT areas_interesse IS NULL AND links_externos IS NULL FROM profile"
                        + " WHERE account_id = (SELECT id FROM account WHERE email = ?)",
                Boolean.class, email))
                .as("lista que esvazia vira coluna nula (RN004)")
                .isTrue();

        // A página pública da Etapa 2 responde com a identidade mínima, sem nada quebrado.
        ResponseEntity<String> pagina = publico("joao-souza");
        assertThat(pagina.getStatusCode().value()).isEqualTo(200);
        JsonNode publico = JSON.readTree(pagina.getBody());
        System.out.println("[E3.5] perfil público com opcionais vazios -> " + publico);

        assertThat(publico.has("biografia")).isFalse();
        assertThat(publico.has("afiliacaoDeclarada")).isFalse();
        assertThat(publico.get("areasInteresse")).isEmpty();
        assertThat(publico.get("linksExternos")).isEmpty();
        assertThat(publico.toString())
                .as("nem 'undefined', nem 'null', nem string vazia na página")
                .doesNotContain("undefined", "null", "\"\"");

        // E o corpo sem os opcionais de forma alguma vale o mesmo.
        assertThat(editar(logar(email), corpoDeEdicao("João Souza", "joao-souza")).getStatusCode().value())
                .isEqualTo(200);
        assertThat(publico("joao-souza").getBody()).isEqualTo(pagina.getBody());
    }

    // ------------------------------- critério 5: nenhum dado de conta no privado nem no público

    @Test
    @DisplayName("nem a resposta privada nem a pública carregam e-mail, hash, token ou id de conta (RNF013)")
    void respostaNaoTrazDadoPrivado() throws Exception {
        String email = PREFIXO + "ana" + DOMINIO;
        cadastrar(email, "Ana de Perfil");
        ativar(email);

        ResponseEntity<String> edicao = editar(logar(email), JSON.createObjectNode()
                .put("nomePublico", "Ana Maria Silva")
                .put("slug", "ana-maria-silva")
                .put("biografia", BIOGRAFIA)
                .put("afiliacaoDeclarada", AFILIACAO));
        assertThat(edicao.getStatusCode().value()).isEqualTo(200);

        String privado = edicao.getBody();
        String publico = publico("ana-maria-silva").getBody();

        for (String corpo : List.of(privado, publico)) {
            for (String proibido : List.of("email", "senha", "hash", "token", "account", "conta",
                    "profile_id", "profileId", "contaId", "perfilId", "\"id\"")) {
                assertThat(corpo)
                        .as("campo privado '%s' no payload de perfil (RNF013)", proibido)
                        .doesNotContain(proibido);
            }
            assertThat(corpo)
                    .as("o e-mail de login não aparece em nenhuma forma (RNF013)")
                    .doesNotContain(email, DOMINIO);
            for (String aval : List.of("revis", "curad", "certific", "verifica", "verificado",
                    "selo", "aprovad", "parecer", "peer", "avaliad")) {
                assertThat(corpo.toLowerCase())
                        .as("marca de aval ou de vínculo verificado '%s' (RN011, CA012)", aval)
                        .doesNotContain(aval);
            }
        }
        System.out.println("[E3.5] varredura privado+público (dado de conta e aval): sem ocorrência");
    }

    // ------------------------------------------------ critério 6: sem cookie → 401, sem CSRF → 403

    @Test
    @DisplayName("fora da interface responde 401 sem sessão e 403 sem o token CSRF, e nada é gravado")
    void escritaExigeSessaoEProtecaoCsrf() throws Exception {
        String email = PREFIXO + "ana" + DOMINIO;
        cadastrar(email, "Ana de Perfil");
        String antes = perfilComoTexto(email);
        String corpo = corpoDeEdicao("Ana Invadida", "ana-invadida").toString();
        Sessao sessao = logar(email);

        // Sem cookie nenhuma: 401 do contrato, antes de qualquer regra de CSRF.
        ResponseEntity<String> semSessao = chamar(HttpMethod.PUT, URL_DA_EDICAO, corpo, null, null, null);
        System.out.println("[E3.5] PUT sem sessão -> 401 " + semSessao.getBody());
        assertThat(semSessao.getStatusCode().value()).isEqualTo(401);
        JsonNode erro = JSON.readTree(semSessao.getBody());
        assertThat(erro.get("type").asText()).isEqualTo("urn:scipath:erro:nao_autenticado");
        assertThat(erro.get("detail").asText()).isEqualTo("É necessário entrar na conta para esta operação.");
        assertThat(erro.get("instance").asText()).isEqualTo(URL_DA_EDICAO);
        assertThat(erro.has("errors")).as("401 não aponta campo").isFalse();

        // Com sessão e sem o cabeçalho CSRF (ou com um que não casa): 403.
        ResponseEntity<String> semCsrf = chamar(HttpMethod.PUT, URL_DA_EDICAO, corpo, sessao, sessao.csrf(), null);
        ResponseEntity<String> csrfErrado = chamar(HttpMethod.PUT, URL_DA_EDICAO, corpo, sessao,
                sessao.csrf(), "valor-que-nao-e-o-token-da-sessao");
        for (ResponseEntity<String> resposta : List.of(semCsrf, csrfErrado)) {
            System.out.println("[E3.5] PUT com sessão e CSRF inválido -> 403 " + resposta.getBody());
            assertThat(resposta.getStatusCode().value()).isEqualTo(403);
            assertThat(JSON.readTree(resposta.getBody()).get("type").asText())
                    .isEqualTo("urn:scipath:erro:acesso_negado");
        }

        assertThat(perfilComoTexto(email)).as("nenhuma recusa grava nada").isEqualTo(antes);

        // Controle: com sessão e CSRF corretos a escrita passa (é a mesma requisição).
        Sessao viva = logar(email);
        assertThat(chamar(HttpMethod.PUT, URL_DA_EDICAO, corpo, viva, viva.csrf(), viva.csrf())
                .getStatusCode().value()).isEqualTo(200);
        assertThat(perfilNoBanco(email).get("slug")).isEqualTo("ana-invadida");
    }

    // ------------------------------------------------------------------ utilidades

    /** Sessão como o cliente a tem: o valor da cookie de sessão e o token CSRF que ela carrega. */
    private record Sessao(String token, String csrf) {
    }

    /** Cadastro pelo endpoint do contrato. A sessão que ele abre não é usada: quem edita é o login. */
    private void cadastrar(String email, String nomePublico) {
        String csrf = csrfDeVisitante();
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_DO_CADASTRO,
                JSON.createObjectNode().put("email", email).put("senha", SENHA)
                        .put("nomePublico", nomePublico).toString(),
                null, csrf, csrf);
        assertThat(resposta.getStatusCode().value()).as("cadastro de apoio falhou: " + resposta.getBody())
                .isEqualTo(201);
    }

    /**
     * Abre uma sessão pelo login — a identidade que as escritas reapresentam.
     *
     * <p>O login é chamado imediatamente antes de cada escrita (ver o javadoc da classe): o
     * prazo de inatividade do perfil de teste é de segundos, e o que este card prova não é
     * prazo de sessão.
     */
    private Sessao logar(String email) {
        String csrf = csrfDeVisitante();
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_DO_LOGIN,
                JSON.createObjectNode().put("email", email).put("senha", SENHA).toString(),
                null, csrf, csrf);
        assertThat(resposta.getStatusCode().value()).as("login de apoio falhou: " + resposta.getBody())
                .isEqualTo(200);
        return new Sessao(
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow(),
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow());
    }

    /** Edição do próprio perfil com a sessão e o CSRF dela — o caminho de um navegador. */
    private ResponseEntity<String> editar(Sessao sessao, ObjectNode corpo) {
        return chamar(HttpMethod.PUT, URL_DA_EDICAO, corpo.toString(), sessao, sessao.csrf(), sessao.csrf());
    }

    private static ObjectNode corpoDeEdicao(String nomePublico, String slug) {
        return JSON.createObjectNode().put("nomePublico", nomePublico).put("slug", slug);
    }

    /** Perfil público do autor — a rota da Etapa 2, sem autenticação. */
    private ResponseEntity<String> publico(String slug) {
        return rest.getForEntity("/api/autores/" + slug, String.class);
    }

    /** Token CSRF que o frontend lê na cookie XSRF-TOKEN da primeira leitura do app. */
    private String csrfDeVisitante() {
        ResponseEntity<String> estado = rest.getForEntity(URL_DO_ESTADO, String.class);
        return valorDaCookie(estado, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow(
                () -> new AssertionError("GET " + URL_DO_ESTADO + " não emitiu a cookie XSRF-TOKEN"));
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

    /**
     * Marca a conta como ATIVO — o estado que a verificação de e-mail (E3.4) produz. O caminho
     * do e-mail não é o assunto deste card: o que ele prova é que a edição reflete no perfil
     * público, e a visibilidade pública exige {@code ATIVO} (ADR-0003 (b)).
     */
    private void ativar(String email) {
        jdbc.update("UPDATE account SET estado = 'ATIVO', email_verificado_em = now() WHERE email = ?", email);
    }

    private UUID contaDe(String email) {
        return jdbc.queryForObject("SELECT id FROM account WHERE email = ?", UUID.class, email);
    }

    /** A linha de {@code profile} como o SQL a guarda — a prova de que a resposta não mente (RN013). */
    private Map<String, Object> perfilNoBanco(String email) {
        return jdbc.queryForMap("SELECT nome_publico, slug, biografia, afiliacao_declarada,"
                + " areas_interesse, links_externos FROM profile"
                + " WHERE account_id = (SELECT id FROM account WHERE email = ?)", email);
    }

    /**
     * A linha inteira do perfil como <b>texto</b>, inclusive {@code atualizado_em}.
     *
     * <p>Serve para comparar "antes" e "depois" de uma requisição recusada: as colunas
     * {@code text[]} chegam como {@code java.sql.Array}, que não tem {@code equals}, e o
     * instante de atualização é o que denuncia uma escrita que a resposta não admitiu.
     */
    private String perfilComoTexto(String email) {
        return jdbc.queryForObject("SELECT nome_publico || '|' || slug || '|' || coalesce(biografia, '~')"
                + " || '|' || coalesce(afiliacao_declarada, '~')"
                + " || '|' || coalesce(array_to_string(areas_interesse, ','), '~')"
                + " || '|' || coalesce(array_to_string(links_externos, ','), '~')"
                + " || '|' || atualizado_em FROM profile"
                + " WHERE account_id = (SELECT id FROM account WHERE email = ?)", String.class, email);
    }

    /** Valor da cookie pelo nome, se a resposta a emitiu. */
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

    private static List<String> textos(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText).toList();
    }

    private static List<String> camposDe(JsonNode objeto) {
        return java.util.stream.StreamSupport.stream(
                ((Iterable<String>) () -> objeto.fieldNames()).spliterator(), false).toList();
    }

    /** Remove só as linhas criadas por esta classe (perfil e sessões saem por cascata). */
    private void limparDadosDeTeste() {
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
    }
}
