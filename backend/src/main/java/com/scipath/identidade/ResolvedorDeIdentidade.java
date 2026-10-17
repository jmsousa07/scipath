package com.scipath.identidade;

import com.scipath.error.NaoAutenticadoException;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Entrega ao controller a identidade que o {@link InterceptorDeIdentidade} resolveu —
 * ninguém lê cookie nem consulta a tabela dentro de um controller.
 *
 * <p>Dois formatos, e o tipo do parâmetro decide qual:
 * <ul>
 *   <li>{@code SessaoAutenticada} — a rota exige identidade. O interceptor já recusou a
 *       requisição sem sessão (401), então chegar aqui sem identidade é estado impossível; a
 *       exceção abaixo existe como rede de segurança, para o dia em que alguém registrar o
 *       interceptor num caminho mais estreito que o resolver. Falhar alto (401) é melhor que
 *       entregar {@code null} ao controller.</li>
 *   <li>{@code Optional<SessaoAutenticada>} — a rota aceita visitante e devolve o estado
 *       anônimo (200 com {@code autenticado: false}), que é o caso de
 *       {@code GET /sessoes/atual}.</li>
 * </ul>
 */
public class ResolvedorDeIdentidade implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        if (!parametro.hasParameterAnnotation(IdentidadeAutenticada.class)) {
            return false;
        }
        Class<?> tipo = parametro.getParameterType();
        return SessaoAutenticada.class.isAssignableFrom(tipo) || Optional.class.isAssignableFrom(tipo);
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer mavContainer,
            NativeWebRequest requisicao, WebDataBinderFactory binderFactory) {
        Object sessao = requisicao.getAttribute(InterceptorDeIdentidade.ATRIBUTO_DA_SESSAO,
                RequestAttributes.SCOPE_REQUEST);
        if (Optional.class.isAssignableFrom(parametro.getParameterType())) {
            return Optional.ofNullable(sessao);
        }
        if (sessao == null) {
            throw new NaoAutenticadoException(InterceptorDeIdentidade.DETALHE_SEM_SESSAO);
        }
        return sessao;
    }
}
