package com.scipath.taxonomia;

import java.util.UUID;

/**
 * Tipo de material de referência, como o contrato v0.2 publica (schema
 * {@code TipoMaterial}). DTO de resposta — não há entidade JPA neste projeto.
 *
 * <p>O tipo não tem descrição: é rótulo curto de categorização (nota de aula, slides,
 * artigo...), e o contrato não publica campo nenhum além de {@code id}, {@code slug},
 * {@code nome} e {@code ativo}
 */
public record TipoMaterialResposta(UUID id, String slug, String nome, boolean ativo) {
}
