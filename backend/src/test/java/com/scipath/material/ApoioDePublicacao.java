package com.scipath.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scipath.arquivo.PropriedadesDeArmazenamento;
import com.scipath.arquivo.PropriedadesDeUpload;
import com.scipath.identidade.CookiesDeSessao;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Apoio das suíte da publicação de material (card E4.4): conta e sessão de apoio, o
 * {@code multipart/form-data} montado como um navegador o manda, leitura do banco e limpeza.
 *
 * <p>Existe como classe separada pelo mesmo motivo de {@code ApoioDeTestesDeToken} (E3.4): as duas
 * classes de teste do card precisam das mesmas operações, e copiá-las seria a maneira mais rápida
 * de as duas divergirem no dia em que o formulário mudar.
 *
 * <p><b>O que este apoio não faz.</b> Nenhuma asserção de contrato: cada teste decide o que
 * verificar e o apoio só devolve a resposta crua, o JSON lido ou a linha do banco. Um apoio que
 * assertasse esconderia justamente o que a evidência precisa mostrar.
 *
 * <p><b>Dados marcados e limpos.</b> Tudo que o apoio cria leva o prefixo {@value #PREFIXO} no
 * e-mail, no nome do arquivo e (por consequência) no slug do material — o que permite limpar só o
 * que a suíte criou, antes e depois de cada caso, num banco de teste que é compartilhado com as
 * outras baterias.
 */
final class ApoioDePublicacao {

    /** Marcador dos dados desta suíte: e-mail, nome do arquivo e linha em {@code file_asset}. */
    static final String PREFIXO = "teste-e4-4-";

    static final String DOMINIO = "@example.test";
    static final String SENHA = "nota-de-aula-2026";

    /** Taxonomia de referência da seed V2 — os mesmos slugs que a leitura pública devolve (RF018). */
    static final String CATEGORIA = "computacao-e-tecnologia";
    static final String TIPO = "nota-de-aula";
    static final String LICENCA = "cc-by-4.0";

    /** Assinatura de PDF: o conteúdo mínimo que o recebimento aceita (E4.2). */
    private static final String ASSINATURA = "%PDF-1.7\n";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TestRestTemplate rest;
    private final JdbcTemplate jdbc;
    private final PropriedadesDeUpload upload;
    private final PropriedadesDeArmazenamento armazenamento;

    ApoioDePublicacao(TestRestTemplate rest, JdbcTemplate jdbc, PropriedadesDeUpload upload,
            PropriedadesDeArmazenamento armazenamento) {
        this.rest = rest;
        this.jdbc = jdbc;
        this.upload = upload;
        this.armazenamento = armazenamento;
    }

    // ------------------------------------------------------------------ conta e sessão

    /** Sessão como o cliente a tem: o valor da cookie de sessão e o token CSRF que ela carrega. */
    record Sessao(String token, String csrf) {
    }

    /**
     * Autor das publicações da suíte: conta <b>ATIVO</b> (o estado que publicar exige, ADR-0003 (b))
     * com perfil, autenticada. {@code identificador} vira o e-mail e o nome público (e daí o slug do
     * perfil).
     */
    Sessao autorAtivo(String identificador) {
        String email = PREFIXO + identificador + DOMINIO;
        cadastrar(email, "Autor " + identificador);
        ativar(email);
        return logar(email);
    }

    /** E-mail da conta de apoio correspondente ao identificador. */
    static String emailDe(String identificador) {
        return PREFIXO + identificador + DOMINIO;
    }

    void cadastrar(String email, String nomePublico) {
        String csrf = csrfDeVisitante();
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, "/api/contas",
                JSON.createObjectNode().put("email", email).put("senha", SENHA)
                        .put("nomePublico", nomePublico).toString(),
                null, csrf, csrf);
        assertThat(resposta.getStatusCode().value())
                .as("cadastro de apoio falhou: %s", resposta.getBody())
                .isEqualTo(201);
    }

    /**
     * Leva a conta a {@code ATIVO} por SQL, e não pelo fluxo de confirmação de e-mail (E3.4): o que
     * esta suíte mede é a publicação, e passar pelo token faria cada caso depender de outro card.
     * A coluna {@code email_verificado_em} acompanha porque é ela que diz que a verificação
     * aconteceu.
     */
    void ativar(String email) {
        int linhas = jdbc.update("UPDATE account SET estado = 'ATIVO', email_verificado_em = now()"
                + " WHERE email = ?", email);
        assertThat(linhas).as("conta de apoio '%s' não existe para ativar", email).isEqualTo(1);
    }

    Sessao logar(String email) {
        String csrf = csrfDeVisitante();
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, "/api/sessoes",
                JSON.createObjectNode().put("email", email).put("senha", SENHA).toString(),
                null, csrf, csrf);
        assertThat(resposta.getStatusCode().value())
                .as("login de apoio falhou: %s", resposta.getBody())
                .isEqualTo(200);
        return new Sessao(
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow(),
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow());
    }

    /** O token que o frontend lê na cookie {@code XSRF-TOKEN} da primeira leitura do app. */
    String csrfDeVisitante() {
        ResponseEntity<String> resposta = rest.getForEntity("/api/sessoes/atual", String.class);
        return valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow(
                () -> new AssertionError("GET /api/sessoes/atual não emitiu a cookie de CSRF"));
    }

    // ------------------------------------------------------------------ formulário

    /**
     * Campos mínimos válidos do RF012 — o ponto de partida de cada caso, que troca só o que mede.
     *
     * <p>{@code LinkedHashMap} para a ordem ser estável na evidência do multipart.
     */
    static Map<String, String> camposValidos() {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("titulo", "Ordenação por comparação na prática");
        campos.put("descricao", "Nota de aula sobre ordenação por comparação, com análise de "
                + "complexidade e exemplos em pseudocódigo.");
        campos.put("categoriaSlug", CATEGORIA);
        campos.put("tipoSlug", TIPO);
        campos.put("licencaSlug", LICENCA);
        campos.put("declaracaoDireito", "true");
        return campos;
    }

    /** Publicação com os campos informados e o arquivo (ou sem ele, quando {@code conteudo} é nulo). */
    ResponseEntity<String> publicar(Sessao sessao, Map<String, String> campos, byte[] conteudo,
            String nomeArquivo, String tipoDeclarado) {
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        campos.forEach(partes::add);
        if (conteudo != null) {
            ByteArrayResource recurso = new ByteArrayResource(conteudo) {
                @Override
                public String getFilename() {
                    return nomeArquivo;
                }
            };
            HttpHeaders cabecalhoDaParte = new HttpHeaders();
            cabecalhoDaParte.setContentType(MediaType.parseMediaType(tipoDeclarado));
            partes.add("arquivo", new HttpEntity<>(recurso, cabecalhoDaParte));
        }
        return multipart(sessao, partes);
    }

    /** Publicação válida: campos mínimos do RF012 e PDF de verdade. */
    ResponseEntity<String> publicarValido(Sessao sessao, byte[] conteudo, String nomeArquivo) {
        return publicar(sessao, camposValidos(), conteudo, nomeArquivo, "application/pdf");
    }

    /** Publicação anônima: sem cookie de sessão — só o par de CSRF de visitante. */
    ResponseEntity<String> publicarSemSessao(Map<String, String> campos, byte[] conteudo,
            String nomeArquivo) {
        return publicar(new Sessao(null, csrfDeVisitante()), campos, conteudo, nomeArquivo,
                MediaType.APPLICATION_PDF_VALUE);
    }

    private ResponseEntity<String> multipart(Sessao sessao, MultiValueMap<String, Object> partes) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        StringBuilder cookies = new StringBuilder();
        if (sessao.token() != null) {
            cookies.append(CookiesDeSessao.COOKIE_DE_SESSAO).append('=').append(sessao.token());
        }
        if (sessao.csrf() != null) {
            if (!cookies.isEmpty()) {
                cookies.append("; ");
            }
            cookies.append(CookiesDeSessao.COOKIE_DE_CSRF).append('=').append(sessao.csrf());
            cabecalhos.add("X-CSRF-Token", sessao.csrf());
        }
        if (!cookies.isEmpty()) {
            cabecalhos.add(HttpHeaders.COOKIE, cookies.toString());
        }
        return rest.exchange("/api/materiais", HttpMethod.POST, new HttpEntity<>(partes, cabecalhos), String.class);
    }

    /** Requisição de leitura autenticada, para conferir o que a publicação deixou público. */
    ResponseEntity<String> ler(Sessao sessao, String caminho) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (sessao != null) {
            cabecalhos.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_SESSAO + "=" + sessao.token());
        }
        return rest.exchange(caminho, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }

    /** Envia JSON para o endpoint de publicação — caso de {@code Content-Type} não aceito. */
    ResponseEntity<String> publicarComoJson(Sessao sessao) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.add(HttpHeaders.COOKIE, CookiesDeSessao.COOKIE_DE_SESSAO + "=" + sessao.token()
                + "; " + CookiesDeSessao.COOKIE_DE_CSRF + "=" + sessao.csrf());
        cabecalhos.add("X-CSRF-Token", sessao.csrf());
        return rest.exchange("/api/materiais", HttpMethod.POST, new HttpEntity<>("{}", cabecalhos), String.class);
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String caminho, String corpo,
            Sessao sessao, String cookieDeCsrf, String cabecalhoDeCsrf) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (corpo != null) {
            cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        }
        StringBuilder cookies = new StringBuilder();
        if (sessao != null && sessao.token() != null) {
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

    // ------------------------------------------------------------------ leitura de resposta

    static JsonNode corpo(ResponseEntity<String> resposta) {
        try {
            return JSON.readTree(resposta.getBody());
        } catch (IOException impossivel) {
            throw new IllegalStateException("resposta ilegível: " + resposta.getBody(), impossivel);
        }
    }

    static String tipoDoErro(ResponseEntity<String> resposta) {
        return corpo(resposta).get("type").asText();
    }

    /** O item de {@code errors[]} do campo informado, ou falha se ele não estiver lá. */
    static JsonNode erroDoCampo(ResponseEntity<String> resposta, String campo) {
        for (JsonNode erro : corpo(resposta).withArray("errors")) {
            if (campo.equals(erro.get("campo").asText())) {
                return erro;
            }
        }
        throw new AssertionError("errors[] não aponta o campo '" + campo + "': " + resposta.getBody());
    }

    // ------------------------------------------------------------------ banco

    Map<String, Object> linhaDoArquivo(String nomeOriginalSeguro) {
        List<Map<String, Object>> linhas = linhasDoArquivo(nomeOriginalSeguro);
        assertThat(linhas).as("file_asset de '%s'", nomeOriginalSeguro).hasSize(1);
        return linhas.get(0);
    }

    List<Map<String, Object>> linhasDoArquivo(String nomeOriginalSeguro) {
        return jdbc.queryForList("SELECT id, chave_interna, nome_original_seguro, tipo_detectado,"
                + " tamanho_bytes, checksum, estado_processamento FROM file_asset"
                + " WHERE nome_original_seguro = ?", nomeOriginalSeguro);
    }

    /** Linhas de {@code material} do autor, com a chave do arquivo — o que a publicação gravou. */
    List<Map<String, Object>> materiaisDoAutor(String email) {
        return jdbc.queryForList("SELECT m.id, m.slug, m.titulo, m.estado_publicacao,"
                + " m.publicado_em, x.slug AS categoria, t.slug AS tipo, l.slug AS licenca,"
                + " f.chave_interna, p.id AS perfil_id"
                + " FROM material m"
                + " JOIN profile p ON p.id = m.profile_id"
                + " JOIN account a ON a.id = p.account_id"
                + " JOIN category x ON x.id = m.category_id"
                + " JOIN material_type t ON t.id = m.material_type_id"
                + " JOIN license l ON l.id = m.license_id"
                + " JOIN file_asset f ON f.id = m.file_asset_id"
                + " WHERE a.email = ?", email);
    }

    Map<String, Object> materialPorSlug(String slug) {
        return jdbc.queryForMap("SELECT id, slug, titulo, descricao, estado_publicacao, profile_id,"
                + " file_asset_id, publicado_em, atualizado_em FROM material WHERE slug = ?", slug);
    }

    Optional<String> estadoDoArquivo(String chaveInterna) {
        return jdbc.query("SELECT estado_processamento FROM file_asset WHERE chave_interna = ?",
                (rs, linha) -> rs.getString(1), chaveInterna).stream().findFirst();
    }

    UUID contaId(String email) {
        return jdbc.queryForObject("SELECT id FROM account WHERE email = ?", UUID.class, email);
    }

    UUID perfilId(String email) {
        return jdbc.queryForObject("SELECT p.id FROM profile p JOIN account a ON a.id = p.account_id"
                + " WHERE a.email = ?", UUID.class, email);
    }

    Integer totalDeMateriais(String titulo) {
        return jdbc.queryForObject("SELECT count(*) FROM material WHERE titulo = ?", Integer.class, titulo);
    }

    // ------------------------------------------------------------------ disco

    /** Área temporária de recebimento configurada (perfil de teste: {@code target/}). */
    Path areaTemporaria() {
        return upload.diretorioTemporario().toAbsolutePath().normalize();
    }

    /** Armazenamento definitivo configurado (perfil de teste: {@code target/}). */
    Path armazenamentoDefinitivo() {
        return armazenamento.diretorio();
    }

    List<Path> arquivosNaAreaTemporaria() {
        return arquivosEm(areaTemporaria());
    }

    List<Path> arquivosNoArmazenamento() {
        return arquivosEm(armazenamentoDefinitivo());
    }

    /** O arquivo sob a chave está no armazenamento definitivo? É o passo 2 do RN008, observado. */
    boolean arquivoArmazenado(String chaveInterna) {
        return Files.isRegularFile(armazenamentoDefinitivo().resolve(chaveInterna));
    }

    private static List<Path> arquivosEm(Path diretorio) {
        if (!Files.exists(diretorio)) {
            return List.of();
        }
        try (Stream<Path> entradas = Files.list(diretorio)) {
            return entradas.toList();
        } catch (IOException falha) {
            throw new IllegalStateException("não foi possível listar " + diretorio, falha);
        }
    }

    // ------------------------------------------------------------------ conteúdo e limpeza

    /** PDF sintaticamente aceitável: assinatura {@code %PDF-} e corpo do tamanho pedido. */
    static byte[] pdf(int tamanho) {
        byte[] cabecalho = ASSINATURA.getBytes(StandardCharsets.UTF_8);
        byte[] conteudo = new byte[Math.max(tamanho, cabecalho.length)];
        System.arraycopy(cabecalho, 0, conteudo, 0, cabecalho.length);
        for (int i = cabecalho.length; i < conteudo.length; i++) {
            conteudo[i] = (byte) ('a' + (i % 26));
        }
        return conteudo;
    }

    static String sha256Hex(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (Exception impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }

    /**
     * Remove só o que esta suíte criou, na ordem que as chaves estrangeiras exigem
     * ({@code material} → {@code file_asset} → {@code profile} → {@code account}), e apaga os
     * binários dos dois diretórios pela chave registrada.
     */
    void limpar() {
        List<String> chaves = jdbc.queryForList("SELECT chave_interna FROM file_asset"
                + " WHERE nome_original_seguro LIKE ?", String.class, PREFIXO + "%");
        jdbc.update("DELETE FROM material WHERE profile_id IN (SELECT p.id FROM profile p"
                + " JOIN account a ON a.id = p.account_id WHERE a.email LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM file_asset WHERE nome_original_seguro LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM profile WHERE account_id IN (SELECT id FROM account WHERE email LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
        for (String chave : chaves) {
            apagar(areaTemporaria().resolve(chave));
            apagar(armazenamentoDefinitivo().resolve(chave));
        }
        for (Path arquivo : arquivosNaAreaTemporaria()) {
            apagar(arquivo);
        }
    }

    private static void apagar(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException falha) {
            throw new IllegalStateException("não foi possível limpar " + arquivo, falha);
        }
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

    /** Caminho de leitura pública do material (o mesmo {@code urlPublica} do 201, com o prefixo da API). */
    static String caminhoDaApi(String urlPublica) {
        return "/api" + urlPublica;
    }
}
