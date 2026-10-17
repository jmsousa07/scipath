package com.scipath.token;

import java.time.Instant;

/**
 * Aceite do reenvio de verificação — schema {@code ReenvioVerificacao} do contrato v0.4
 * (ADR-0003 (c)).
 *
 * <p>Os dois instantes existem para o frontend <b>contar o tempo</b> até o próximo reenvio em
 * vez de descobrir o cooldown de 60 s por tentativa e erro (RNF004): {@code podeReenviarEm} é o
 * mesmo instante que o {@code Retry-After} do 429 anuncia em segundos, e é calculado a partir do
 * {@code criadoEm} que o banco acabou de receber — não de um relógio paralelo.
 *
 * <p>Nenhum campo de conta: o reenvio é para o e-mail da <b>própria</b> conta (a sessão é que
 * diz quem), e o contrato não publica destinatário aqui (RNF013).
 */
public record ReenvioDeVerificacaoResposta(
        Instant reenviadoEm,
        Instant podeReenviarEm) {
}
