package com.scipath.material;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Licença do material no detalhe — schema {@code LicencaRef} do contrato v0.3, com os dois
 * campos que só o detalhe tem onde exibir.
 *
 * <p>{@code descricaoCurta} continua <b>obrigatória</b> (RN006): é ela que explica, em
 * linguagem simples, o que a licença permite — e o que não permite. Sem ela, o visitante leria
 * "CC BY-NC 4.0" e concluiria o que quisesse; por isso o campo nunca é omitido.
 *
 * <p>{@code identificador} e {@code urlReferencia} são <b>ausentes</b> no JSON quando a
 * licença não tem identificador público nem texto de referência — é o caso de "Todos os
 * direitos reservados", que a seed V2 grava com {@code NULL} nas duas colunas. A ausência é
 * informação (§13.2): não existe URL para linkar, e inventar uma seria publicar o que não
 * existe. Por isso {@code NON_NULL} aqui, enquanto {@code descricaoCurta} e o rótulo saem
 * sempre.
 *
 * <p>Por que um record novo, e não {@code LicencaReferencia} da listagem: aquele é o recorte
 * do item de lista (slug, nome, descrição curta) e o E2.2 registrou por escrito que deixa
 * identificador e URL fora porque a listagem não os usa. Acrescentar os campos lá mudaria a
 * resposta da listagem — fronteira de outro card — sem que a tela da lista passasse a usá-los.
 * Também não é o {@code LicencaResposta} da taxonomia: aquele publica {@code id} interno e
 * {@code ativo}, que o schema público do material não tem (RNF013).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LicencaDetalheResposta(
        String slug,
        String nome,
        String identificador,
        String urlReferencia,
        String descricaoCurta) {
}
