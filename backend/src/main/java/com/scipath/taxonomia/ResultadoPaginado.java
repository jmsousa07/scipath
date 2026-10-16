package com.scipath.taxonomia;

import java.util.List;

/**
 * Resultado de uma leitura paginada no banco, ainda sem o envelope do contrato: os itens
 * da página corrente e o total da consulta. Existe para que a contagem (que ignora
 * {@code LIMIT}/{@code OFFSET}) e a página sejam lidas na mesma chamada ao repositório,
 * sem o serviço ter de saber que são duas consultas SQL.
 */
public record ResultadoPaginado<T>(List<T> itens, long total) {
}
