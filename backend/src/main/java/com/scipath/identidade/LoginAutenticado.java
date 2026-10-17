package com.scipath.identidade;

import com.scipath.conta.Conta;
import com.scipath.conta.SessaoAtualResposta;
import java.util.UUID;

/**
 * Resultado de um login bem-sucedido: <b>quem</b> entrou e o que a resposta publica.
 *
 * <p>O {@code contaId} existe porque abrir a sessão precisa dele e o schema
 * {@code SessaoAtual} não tem identificador de conta — de propósito (RNF013). Este record é
 * interno à aplicação: o {@code contaId} não vira campo de resposta, ele fica entre o serviço
 * de credenciais e o serviço de sessão.
 */
public record LoginAutenticado(UUID contaId, SessaoAtualResposta estado) {
}
