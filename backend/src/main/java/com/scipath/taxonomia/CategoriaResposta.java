package com.scipath.taxonomia;

import java.util.UUID;

/**
 * Categoria científica de referência, como o contrato v0.2 publica (schema
 * {@code Categoria}). É DTO de resposta, não entidade de persistência: a tabela
 * {@code category} é lida por SQL explícito e não existe mapeamento JPA no projeto.
 *
 * <p>{@code slug} é a chave estável que o cliente guarda e reenvia; {@code nome} é rótulo
 * de exibição e pode mudar sem quebrar dado nenhum (ADR-0006, RN009).
 *
 * <p>{@code descricao} integra os campos obrigatórios do schema. Na coluna ela é anulável
 * (V1), mas a seed V2 preenche as 13 — se alguma vier nula, o campo sai como {@code null}
 * em vez de desaparecer, porque o contrato o exige presente e omitir seria quebra de
 * schema silenciosa.
 */
public record CategoriaResposta(UUID id, String slug, String nome, String descricao, boolean ativo) {
}
