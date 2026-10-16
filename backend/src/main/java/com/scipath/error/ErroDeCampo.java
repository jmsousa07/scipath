package com.scipath.error;

/**
 * Um campo recusado pela validação, dentro de {@code errors[]} do formato único de
 * erro do contrato ({@code ProblemDetail}, {@code ErroDeCampo} em
 * {@code contracts/openapi.yaml}).
 *
 * <p>{@code campo} é o caminho do campo no payload ou o nome do parâmetro de consulta;
 * {@code mensagem} diz o que corrigir e nunca carrega detalhe interno (RNF004, RNF012).
 */
public record ErroDeCampo(String campo, String mensagem) {
}
