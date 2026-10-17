package com.scipath.autor;

import java.util.List;

/**
 * Linha da tabela {@code profile} como o perfil público a lê — dado interno do pacote
 * {@code autor}, entre o repositório e o serviço. Não é resposta: o que sai na API é
 * {@link AutorPublicoResposta}, que compõe esta identidade com a página de materiais.
 *
 * <p>As colunas são exatamente as do schema {@code AutorPublico}, menos {@code materiais}
 * (que não é coluna de perfil): {@code slug}, {@code nome_publico}, {@code biografia},
 * {@code afiliacao_declarada}, {@code areas_interesse} e {@code links_externos}. Nem
 * {@code id} de perfil nem {@code account_id} entram — o repositório público não lê id
 * interno, e é assim que ele não tem como vazar (RNF013).
 *
 * <p>{@code biografia} e {@code afiliacaoDeclarada} podem ser {@code null} (colunas
 * anuláveis); {@code areasInteresse} e {@code linksExternos} são sempre lista, vazia quando
 * a coluna é {@code NULL} — a diferença entre "não informou" e "informou vazio" não existe
 * para o produto, e o contrato exige os dois campos sempre presentes.
 */
record PerfilPublico(
        String slug,
        String nomePublico,
        String biografia,
        String afiliacaoDeclarada,
        List<String> areasInteresse,
        List<String> linksExternos) {
}
