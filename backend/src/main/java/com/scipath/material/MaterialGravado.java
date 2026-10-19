package com.scipath.material;

import java.util.UUID;

/**
 * O material como a linha ficou gravada — o que o {@code INSERT} da publicação devolve por
 * {@code RETURNING}.
 *
 * <p><b>A leitura vem do banco de propósito.</b> Quem publica não monta a resposta com o que
 * pretendia gravar: o que o 201 afirma é o que a coluna tem (RN013). Se o {@code estado_publicacao}
 * gravado divergisse do esperado, a resposta diria a verdade — e a divergência apareceria em vez
 * de ficar escondida atrás do valor que o serviço achava ter escrito.
 *
 * <p>É objeto de dentro do servidor, não schema do contrato: o {@code id} interno existe porque a
 * resposta e as asserções de banco o usam, e nenhum campo daqui aparece numa resposta HTTP
 * (RNF013). O {@code slug} é o único dos três que o cliente recebe — como parte do
 * {@code MaterialPublicado} do contrato.
 */
record MaterialGravado(UUID id, String slug, EstadoPublicacao estado) {
}
