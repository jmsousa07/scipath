package com.scipath.token;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Escrita e leitura da tabela {@code verification_token} do {@code V1__modelo_mvp.sql} — sem
 * migração nova, como o card exige: {@code token_hash} (UNIQUE), {@code tipo}, {@code expira_em}
 * e {@code consumido_em} já estão no modelo aplicado, e são eles que dão o uso único.
 *
 * <p><b>O uso único é uma cláusula do banco, não um {@code if}.</b> O consumo é um
 * {@code UPDATE ... WHERE ... AND consumido_em IS NULL AND expira_em > :agora RETURNING
 * account_id}: quem marca o token como queimado e quem decide se ele ainda valia são <b>a mesma
 * instrução</b>. A alternativa em duas etapas (ler, decidir, gravar) teria uma janela entre a
 * leitura e a gravação em que dois pedidos com o mesmo token passariam os dois — e é exatamente
 * nessa janela que mora o defeito de uso único que o ADR-0003 (c) quer evitar.
 *
 * <p><b>Inválido, expirado e já consumido dão no mesmo lugar.</b> Os três casos devolvem
 * {@link Optional#empty()}, e não três resultados distintos: o serviço precisa responder a mesma
 * coisa nos três (RNF013 — o cliente não pode aprender nada sobre o token nem sobre a conta), e
 * a forma mais segura de responder igual é <b>não saber</b> a diferença.
 *
 * <p><b>O token em claro não passa por aqui.</b> Toda coluna é chaveada por {@code token_hash}
 * (SHA-256, calculado em {@link com.scipath.identidade.TokensOpacos}); o valor que viajou no
 * e-mail não entra na string do SQL nem em parâmetro, e não há como a linha devolvê-lo
 * (RNF009).
 */
@Repository
public class TokenRepositorio {

    /** Instante da última emissão por conta e tipo — é o relógio do cooldown. */
    private static final String ULTIMA_EMISSAO = """
            SELECT max(criado_em) AS ultima
            FROM verification_token
            WHERE account_id = :contaId AND tipo = :tipo
            """;

    /** Consumo atômico: só apaga a validade do token quem chegar primeiro (uso único). */
    private static final String CONSUMO = """
            UPDATE verification_token
               SET consumido_em = :agora
             WHERE token_hash = :tokenHash
               AND tipo = :tipo
               AND consumido_em IS NULL
               AND expira_em > :agora
            RETURNING account_id
            """;

    private final JdbcClient jdbc;

    public TokenRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Grava o token <b>já hasheado</b> e devolve o {@code id} gerado pelo banco.
     *
     * <p>Os dois instantes chegam do serviço (como na {@code sessao}, V4): assim a validade
     * gravada é a mesma que a resposta e a evidência mostram, sem depender do relógio do banco.
     *
     * @param tokenHash SHA-256 do token — nunca o valor em claro
     */
    public UUID inserir(UUID contaId, String tokenHash, TipoDeToken tipo, Instant criadoEm, Instant expiraEm) {
        return jdbc.sql("INSERT INTO verification_token (account_id, token_hash, tipo, criado_em, expira_em)"
                        + " VALUES (:contaId, :tokenHash, :tipo, :criadoEm, :expiraEm) RETURNING id")
                .param("contaId", contaId)
                .param("tokenHash", tokenHash)
                .param("tipo", tipo.name())
                .param("criadoEm", OffsetDateTime.ofInstant(criadoEm, ZoneOffset.UTC))
                .param("expiraEm", OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC))
                .query(UUID.class)
                .single();
    }

    /**
     * Consome o token e devolve a conta dona, se ele ainda valia — {@link Optional#empty()} para
     * inexistente, de outro tipo, expirado ou já consumido (os quatro são o mesmo caso, ver o
     * javadoc da classe).
     */
    public Optional<UUID> consumir(String tokenHash, TipoDeToken tipo, Instant agora) {
        return jdbc.sql(CONSUMO)
                .param("tokenHash", tokenHash)
                .param("tipo", tipo.name())
                .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
                .query(UUID.class)
                .optional();
    }

    /**
     * Instante da última emissão de um token daquele tipo para a conta — a base do cooldown de
     * reenvio (60 s, ADR-0003 (c)) e da janela silenciosa da recuperação.
     *
     * <p>Vazio quando a conta nunca teve token daquele tipo — inclusive quando a emissão
     * anterior <b>falhou</b> no envio: nada é gravado antes de a mensagem sair
     * ({@link EmissorDeTokens}), então uma falha de canal não deixa a conta presa em um cooldown
     * de um e-mail que ninguém recebeu.
     */
    public Optional<Instant> ultimaEmissao(UUID contaId, TipoDeToken tipo) {
        // list() + findFirst em vez de optional(): max() devolve SEMPRE uma linha, com NULL
        // quando não há token — e o mapeamento de NULL para Instant explodiria em vez de
        // significar "nunca emitido".
        return jdbc.sql(ULTIMA_EMISSAO)
                .param("contaId", contaId)
                .param("tipo", tipo.name())
                .query((rs, linha) -> rs.getObject("ultima", OffsetDateTime.class))
                .list().stream()
                .filter(Objects::nonNull)
                .map(OffsetDateTime::toInstant)
                .findFirst();
    }

    /** Tokens da conta que ainda valem — leitura de diagnóstico e de teste, não de fluxo. */
    public int aindaValidos(UUID contaId, TipoDeToken tipo, Instant agora) {
        return jdbc.sql("SELECT count(*) FROM verification_token"
                        + " WHERE account_id = :contaId AND tipo = :tipo"
                        + " AND consumido_em IS NULL AND expira_em > :agora")
                .param("contaId", contaId)
                .param("tipo", tipo.name())
                .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
                .query(Integer.class)
                .single();
    }
}
