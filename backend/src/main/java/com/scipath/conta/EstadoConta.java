package com.scipath.conta;

/**
 * Estado da conta (ADR-0003 (b), coluna {@code account.estado} do V1).
 *
 * <p>O enum existe porque o banco tem o mesmo conjunto fechado no
 * {@code CHECK account_estado_valido}: um valor fora dele não entra, e ter o vocabulário
 * também em Java evita string solta espalhada pelo serviço.
 *
 * <ul>
 *   <li>{@code PENDENTE_VERIFICACAO} — estado inicial de toda conta criada no cadastro.
 *       Login funciona aqui; <b>publicar</b> não (é o que o ADR-0003 (b) fixa).</li>
 *   <li>{@code ATIVO} — e-mail confirmado ({@code account.email_verificado_em} preenchido).
 *       É o único estado que libera publicação (RN002).</li>
 *   <li>{@code SUSPENSO} — reservado para abuso. Não é produzido por nenhum card do MVP.</li>
 * </ul>
 */
public enum EstadoConta {
    PENDENTE_VERIFICACAO,
    ATIVO,
    SUSPENSO
}
