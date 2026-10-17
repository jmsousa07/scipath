package com.scipath.identidade;

import com.scipath.error.AcessoNegadoException;
import com.scipath.error.NaoAutenticadoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * O ponto em que a sessão vira <b>autorização no servidor</b> (RNF010): resolve a cookie,
 * deixa a identidade disponível para o controller e recusa a escrita sem o token CSRF.
 *
 * <p>É um {@link HandlerInterceptor} e não um {@code Filter} por três motivos concretos:
 * <ul>
 *   <li><b>Erro no formato único.</b> Exceção lançada aqui é tratada pelo
 *       {@code ApiExceptionHandler} (o interceptor roda dentro do {@code DispatcherServlet});
 *       num filtro, o 403 teria de ser escrito à mão e o projeto passaria a ter dois lugares
 *       montando {@code problem+json}.</li>
 *   <li><b>Só rota que existe.</b> Requisição a caminho inexistente (404) ou com método não
 *       aceito (405) nem chega a montar a cadeia de interceptores — então {@code POST
 *       /api/health} continua respondendo 405, e não 403 de CSRF. Proteger "tudo que é POST"
 *       antes de saber se existe rota inverteria esses dois códigos.</li>
 *   <li><b>A ordem do contrato.</b> Autenticação precede CSRF: numa escrita que exige
 *       identidade e chega sem sessão, a resposta é 401, não 403. Aqui dá para saber se a rota
 *       exige identidade — o handler está na mão ({@link #exigeIdentidade}).</li>
 * </ul>
 *
 * <p><b>A sessão é resolvida uma vez por requisição</b>, aqui, e o controller recebe o
 * resultado por {@link IdentidadeAutenticada}. Nenhum controller abre cookie, consulta a
 * tabela ou decide se está autenticado.
 *
 * <p><b>CSRF: token duplo, com amarração na sessão.</b> O valor chega em dois canais — a
 * cookie legível {@code XSRF-TOKEN} e o cabeçalho {@code X-CSRF-Token} — e os dois precisam
 * coincidir. Isso já barra o ataque clássico (um site de terceiro consegue <i>enviar</i> a
 * cookie de sessão, mas não consegue <i>ler</i> a cookie de CSRF para montar o cabeçalho).
 * Quando há sessão, o valor ainda é conferido contra o token gravado na linha da sessão: um
 * cookie de CSRF plantado por outra origem (subdomínio, histórico de outra conta) não basta
 * para escrever.
 *
 * <p>Os valores comparados são <b>iguais em tempo constante</b> ({@link TokensOpacos#iguais}):
 * comparar byte a byte com {@code equals} deixaria o tempo de resposta contar quantos
 * caracteres iniciais o atacante acertou.
 *
 * <p><b>Exceção declarada pelo contrato:</b> as duas confirmações por token
 * ({@code /verificacao-email/confirmacoes} e {@code /recuperacao-senha/confirmacoes}) não
 * exigem o cabeçalho em nenhum caso — o segredo vai no corpo e não há cookie de sessão em
 * jogo (ADR-0003 (c) e (d)). A lista está aqui, com os caminhos do contrato, porque a dispensa
 * é regra do <b>interceptor</b> e não de cada controller: o E3.4 a consultou e acrescentou a
 * solicitação de recuperação, que o contrato v0.4 também não põe entre as escritas com CSRF
 * (ver {@link #ESCRITAS_SEM_CSRF}).
 */
public class InterceptorDeIdentidade implements HandlerInterceptor {

    /** Atributo da requisição com a {@link SessaoAutenticada} — o que o resolvedor entrega ao controller. */
    public static final String ATRIBUTO_DA_SESSAO = "com.scipath.identidade.sessao";

    /** Atributo com a sessão resolvida <i>interna</i> (com token e prazos) — só este pacote lê. */
    static final String ATRIBUTO_DA_SESSAO_RESOLVIDA = "com.scipath.identidade.sessao.resolvida";

    /** Cabeçalho publicado no contrato como {@code TokenCsrf}. */
    static final String CABECALHO_DE_CSRF = "X-CSRF-Token";

    /** Detalhe do 401 — o mesmo texto do contrato para rota privada sem sessão. */
    static final String DETALHE_SEM_SESSAO = "É necessário entrar na conta para esta operação.";

    /** Detalhe do 403 — o mesmo texto do contrato para escrita sem o token CSRF. */
    static final String DETALHE_CSRF = "Falta o cabeçalho 'X-CSRF-Token' ou ele não corresponde à sessão corrente.";

    private static final Set<String> METODOS_DE_ESCRITA = Set.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * Escritas que o contrato dispensa de CSRF (ADR-0003 (c) e (d)). O caminho é comparado por
     * igualdade exata.
     *
     * <ul>
     *   <li>As <b>duas confirmações por token</b> — o segredo vai no corpo e não há cookie de
     *       sessão em jogo: quem clica no link pode estar em outro navegador, sem cookie nenhuma,
     *       e exigir o cabeçalho ali seria exigir um token de uma sessão que não existe.</li>
     *   <li>A <b>solicitação de recuperação</b> (E3.4). O contrato v0.4 não a lista entre as
     *       escritas que exigem {@code X-CSRF-Token} (o parâmetro do contrato enumera cadastro,
     *       login, logout, editar perfil e reenvio de verificação) e não publica 403 para esta
     *       rota — um 403 que o frontend não trata transformaria a página de recuperação em beco
     *       sem saída em navegador sem a cookie {@code XSRF-TOKEN}, e o pior caso que o CSRF
     *       evitaria aqui é enviar um link de recuperação a quem não pediu, não uma escrita em
     *       nome de outra pessoa. Exigir o que o contrato não exige seria inventar uma recusa que
     *       o cliente não sabe ler (AGENTS.md regra 1: o contrato vem primeiro).</li>
     * </ul>
     */
    private static final Set<String> ESCRITAS_SEM_CSRF = Set.of(
            "/api/verificacao-email/confirmacoes",
            "/api/recuperacao-senha/confirmacoes",
            "/api/recuperacao-senha/solicitacoes");

    private final SessaoService sessoes;
    private final CookiesDeSessao cookies;
    private final TokensOpacos tokens;

    public InterceptorDeIdentidade(SessaoService sessoes, CookiesDeSessao cookies, TokensOpacos tokens) {
        this.sessoes = sessoes;
        this.cookies = cookies;
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest requisicao, HttpServletResponse resposta, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return true;
        }

        Optional<SessaoResolvida> sessao = sessoes.resolver(
                CookiesDeSessao.ler(requisicao, CookiesDeSessao.COOKIE_DE_SESSAO).orElse(null));
        sessao.ifPresent(resolvida -> {
            requisicao.setAttribute(ATRIBUTO_DA_SESSAO, resolvida.identidade());
            requisicao.setAttribute(ATRIBUTO_DA_SESSAO_RESOLVIDA, resolvida);
        });

        sincronizarCookieDeCsrf(requisicao, resposta, sessao.orElse(null));

        if (METODOS_DE_ESCRITA.contains(requisicao.getMethod()) && !ESCRITAS_SEM_CSRF.contains(requisicao.getRequestURI())) {
            if (exigeIdentidade(metodo) && sessao.isEmpty()) {
                throw new NaoAutenticadoException(DETALHE_SEM_SESSAO);
            }
            validarCsrf(requisicao, sessao.orElse(null));
        }
        return true;
    }

    /**
     * Mantém a cookie legível de CSRF em sincronia com a sessão resolvida; para o visitante
     * sem cookie nenhuma, emite uma na primeira leitura do app — é o que o contrato promete
     * ("a primeira leitura do app já a traz") e o que permite ao formulário de cadastro
     * (que responde sem sessão) mandar o cabeçalho.
     *
     * <p>Não emite em método de escrita: um cliente que chega escrevendo sem token nenhum
     * recebe 403, e não um token de cortesia na resposta de recusa.
     */
    private void sincronizarCookieDeCsrf(HttpServletRequest requisicao, HttpServletResponse resposta,
            SessaoResolvida sessao) {
        String csrfDoCliente = CookiesDeSessao.ler(requisicao, CookiesDeSessao.COOKIE_DE_CSRF).orElse(null);
        if (sessao != null) {
            if (!TokensOpacos.iguais(sessao.csrfToken(), csrfDoCliente)) {
                cookies.emitirCsrf(resposta, sessao.csrfToken());
            }
            return;
        }
        if (csrfDoCliente == null && !METODOS_DE_ESCRITA.contains(requisicao.getMethod())) {
            cookies.emitirCsrf(resposta, tokens.gerar());
        }
    }

    /**
     * Token duplo: cabeçalho e cookie precisam coincidir; havendo sessão, o valor ainda é
     * conferido contra o token da linha (ver o javadoc da classe).
     */
    private void validarCsrf(HttpServletRequest requisicao, SessaoResolvida sessao) {
        String cabecalho = requisicao.getHeader(CABECALHO_DE_CSRF);
        String csrfDoCliente = CookiesDeSessao.ler(requisicao, CookiesDeSessao.COOKIE_DE_CSRF).orElse(null);
        if (cabecalho == null || csrfDoCliente == null || !TokensOpacos.iguais(cabecalho, csrfDoCliente)) {
            throw new AcessoNegadoException(DETALHE_CSRF);
        }
        if (sessao != null && !TokensOpacos.iguais(cabecalho, sessao.csrfToken())) {
            throw new AcessoNegadoException(DETALHE_CSRF);
        }
    }

    /**
     * A rota exige identidade quando declara {@code @IdentidadeAutenticada} com o tipo
     * concreto; com {@code Optional} ela funciona com ou sem sessão ({@code GET
     * /sessoes/atual}). É esta checagem que faz o 401 vir antes do 403 de CSRF.
     */
    private static boolean exigeIdentidade(HandlerMethod metodo) {
        for (MethodParameter parametro : metodo.getMethodParameters()) {
            if (parametro.hasParameterAnnotation(IdentidadeAutenticada.class)
                    && !Optional.class.equals(parametro.getParameterType())) {
                return true;
            }
        }
        return false;
    }
}
