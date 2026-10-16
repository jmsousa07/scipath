package com.scipath.material;

/**
 * Categoria anexada a um material na descoberta — schema {@code CategoriaRef} do contrato
 * v0.3. É a categoria de referência (ADR-0006) reduzida ao que a listagem exibe e navega:
 * o cliente usa {@code slug} para filtrar (é o mesmo valor aceito pelo parâmetro
 * {@code categoria}) e linkar a página da categoria (RF007); {@code nome} é só rótulo,
 * que pode mudar sem quebrar dado nenhum.
 *
 * <p>O {@code id} interno da categoria fica fora do schema de propósito: o cliente não
 * deriva significado dele e a fronteira pública não publica identificador de tabela.
 */
public record CategoriaReferencia(String slug, String nome) {
}
