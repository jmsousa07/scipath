package com.scipath.error;

/**
 * A identidade é conhecida mas não tem permissão para a operação — vira {@code 403}
 * no formato único de erro.
 *
 * <p>Ver {@link NaoAutenticadoException} para o porquê de a classe existir antes da
 * Etapa 3. Regra de autorização é do servidor (RNF010): esconder botão no frontend
 * não é controle.
 */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String detalheSeguroParaExibir) {
        super(detalheSeguroParaExibir);
    }
}
