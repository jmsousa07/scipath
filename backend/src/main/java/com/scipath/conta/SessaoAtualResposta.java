package com.scipath.conta;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scipath.autor.PerfilPublico;

/**
 * Estado da sessão corrente — schema {@code SessaoAtual} do contrato v0.4 (RF009), a mesma
 * forma que {@code GET /sessoes/atual} publica. O cadastro responde <b>201</b> com este
 * corpo, como o contrato manda.
 *
 * <p><b>Fronteira entre E3.2 e E3.3.</b> O E3.2 gravou a conta e o perfil e passou a devolver
 * este corpo; o E3.3 (sessão) fez o {@code Set-Cookie} das duas cookies no mesmo 201, porque o
 * contrato diz que o cadastro já sai autenticado. O record continua sem campo de conta: o
 * identificador que a abertura da sessão usa viaja em
 * {@link com.scipath.conta.ResultadoDeCadastro}, que não é serializado.
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
