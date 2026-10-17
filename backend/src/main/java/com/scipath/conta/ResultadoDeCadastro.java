package com.scipath.conta;

import java.util.UUID;

/**
 * O que o cadastro produz: o estado que a resposta publica e o {@code id} da conta criada.
 *
 * <p>O {@code contaId} existe porque <b>o cadastro já sai autenticado</b> (contrato v0.4:
 * "sucesso devolve 201 e já abre a sessão") e abrir a sessão precisa saber de quem ela é. Ele
 * não vira campo de resposta: o schema {@code SessaoAtual} não tem identificador de conta, de
 * propósito (RNF013). Este record é o que atravessa essa fronteira — do serviço de cadastro
 * para o serviço de sessão — sem que o dado privado entre no corpo HTTP.
 */
public record ResultadoDeCadastro(SessaoAtualResposta estado, UUID contaId) {
}
