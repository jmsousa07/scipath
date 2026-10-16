package com.scipath.taxonomia;

import java.util.List;

/**
 * Envelope padrão de lista paginada do contrato v0.2 (schema {@code Pagina},
 * {@code contracts/openapi.yaml}): sempre exatamente estes quatro campos, nada mais.
 *
 * <p>O nome do recurso não entra no envelope: {@code PaginaCategorias},
 * {@code PaginaTiposMaterial} e {@code PaginaLicencas} no contrato só especializam o tipo
 * de {@code items}. Por isso há um único envelope genérico aqui, em vez de três quase
 * iguais.
 *
 * <p>{@code total} é o total que atende à consulta, **não** o tamanho de {@code items}:
 * é o que permite ao cliente saber quantas páginas existem sem varrer a base
 * (RNF021 — teto de {@code size} por requisição).
 */
public record Pagina<T>(List<T> items, int page, int size, long total) {
}
