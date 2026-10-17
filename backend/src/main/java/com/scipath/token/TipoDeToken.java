package com.scipath.token;

/**
 * Os dois usos do token de uso único (ADR-0003 (c) e (d)) — o mesmo vocabulário do
 * {@code CHECK verification_token_tipo_valido} do {@code V1__modelo_mvp.sql}.
 *
 * <p>O enum existe pelo mesmo motivo de {@link com.scipath.conta.EstadoConta}: o banco tem o
 * conjunto fechado, e ter os dois valores também em Java evita string solta no serviço. É o
 * tipo que separa os dois fluxos em toda a fronteira — o token de confirmação de e-mail só vale
 * na confirmação de e-mail, o de recuperação só na recuperação. Um token apresentado na rota
 * errada simplesmente <b>não é encontrado</b> (a consulta de consumo filtra por tipo), e a
 * resposta é a mesma recusa de token inválido, sem revelar que aquele valor existe para outro
 * fim (RNF013).
 *
 * <ul>
 *   <li>{@code CONFIRMACAO_EMAIL} — validade de 24 h; confirmar consome o token, registra
 *       {@code account.email_verificado_em} e leva a conta a {@code ATIVO}.</li>
 *   <li>{@code RECUPERACAO_SENHA} — validade de 30 min; confirmar consome o token, troca a senha
 *       e invalida todas as sessões abertas da conta.</li>
 * </ul>
 */
public enum TipoDeToken {
    CONFIRMACAO_EMAIL,
    RECUPERACAO_SENHA
}
