package com.scipath.conta;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scipath.autor.PerfilPublico;

/**
 * Estado da sessão corrente — schema {@code SessaoAtual} do contrato v0.4 (RF009), a mesma
 * forma que {@code GET /sessoes/atual} publica. O cadastro responde <b>201</b> com este
 * corpo, como o contrato manda.
 *
 * <p><b>Fronteira entre E3.2 e E3.3.</b> Este card grava a conta e o perfil e devolve o
 * corpo do contrato; a <b>sessão</b> em si — tabela, cookie {@code SCIPATH_SESSAO}, o
 * {@code Set-Cookie} do 201 e a exigência de {@code X-CSRF-Token} na escrita — é o E3.3,
 * porque o modelo aplicado (V1) não tem tabela de sessão e migração nova é de outro card
 * (AGENTS.md regra 1). Até lá o 201 sai sem {@code Set-Cookie}: o corpo já é o do contrato.
 *
 * <p><b>Nada de conta.</b> E-mail de login, hash de senha, token e identificador interno de
 * conta não existem neste record — nem aqui, nem no contrato (RNF013), nem para o dono. O
 * DTO é a barreira: enquanto o campo não existir, não há como vazá-lo por engano.
 *
 * <p>{@code perfil} é o {@link PerfilPublico} — o <b>mesmo</b> schema do perfil do próprio
 * usuário e da identidade pública do autor: um só lugar descreve os campos públicos, e o
 * pedido privado e o público não podem divergir.
 *
 * <p>{@code estadoConta} é o estado real da conta gravada. Publicar exige {@code ATIVO};
 * a conta recém-cadastrada sai {@code PENDENTE_VERIFICACAO} (ADR-0003 (b)), porque a
 * verificação de e-mail é o E3.4 — o cadastro grava só o estado inicial.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessaoAtualResposta(
        boolean autenticado,
        EstadoConta estadoConta,
        PerfilPublico perfil) {
}
