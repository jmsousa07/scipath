package com.scipath.autor;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Identidade pública do perfil — schema {@code PerfilPublico} do contrato, compartilhado
 * entre o perfil público do autor e o perfil do próprio usuário. Linha da tabela
 * {@code profile} como os endpoints a leem, sem {@code id} nem {@code account_id}: é por não
 * existir campo de conta aqui que o vazamento por engano é impossível, e não por alguém
 * lembrar de não incluir (RNF013).
 *
 * <p>As colunas são exatamente as do schema: {@code slug}, {@code nome_publico},
 * {@code biografia}, {@code afiliacao_declarada}, {@code areas_interesse} e
 * {@code links_externos}.
 *
 * <p><b>Dois consumidores, um schema.</b> O contrato v0.4 declara que {@code AutorPublico}
 * (parte de identidade do perfil público) e {@code SessaoAtual} (perfil devolvido pelo
 * cadastro e por {@code GET /sessoes/atual}) usam o mesmo {@code PerfilPublico} justamente
 * para "o pedido público e o privado não divergirem"; aqui isso é o <b>mesmo tipo Java</b> —
 * o 201 do cadastro (E3.2) monta o próprio {@code perfil} com este record.
 * {@link AutorPublicoResposta} achata estes campos junto da página de materiais, e a
 * instância JSON que o cliente recebe é a mesma.
 *
 * <p><b>Ausente é informação.</b> {@code biografia} e {@code afiliacaoDeclarada} podem ser
 * {@code null} (colunas anuláveis); {@code areasInteresse} e {@code linksExternos} são
 * sempre lista, vazia quando a coluna é {@code NULL} — a diferença entre "não informou" e
 * "informou vazio" não existe para o produto, e o contrato exige os dois campos sempre
 * presentes. Com {@code NON_NULL}, campo não informado <b>não sai</b> em vez de sair vazio.
 *
 * <p>{@code afiliacaoDeclarada} é declaração do próprio autor, nunca vínculo verificado pela
 * plataforma (RN011, §13.1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PerfilPublico(
        String slug,
        String nomePublico,
        String biografia,
        String afiliacaoDeclarada,
        List<String> areasInteresse,
        List<String> linksExternos) {
}
