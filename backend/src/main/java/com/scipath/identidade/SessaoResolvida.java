package com.scipath.identidade;

import com.scipath.autor.PerfilPublico;
import com.scipath.conta.EstadoConta;
import java.time.Instant;
import java.util.UUID;

/**
 * Sessão resolvida dentro do prazo, com a conta e o perfil que ela identifica — o que a
 * requisição autenticada precisa saber, em <b>uma</b> consulta.
 *
 * <p>É um registro interno do pacote: o {@code csrfToken} fica aqui porque a checagem de CSRF
 * acontece no mesmo ponto em que a sessão é resolvida, mas o que chega ao controller é
 * {@link SessaoAutenticada}, sem token. O {@code id} da sessão também não sai: quem precisa
 * revogá-la usa a cookie, não um identificador exposto.
 *
 * <p>{@code estadoConta} é o estado <b>real</b> da conta hoje — lido junto com a sessão, e
 * não o que valia quando ela foi aberta: a conta pode ter sido suspensa no meio da sessão, e
 * o contrato diz que conta suspensa não mantém sessão (por isso o serviço recusa a resolução
 * nesse caso, em vez de devolver um {@code estadoConta} que o schema {@code SessaoAtual} nem
 * enumera).
 */
record SessaoResolvida(
        UUID sessaoId,
        UUID contaId,
        UUID perfilId,
        EstadoConta estadoConta,
        PerfilPublico perfil,
        String csrfToken,
        Instant ultimoUsoEm,
        Instant expiraEm) {

    /** A identidade que o controller vê — sem token, sem prazo, sem id de sessão. */
    SessaoAutenticada identidade() {
        return new SessaoAutenticada(contaId, perfilId, estadoConta, perfil);
    }
}
