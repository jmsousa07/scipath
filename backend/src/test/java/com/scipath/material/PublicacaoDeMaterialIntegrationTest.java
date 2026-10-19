package com.scipath.material;

import static com.scipath.material.ApoioDePublicacao.CATEGORIA;
import static com.scipath.material.ApoioDePublicacao.LICENCA;
import static com.scipath.material.ApoioDePublicacao.PREFIXO;
import static com.scipath.material.ApoioDePublicacao.TIPO;
import static com.scipath.material.ApoioDePublicacao.camposValidos;
import static com.scipath.material.ApoioDePublicacao.caminhoDaApi;
import static com.scipath.material.ApoioDePublicacao.corpo;
import static com.scipath.material.ApoioDePublicacao.emailDe;
import static com.scipath.material.ApoioDePublicacao.erroDoCampo;
import static com.scipath.material.ApoioDePublicacao.pdf;
import static com.scipath.material.ApoioDePublicacao.sha256Hex;
import static com.scipath.material.ApoioDePublicacao.tipoDoErro;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.scipath.arquivo.PropriedadesDeArmazenamento;
import com.scipath.arquivo.PropriedadesDeUpload;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
 * Publicação de material pela <b>borda HTTP de verdade</b> (card E4.4, RF012, RF018, RF019, RN008,
 * RN009): contexto completo, Tomcat, multipart real, {@code ProblemDetail} do contrato v0.5 e o
 * PostgreSQL local de {@code scipath_test}.
 *
 * <p><b>Esta é a borda que substitui o adaptador de teste do E4.2.</b> O
 * {@code ControladorDeTesteDeRecebimento} existia porque o endpoint do contrato não existia; agora
 * existe, e as recusas do recebimento (400, 413, 415) são verificadas <b>através dele</b> — o que
 * prova que a validação de arquivo do E4.2 chegou intacta à rota de produção, e não só que ela
 * funciona isolada num controller de teste.
 *
 * <p><b>O limite de upload observado é o do perfil de teste</b> (1 MB, {@code application-test.yml})
 * e não os 30 MB do ADR-0005 §1, pelo mesmo motivo do E4.2: um limite cravado no código não
 * passaria neste teste.
 *
 * <p><b>O que os casos medem é o efeito observável</b>: a resposta HTTP, a linha em
 * {@code material}, a linha em {@code file_asset} e o arquivo nos dois diretórios. Nenhum caso
 * chama o serviço direto — a evidência do card é o comportamento da rota.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("publicação de material pela borda HTTP (RF012, RF019, RN008)")
class PublicacaoDeMaterialIntegrationTest {

    private static final String AUTOR = "autor";

    /** Caminho do arquivo do material publicado (contrato v0.4/E4.3). */
    private static final String SUFIXO_DO_ARQUIVO = "/arquivo";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PropriedadesDeUpload upload;

    @Autowired
    private PropriedadesDeArmazenamento armazenamento;

    private ApoioDePublicacao apoio;

    @BeforeEach
    void preparar() {
        apoio = new ApoioDePublicacao(rest, jdbc, upload, armazenamento);
        apoio.limpar();
    }

    @AfterEach
    void limpar() {
        apoio.limpar();
    }

    // ------------------------------------------------------------------ caminho feliz

    @Test
    @DisplayName("publicação válida: 201 com slug do servidor, estado PUBLICADO e urlPublica")
    void publicacaoValidaCriaMaterialPublicado() {
        var sessao = apoio.autorAtivo(AUTOR);
        byte[] conteudo = pdf(2048);
        String nome = PREFIXO + "valido.pdf";

        ResponseEntity<String> resposta = apoio.publicarValido(sessao, conteudo, nome);

        System.out.println("[E4.4] 201 POST /api/materiais -> " + resposta.getBody());
        assertThat(resposta.getStatusCode().value()).isEqualTo(201);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        JsonNode publicado = corpo(resposta);
        assertThat(publicado.get("slug").asText()).isEqualTo("ordenacao-por-comparacao-na-pratica");
        assertThat(publicado.get("estado").asText())
                .as("RF019: o material nasce publicado")
                .isEqualTo("PUBLICADO");
        assertThat(publicado.get("urlPublica").asText())
                .as("RNF025: a URL pública é derivada do slug, e o cliente navega para ela")
                .isEqualTo("/materiais/ordenacao-por-comparacao-na-pratica");
        assertThat(publicado.properties().stream().map(Map.Entry::getKey))
                .as("o schema MaterialPublicado tem exatamente estes campos")
                .containsExactlyInAnyOrder("slug", "estado", "urlPublica");

        // A linha gravada: autoria, taxonomia resolvida por slug e o arquivo da mesma requisição.
        List<Map<String, Object>> materiais = apoio.materiaisDoAutor(emailDe(AUTOR));
        assertThat(materiais).hasSize(1);
        Map<String, Object> material = materiais.get(0);
        assertThat(material.get("estado_publicacao")).isEqualTo("PUBLICADO");
        assertThat(material.get("categoria")).isEqualTo(CATEGORIA);
        assertThat(material.get("tipo")).isEqualTo(TIPO);
        assertThat(material.get("licenca")).isEqualTo(LICENCA);
        assertThat(material.get("perfil_id")).isEqualTo(apoio.perfilId(emailDe(AUTOR)));
        assertThat(material.get("publicado_em")).as("publicado_em é do banco, não do cliente").isNotNull();

        // O arquivo: registrado, validado e agora com o binário no armazenamento definitivo (RN008).
        String chave = (String) material.get("chave_interna");
        Map<String, Object> arquivo = apoio.linhaDoArquivo(nome);
        assertThat(arquivo.get("chave_interna")).isEqualTo(chave);
        assertThat(arquivo.get("tipo_detectado")).as("validação por conteúdo, não por extensão").isEqualTo("application/pdf");
        assertThat(((Number) arquivo.get("tamanho_bytes")).longValue()).isEqualTo(conteudo.length);
        assertThat(arquivo.get("checksum")).isEqualTo(sha256Hex(conteudo));
        assertThat(apoio.arquivoArmazenado(chave))
                .as("RN008 passo 2: o binário está no armazenamento definitivo")
                .isTrue();
        assertThat(apoio.arquivosNaAreaTemporaria())
                .as("RN012: a área temporária não guarda o que a publicação levou")
                .isEmpty();
        System.out.println("[E4.4] file_asset " + chave + " -> "
                + apoio.estadoDoArquivo(chave).orElseThrow() + " | material " + material.get("id"));

        // Visto de fora: o detalhe público responde e o arquivo é servido na mesma slug.
        ResponseEntity<String> detalhe = apoio.ler(null, "/api/materiais/" + publicado.get("slug").asText());
        assertThat(detalhe.getStatusCode().value()).isEqualTo(200);
        assertThat(corpo(detalhe).get("estado").asText()).isEqualTo("PUBLICADO");
        assertThat(corpo(detalhe).get("autor").get("nomePublico").asText()).isEqualTo("Autor " + AUTOR);
        assertThat(corpo(detalhe).get("categoria").get("slug").asText()).isEqualTo(CATEGORIA);
        assertThat(corpo(detalhe).get("tipo").get("slug").asText()).isEqualTo(TIPO);
        assertThat(corpo(detalhe).get("licenca").get("slug").asText()).isEqualTo(LICENCA);
        assertThat(corpo(detalhe).get("titulo").asText()).isEqualTo(camposValidos().get("titulo"));
        // O metadado `arquivo` do schema MaterialDetalhe não sai do detalhe do E4.3 — divergência
        // do contrato encontrada ao integrar os dois cards, registrada no handoff do E4.4 e fora
        // do escopo daqui: o E4.4 publica o material, não a leitura dele. O que este caso prova
        // sobre o arquivo é o que a publicação decide: o binário está no armazenamento definitivo
        // e é servido por GET /materiais/{slug}/arquivo (afirmações acima e abaixo).
        ResponseEntity<String> download = apoio.ler(null,
                caminhoDaApi(publicado.get("urlPublica").asText()) + SUFIXO_DO_ARQUIVO);
        assertThat(download.getStatusCode().value()).as("E4.3: o arquivo publicado é servido").isEqualTo(200);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(download.getBody().getBytes(StandardCharsets.ISO_8859_1)).isEqualTo(conteudo);
    }

    @Test
    @DisplayName("publicar exige sessão: sem cookie é 401 e nada é gravado")
    void semSessaoNaoPublica() {
        byte[] conteudo = pdf(512);
        String nome = PREFIXO + "anonimo.pdf";

        ResponseEntity<String> resposta = apoio.publicarSemSessao(camposValidos(), conteudo, nome);

        System.out.println("[E4.4] 401 POST sem sessão -> " + resposta.getBody());
        assertThat(resposta.getStatusCode().value()).isEqualTo(401);
        assertThat(tipoDoErro(resposta)).isEqualTo("urn:scipath:erro:nao_autenticado");
        assertThat(apoio.totalDeMateriais(camposValidos().get("titulo")))
                .as("a recusa acontece antes de qualquer gravação")
                .isZero();
        assertThat(apoio.linhasDoArquivo(nome)).as("nem o arquivo foi registrado").isEmpty();
        assertThat(apoio.arquivosNaAreaTemporaria()).isEmpty();
        assertThat(apoio.arquivosNoArmazenamento()).isEmpty();
    }

    @Test
    @DisplayName("conta PENDENTE_VERIFICACAO não publica: 403 com motivo, e sem arquivo e sem linha")
    void contaPendenteNaoPublica() {
        String email = emailDe("pendente");
        apoio.cadastrar(email, "Autor Pendente");
        var sessao = apoio.logar(email);
        byte[] conteudo = pdf(512);
        String nome = PREFIXO + "pendente.pdf";

        ResponseEntity<String> resposta = apoio.publicarValido(sessao, conteudo, nome);

        System.out.println("[E4.4] 403 conta pendente -> " + resposta.getBody());
        assertThat(resposta.getStatusCode().value()).isEqualTo(403);
        assertThat(tipoDoErro(resposta)).isEqualTo("urn:scipath:erro:acesso_negado");
        assertThat(resposta.getBody())
                .as("ADR-0003 (b): a recusa diz o que falta, não só que faltou permissão")
                .contains("Confirme seu e-mail para publicar material.");
        assertThat(apoio.totalDeMateriais(camposValidos().get("titulo"))).isZero();
        assertThat(apoio.linhasDoArquivo(nome))
                .as("a verificação vem ANTES do recebimento: o arquivo nem chega a ser registrado")
                .isEmpty();
        assertThat(apoio.arquivosNaAreaTemporaria()).isEmpty();
        assertThat(apoio.arquivosNoArmazenamento()).isEmpty();
    }

    @Test
    @DisplayName("taxonomia inexistente ou inativa é 422 com o campo exato, sem gravar nada")
    void taxonomiaInexistenteEhRecusada() {
        var sessao = apoio.autorAtivo("taxonomia");
        String nome = PREFIXO + "taxonomia.pdf";

        for (String campo : List.of("categoriaSlug", "tipoSlug", "licencaSlug")) {
            Map<String, String> campos = new LinkedHashMap<>(camposValidos());
            campos.put(campo, "nao-existe-" + campo);

            ResponseEntity<String> resposta = apoio.publicar(sessao, campos, pdf(512), nome, "application/pdf");

            System.out.println("[E4.4] 422 " + campo + " inexistente -> " + resposta.getBody());
            assertThat(resposta.getStatusCode().value()).isEqualTo(422);
            assertThat(tipoDoErro(resposta)).isEqualTo("urn:scipath:erro:validacao_campo");
            assertThat(erroDoCampo(resposta, campo).get("mensagem").asText()).isNotBlank();
            assertThat(apoio.totalDeMateriais(camposValidos().get("titulo"))).isZero();
        }
        assertThat(apoio.linhasDoArquivo(nome))
                .as("nenhuma das três recusas chegou a registrar o arquivo")
                .isEmpty();
        assertThat(apoio.arquivosNaAreaTemporaria()).isEmpty();
        assertThat(apoio.arquivosNoArmazenamento()).isEmpty();
    }

    @Test
    @DisplayName("taxonomia inativa é 422 igual à inexistente — slug existe, mas não se publica nele")
    void taxonomiaInativaEhRecusada() {
        var sessao = apoio.autorAtivo("inativa");
        jdbc.update("UPDATE material_type SET ativo = false WHERE slug = ?", TIPO);
        try {
            ResponseEntity<String> resposta = apoio.publicarValido(sessao, pdf(512), PREFIXO + "inativa.pdf");

            System.out.println("[E4.4] 422 tipo inativo -> " + resposta.getBody());
            assertThat(resposta.getStatusCode().value()).isEqualTo(422);
            assertThat(erroDoCampo(resposta, "tipoSlug").get("mensagem").asText()).isNotBlank();
            assertThat(apoio.totalDeMateriais(camposValidos().get("titulo"))).isZero();
        } finally {
            jdbc.update("UPDATE material_type SET ativo = true WHERE slug = ?", TIPO);
        }
    }

    // ------------------------------------------------------------------ campos obrigatórios

    @Test
    @DisplayName("campo obrigatório ausente no formulário é 400, e o arquivo ausente também")
    void campoObrigatorioAusenteEhRecusado() {
        var sessao = apoio.autorAtivo("ausente");

        for (String campo : List.of("titulo", "descricao", "categoriaSlug", "tipoSlug", "licencaSlug")) {
            Map<String, String> campos = new LinkedHashMap<>(camposValidos());
            campos.remove(campo);

            ResponseEntity<String> resposta = apoio.publicar(sessao, campos, pdf(512),
                    PREFIXO + "ausente-" + campo + ".pdf", "application/pdf");

            System.out.println("[E4.4] 400 sem " + campo + " -> " + resposta.getBody());
            assertThat(resposta.getStatusCode().value()).isEqualTo(400);
            assertThat(erroDoCampo(resposta, campo).get("mensagem").asText()).contains("obrigatório");
            assertThat(apoio.totalDeMateriais(camposValidos().get("titulo"))).isZero();
        }

        // Sem a parte do arquivo: o contrato exige o arquivo na mesma requisição (RF012).
        ResponseEntity<String> semArquivo = apoio.publicar(sessao, camposValidos(), null,
                PREFIXO + "ausente-arquivo.pdf", "application/pdf");
        System.out.println("[E4.4] 400 sem arquivo -> " + semArquivo.getBody());
        assertThat(semArquivo.getStatusCode().value()).isEqualTo(400);
        assertThat(erroDoCampo(semArquivo, "arquivo").get("mensagem").asText()).contains("obrigatório");
        assertThat(apoio.totalDeMateriais(camposValidos().get("titulo"))).isZero();
        assertThat(apoio.arquivosNaAreaTemporaria()).isEmpty();
    }

    @Test
    @DisplayName("campo presente e vazio é 422, não 400 — o 400 é só para o campo que falta")
    void campoPresenteVazioEh422() {
        var sessao = apoio.autorAtivo("vazio");
        Map<String, String> campos = new LinkedHashMap<>(camposValidos());
        campos.put("titulo", "   ");

        ResponseEntity<String> resposta = apoio.publicar(sessao, campos, pdf(512),
                PREFIXO + "vazio.pdf", "application/pdf");

        System.out.println("[E4.4] 422 título em branco -> " + resposta.getBody());
        assertThat(resposta.getStatusCode().value()).isEqualTo(422);
        assertThat(erroDoCampo(resposta, "titulo").get("mensagem").asText()).contains("em branco");
        assertThat(apoio.totalDeMateriais(camposValidos().get("titulo"))).isZero();
    }

    @Test
    @DisplayName("título acima do máximo do contrato é 422, e o limite não é literal: 200 do schema")
    void tituloAcimaDoMaximoEh422() {
        var sessao = apoio.autorAtivo("longo");
        Map<String, String> campos = new LinkedHashMap<>(camposValidos());
        campos.put("titulo", "T".repeat(201));

        ResponseEntity<String> resposta = apoio.publicar(sessao, campos, pdf(512),
                PREFIXO + "longo.pdf", "application/pdf");

        System.out.println("[E4.4] 422 título com 201 caracteres -> " + erroDoCampo(resposta, "titulo"));
        assertThat(resposta.getStatusCode().value()).isEqualTo(422);
        assertThat(erroDoCampo(resposta, "titulo").get("mensagem").asText()).contains("200");
        assertThat(apoio.totalDeMateriais(campos.get("titulo"))).isZero();
    }

    // ------------------------------------------------------------------ direito de compartilhar

    @Test
    @DisplayName("declaração de direito falsa ou ausente é 422 — sem ela não há publicação (ADR-0005 §3)")
    void declaracaoDeDireitoNaoConfirmada() {
        var sessao = apoio.autorAtivo("direito");

        Map<String, String> falsa = new LinkedHashMap<>(camposValidos());
        falsa.put("declaracaoDireito", "false");
        ResponseEntity<String> comFalsa = apoio.publicar(sessao, falsa, pdf(512),
                PREFIXO + "direito-falso.pdf", "application/pdf");
        System.out.println("[E4.4] 422 declaração falsa -> " + comFalsa.getBody());
        assertThat(comFalsa.getStatusCode().value()).isEqualTo(422);
        assertThat(erroDoCampo(comFalsa, "declaracaoDireito").get("mensagem").asText())
                .isEqualTo("é preciso confirmar que você tem o direito de compartilhar o material");

        Map<String, String> ausente = new LinkedHashMap<>(camposValidos());
        ausente.remove("declaracaoDireito");
        ResponseEntity<String> semDeclaracao = apoio.publicar(sessao, ausente, pdf(512),
                PREFIXO + "direito-ausente.pdf", "application/pdf");
        System.out.println("[E4.4] 422 declaração ausente -> " + semDeclaracao.getBody());
        assertThat(semDeclaracao.getStatusCode().value())
                .as("ausente e false são o mesmo estado: não confirmado — e o contrato fixa 422")
                .isEqualTo(422);
        assertThat(erroDoCampo(semDeclaracao, "declaracaoDireito")).isNotNull();

        assertThat(apoio.totalDeMateriais(camposValidos().get("titulo"))).isZero();
        assertThat(apoio.arquivosNaAreaTemporaria()).isEmpty();
        assertThat(apoio.arquivosNoArmazenamento()).isEmpty();
    }

    // ------------------------------------------------------------------ estado

    @Test
    @DisplayName("estado arbitrário é recusado: o cliente não define o estado do material (RF019)")
    void estadoArbitrarioEhRecusado() {
        var sessao = apoio.autorAtivo("estado");

        Map<String, String> arbitrario = new LinkedHashMap<>(camposValidos());
        arbitrario.put("estado", "EXCLUIDO");
        ResponseEntity<String> recusado = apoio.publicar(sessao, arbitrario, pdf(512),
                PREFIXO + "estado-arbitrario.pdf", "application/pdf");
        System.out.println("[E4.4] 422 estado=EXCLUIDO -> " + recusado.getBody());
        assertThat(recusado.getStatusCode().value()).isEqualTo(422);
        assertThat(erroDoCampo(recusado, "estado").get("mensagem").asText()).contains("PUBLICADO");
        assertThat(apoio.totalDeMateriais(camposValidos().get("titulo"))).isZero();

        // O estado inicial declarado coincide com a decisão do servidor: aceito.
        Map<String, String> inicial = new LinkedHashMap<>(camposValidos());
        inicial.put("estado", "PUBLICADO");
        ResponseEntity<String> aceito = apoio.publicar(sessao, inicial, pdf(512),
                PREFIXO + "estado-inicial.pdf", "application/pdf");
        System.out.println("[E4.4] 201 estado=PUBLICADO -> " + aceito.getBody());
        assertThat(aceito.getStatusCode().value()).isEqualTo(201);
        assertThat(corpo(aceito).get("estado").asText()).isEqualTo("PUBLICADO");
    }

    // ------------------------------------------------------------------ autoria

    @Test
    @DisplayName("campo de autor no corpo não tem destino: a autoria vem da sessão (ADR-0003 (j))")
    void autorDoCorpoEhIgnorado() {
        var sessao = apoio.autorAtivo("proprio");
        UUID perfilDeOutraConta = perfilDeOutraConta();

        Map<String, String> campos = new LinkedHashMap<>(camposValidos());
        campos.put("autor", "outra-pessoa");
        campos.put("autorSlug", "outra-pessoa");
        campos.put("profileId", perfilDeOutraConta.toString());
        campos.put("perfilSlug", "outra-pessoa");
        campos.put("contaId", perfilDeOutraConta.toString());
        campos.put("email", "outra-pessoa" + ApoioDePublicacao.DOMINIO);
        campos.put("estadoConta", "ATIVO");

        ResponseEntity<String> resposta = apoio.publicar(sessao, campos, pdf(512),
                PREFIXO + "autor.pdf", "application/pdf");

        System.out.println("[E4.4] 201 com autor forjado no corpo -> " + resposta.getBody());
        assertThat(resposta.getStatusCode().value()).isEqualTo(201);

        List<Map<String, Object>> materiais = apoio.materiaisDoAutor(emailDe("proprio"));
        assertThat(materiais).hasSize(1);
        assertThat(materiais.get(0).get("perfil_id"))
                .as("o perfil gravado é o da sessão, não o do formulário")
                .isEqualTo(apoio.perfilId(emailDe("proprio")))
                .isNotEqualTo(perfilDeOutraConta);

        ResponseEntity<String> detalhe = apoio.ler(null,
                "/api/materiais/" + corpo(resposta).get("slug").asText());
        assertThat(corpo(detalhe).get("autor").get("nomePublico").asText()).isEqualTo("Autor proprio");
    }

    /** Uma segunda conta, só para o caso do autor forjado ter um identificador alheio de verdade. */
    private UUID perfilDeOutraConta() {
        String email = emailDe("estranho");
        apoio.cadastrar(email, "Estranho");
        return apoio.perfilId(email);
    }

    // ------------------------------------------------------------------ slug

    @Test
    @DisplayName("título repetido não sobrescreve material: o segundo recebe sufixo e o primeiro mantém o slug")
    void slugRepetidoRecebeSufixoEOPrimeiroEhPreservado() {
        var sessao = apoio.autorAtivo("slug");
        String primeiroNome = PREFIXO + "slug-um.pdf";
        String segundoNome = PREFIXO + "slug-dois.pdf";

        ResponseEntity<String> primeiro = apoio.publicarValido(sessao, pdf(512), primeiroNome);
        ResponseEntity<String> segundo = apoio.publicarValido(sessao, pdf(1024), segundoNome);

        System.out.println("[E4.4] 201 slug 1 -> " + corpo(primeiro).get("slug").asText());
        System.out.println("[E4.4] 201 slug 2 -> " + corpo(segundo).get("slug").asText());
        assertThat(primeiro.getStatusCode().value()).isEqualTo(201);
        assertThat(segundo.getStatusCode().value()).isEqualTo(201);
        assertThat(corpo(primeiro).get("slug").asText()).isEqualTo("ordenacao-por-comparacao-na-pratica");
        assertThat(corpo(segundo).get("slug").asText()).isEqualTo("ordenacao-por-comparacao-na-pratica-2");

        // Os dois materiais existem, com arquivos diferentes.
        List<Map<String, Object>> materiais = apoio.materiaisDoAutor(emailDe("slug"));
        assertThat(materiais).hasSize(2);
        assertThat(materiais.stream().map(linha -> (String) linha.get("slug")).toList())
                .containsExactlyInAnyOrder("ordenacao-por-comparacao-na-pratica",
                        "ordenacao-por-comparacao-na-pratica-2");

        // RN009/RNF025: o slug é estável — publicar outro material não muda a URL do primeiro.
        String slugDoPrimeiro = corpo(primeiro).get("slug").asText();
        ResponseEntity<String> detalhe = apoio.ler(null, "/api/materiais/" + slugDoPrimeiro);
        assertThat(detalhe.getStatusCode().value()).isEqualTo(200);
        assertThat(corpo(detalhe).get("slug").asText()).isEqualTo(slugDoPrimeiro);
        ResponseEntity<String> arquivoDoPrimeiro = apoio.ler(null,
                "/api/materiais/" + slugDoPrimeiro + SUFIXO_DO_ARQUIVO);
        assertThat(arquivoDoPrimeiro.getStatusCode().value()).isEqualTo(200);
        String chaveDoPrimeiro = (String) materiais.stream()
                .filter(linha -> slugDoPrimeiro.equals(linha.get("slug")))
                .findFirst().orElseThrow().get("chave_interna");
        assertThat(apoio.arquivoArmazenado(chaveDoPrimeiro))
                .as("o arquivo do primeiro material continua no armazenamento, não é sobrescrito")
                .isTrue();
        assertThat(apoio.linhaDoArquivo(segundoNome).get("chave_interna"))
                .as("cada material tem o seu arquivo — o segundo não herda o do primeiro")
                .isNotEqualTo(chaveDoPrimeiro);
        assertThat(apoio.linhaDoArquivo(primeiroNome).get("chave_interna")).isEqualTo(chaveDoPrimeiro);
    }

    // ------------------------------------------------------------------ recusas do arquivo (E4.2)

    @Test
    @DisplayName("as recusas do recebimento chegam inteiras à rota de produção: 415 e 413")
    void recusasDoArquivoAtravessamOPost() {
        var sessao = apoio.autorAtivo("arquivo");

        // Não é PDF por conteúdo, mesmo com Content-Type de PDF e nome .pdf (RN008 passo 1).
        ResponseEntity<String> naoPdf = apoio.publicarValido(sessao,
                "nao sou um pdf, so finjo ser".getBytes(StandardCharsets.UTF_8), PREFIXO + "falso.pdf");
        System.out.println("[E4.4] 415 conteúdo não-PDF -> " + naoPdf.getBody());
        assertThat(naoPdf.getStatusCode().value()).isEqualTo(415);
        assertThat(tipoDoErro(naoPdf)).isEqualTo("urn:scipath:erro:tipo_nao_suportado");

        // Acima do limite do perfil de teste (1 MB).
        ResponseEntity<String> grande = apoio.publicarValido(sessao, pdf(1_500_000), PREFIXO + "grande.pdf");
        System.out.println("[E4.4] 413 arquivo acima do limite -> " + grande.getBody());
        assertThat(grande.getStatusCode().value()).isEqualTo(413);
        assertThat(tipoDoErro(grande)).isEqualTo("urn:scipath:erro:arquivo_muito_grande");

        // O endpoint só aceita o formulário: JSON puro é 415, e o motivo diz o que ele aceita.
        ResponseEntity<String> comoJson = apoio.publicarComoJson(sessao);
        System.out.println("[E4.4] 415 corpo JSON -> " + comoJson.getBody());
        assertThat(comoJson.getStatusCode().value()).isEqualTo(415);
        assertThat(comoJson.getBody()).contains("multipart/form-data");

        assertThat(apoio.totalDeMateriais(camposValidos().get("titulo"))).isZero();
        assertThat(apoio.arquivosNaAreaTemporaria())
                .as("RN012: a área temporária é limpa mesmo quando o recebimento recusa")
                .isEmpty();
        assertThat(apoio.arquivosNoArmazenamento())
                .as("o que não passou pela validação nunca chega ao armazenamento")
                .isEmpty();
    }

    @Test
    @DisplayName("nome de arquivo hostil não vira caminho: o cliente manda travessia e o disco recebe a chave (RNF011)")
    void nomeHostilNaoEscapaDoDiretorio() {
        var sessao = apoio.autorAtivo("hostil");
        String nome = PREFIXO + "hostil.pdf";

        // Caminho relativo no nome original: o saneamento tira o diretório e neutraliza os pontos.
        ResponseEntity<String> resposta = apoio.publicarValido(sessao, pdf(512), "../../fora/" + nome);
        System.out.println("[E4.4] 201 nome hostil ../../fora/" + nome + " -> " + resposta.getBody());
        assertThat(resposta.getStatusCode().value()).isEqualTo(201);

        Map<String, Object> arquivo = apoio.linhaDoArquivo(nome);
        String chave = (String) arquivo.get("chave_interna");
        assertThat(arquivo.get("nome_original_seguro"))
                .as("RNF011: o nome guardado é o último componente, sem diretório e sem '..'")
                .isEqualTo(nome);
        assertThat(chave).as("a chave interna não tem o nome original — o disco não vê o nome do cliente")
                .doesNotContain("hostil");
        assertThat(apoio.arquivoArmazenado(chave)).isTrue();
        Path destino = apoio.armazenamentoDefinitivo().resolve(chave).normalize();
        assertThat(destino.getParent())
                .as("RNF011: o destino é o diretório configurado, sem sair dele")
                .isEqualTo(apoio.armazenamentoDefinitivo().normalize());
    }
}
