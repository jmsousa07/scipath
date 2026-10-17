package com.scipath.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scipath.email.EmailSenderDeOutbox;
import com.scipath.email.PropriedadesDeEmail;
import com.scipath.identidade.CookiesDeSessao;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Apoio comum dos testes do card E3.4 — <b>o que os três têm em comum</b>: falar com a API como
 * um cliente fala (cookie na mão, cabeçalho na mão) e ler o que o canal de e-mail registrou.
 *
 * <p><b>Sem jar de cookie.</b> Como no E3.3: {@link TestRestTemplate} não guarda cookie nenhum,
 * cada requisição declara o que manda, e é isso que faz cada caso provar o que diz provar. O que
 * este apoio acrescenta é a leitura do <b>outbox</b> (application-test.yml aponta o canal para
 * {@code backend/target/outbox-de-teste/outbox.jsonl}): é dele que o teste tira o token que o
 * cadastro ou a solicitação de recuperação emitiu — <b>sem substituir</b> o canal nem espiar
 * objeto interno, exatamente como um desenvolvedor faria com {@code make email:outbox}.
 *
 * <p><b>O token é mascarado na saída padrão.</b> O log da suíte é a evidência (docs/qa), e a
 * evidência não carrega segredo vivo: o que os testes imprimem passa por {@link #mascararLink} —
 * o valor em claro existe no outbox e na requisição, nunca no log (RNF009).
 *
 * <p>Dados controlados pelo prefixo {@code teste-e3-4-} no e-mail, apagados antes e depois de
 * cada caso (a FK {@code ON DELETE CASCADE} leva perfil, token e sessão junto).
 */
abstract class ApoioDeTestesDeToken {

    static final ObjectMapper JSON = new ObjectMapper();

    static final String PREFIXO = "teste-e3-4-";
    static final String DOMINIO = "@example.test";

    /** Senha do cadastro (8–72 caracteres, ADR-0003 (a)). */
    static final String SENHA = "nota-de-aula-2026";

    /** Senha usada pela recuperação — diferente da anterior para o teste não se enganar. */
    static final String SENHA_NOVA = "senha-nova-de-2026";

    static final String URL_CADASTRO = "/api/contas";
    static final String URL_LOGIN = "/api/sessoes";
    static final String URL_ESTADO_DA_SESSAO = "/api/sessoes/atual";
    static final String URL_REENVIOS = "/api/verificacao-email/reenvios";
    static final String URL_CONFIRMACAO_DE_EMAIL = "/api/verificacao-email/confirmacoes";
    static final String URL_SOLICITACAO_DE_RECUPERACAO = "/api/recuperacao-senha/solicitacoes";
    static final String URL_CONFIRMACAO_DE_RECUPERACAO = "/api/recuperacao-senha/confirmacoes";

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    /** Diretório do outbox e endereços dos links — o mesmo que o canal de e-mail usa. */
    @Autowired
    PropriedadesDeEmail propriedadesDeEmail;

    @BeforeEach
    void limparAntes() throws IOException {
        limparDadosDeTeste();
        esvaziarOutbox();
    }

    @AfterEach
    void limparDepois() {
        limparDadosDeTeste();
    }

    /**
     * Zera o arquivo de outbox antes de cada caso.
     *
     * <p>Por que apagar e não contar por diferença: o outbox é <b>um arquivo só para a suíte
     * inteira</b> (o caminho vem da configuração, não do teste), e ele <b>acumula entre execuções</b>
     * — sem isto, a segunda rodada do mesmo teste veria as mensagens da primeira e uma asserção de
     * "uma mensagem" viraria "duas". Cada caso começa, então, com o canal vazio: o que o teste lê é
     * exatamente o que o caso acabou de produzir.
     */
    private void esvaziarOutbox() throws IOException {
        Path diretorio = Path.of(propriedadesDeEmail.getOutbox().getDiretorio());
        Files.createDirectories(diretorio);
        Files.writeString(arquivoDoOutbox(), "", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // ------------------------------------------------------------------ HTTP

    /** Sessão como o cliente a tem: o valor da cookie opaca e o token CSRF que ela carrega. */
    record Sessao(String token, String csrf) {
    }

    /** Cadastro pelo endpoint do contrato (201). Devolve a sessão que o 201 já abriu. */
    Sessao criarConta(String email, String nomePublico) {
        String csrf = csrfDeVisitante();
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_CADASTRO,
                corpoDoCadastro(email, nomePublico), null, csrf, csrf);
        assertThat(resposta.getStatusCode().value())
                .as("cadastro de apoio falhou: " + resposta.getBody())
                .isEqualTo(201);
        return new Sessao(
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow(),
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow());
    }

    /** Login pelo endpoint do contrato; falha o teste se não vier 200. */
    Sessao logar(String email, String senha) {
        return logar(email, senha, null);
    }

    Sessao logar(String email, String senha, Sessao anterior) {
        String csrf = anterior == null ? csrfDeVisitante() : anterior.csrf();
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, URL_LOGIN, credenciais(email, senha),
                anterior, csrf, csrf);
        assertThat(resposta.getStatusCode().value())
                .as("login de apoio falhou: " + resposta.getBody())
                .isEqualTo(200);
        return new Sessao(
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_SESSAO).orElseThrow(),
                valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow());
    }

    /** Status do login sem exigir sucesso — para provar que a senha antiga deixou de valer. */
    int statusDoLogin(String email, String senha) {
        String csrf = csrfDeVisitante();
        return chamar(HttpMethod.POST, URL_LOGIN, credenciais(email, senha), null, csrf, csrf)
                .getStatusCode().value();
    }

    /** O token que o frontend lê na cookie {@code XSRF-TOKEN} da primeira leitura do app. */
    String csrfDeVisitante() {
        ResponseEntity<String> resposta = rest.getForEntity(URL_ESTADO_DA_SESSAO, String.class);
        return valorDaCookie(resposta, CookiesDeSessao.COOKIE_DE_CSRF).orElseThrow(
                () -> new AssertionError("GET " + URL_ESTADO_DA_SESSAO + " não emitiu a cookie XSRF-TOKEN"));
    }

    /** Requisição autenticada: manda a cookie da sessão e o token CSRF dela no cabeçalho. */
    ResponseEntity<String> autenticado(HttpMethod metodo, String caminho, String corpo, Sessao sessao) {
        return chamar(metodo, caminho, corpo, sessao, sessao == null ? null : sessao.csrf(),
                sessao == null ? null : sessao.csrf());
    }

    ResponseEntity<String> chamar(HttpMethod metodo, String caminho, String corpo,
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

    static String corpoDoCadastro(String email, String nomePublico) {
        return JSON.createObjectNode().put("email", email).put("senha", SENHA).put("nomePublico", nomePublico)
                .toString();
    }

    static String credenciais(String email, String senha) {
        return JSON.createObjectNode().put("email", email).put("senha", senha).toString();
    }

    static String corpoDoToken(String token) {
        return JSON.createObjectNode().put("token", token).toString();
    }

    static String corpoDaSolicitacaoDeRecuperacao(String email) {
        return JSON.createObjectNode().put("email", email).toString();
    }

    static String corpoDaConfirmacaoDeRecuperacao(String token, String novaSenha) {
        return JSON.createObjectNode().put("token", token).put("novaSenha", novaSenha).toString();
    }

    static Optional<String> valorDaCookie(ResponseEntity<?> resposta, String nome) {
        return linhasDeCookie(resposta, nome)
                .map(linha -> {
                    int igual = linha.indexOf('=');
                    int pontoEVirgula = linha.indexOf(';');
                    return pontoEVirgula < 0 ? linha.substring(igual + 1) : linha.substring(igual + 1, pontoEVirgula);
                })
                .filter(valor -> !valor.isBlank())
                .findFirst();
    }

    static Stream<String> linhasDeCookie(ResponseEntity<?> resposta, String nome) {
        return resposta.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).stream()
                .filter(linha -> linha.startsWith(nome + "="));
    }

    // --------------------------------------------------------------- outbox

    /** Caminho do arquivo de outbox que o perfil de teste configurou (dentro de {@code target/}). */
    Path arquivoDoOutbox() {
        return Path.of(propriedadesDeEmail.getOutbox().getDiretorio()).resolve(EmailSenderDeOutbox.ARQUIVO);
    }

    /**
     * Todas as mensagens já registradas para um destinatário, na ordem do arquivo (a última é a
     * mais recente). Lê o arquivo de verdade: é o mesmo dado que {@code make email:outbox} mostra.
     *
     * <p>Cuidado ao contar: um mesmo endereço recebe tipos diferentes — o cadastro manda
     * {@code CONFIRMACAO_EMAIL} e a recuperação manda {@code RECUPERACAO_SENHA}. Quando o número
     * importa, conte pelo {@link #mensagensPara(String, TipoDeToken) tipo}.
     */
    List<EmailSenderDeOutbox.LinhaDoOutbox> mensagensPara(String destinatario) {
        return mensagensPara(destinatario, null);
    }

    /** As mensagens de um destinatário <b>de um tipo</b> ({@code null} = todos os tipos). */
    List<EmailSenderDeOutbox.LinhaDoOutbox> mensagensPara(String destinatario, TipoDeToken tipo) {
        Path arquivo = arquivoDoOutbox();
        if (!Files.exists(arquivo)) {
            return List.of();
        }
        try (Stream<String> linhas = Files.lines(arquivo, StandardCharsets.UTF_8)) {
            return linhas
                    .map(ApoioDeTestesDeToken::linhaDoOutbox)
                    .filter(Objects::nonNull)
                    .filter(linha -> destinatario.equals(linha.destinatario()))
                    .filter(linha -> tipo == null || tipo.name().equals(linha.tipo()))
                    .toList();
        } catch (IOException falha) {
            throw new IllegalStateException("não foi possível ler o outbox de teste em " + arquivo, falha);
        }
    }

    Optional<EmailSenderDeOutbox.LinhaDoOutbox> ultimaMensagemPara(String destinatario) {
        return ultimaMensagemPara(destinatario, null);
    }

    /** A última mensagem do destinatário daquele tipo — o token que o fluxo acabou de emitir. */
    Optional<EmailSenderDeOutbox.LinhaDoOutbox> ultimaMensagemPara(String destinatario, TipoDeToken tipo) {
        List<EmailSenderDeOutbox.LinhaDoOutbox> mensagens = mensagensPara(destinatario, tipo);
        return mensagens.isEmpty() ? Optional.empty() : Optional.of(mensagens.get(mensagens.size() - 1));
    }

    /** A linha crua do outbox (uma por mensagem) — o que o grep da evidência percorre. */
    List<String> linhasCruasDoOutbox() {
        Path arquivo = arquivoDoOutbox();
        if (!Files.exists(arquivo)) {
            return List.of();
        }
        try {
            return Files.readAllLines(arquivo, StandardCharsets.UTF_8);
        } catch (IOException falha) {
            throw new IllegalStateException("não foi possível ler o outbox de teste em " + arquivo, falha);
        }
    }

    private static EmailSenderDeOutbox.LinhaDoOutbox linhaDoOutbox(String linha) {
        if (linha.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(linha, EmailSenderDeOutbox.LinhaDoOutbox.class);
        } catch (IOException falha) {
            throw new IllegalStateException("linha do outbox não é JSON válido: " + falha.getMessage(), falha);
        }
    }

    /**
     * O token que o link carrega — o fragmento {@code #token=...} é o único lugar em que o valor
     * em claro existe fora da mensagem. Um link sem {@code token=} é defeito do modelo de
     * mensagem, e o teste falha aqui, não três passos depois.
     */
    static String tokenDoLink(String link) {
        int marcador = link.indexOf("token=");
        assertThat(marcador)
                .as("o link do e-mail precisa carregar o token no fragmento: " + mascararLink(link))
                .isGreaterThanOrEqualTo(0);
        return link.substring(marcador + "token=".length());
    }

    /**
     * Link com o token trocado por um marcador. Tudo o que os testes imprimem passa por aqui: a
     * evidência mostra a forma do link e não entrega um token que ainda vale (RNF009).
     */
    static String mascararLink(String link) {
        int marcador = link.indexOf("token=");
        if (marcador < 0) {
            return link;
        }
        return link.substring(0, marcador) + "token=<valor opaco omitido>";
    }

    // ------------------------------------------------------------------ banco

    UUID contaId(String email) {
        return jdbc.queryForObject("SELECT id FROM account WHERE email = ?", UUID.class, email);
    }

    String estadoDaConta(String email) {
        return jdbc.queryForObject("SELECT estado FROM account WHERE email = ?", String.class, email);
    }

    String verificadoEmDaConta(String email) {
        return jdbc.queryForObject("SELECT email_verificado_em::text FROM account WHERE email = ?",
                String.class, email);
    }

    String senhaHashDaConta(String email) {
        return jdbc.queryForObject("SELECT senha_hash FROM account WHERE email = ?", String.class, email);
    }

    /** Linhas de token do tipo pedido para a conta (qualquer estado: válido, expirado ou consumido). */
    int linhasDeToken(String email, TipoDeToken tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM verification_token t"
                + " JOIN account a ON a.id = t.account_id WHERE a.email = ? AND t.tipo = ?",
                Integer.class, email, tipo.name());
    }

    /** O token foi consumido? (uso único: a marca é {@code consumido_em}). */
    String consumidoEmDoToken(String tokenEmClaro, TipoDeToken tipo) {
        return jdbc.queryForObject("SELECT consumido_em::text FROM verification_token"
                + " WHERE token_hash = ? AND tipo = ?",
                String.class, com.scipath.identidade.TokensOpacos.hash(tokenEmClaro), tipo.name());
    }

    /** Duração gravada do token, em segundos — o que a evidência compara com o ADR-0003. */
    long duracaoGravadaEmSegundos(String tokenEmClaro, TipoDeToken tipo) {
        Double segundos = jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM (expira_em - criado_em)) FROM"
                + " verification_token WHERE token_hash = ? AND tipo = ?",
                Double.class, com.scipath.identidade.TokensOpacos.hash(tokenEmClaro), tipo.name());
        return segundos == null ? -1 : Math.round(segundos);
    }

    /** Quantas sessões abertas a conta tem — usado para provar que a recuperação as derrubou. */
    int sessoesDaConta(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM sessao s"
                + " JOIN account a ON a.id = s.account_id WHERE a.email = ?", Integer.class, email);
    }

    /** Linhas de sessão que um valor de cookie identifica (0 = aquela cookie não autentica mais). */
    int sessoesDoToken(String tokenDaCookie) {
        return jdbc.queryForObject("SELECT count(*) FROM sessao WHERE token_hash = ?", Integer.class,
                com.scipath.identidade.TokensOpacos.hash(tokenDaCookie));
    }

    /** Remove só as linhas criadas por esta família de testes (perfil antes da conta, pela FK). */
    void limparDadosDeTeste() {
        jdbc.update("DELETE FROM profile WHERE account_id IN"
                + " (SELECT id FROM account WHERE email LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
    }
}
