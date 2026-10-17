package com.scipath.conta;

import java.time.Instant;
import java.util.UUID;

/**
 * Conta de autenticação (DRS §11.1.1, tabela {@code account} do V1) — o lado <b>privado</b>
 * da identidade. É a entidade que o cadastro grava e que o login (E3.3) confere.
 *
 * <p><b>Este record é interno e não é DTO de resposta.</b> {@code senhaHash} existe aqui
 * porque é coluna da tabela e o serviço precisa dela para gravar (e, no E3.3, para
 * conferir a senha); o que sai na API é {@link SessaoAtualResposta}, que não tem campo
 * nenhum de conta — nem e-mail, nem hash, nem identificador (RNF009, RNF013). Nenhum
 * {@code toString()} desta classe é registrado em log.
 *
 * <p>{@code email} é sempre o valor normalizado do V1 ({@code account_email_normalizado}:
 * minúsculas e sem espaços nas pontas) — a normalização acontece antes da gravação, em
 * {@link CadastroContaRequisicao}, e a coluna recusa quem tentar gravar fora disso.
 *
 * <p>{@code emailVerificadoEm} é {@code null} enquanto o e-mail não foi confirmado: é o
 * marco que separa "pode publicar" de "não pode" (ADR-0003 (b)). O card E3.2 só grava o
 * estado inicial; quem preenche esse instante é o E3.4.
 */
public record Conta(
        UUID id,
        String email,
        String senhaHash,
        EstadoConta estado,
        Instant emailVerificadoEm) {
}
