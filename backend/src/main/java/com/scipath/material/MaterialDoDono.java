package com.scipath.material;

import java.time.Instant;

/**
 * Um material da conta autenticada <b>lido do banco</b> — a linha do material com o arquivo, antes de
 * virar resposta (RF015, ADR-0011 §2). É o equivalente, na visão do dono, do
 * {@link DetalheDoMaterial} na visão pública.
 *
 * <p><b>Não é schema do contrato.</b> É a projeção interna que o {@link MateriaisPropriosService}
 * recebe do repositório para montar o {@link MaterialProprioResposta}: os campos que o contrato
 * publica <b>mais</b> o que ele nunca publica e a decisão do serviço precisa — o estado do
 * {@code file_asset} e a chave do armazenamento, que decidem {@code arquivo.disponivel}.
 *
 * <p><b>Não tem autor, e é de propósito.</b> A autoria é sempre o perfil da sessão (RN001): a
 * consulta já é restrita a {@code profile_id} da sessão, então um campo {@code autor} carregaria um
 * valor só — o próprio dono (RNF013, contrato v0.6, schema {@code MaterialProprio}).
 *
 * <p><b>Os três estados chegam aqui.</b> Ao contrário da leitura pública (que só devolve
 * {@code PUBLICADO} de conta {@code ATIVO}, RN002), a visão do dono inclui {@code RETIRADO} e
 * {@code EXCLUIDO} — quem excluiu não perde o registro do que excluiu (ADR-0011 §2). O estado é
 * publicado como veio do banco, sem tradução.
 *
 * @param slug             identificador público e estável do material (RNF025)
 * @param titulo           título (RN004)
 * @param descricao        descrição (RN004)
 * @param categoria        categoria com o slug estável da taxonomia (ADR-0006)
 * @param tipo             tipo de material com o slug estável da taxonomia
 * @param licenca          licença com o que ela permite (RN006)
 * @param estadoPublicacao estado no banco — {@code PUBLICADO}, {@code RETIRADO} ou {@code EXCLUIDO}
 * @param publicadoEm      instante da publicação
 * @param atualizadoEm     instante da última escrita; é a chave da ordenação da lista do dono
 * @param arquivo          o arquivo do material, lido como o download o lê ({@link ArquivoDoMaterial})
 */
public record MaterialDoDono(
        String slug,
        String titulo,
        String descricao,
        CategoriaReferencia categoria,
        TipoMaterialReferencia tipo,
        LicencaReferencia licenca,
        String estadoPublicacao,
        Instant publicadoEm,
        Instant atualizadoEm,
        ArquivoDoMaterial arquivo) {
}
