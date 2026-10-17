package com.scipath.identidade;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha da tabela {@code sessao} (V4) como o repositório a lê — o lado <b>interno</b> da
 * sessão. Não é DTO de resposta e nunca é serializada: o que a API publica é
 * {@link SessaoAutenticada}, que não tem token nenhum.
 *
 * <p>{@code tokenHash} é o SHA-256 do valor da cookie, e não o valor: é assim que a
 * resolução procura a linha sem que a tabela guarde a credencial (RNF009). {@code csrfToken}
 * é o valor que o frontend lê na cookie {@code XSRF-TOKEN} — não é credencial de acesso,
 * porque sozinho não autentica nada.
 */
record Sessao(
        UUID id,
        UUID contaId,
        String tokenHash,
        String csrfToken,
        Instant criadaEm,
        Instant ultimoUsoEm,
        Instant expiraEm) {
}
