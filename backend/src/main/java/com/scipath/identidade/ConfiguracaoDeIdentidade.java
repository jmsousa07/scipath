package com.scipath.identidade;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Liga a identidade ao MVC: o interceptor que resolve a sessão e checa CSRF, e o resolvedor
 * que entrega {@link SessaoAutenticada} aos controllers.
 *
 * <p>É aqui, e só aqui, que a regra passa a valer para as rotas — o interceptor é um objeto
 * comum, e sem este registro ele existiria sem proteger nada. A fiação está registrada em
 * {@code /api/**} porque toda a fronteira do contrato mora sob esse prefixo; nada fora dele
 * (recurso estático, futuro endpoint de diagnóstico) é afetado.
 */
@Configuration
public class ConfiguracaoDeIdentidade implements WebMvcConfigurer {

    private final SessaoService sessoes;
    private final CookiesDeSessao cookies;
    private final TokensOpacos tokens;

    public ConfiguracaoDeIdentidade(SessaoService sessoes, CookiesDeSessao cookies, TokensOpacos tokens) {
        this.sessoes = sessoes;
        this.cookies = cookies;
        this.tokens = tokens;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new InterceptorDeIdentidade(sessoes, cookies, tokens))
                .addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvedores) {
        resolvedores.add(new ResolvedorDeIdentidade());
    }
}
