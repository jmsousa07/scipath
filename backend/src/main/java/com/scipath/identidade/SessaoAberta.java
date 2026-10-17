package com.scipath.identidade;

import java.time.Instant;

/**
 * Sessão recém-aberta — o <b>único</b> momento em que o valor em claro do token de sessão
 * existe fora do cliente.
 *
 * <p>O record é a fronteira: quem recebe este objeto tem os valores para escrever as duas
 * cookies (a de sessão e a de CSRF) e mais nada. Depois de gravada, a linha só tem o hash
 * ({@link Sessao#tokenHash()}) — nem o serviço consegue recuperar o valor apresentado
 * antes, o que é exatamente o que faz um vazamento da tabela não entregar sessão utilizável.
 */
public record SessaoAberta(String token, String csrfToken, Instant expiraEm) {
}
