package com.scipath.identidade;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Parâmetro de controller que vem da <b>sessão</b>, e não do corpo nem do caminho da
 * requisição (ADR-0003 (j), CA007).
 *
 * <p>Dois usos, e a diferença entre eles é a diferença entre 401 e 200:
 * <ul>
 *   <li>{@code @IdentidadeAutenticada SessaoAutenticada sessao} — a rota <b>exige</b>
 *       identidade. Sem sessão válida, a resposta é <b>401</b> antes de qualquer regra de
 *       negócio rodar, e o parâmetro nunca chega nulo ao controller: não há caminho em que
 *       alguém "esqueça" de checar se está autenticado.</li>
 *   <li>{@code @IdentidadeAutenticada Optional<SessaoAutenticada> sessao} — a rota funciona
 *       <b>com ou sem</b> sessão. É o caso de {@code GET /sessoes/atual}, que responde 200 com
 *       {@code autenticado: false} para o visitante: não estar autenticado não é erro
 *       (RNF004).</li>
 * </ul>
 *
 * <p>A regra de autorização é do <b>servidor</b> (RNF010): esconder o botão no frontend não é
 * controle. Quem opera sobre recurso próprio usa {@link SessaoAutenticada#perfilId()} desta
 * anotação — nunca um identificador que veio no payload.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdentidadeAutenticada {
}
