package com.scipath.error;

/**
 * Reenvio de verificação pedido antes de passar o cooldown de 60 s — vira <b>429</b>
 * {@code urn:scipath:erro:reenvio_em_cooldown}, com {@code Retry-After} em segundos (ADR-0003
 * (c), contrato v0.4).
 *
 * <p><b>Não é erro de campo</b>, e por isso não carrega {@code errors[]}: o pedido está correto,
 * só chegou cedo. O cabeçalho {@code Retry-After} é o que transforma a recusa em algo acionável
 * — o frontend conta o tempo até poder reenviar em vez de descobrir o limite por tentativa e
 * erro (RNF004).
 *
 * <p>É o <b>único</b> 429 do contrato. A solicitação de recuperação de acesso não o usa: um
 * código que só aparecesse para conta existente seria, ele mesmo, o oráculo de existência que a
 * resposta de 202 sempre evita (RNF013).
 */
public class ReenvioEmCooldownException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Texto do {@code detail} — o mesmo do contrato, sem repetir nada da conta. */
    public static final String DETALHE = "Aguarde antes de pedir um novo e-mail de verificação.";

    private final int segundosAteLiberar;

    /**
     * @param segundosAteLiberar segundos até o próximo reenvio ser aceito; mínimo 1, porque
     *                           {@code Retry-After} de 0 s convidaria o cliente a repetir na hora
     */
    public ReenvioEmCooldownException(int segundosAteLiberar) {
        super(DETALHE);
        this.segundosAteLiberar = Math.max(1, segundosAteLiberar);
    }

    public int segundosAteLiberar() {
        return segundosAteLiberar;
    }
}
