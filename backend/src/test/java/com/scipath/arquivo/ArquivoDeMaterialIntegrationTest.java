package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scipath.error.ApiExceptionHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

/**
 * {@code GET /api/materiais/{slug}/arquivo} pela <b>borda HTTP de verdade</b>: contexto completo,
 * Tomcat, PostgreSQL local de {@code scipath_test} e o armazenamento em disco (card E4.3, RF014,
 * RN002, RN003, RN008).
 *
 * <p><b>Dados controlados.</b> A classe semeia oito arquivos: dois públicos e íntegros (um com nome
 * ASCII, um com acento — o caso normal deste público), um {@code RETIRADO}, um {@code EXCLUIDO}, um
 * de conta {@code PENDENTE_VERIFICACAO} (todos os três <b>com</b> o binário no armazenamento, para
 * que o 404 não possa ser explicado por "faltou o arquivo"), um público com checksum divergente, um
 * público com o binário ausente e um público com o {@code file_asset} já {@code PURGADO}. O banco e o
 * diretório de armazenamento são limpos antes e depois de cada teste, com o prefixo
 * {@code teste-e4-3-} como marcador.
 *
 * <p><b>O que só a borda prova.</b> Que o arquivo <b>sai</b> com o tipo e o nome sugerido certos,
 * que os quatro "não é público" respondem o <b>mesmo corpo</b> (não só o mesmo status), que o corpo
 * e os cabeçalhos não carregam caminho, chave de armazenamento nem checksum, que o diretório de
 * armazenamento não é servido por endereço direto, e que integridade divergente <b>não</b> vira PDF
 * servido.
 *
 * <p>Mesma configuração de contexto das outras integrações ({@code RANDOM_PORT}), e sem CSRF: o
 * download é leitura pública (GET), não escrita.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("download do arquivo de material público (RF014, RN002/RN003, RN008)")
class ArquivoDeMaterialIntegrationTest {

    /** Marcador dos dados desta classe em slug, e-mail e chave de armazenamento. */
    private static final String PREFIXO = "teste-e4-3-";

    private static final String CATEGORIA = "computacao-e-tecnologia";
    private static final String TIPO = "nota-de-aula";
    private static final String LICENCA = "cc-by-4.0";

    private static final String TITULO = "Material com arquivo";
    private static final String DESCRICAO = "Material usado para exercitar o download do arquivo, com autoria e licença visíveis.";

    private static final Instant PUBLICADO_EM = Instant.parse("2026-10-16T12:00:00Z");

    private static final String NOME_PUBLICO = "introducao-a-algoritmos.pdf";
    private static final String NOME_ACENTUADO = "introdução-à-ordenação.pdf";

    /** Conteúdo de apoio: assinatura de PDF de verdade + corpo determinístico. */
    private static final byte[] CONTEUDO_PUBLICO = pdfComCorpo(2048);
    private static final byte[] CONTEUDO_ACENTUADO = pdfComCorpo(96);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PropriedadesDeArmazenamento propriedades;

    @Autowired
    private StorageService armazenamento;

    @Autowired
    private ApplicationContext contexto;

    @TempDir
    Path areaDeApoio;

    @BeforeEach
    void semearDadosControlados() {
        limparDadosDeTeste();

        UUID contaAtiva = inserirConta("ativo", "ATIVO");
        UUID contaPendente = inserirConta("pendente", "PENDENTE_VERIFICACAO");
        UUID perfilAtivo = inserirPerfil(contaAtiva, "Ana de Arquivo", PREFIXO + "ana");
        UUID perfilPendente = inserirPerfil(contaPendente, "Bruno de Arquivo", PREFIXO + "bruno");

        semearMaterial(perfilAtivo, "publico-01", "PUBLICADO", NOME_PUBLICO, "VALIDADO",
                CONTEUDO_PUBLICO, CONTEUDO_PUBLICO);
        semearMaterial(perfilAtivo, "publico-acentuado", "PUBLICADO", NOME_ACENTUADO, "VALIDADO",
                CONTEUDO_ACENTUADO, CONTEUDO_ACENTUADO);
        semearMaterial(perfilAtivo, "retirado-01", "RETIRADO", NOME_PUBLICO, "VALIDADO",
                CONTEUDO_PUBLICO, CONTEUDO_PUBLICO);
        semearMaterial(perfilAtivo, "excluido-01", "EXCLUIDO", NOME_PUBLICO, "VALIDADO",
                CONTEUDO_PUBLICO, CONTEUDO_PUBLICO);
        semearMaterial(perfilPendente, "pendente-01", "PUBLICADO", NOME_PUBLICO, "VALIDADO",
                CONTEUDO_PUBLICO, CONTEUDO_PUBLICO);
        // Registrado A, no disco B: o download não pode servir o conteúdo que ninguém validou (RN008).
        semearMaterial(perfilAtivo, "divergente-01", "PUBLICADO", NOME_PUBLICO, "VALIDADO",
                CONTEUDO_PUBLICO, pdfComCorpo(1024));
        // Registrado, mas nada foi promovido para o armazenamento.
        semearMaterial(perfilAtivo, "ausente-01", "PUBLICADO", NOME_PUBLICO, "VALIDADO",
                CONTEUDO_PUBLICO, null);
        // Binário purgado pelo coletor: o material continua público, o arquivo não existe mais.
        semearMaterial(perfilAtivo, "purgado-01", "PUBLICADO", NOME_PUBLICO, "PURGADO",
                CONTEUDO_PUBLICO, null);
    }

    @AfterEach
    void removerDadosDeTeste() {
        limparDadosDeTeste();
    }

    // ------------------------------------------------- critério 1: o arquivo público é servido

    @Test
    @DisplayName("material público baixa 200 com application/pdf, Content-Disposition attachment e o conteúdo íntegro")
    void materialPublicoBaixa() {
        ResponseEntity<byte[]> resposta = rest.getForEntity(
                endereco("publico-01"), byte[].class);

        assertThat(resposta.getStatusCode().value()).as("material publicado é público").isEqualTo(200);
        assertThat(resposta.getHeaders().getContentType())
                .as("o contrato publica application/pdf")
                .isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .as("attachment com o nome original sanitizado")
                .isEqualTo("attachment; filename=\"" + NOME_PUBLICO + "\"");
        assertThat(resposta.getHeaders().getContentLength())
                .as("tamanho do registro do recebimento")
                .isEqualTo(CONTEUDO_PUBLICO.length);
        assertThat(resposta.getBody()).as("o conteúdo servido é o arquivo registrado").isEqualTo(CONTEUDO_PUBLICO);

        assertThat(resposta.getBody()).as("o corpo é o PDF do autor, não uma página de erro")
                .startsWith("%PDF-".getBytes(StandardCharsets.UTF_8));

        // Nada de infraestrutura na resposta: nem no corpo (é o binário), nem nos cabeçalhos.
        for (String proibido : cabecalhosProibidos()) {
            assertThat(resposta.getHeaders().toString())
                    .as("cabeçalho não pode carregar '%s' (RF014, RNF014)", proibido)
                    .doesNotContain(proibido);
        }
        System.out.println("[E4.3] GET " + endereco("publico-01") + " -> 200, "
                + resposta.getHeaders().getContentLength() + " bytes, "
                + resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    @DisplayName("nome com acento vai codificado no filename* (RFC 5987), sem quebrar o cabeçalho")
    void nomeAcentuadoUsaCodificacaoDoCabecalho() {
        ResponseEntity<byte[]> resposta = rest.getForEntity(endereco("publico-acentuado"), byte[].class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        String cabecalho = resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(cabecalho)
                .as("o parâmetro clássico fica em ASCII e o codificado carrega o nome real")
                .startsWith("attachment; filename=\"introdu__o-_-ordena__o.pdf\"")
                .contains("filename*=UTF-8''introdu%C3%A7%C3%A3o-%C3%A0-ordena%C3%A7%C3%A3o.pdf");
        assertThat(resposta.getBody()).isEqualTo(CONTEUDO_ACENTUADO);
        System.out.println("[E4.3] Content-Disposition com acento: " + cabecalho);
    }

    // ------------------------------------ critério 1: 404 indistinguível + critério de não vazamento

    @Test
    @DisplayName("retirado, excluído, de conta não ativa e inexistente respondem 404 de corpo idêntico")
    void foraDoPublicoResponde404Indistinguivel() {
        Map<String, String> casos = new LinkedHashMap<>();
        casos.put("retirado", "retirado-01");
        casos.put("excluido", "excluido-01");
        casos.put("de conta nao ativa", "pendente-01");
        casos.put("inexistente", "nunca-existiu");

        Map<String, String> corposNormalizados = new LinkedHashMap<>();
        for (Map.Entry<String, String> caso : casos.entrySet()) {
            ResponseEntity<String> resposta = rest.getForEntity(endereco(caso.getValue()), String.class);

            assertThat(resposta.getStatusCode().value())
                    .as("material %s -> 404 (RN002, RN003, CA008)", caso.getKey())
                    .isEqualTo(404);
            assertThat(resposta.getHeaders().getContentType())
                    .as("o erro único do contrato (RFC 7807)")
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

            String corpo = resposta.getBody();
            assertThat(corpo)
                    .contains("\"type\":\"urn:scipath:erro:nao_encontrado\"")
                    .contains("\"title\":\"Recurso não encontrado\"")
                    .contains("\"status\":404")
                    .contains("\"detail\":\"O recurso solicitado não foi encontrado.\"")
                    .doesNotContain("\"errors\"");
            for (String proibido : cabecalhosProibidos()) {
                assertThat(corpo).as("404 não pode revelar '%s' (RNF014)", proibido).doesNotContain(proibido);
            }

            corposNormalizados.put(caso.getKey(), semInstance(corpo));
            System.out.println("[E4.3] GET " + endereco(caso.getValue()) + " -> 404 " + corpo);
        }

        List<String> distintos = new ArrayList<>(new LinkedHashSet<>(corposNormalizados.values()));
        assertThat(distintos)
                .as("os quatro 404 são o MESMO corpo, tirando o instance: %s", corposNormalizados)
                .hasSize(1);
    }

    @Test
    @DisplayName("slug fora do formato do contrato responde 400, não 404 (endereço errado ≠ recurso ausente)")
    void slugMalformadoResponde400() {
        for (String slug : List.of("Material-Invalido", "material_com_underscore", "a".repeat(121))) {
            ResponseEntity<String> resposta = rest.getForEntity(
                    "/api/materiais/" + slug + "/arquivo", String.class);

            assertThat(resposta.getStatusCode().value()).as("GET %s -> 400", slug).isEqualTo(400);
            assertThat(resposta.getBody()).contains("\"type\":\"urn:scipath:erro:validacao\"")
                    .contains("\"campo\":\"slug\"");
            System.out.println("[E4.3] slug inválido -> 400 " + resposta.getBody());
        }
    }

    // ------------------------------------------------- critério 3: integridade divergente

    @Test
    @DisplayName("checksum divergente NÃO é servido: 500 sem detalhe interno e incidente registrado (RN008)")
    void checksumDivergenteNaoServeOArquivo() {
        ListAppender<ILoggingEvent> capturado = capturarLogDeIncidentes();
        ResponseEntity<String> resposta;
        try {
            resposta = rest.getForEntity(endereco("divergente-01"), String.class);
        } finally {
            pararDeCapturarLog(capturado);
        }

        assertThat(resposta.getStatusCode().value())
                .as("o arquivo não é servido: falha de integridade é erro do servidor, nunca 404 nem 200")
                .isEqualTo(500);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resposta.getBody())
                .contains("\"type\":\"urn:scipath:erro:interno\"")
                .contains("\"status\":500")
                .as("nada de caminho, chave, checksum ou stack (RNF012, RNF014)")
                .doesNotContain(propriedades.diretorio().toString())
                .doesNotContain(PREFIXO + "chave-divergente-01")
                .doesNotContain(sha256Hex(CONTEUDO_PUBLICO))
                .doesNotContain("Exception")
                .doesNotContain("at com.scipath");

        assertThat(capturado.list)
                .as("o incidente de integridade fica registrado (RN008)")
                .anySatisfy(evento -> {
                    assertThat(evento.getLevel().toString()).isEqualTo("ERROR");
                    assertThat(evento.getFormattedMessage())
                            .contains("incidente de integridade")
                            .contains(PREFIXO + "divergente-01")
                            .contains(PREFIXO + "chave-divergente-01")
                            .doesNotContain(propriedades.diretorio().toString());
                });
        System.out.println("[E4.3] checksum divergente -> 500 " + resposta.getBody());
        System.out.println("[E4.3] incidente registrado: " + capturado.list.get(0).getFormattedMessage());
    }

    @Test
    @DisplayName("binário ausente no armazenamento não vira 404 nem 200 vazio: erro de servidor com incidente")
    void binarioAusenteNaoEhServido() {
        ListAppender<ILoggingEvent> capturado = capturarLogDeIncidentes();
        ResponseEntity<String> resposta;
        try {
            resposta = rest.getForEntity(endereco("ausente-01"), String.class);
        } finally {
            pararDeCapturarLog(capturado);
        }

        assertThat(resposta.getStatusCode().value()).isEqualTo(500);
        assertThat(resposta.getBody()).contains("\"type\":\"urn:scipath:erro:interno\"")
                .doesNotContain(propriedades.diretorio().toString());
        assertThat(capturado.list).as("incidente registrado").anySatisfy(evento ->
                assertThat(evento.getFormattedMessage()).contains("não há arquivo no armazenamento"));
        System.out.println("[E4.3] binário ausente -> 500 " + resposta.getBody());
    }

    @Test
    @DisplayName("arquivo já PURGADO não é servido, mesmo com o material ainda PUBLICADO")
    void arquivoPurgadoNaoEhServido() {
        ResponseEntity<String> resposta = rest.getForEntity(endereco("purgado-01"), String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(500);
        assertThat(resposta.getBody()).contains("\"type\":\"urn:scipath:erro:interno\"");
        System.out.println("[E4.3] arquivo purgado -> 500 " + resposta.getBody());
    }

    // ---------------------------- critério 2: o armazenamento está fora das raízes servidas

    @Test
    @DisplayName("o diretório de armazenamento está fora de toda raiz servida e não é alcançável por endereço direto")
    void armazenamentoForaDeRaizServidaENaoServido() {
        Path dirDoArmazenamento = propriedades.diretorio();
        String chave = PREFIXO + "chave-publico-01";

        // (1) Garantia ESTRUTURAL: o diretório não está sob nenhuma raiz de onde o servidor
        //     entregaria arquivo estático.
        List<Path> raizes = raizesServidas();
        assertThat(raizes).as("a suíte precisa enxergar as raízes servidas, senão a asserção é vazia")
                .isNotEmpty();
        for (Path raiz : raizes) {
            assertThat(dirDoArmazenamento.startsWith(raiz))
                    .as("o armazenamento caiu dentro de uma raiz servida: %s", raiz)
                    .isFalse();
        }
        // As duas raízes que o card nomeia, mesmo que não existam nesta máquina.
        for (Path proibida : List.of(Path.of("src/main/resources"), Path.of("..", "frontend"))) {
            assertThat(dirDoArmazenamento.startsWith(proibida.toAbsolutePath().normalize()))
                    .as("o armazenamento não pode estar sob %s", proibida)
                    .isFalse();
        }

        // (2) Nenhuma rota mapeada menciona o diretório nem o nome físico do arquivo.
        for (String rota : rotasMapeadas()) {
            assertThat(rota)
                    .as("rota '%s' não pode apontar para o armazenamento", rota)
                    .doesNotContain(dirDoArmazenamento.toString())
                    .doesNotContain(chave);
        }

        // (3) De fora, o arquivo NÃO sai por endereço nenhum que não seja o endpoint do contrato:
        //     nem pela raiz do servidor com a chave, nem por um diretório de arquivos inventado.
        for (String enderecoDireto : List.of("/" + chave, "/arquivos/" + chave, "/api/" + chave,
                "/storage/" + chave, "/" + NOME_PUBLICO)) {
            int status = rest.getForEntity(enderecoDireto, String.class).getStatusCode().value();
            assertThat(status)
                    .as("GET %s não pode devolver o arquivo do armazenamento", enderecoDireto)
                    .isEqualTo(404);
        }

        // (4) E o caminho legítimo continua funcionando: o contraste é a prova de que o arquivo
        //     existe e é servido — só que pela porta que decide visibilidade (RF014).
        assertThat(rest.getForEntity(endereco("publico-01"), byte[].class).getStatusCode().value()).isEqualTo(200);

        System.out.println("[E4.3] armazenamento: " + dirDoArmazenamento);
        System.out.println("[E4.3] raízes servidas conferidas: " + raizes);
        System.out.println("[E4.3] rotas mapeadas: " + rotasMapeadas().size());
    }

    // ---------------------------------------------------------------------------- auxiliares

    private String endereco(String identificador) {
        return "/api/materiais/" + PREFIXO + identificador + "/arquivo";
    }

    /** Paths e chaves que não podem aparecer em resposta nenhuma do endpoint. */
    private List<String> cabecalhosProibidos() {
        return List.of(propriedades.diretorio().toString(), "chave-", "checksum", "arquivoTemporario", "storage");
    }

    private ListAppender<ILoggingEvent> capturarLogDeIncidentes() {
        Logger loggerDoHandler = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ListAppender<ILoggingEvent> capturado = new ListAppender<>();
        capturado.start();
        loggerDoHandler.addAppender(capturado);
        return capturado;
    }

    private static void pararDeCapturarLog(ListAppender<ILoggingEvent> capturado) {
        ((Logger) LoggerFactory.getLogger(ApiExceptionHandler.class)).detachAppender(capturado);
    }

    /** Corpo do 404 sem o {@code instance} — o único campo que pode diferir entre dois 404. */
    private static String semInstance(String corpo) {
        return corpo.replaceFirst("\"instance\":\"[^\"]*\"", "\"instance\":\"\"");
    }

    private void limparDadosDeTeste() {
        jdbc.update("DELETE FROM material WHERE slug LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM file_asset WHERE chave_interna LIKE ?", PREFIXO + "%");
        // Apagar a conta leva o perfil junto (ON DELETE CASCADE da V1).
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
        for (Path arquivo : arquivosNoArmazenamento()) {
            try {
                Files.deleteIfExists(arquivo);
            } catch (IOException falha) {
                throw new UncheckedIOException("não foi possível limpar " + arquivo, falha);
            }
        }
    }

    /** Só os arquivos desta classe — o diretório é o mesmo das outras integrações. */
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

    /**
     * Semeia {@code file_asset} + {@code material}. {@code conteudoRegistrado} define tamanho e
     * checksum gravados; {@code conteudoNoDisco} é o que de fato vai para o armazenamento —
     * diferentes de propósito no caso divergente, e {@code null} quando nada foi promovido.
     */
    private void semearMaterial(UUID perfil, String identificador, String estadoPublicacao,
            String nomeOriginal, String estadoAsset, byte[] conteudoRegistrado, byte[] conteudoNoDisco) {
        String chave = PREFIXO + "chave-" + identificador;

        jdbc.update("""
                INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,
                                        tamanho_bytes, checksum, estado_processamento)
                VALUES (?, ?, 'application/pdf', ?, ?, ?)
                """, chave, nomeOriginal, (long) conteudoRegistrado.length, sha256Hex(conteudoRegistrado), estadoAsset);

        jdbc.update("""
                INSERT INTO material (slug, titulo, descricao, profile_id, category_id, material_type_id,
                                      license_id, file_asset_id, estado_publicacao, publicado_em, atualizado_em)
                SELECT ?, ?, ?, ?,
                       (SELECT id FROM category WHERE slug = ?),
                       (SELECT id FROM material_type WHERE slug = ?),
                       (SELECT id FROM license WHERE slug = ?),
                       (SELECT id FROM file_asset WHERE chave_interna = ?),
                       ?, ?, ?
                """, PREFIXO + identificador, TITULO, DESCRICAO, perfil, CATEGORIA, TIPO, LICENCA, chave,
                estadoPublicacao, emUtc(PUBLICADO_EM), emUtc(PUBLICADO_EM));

        if (conteudoNoDisco != null) {
            gravarNoArmazenamento(chave, conteudoNoDisco);
        }
    }

    /** Promove um conteúdo para o armazenamento pela MESMA porta que a aplicação usa (E4.3). */
    private void gravarNoArmazenamento(String chave, byte[] conteudo) {
        try {
            Path origem = areaDeApoio.resolve(chave + ".pdf");
            Files.write(origem, conteudo);
            armazenamento.guardar(chave, origem);
        } catch (IOException falha) {
            throw new UncheckedIOException("não foi possível preparar o arquivo de apoio " + chave, falha);
        }
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

    /**
     * Raízes de onde o servidor serviria arquivo estático: os diretórios de recurso do projeto (se
     * existirem), os do frontend e o próprio classpath compilado ({@code target/classes}, de onde o
     * Spring serve {@code static/}). Mesma enumeração da bateria do E4.2 — o armazenamento
     * definitivo não pode estar dentro de nenhuma delas.
     */
    private List<Path> raizesServidas() {
        List<Path> raizes = new ArrayList<>();
        for (String candidato : List.of("target/classes", "src/main/resources/static",
                "src/main/resources/public", "src/main/resources/META-INF/resources",
                "../frontend/public", "../frontend/static", "../frontend/.next/static")) {
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

    /** Todas as rotas mapeadas no contexto — a prova de que nenhuma aponta para o armazenamento. */
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

    private static byte[] pdfComCorpo(int tamanho) {
        byte[] cabecalho = "%PDF-1.7\n".getBytes(StandardCharsets.UTF_8);
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

    private static OffsetDateTime emUtc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
