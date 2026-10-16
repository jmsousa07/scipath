package com.scipath.error;

/**
 * A requisição não traz identidade válida — vira {@code 401} no formato único de erro.
 *
 * <p>Existe agora, junto com {@link AcessoNegadoException}, para que o tratamento de
 * erro já tenha as quatro classes exigidas pelo card E1.4 (400/404/401-403/500) e a
 * Etapa 3 (autenticação, ADR-0003) só precise lançar estas exceções — nenhum segundo
 * formato de erro nasce depois. Nada é autenticado hoje: segurança e CORS são a
 * Etapa 3 (ADR-0004, ajuste 8).
 */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException(String detalheSeguroParaExibir) {
        super(detalheSeguroParaExibir);
    }
}
