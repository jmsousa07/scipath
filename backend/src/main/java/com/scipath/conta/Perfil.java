package com.scipath.conta;

import java.util.List;
import java.util.UUID;

/**
 * Perfil (tabela {@code profile} do V1) — o lado <b>público</b> da identidade, 1:1 com a
 * conta ({@code profile_conta_unica}). É linha da tabela como o cadastro a lê de volta
 * depois de gravar: a resposta do endpoint sai deste registro, não da entrada, e é por isso
 * que o teste de integração prova que o que voltou é o que o <b>SQL</b> guardou.
 *
 * <p>{@code slug} é o identificador público único (RN009) — derivado do nome público no
 * cadastro e garantido único pelo serviço. {@code biografia}, {@code afiliacaoDeclarada},
 * {@code areasInteresse} e {@code linksExternos} nascem nulos no cadastro (o cliente não os
 * envia) e passam a ter conteúdo na edição de perfil (E3.5).
 *
 * <p>{@code contaId} é {@code account_id}: liga este perfil à conta dona. Nem ele nem
 * {@code id} chegam à resposta — a fronteira pública é
 * {@link com.scipath.autor.PerfilPublico} (RNF013).
 */
record Perfil(
        UUID id,
        UUID contaId,
        String nomePublico,
        String slug,
        String biografia,
        String afiliacaoDeclarada,
        List<String> areasInteresse,
        List<String> linksExternos) {
}
