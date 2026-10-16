package com.scipath.taxonomia;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Licença de reutilização de referência, como o contrato v0.2 publica (schema
 * {@code Licenca}). DTO de resposta — não há entidade JPA neste projeto.
 *
 * <p>{@code identificador} e {@code urlReferencia} são <b>ausentes</b> no JSON quando a
 * licença não tem identificador nem texto público — é o caso de "Todos os direitos
 * reservados", que na V2 grava {@code NULL} nas duas colunas. O contrato trata os dois
 * campos como opcionais e a ausência é informação: não existe URL de referência a exibir.
 * Por isso {@code NON_NULL} aqui, enquanto nos outros DTOs o campo obrigatório continua
 * saindo mesmo nulo.
 *
 * <p>{@code descricaoCurta} é obrigatório (RN006): explica em linguagem simples o que a
 * licença permite, para a licença não virar enfeite na tela.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LicencaResposta(UUID id, String slug, String nome, String identificador,
        String urlReferencia, String descricaoCurta, boolean ativo) {
}
