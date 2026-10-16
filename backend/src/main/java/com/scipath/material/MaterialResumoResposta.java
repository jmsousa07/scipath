package com.scipath.material;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Item de material na descoberta pública — schema {@code MaterialResumo} do contrato v0.3
 * (RF002). DTO de resposta: o projeto não usa JPA e o material é lido por SQL explícito.
 *
 * <p>Todo item devolvido está PUBLICADO e pertence a conta ATIVA (RN002, §11.3) — por isso
 * {@code estado} <b>não</b> é campo aqui: nesta superfície ele é sempre PUBLICADO, e material
 * retirado ou excluído simplesmente não aparece (o detalhe, que é outra superfície, publica
 * o estado).
 *
 * <p>{@code atualizadoEm} é <b>ausente</b> quando a atualização coincide com a publicação: o
 * contrato o descreve como "presente quando difere da publicação", e repetir a mesma data em
 * dois campos sugere ao leitor uma edição que não houve. {@code NON_NULL} faz a omissão — a
 * data que interessa continua saindo quando existe.
 *
 * <p>{@code slug} é o identificador público e estável (a URL canônica do detalhe); o
 * {@code id} interno da tabela não entra na fronteira pública.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaterialResumoResposta(
        String slug,
        String titulo,
        String descricao,
        AutorResumoResposta autor,
        CategoriaReferencia categoria,
        TipoMaterialReferencia tipo,
        LicencaReferencia licenca,
        Instant publicadoEm,
        Instant atualizadoEm) {
}
