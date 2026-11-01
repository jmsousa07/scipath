package com.scipath.material;

import java.util.UUID;

/**
 * O que a publicação resolveu do formulário: o texto já validado e o <b>identificador interno</b>
 * de cada item da taxonomia (RF018).
 *
 * <p><b>Existe para o slug não virar identificador no meio do caminho.</b> O cliente envia
 * {@code categoriaSlug}, {@code tipoSlug} e {@code licencaSlug} — as chaves estáveis do ADR-0006,
 * as mesmas que a leitura devolve e que os filtros de descoberta aceitam. A tabela do material
 * guarda {@code category_id}, {@code material_type_id} e {@code license_id}. A tradução é feita
 * <b>consultando o banco</b>, e a resposta a "existe?" é o que decide o 422 do campo (RF018): item
 * inexistente <b>ou inativo</b> não tem identificador que o serviço possa inventar.
 *
 * <p>Taxonomia inativa é recusada de propósito: a lista pública de referência só devolve ativos, e
 * aceitar um item desativado permitiria publicar material com categoria que ninguém consegue
 * filtrar (o filtro da descoberta casa por {@code slug} sem olhar {@code ativo}).
 *
 * <p><b>O mesmo formato serve aos dois atos que escrevem metadados</b> — publicar (RF012) e editar
 * (RF016, E5.3): os campos editáveis são os mesmos cinco, com os mesmos limites e as mesmas
 * recusas, então quem os carrega é um só. A diferença entre os atos fica no que <i>cerca</i> este
 * record (a declaração de direito e o estado inicial são só da publicação), não no conteúdo dele.
 *
 * <p>Record de dentro do servidor: nenhum destes campos é publicado — o 201 devolve apenas slug,
 * estado e URL (RF012), e o 200 da edição é o {@code MaterialProprio} relido do banco.
 *
 * @param titulo     título do material, já validado (RN004)
 * @param descricao  descrição do material, já validada (RN004)
 * @param categoriaId {@code category.id} do slug informado, existente e ativo
 * @param tipoId     {@code material_type.id} do slug informado, existente e ativo
 * @param licencaId  {@code license.id} do slug informado, existente e ativa — nunca escolhida
 *                   pelo servidor (RF018: nenhuma licença é atribuída automaticamente)
 */
record MetadadosDaPublicacao(String titulo, String descricao, UUID categoriaId, UUID tipoId,
        UUID licencaId) {
}
