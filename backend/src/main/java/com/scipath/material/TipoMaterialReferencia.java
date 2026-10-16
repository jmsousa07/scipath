package com.scipath.material;

/**
 * Tipo de material anexado a um material na descoberta — schema {@code TipoMaterialRef} do
 * contrato v0.3. {@code slug} é a chave estável aceita pelo parâmetro de filtro
 * {@code tipo}; {@code nome} é rótulo de exibição (ADR-0006).
 */
public record TipoMaterialReferencia(String slug, String nome) {
}
