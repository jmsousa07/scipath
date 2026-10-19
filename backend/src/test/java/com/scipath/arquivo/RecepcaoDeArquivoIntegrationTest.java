package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scipath.identidade.CookiesDeSessao;
import jakarta.servlet.MultipartConfigElement;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

/**
 * Recebimento e validação do arquivo pela <b>borda HTTP de verdade</b>: contexto completo,
 * Tomcat, multipart real, {@code ProblemDetail} do contrato e o PostgreSQL local de
 * {@code scipath_test} (card E4.2, RF013, RN008, RNF004).
 *
 * <p>A borda usada é {@link ControladorDeTesteDeRecebimento} — o adaptador de teste entre
 * {@code multipart/form-data} e o serviço, porque o endpoint do contrato que recebe o arquivo
 * ({@code POST /materiais}) é do E4.4. O javadoc daquele controller explica por que ele existe
 * e por que não é uma segunda fronteira de API.
 *
 * <p><b>O limite observado aqui é o do perfil de teste: 1 MB</b>
 * ({@code src/test/resources/application-test.yml}), e não os 30 MB do ADR-0005 §1. É de
 * propósito: com o mesmo código respondendo 413 a 1 MB nesta suíte e aceitando 30 MB em
 * produção, um limite cravado no código não passaria — é o critério "nenhum valor de limite
 * literal" observado de fora, pela resposta HTTP.
 *
 * <p>Mesma configuração de contexto das outras integrações ({@code RANDOM_PORT}, sem
 * configuração extra): o Spring reusa o contexto já subido e a máquina tem 954 MB (AGENTS.md,
 * higiene de recursos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("recepção do arquivo pela borda HTTP (RF013, RN008)")
class RecepcaoDeArquivoIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Marcador dos dados desta classe: nome de arquivo (e, por ele, a linha em file_asset). */
    private static final String PREFIXO = "teste-e4-2-";

    private static final String CAMINHO = ControladorDeTesteDeRecebimento.CAMINHO;
    private static final String ASSINATURA = "%PDF-1.7\n";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PropriedadesDeUpload propriedades;

    @Autowired
    private MultipartConfigElement limitesDoMultipart;

    @Autowired
    private ApplicationContext contexto;

    /** Cookie/header CSRF de visitante — a escrita é protegida igual à de produção. */
    private String csrfDeVisitante;

    @BeforeEach
    void limparAntes() {
        limparDadosDeTeste();
        csrfDeVisitante = csrfDeVisitante();
    }

    @AfterEach
    void limparDepois() {
        limparDadosDeTeste();
    }

    // ------------------------------------------------------ critério 1: caminho de sucesso

    @Test
    @DisplayName("PDF válido é aceito (200), registrado em file_asset e gravado na área temporária")
    void pdfValidoAceito() throws IOException {
        byte[] conteudo = pdfComCorpo(2048);
        String nome = PREFIXO + "nota-de-aula.pdf";

        ResponseEntity<String> resposta = enviar(conteudo, nome, "application/pdf");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        System.out.println("[E4.2] POST arquivo válido -> 200 " + corpo);

        assertThat(corpo.get("nomeOriginalSeguro").asText()).isEqualTo(nome);
        assertThat(corpo.get("tipoDetectado").asText()).isEqualTo("application/pdf");
        assertThat(corpo.get("tamanhoBytes").asLong()).isEqualTo(conteudo.length);
        assertThat(corpo.get("checksum").asText()).isEqualTo(sha256Hex(conteudo));

        // O que ficou gravado é o que a resposta afirmou (RN013)...
        Map<String, Object> linha = linhaDoArquivo(nome);
        assertThat(linha.get("chave_interna")).isNotNull();
        assertThat(linha.get("nome_original_seguro")).isEqualTo(nome);
        assertThat(linha.get("tipo_detectado")).isEqualTo("application/pdf");
        assertThat(linha.get("tamanho_bytes")).isEqualTo((long) conteudo.length);
        assertThat(linha.get("checksum")).isEqualTo(sha256Hex(conteudo));
        assertThat(linha.get("estado_processamento"))
                .as("a linha só nasce depois de o conteúdo passar por todas as conferências")
                .isEqualTo("VALIDADO");

        // ...e o arquivo está na área temporária, com o nome físico gerado pelo sistema.
        String chave = (String) linha.get("chave_interna");
        Path arquivo = areaTemporaria().resolve(chave);
        assertThat(Files.exists(arquivo)).as("o arquivo recebido está na área temporária").isTrue();
        assertThat(Files.readAllBytes(arquivo)).isEqualTo(conteudo);
        assertThat(areaTemporaria().resolve(nome)).as("nada é gravado com o nome do usuário").doesNotExist();
        System.out.println("[E4.2] chave interna " + chave + " em " + areaTemporaria());
    }

    @Test
    @DisplayName("nome enviado com travessia é neutralizado no metadado e não decide o caminho")
    void travessiaNeutralizada() throws IOException {
        String nomeEnviado = "../../../etc/" + PREFIXO + "passwd.pdf";

        ResponseEntity<String> resposta = enviar(pdfComCorpo(128), nomeEnviado, "application/pdf");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        System.out.println("[E4.2] nome com travessia -> " + corpo);

        assertThat(corpo.get("nomeOriginalSeguro").asText())
                .as("o valor guardado não pode ser interpretado como caminho")
                .isEqualTo(PREFIXO + "passwd.pdf")
                .doesNotContain("..", "/", "\\");
        assertThat(linhaDoArquivo(PREFIXO + "passwd.pdf").get("chave_interna"))
                .as("o caminho do arquivo vem da chave, não do nome")
                .isNotEqualTo(nomeEnviado);
    }

    // ------------------------------------ critério 2: nada acessível antes da validação

    @Test
    @DisplayName("o arquivo recebido não é servido por nenhum endpoint: área temporária fora das raízes servidas")
    void arquivoNaoEAcessivelPorNenhumEndpoint() throws Exception {
        responder(PREFIXO + "inacessivel.pdf", pdfComCorpo(256));
        String chave = (String) linhaDoArquivo(PREFIXO + "inacessivel.pdf").get("chave_interna");

        // (1) A área de recebimento está FORA de toda raiz servida. É a garantia estrutural: não
        //     existe handler estático que alcance este caminho, hoje ou depois de o arquivo ser
        //     promovido (E4.3, cujo storage definitivo também fica fora — ADR-0005 §2).
        List<Path> raizesServidas = raizesServidas();
        assertThat(raizesServidas)
                .as("a suíte precisa enxergar as raízes servidas, senão a asserção abaixo é vazia")
                .isNotEmpty();
        for (Path raiz : raizesServidas) {
            assertThat(areaTemporaria().startsWith(raiz))
                    .as("a área temporária está dentro de uma raiz servida: %s", raiz)
                    .isFalse();
        }

        // (2) Nenhum endpoint é construído a partir do que foi recebido: nenhuma rota mapeada
        //     menciona a área temporária nem o nome físico do arquivo.
        for (String rota : rotasMapeadas()) {
            assertThat(rota)
                    .as("rota '%s' não pode apontar para a área de recebimento", rota)
                    .doesNotContain(chave)
                    .doesNotContain(areaTemporaria().toString());
        }

        // (3) E, de fora, o arquivo não sai por endereço nenhum: nem pelo caminho da API com a
        //     chave, nem pela raiz do servidor com o nome do arquivo.
        for (String endereco : List.of("/api/" + chave, "/" + chave, "/api/arquivos/" + chave,
                "/" + PREFIXO + "inacessivel.pdf", "/materiais/" + PREFIXO + "inacessivel.pdf/arquivo")) {
            int status = rest.getForEntity(endereco, String.class).getStatusCode().value();
            assertThat(status)
                    .as("GET %s não pode devolver o arquivo recebido", endereco)
                    .isEqualTo(404);
        }

        // (4) A resposta do sucesso também não publica onde o arquivo está (RF014, RNF012).
        ResponseEntity<String> resposta = responder(PREFIXO + "sem-vazamento.pdf", pdfComCorpo(64));
        assertThat(resposta.getBody())
                .as("nem caminho, nem chave de storage, nem nome físico na resposta")
                .doesNotContain(areaTemporaria().toString())
                .doesNotContain("chaveInterna")
                .doesNotContain("caminhoTemporario")
                .doesNotContain("null");
        System.out.println("[E4.2] raízes servidas conferidas: " + raizesServidas
                + " | rotas mapeadas: " + rotasMapeadas().size());
    }

    // ------------------------------------------------------- critério 1: caminhos de recusa

    @Test
    @DisplayName("arquivo renomeado '.pdf' sem assinatura é recusado com 415 e não deixa rastro")
    void renomeadoSemAssinaturaRecusado() {
        byte[] disfarcado = "MZ\u0090\u0000não sou PDF".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<String> resposta = enviar(disfarcado, PREFIXO + "disfarcado.pdf", "application/pdf");

        assertThat(resposta.getStatusCode().value())
                .as("ADR-0005 §1: a extensão nunca decide sozinha")
                .isEqualTo(415);
        JsonNode erro = erroDe(resposta);
        assertThat(erro.get("type").asText()).isEqualTo("urn:scipath:erro:tipo_nao_suportado");
        assertThat(erro.get("status").asInt()).isEqualTo(415);
        assertThat(erro.get("detail").asText()).isEqualTo("O arquivo enviado não é um PDF válido.");
        assertThat(erro.get("instance").asText()).isEqualTo(CAMINHO);
        assertThat(erro.has("errors")).as("415 não aponta campo").isFalse();
        assertThat(resposta.getHeaders().getContentType())
                .as("o erro único do contrato, não o JSON padrão do Spring")
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(linhasDoArquivo(PREFIXO + "disfarcado.pdf")).isEmpty();
        assertThat(arquivosNaAreaTemporaria()).as("nada fica na área temporária (RN012)").isEmpty();
        System.out.println("[E4.2] PDF renomeado -> 415 " + resposta.getBody());
    }

    @Test
    @DisplayName("MIME declarado divergente do conteúdo é recusado com 415 (PDF de verdade como text/plain)")
    void mimeDeclaradoDivergenteRecusado() {
        ResponseEntity<String> resposta = enviar(pdfComCorpo(128), PREFIXO + "mime.pdf", "text/plain");

        assertThat(resposta.getStatusCode().value()).isEqualTo(415);
        assertThat(erroDe(resposta).get("type").asText()).isEqualTo("urn:scipath:erro:tipo_nao_suportado");
        assertThat(erroDe(resposta).get("detail").asText()).isEqualTo("O arquivo enviado não é um PDF válido.");
        assertThat(linhasDoArquivo(PREFIXO + "mime.pdf")).isEmpty();
        assertThat(arquivosNaAreaTemporaria()).isEmpty();
        System.out.println("[E4.2] MIME divergente -> 415 " + resposta.getBody());
    }

    @Test
    @DisplayName("acima do limite responde 413 com a mensagem do limite configurado, e nada é gravado")
    void acimaDoLimiteRecusadoComMensagemDoLimite() {
        // 1,5 MB no perfil de teste (limite de 1 MB): passa da rede de segurança do contêiner
        // (1 MB + 1 MB) e é recusado pelo serviço, com a mensagem do valor configurado.
        byte[] grande = pdfComCorpo(1536 * 1024);

        ResponseEntity<String> resposta = enviar(grande, PREFIXO + "grande.pdf", "application/pdf");

        assertThat(resposta.getStatusCode().value()).isEqualTo(413);
        JsonNode erro = erroDe(resposta);
        assertThat(erro.get("type").asText()).isEqualTo("urn:scipath:erro:arquivo_muito_grande");
        assertThat(erro.get("title").asText()).isEqualTo("Arquivo muito grande");
        assertThat(erro.get("detail").asText())
                .as("a mensagem nomeia o limite CONFIGURADO (1 MB neste perfil), não um literal do código")
                .isEqualTo("O arquivo excede o limite de 1 MB.");
        assertThat(erro.has("errors")).isFalse();
        assertThat(erro.get("instance").asText()).isEqualTo(CAMINHO);

        assertThat(linhasDoArquivo(PREFIXO + "grande.pdf")).isEmpty();
        assertThat(arquivosNaAreaTemporaria())
                .as("nem o parcial sobrevive: a gravação para no limite (RN012)")
                .isEmpty();
        System.out.println("[E4.2] acima do limite -> 413 " + resposta.getBody());
    }

    @Test
    @DisplayName("a rede de segurança do contêiner também responde 413 (nunca 500) e deriva do mesmo valor")
    void redeDeSegurancaDoConteiner() {
        // Os dois limites do multipart são calculados a partir do limite único de upload.
        long limite = propriedades.limiteEmBytes();
        assertThat(limitesDoMultipart.getMaxFileSize()).isEqualTo(limite + DataSize.ofMegabytes(1).toBytes());
        assertThat(limitesDoMultipart.getMaxRequestSize()).isEqualTo(limite + 2 * DataSize.ofMegabytes(1).toBytes());
        assertThat(limitesDoMultipart.getFileSizeThreshold()).isZero();
        System.out.println("[E4.2] limite do produto " + limite + " bytes | multipart do contêiner: arquivo "
                + limitesDoMultipart.getMaxFileSize() + " bytes, requisição "
                + limitesDoMultipart.getMaxRequestSize() + " bytes (folga de 1 MB e 2 MB)");

        // 2,5 MB: acima da rede de segurança (2 MB) e é o contêiner que recusa — a resposta, no
        // entanto, é o MESMO 413 do contrato, e não um 500 do catch-all (RNF004).
        ResponseEntity<String> resposta = enviar(pdfComCorpo(2560 * 1024), PREFIXO + "enorme.pdf", "application/pdf");

        assertThat(resposta.getStatusCode().value()).isEqualTo(413);
        JsonNode erro = erroDe(resposta);
        assertThat(erro.get("type").asText()).isEqualTo("urn:scipath:erro:arquivo_muito_grande");
        assertThat(erro.get("detail").asText()).startsWith("O arquivo excede o limite de ");
        assertThat(linhasDoArquivo(PREFIXO + "enorme.pdf")).isEmpty();
        assertThat(arquivosNaAreaTemporaria()).isEmpty();
        System.out.println("[E4.2] rede de segurança do contêiner -> 413 " + resposta.getBody());
    }

    @Test
    @DisplayName("parte 'arquivo' ausente no formulário responde 400, não 500")
    void parteAusenteResponde400() {
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("outroCampo", "valor"); // formulário válido, sem a parte do arquivo
        ResponseEntity<String> resposta = enviarPartes(partes);

        assertThat(resposta.getStatusCode().value())
                .as("contrato v0.5: campo obrigatório ausente no formulário é 400")
                .isEqualTo(400);
        JsonNode erro = erroDe(resposta);
        assertThat(erro.get("type").asText()).isEqualTo("urn:scipath:erro:validacao");
        assertThat(erro.get("errors").get(0).get("campo").asText()).isEqualTo("arquivo");
        assertThat(erro.get("errors").get(0).get("mensagem").asText()).isEqualTo("é obrigatório");
        System.out.println("[E4.2] parte ausente -> 400 " + resposta.getBody());
    }

    @Test
    @DisplayName("parte presente e vazia responde 400: não há arquivo para validar")
    void parteVaziaResponde400() {
        ResponseEntity<String> resposta = enviar(new byte[0], PREFIXO + "vazio.pdf", "application/pdf");

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        JsonNode erro = erroDe(resposta);
        assertThat(erro.get("type").asText()).isEqualTo("urn:scipath:erro:validacao");
        assertThat(erro.get("errors").get(0).get("campo").asText()).isEqualTo("arquivo");
        assertThat(arquivosNaAreaTemporaria()).isEmpty();
        System.out.println("[E4.2] parte vazia -> 400 " + resposta.getBody());
    }

    // ------------------------------------------------------------------------ utilidades

    private ResponseEntity<String> responder(String nome, byte[] conteudo) {
        ResponseEntity<String> resposta = enviar(conteudo, nome, "application/pdf");
        assertThat(resposta.getStatusCode().value()).as("upload de apoio falhou: " + resposta.getBody())
                .isEqualTo(200);
        return resposta;
    }

    /** Envia o arquivo como {@code multipart/form-data}, com o tipo declarado da parte. */
    private ResponseEntity<String> enviar(byte[] conteudo, String nome, String tipoDeclarado) {
        ByteArrayResource recurso = new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nome;
            }
        };
        HttpHeaders cabecalhoDaParte = new HttpHeaders();
        cabecalhoDaParte.setContentType(MediaType.parseMediaType(tipoDeclarado));
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("arquivo", new HttpEntity<>(recurso, cabecalhoDaParte));
        return enviarPartes(partes);
    }

    private ResponseEntity<String> enviarPartes(MultiValueMap<String, Object> partes) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        // A escrita passa pelo mesmo CSRF de produção (E3.3): cookie legível + cabeçalho, iguais.
        cabecalhos.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_CSRF + "=" + csrfDeVisitante);
        cabecalhos.add("X-CSRF-Token", csrfDeVisitante);
        return rest.exchange(CAMINHO, HttpMethod.POST, new HttpEntity<>(partes, cabecalhos), String.class);
    }

    /**
     * Token CSRF de visitante, como o frontend o lê na primeira leitura do app: uma requisição de
     * leitura emite a cookie {@code XSRF-TOKEN} e o formulário a reapresenta no cabeçalho
     * (ADR-0003 (e)). Sem sessão, a escrita continua exigindo o par — o recebimento não é uma
     * porta lateral de escrita.
     */
    private String csrfDeVisitante() {
        ResponseEntity<String> estado = rest.getForEntity("/api/sessoes/atual", String.class);
        return estado.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).stream()
                .filter(linha -> linha.startsWith(CookiesDeSessao.COOKIE_DE_CSRF + "="))
                .map(linha -> {
                    int igual = linha.indexOf('=');
                    int pontoEVirgula = linha.indexOf(';');
                    return pontoEVirgula < 0 ? linha.substring(igual + 1) : linha.substring(igual + 1, pontoEVirgula);
                })
                .filter(valor -> !valor.isBlank())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "GET /api/sessoes/atual não emitiu a cookie " + CookiesDeSessao.COOKIE_DE_CSRF
                                + ": sem ela não há como provar a escrita como um navegador a faz"));
    }

    private static JsonNode erroDe(ResponseEntity<String> resposta) {
        try {
            return JSON.readTree(resposta.getBody());
        } catch (IOException impossivel) {
            throw new IllegalStateException("resposta de erro ilegível: " + resposta.getBody(), impossivel);
        }
    }

    /** Área temporária de recebimento configurada (perfil de teste: {@code target/}). */
    private Path areaTemporaria() {
        return propriedades.diretorioTemporario().toAbsolutePath().normalize();
    }

    private List<Path> arquivosNaAreaTemporaria() {
        if (!Files.exists(areaTemporaria())) {
            return List.of();
        }
        try (Stream<Path> entradas = Files.list(areaTemporaria())) {
            return entradas.toList();
        } catch (IOException falha) {
            throw new IllegalStateException("não foi possível listar a área temporária de recebimento", falha);
        }
    }

    /**
     * Raízes de onde o servidor serviria arquivo: o diretório de recursos estáticos do projeto
     * (se existir), o do frontend e o próprio classpath compilado ({@code target/classes}, de
     * onde o Spring serve {@code static/}). A área temporária não pode estar dentro de nenhuma.
     */
    private List<Path> raizesServidas() {
        List<Path> raizes = new ArrayList<>();
        for (String candidato : List.of("target/classes", "src/main/resources/static",
                "src/main/resources/public", "../frontend/public", "../frontend/.next/static")) {
            Path caminho = Path.of(candidato).toAbsolutePath().normalize();
            if (Files.exists(caminho)) {
                raizes.add(caminho);
            }
        }
        URL estaticoNoClasspath = getClass().getClassLoader().getResource("static");
        if (estaticoNoClasspath != null) {
            try {
                raizes.add(Path.of(estaticoNoClasspath.toURI()).toAbsolutePath().normalize());
            } catch (Exception ignorado) {
                // Recurso dentro de jar: não é uma raiz de sistema de arquivos, nada a comparar.
            }
        }
        return raizes;
    }

    /** Todas as rotas mapeadas no contexto — a prova de que nenhuma aponta para o recebido. */
    private List<String> rotasMapeadas() {
        List<String> rotas = new ArrayList<>();
        for (HandlerMapping mapeamento : contexto.getBeansOfType(HandlerMapping.class).values()) {
            if (mapeamento instanceof AbstractHandlerMethodMapping<?> porMetodo) {
                porMetodo.getHandlerMethods().keySet().forEach(chave -> rotas.add(chave.toString()));
            }
            if (mapeamento instanceof SimpleUrlHandlerMapping porUrl) {
                rotas.addAll(porUrl.getUrlMap().keySet());
            }
        }
        return rotas;
    }

    private Map<String, Object> linhaDoArquivo(String nomeOriginalSeguro) {
        List<Map<String, Object>> linhas = linhasDoArquivo(nomeOriginalSeguro);
        assertThat(linhas).as("file_asset de '%s'", nomeOriginalSeguro).hasSize(1);
        return linhas.get(0);
    }

    private List<Map<String, Object>> linhasDoArquivo(String nomeOriginalSeguro) {
        return jdbc.queryForList("SELECT chave_interna, nome_original_seguro, tipo_detectado,"
                + " tamanho_bytes, checksum, estado_processamento FROM file_asset"
                + " WHERE nome_original_seguro = ?", nomeOriginalSeguro);
    }

    /** Remove só o que esta classe criou — a linha de {@code file_asset} e o arquivo em disco. */
    private void limparDadosDeTeste() {
        jdbc.update("DELETE FROM file_asset WHERE nome_original_seguro LIKE ?", PREFIXO + "%");
        for (Path arquivo : arquivosNaAreaTemporaria()) {
            try {
                Files.deleteIfExists(arquivo);
            } catch (IOException falha) {
                throw new IllegalStateException("não foi possível limpar " + arquivo, falha);
            }
        }
    }

    private static byte[] pdfComCorpo(int tamanho) {
        byte[] cabecalho = ASSINATURA.getBytes(StandardCharsets.UTF_8);
        byte[] conteudo = new byte[Math.max(tamanho, cabecalho.length)];
        System.arraycopy(cabecalho, 0, conteudo, 0, cabecalho.length);
        for (int i = cabecalho.length; i < conteudo.length; i++) {
            conteudo[i] = (byte) ('a' + (i % 26));
        }
        return conteudo;
    }

    private static String sha256Hex(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (Exception impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }
}
