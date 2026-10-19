package com.scipath.material;

/**
 * O formulário de publicação como ele chegou do {@code multipart/form-data} (contrato v0.5, schema
 * {@code PublicacaoMaterial}) — <b>texto cru, nada validado neste tipo</b>.
 *
 * <p>Todo campo é {@code String} aqui, e o motivo principal é um só: <b>ausente e vazio são estados
 * diferentes no contrato</b>. O pedido que o servidor não conseguiu ler como veio é <b>400</b>
 * (campo obrigatório ausente no formulário); o corpo lido com campo de valor inválido é <b>422</b>.
 * Um {@code Boolean} no binding apagaria essa diferença, e {@code declaracaoDireito=false} é
 * exatamente o caso de 422 enquanto {@code titulo} ausente é 400.
 *
 * <p><b>A validação é do serviço, e ela aponta o campo pelo nome do contrato.</b> Quem recusa diz
 * {@code categoriaSlug} — o nome que o cliente enviou e o que o {@code errors[]} publica. A
 * validação de cliente é conveniência; a autoridade é o servidor (RNF011).
 *
 * <p><b>{@code estado} não é campo do schema do contrato.</b> Ele aparece aqui apenas para que o
 * serviço possa <b>recusar</b> quem tentar definir o estado por requisição (RF019): o material
 * nasce sempre em {@link EstadoPublicacao#INICIAL} e o cliente não escolhe.
 *
 * <p><b>Não existe campo de autor, de conta nem de perfil</b> (ADR-0003 (j)): a autoria vem da
 * sessão. Um desses campos enviado no formulário não encontra destino nenhum — não há componente
 * aqui que o receba, e é essa ausência que faz a tentativa ser <i>ignorada</i> em vez de obedecida.
 */
record PublicacaoDeMaterialRequisicao(
        String titulo,
        String descricao,
        String categoriaSlug,
        String tipoSlug,
        String licencaSlug,
        String declaracaoDireito,
        String estado) {
}
