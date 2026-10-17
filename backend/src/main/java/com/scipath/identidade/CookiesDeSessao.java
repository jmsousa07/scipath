package com.scipath.identidade;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * As duas cookies da identidade (ADR-0003 (e), contrato v0.4) — o único lugar que escreve
 * {@code Set-Cookie} de sessão, para os atributos não divergirem entre login, cadastro e
 * logout.
 *
 * <p><b>{@code SCIPATH_SESSAO}</b> — a credencial: {@code HttpOnly} (o JavaScript do frontend
 * não a lê), {@code Secure}, {@code SameSite=Lax} e {@code Path=/}. Nada de sessão em
 * {@code localStorage}: a cookie viaja sozinha na mesma origem (proxy {@code /api} do Next) e
 * um XSS não consegue copiar a sessão para outra máquina.
 *
 * <p><b>{@code XSRF-TOKEN}</b> — o token de CSRF: <b>sem</b> {@code HttpOnly}, de propósito,
 * porque é o frontend que precisa lê-la para devolvê-la no cabeçalho {@code X-CSRF-Token}. Não
 * é credencial de acesso: sozinho não autentica nada, e por isso pode ser legível (o contrato
 * diz isso em letras).
 *
 * <p><b>{@code Secure} sempre</b>, inclusive em desenvolvimento: o contrato e o ADR fixam o
 * atributo sem exceção, e o frontend de desenvolvimento fala com a API pela <b>mesma origem</b>
 * ({@code http://localhost:3000/api} pelo proxy do Next) — origem que os navegadores tratam
 * como confiável, onde cookie {@code Secure} é aceita. Trocar o atributo conforme o ambiente
 * seria a diferença que faz o produto funcionar em dev e falhar no ar.
 *
 * <p>O {@code Max-Age} é o <b>limite absoluto</b> da sessão, e não o prazo de inatividade: é o
 * único dos dois que não se estende, então é o único que o navegador pode anunciar. A
 * inatividade continua sendo julgada no servidor, a cada requisição.
 */
@Component
public class CookiesDeSessao {

    /** Nome da cookie de sessão — o mesmo do {@code securityScheme SessaoCookie} do contrato. */
    public static final String COOKIE_DE_SESSAO = "SCIPATH_SESSAO";

    /** Nome da cookie de CSRF — o mesmo que o parâmetro {@code TokenCsrf} do contrato publica. */
    public static final String COOKIE_DE_CSRF = "XSRF-TOKEN";

    private final PropriedadesDeSessao propriedades;

    public CookiesDeSessao(PropriedadesDeSessao propriedades) {
        this.propriedades = propriedades;
    }

    /**
     * Escreve as duas cookies de uma sessão recém-aberta (login e cadastro).
     *
     * <p>As duas no mesmo instante: uma sessão sem o token CSRF correspondente seria
     * inutilizável — toda escrita responderia 403.
     */
    public void abrir(HttpServletResponse resposta, SessaoAberta sessao) {
        escrever(resposta, COOKIE_DE_SESSAO, sessao.token(), propriedades.duracaoDaCookie(), true);
        escrever(resposta, COOKIE_DE_CSRF, sessao.csrfToken(), propriedades.duracaoDaCookie(), false);
    }

    /**
     * Emite (ou reemite) só a cookie de CSRF — usada quando a sessão já existe e o cliente
     * perdeu ou nunca teve o token legível, e na primeira leitura do app por um visitante.
     */
    public void emitirCsrf(HttpServletResponse resposta, String csrfToken) {
        escrever(resposta, COOKIE_DE_CSRF, csrfToken, propriedades.duracaoDaCookie(), false);
    }

    /**
     * Expira as duas cookies no logout.
     *
     * <p>Isto é higiene de cliente, não segurança: o que invalida a sessão é a linha apagada
     * no servidor ({@link SessaoService#encerrar}). Mesmo que o cliente ignore a expiração e
     * reapresente o valor, ele não encontra mais sessão.
     */
    public void encerrar(HttpServletResponse resposta) {
        escrever(resposta, COOKIE_DE_SESSAO, "", Duration.ZERO, true);
        escrever(resposta, COOKIE_DE_CSRF, "", Duration.ZERO, false);
    }

    /** Valor da cookie na requisição, se o cliente a mandou. */
    public static Optional<String> ler(HttpServletRequest requisicao, String nome) {
        Cookie[] cookies = requisicao.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> nome.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst();
    }

    private void escrever(HttpServletResponse resposta, String nome, String valor, Duration validade, boolean httpOnly) {
        ResponseCookie cookie = ResponseCookie.from(nome, valor)
                .httpOnly(httpOnly)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(validade)
                .build();
        resposta.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
