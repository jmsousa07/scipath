package com.scipath.error;

/**
 * Recurso pedido não existe (ou não é visível para quem pediu) — vira {@code 404}
 * no formato único de erro.
 *
 * <p>A mensagem passada no construtor vai para o {@code detail} da resposta, então
 * ela precisa ser segura para exibição ao usuário (RNF012) e acionável (RNF004):
 * diga o que não foi encontrado, nunca como o banco está organizado.
 */
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String detalheSeguroParaExibir) {
        super(detalheSeguroParaExibir);
    }
}
