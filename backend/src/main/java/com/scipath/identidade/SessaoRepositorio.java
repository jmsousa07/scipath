package com.scipath.identidade;

import com.scipath.autor.PerfilPublico;
import com.scipath.conta.EstadoConta;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Escrita e leitura da tabela {@code sessao} (V4). SQL explícito com {@link JdbcClient}, como
 * o resto do projeto: são três operações e nenhuma precisa de ORM.
 *
 * <p><b>A resolução é uma consulta só.</b> Sessão, conta e perfil vêm no mesmo
 * {@code SELECT} — a alternativa (três idas ao banco por requisição autenticada) pagaria o
 * custo que o contrato já declara aceitável ("uma consulta por requisição autenticada") três
 * vezes. O {@code LEFT JOIN} do perfil é defensivo: cadastro e perfil nascem na mesma
 * transação (E3.2), então perfil ausente é invariante quebrada por intervenção externa — e
 * nesse caso não há identidade pública para devolver, então a linha é tratada como sessão
 * irresolúvel em vez de virar 500.
 *
 * <p><b>O token nunca aparece em SQL literal nem em log.</b> A busca é por
 * {@code token_hash}, com o hash calculado em {@link TokensOpacos} — o valor que veio na
 * cookie não entra na consulta, e a linha não tem como devolvê-lo.
 */
@Repository
public class SessaoRepositorio {

    private static final String RESOLUCAO = """
            SELECT s.id                AS sessao_id,
                   s.csrf_token        AS csrf_token,
                   s.ultimo_uso_em     AS ultimo_uso_em,
                   s.expira_em         AS expira_em,
                   a.id                AS conta_id,
                   a.estado            AS estado,
                   p.id                AS perfil_id,
                   p.nome_publico      AS nome_publico,
                   p.slug              AS slug,
                   p.biografia         AS biografia,
                   p.afiliacao_declarada AS afiliacao_declarada,
                   p.areas_interesse   AS areas_interesse,
                   p.links_externos    AS links_externos
            FROM sessao s
            JOIN account a ON a.id = s.account_id
            LEFT JOIN profile p ON p.account_id = a.id
            WHERE s.token_hash = :tokenHash
            """;

    private final JdbcClient jdbc;

    public SessaoRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Grava a sessão e devolve o {@code id} gerado.
     *
     * <p>{@code criada_em} e {@code expira_em} chegam do serviço, calculados com um
     * {@link Instant} só — o limite absoluto nasce aqui e não se estende depois. Os dois
     * instantes são gravados de propósito: o {@code CHECK sessao_expira_depois_de_criar}
     * recusa linha sem prazo, e a evidência do card consegue mostrar a duração aplicada sem
     * depender do relógio do banco.
     */
    public UUID inserir(UUID contaId, String tokenHash, String csrfToken, Instant criadaEm, Instant expiraEm) {
        return jdbc.sql("INSERT INTO sessao (account_id, token_hash, csrf_token, criada_em, ultimo_uso_em, expira_em)"
                        + " VALUES (:contaId, :tokenHash, :csrfToken, :criadaEm, :criadaEm, :expiraEm) RETURNING id")
                .param("contaId", contaId)
                .param("tokenHash", tokenHash)
                .param("csrfToken", csrfToken)
                .param("criadaEm", OffsetDateTime.ofInstant(criadaEm, java.time.ZoneOffset.UTC))
                .param("expiraEm", OffsetDateTime.ofInstant(expiraEm, java.time.ZoneOffset.UTC))
                .query(UUID.class)
                .single();
    }

    /**
     * Sessão, conta e perfil da cookie apresentada — {@link Optional#empty()} quando o hash
     * não corresponde a nenhuma linha.
     *
     * <p>Este método <b>não</b> decide prazo: ele devolve os instantes e quem julga é o
     * {@link SessaoService}. A separação é de propósito — a regra de expiração fica em um
     * lugar só, testável sem banco, e o repositório não ganha um relógio.
     */
    public Optional<SessaoResolvida> buscarResolvida(String tokenHash) {
        // list() + findFirst em vez de optional(): o mapeamento devolve null quando a
        // invariante perfil↔conta está quebrada, e Optional.of(null) explodiria em vez de
        // simplesmente não haver identidade (o caso que o LEFT JOIN acima prevê).
        return jdbc.sql(RESOLUCAO)
                .param("tokenHash", tokenHash)
                .query((rs, linha) -> {
                    UUID perfilId = rs.getObject("perfil_id", UUID.class);
                    if (perfilId == null) {
                        return null;
                    }
                    OffsetDateTime ultimoUso = rs.getObject("ultimo_uso_em", OffsetDateTime.class);
                    OffsetDateTime expira = rs.getObject("expira_em", OffsetDateTime.class);
                    return new SessaoResolvida(
                            rs.getObject("sessao_id", UUID.class),
                            rs.getObject("conta_id", UUID.class),
                            perfilId,
                            EstadoConta.valueOf(rs.getString("estado")),
                            new PerfilPublico(
                                    rs.getString("slug"),
                                    rs.getString("nome_publico"),
                                    rs.getString("biografia"),
                                    rs.getString("afiliacao_declarada"),
                                    listaDeTexto(rs.getArray("areas_interesse")),
                                    listaDeTexto(rs.getArray("links_externos"))),
                            rs.getString("csrf_token"),
                            ultimoUso.toInstant(),
                            expira.toInstant());
                })
                .list().stream()
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    /**
     * Desloca o último uso da sessão. Chamado no máximo uma vez por
     * {@link PropriedadesDeSessao#janelaDeToque()} — não a cada requisição (ver o javadoc da
     * janela: sem isso toda leitura autenticada viraria escrita).
     */
    public void registrarUso(UUID sessaoId, Instant agora) {
        jdbc.sql("UPDATE sessao SET ultimo_uso_em = :agora WHERE id = :id")
                .param("agora", OffsetDateTime.ofInstant(agora, java.time.ZoneOffset.UTC))
                .param("id", sessaoId)
                .update();
    }

    /**
     * Revoga a sessão da cookie apresentada — <b>é isto</b> que o logout precisa garantir:
     * o valor reapresentado depois não encontra mais linha, mesmo que o cliente o guarde.
     *
     * @return linhas apagadas (0 quando a cookie já não valia — o logout é idempotente)
     */
    public int revogarPorTokenHash(String tokenHash) {
        return jdbc.sql("DELETE FROM sessao WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash)
                .update();
    }

    /**
     * Revoga <b>todas</b> as sessões de uma conta — o que a troca de senha por recuperação de
     * acesso exige (E3.4, RNF012).
     *
     * <p>Por sessão individual isto seria impossível: quem trocou a senha pode ter sessão aberta
     * em um navegador que o servidor não conhece (a cookie guarda só o valor opaco, e o mapa
     * cookie→sessão é a tabela). <b>Apagar por conta é o que garante</b> que nenhuma cookie
     * obtida antes da troca continue valendo — e é a razão concreta de a sessão viver em tabela
     * (V4, contrato v0.4).
     *
     * <p>O índice {@code sessao_conta_idx} do V4 existe para esta consulta.
     *
     * @return linhas apagadas (0 = a conta não tinha sessão aberta; o logout dela já aconteceu)
     */
    public int revogarPorConta(UUID contaId) {
        return jdbc.sql("DELETE FROM sessao WHERE account_id = :contaId")
                .param("contaId", contaId)
                .update();
    }

    /** {@code text[]} do PostgreSQL como lista Java; {@code NULL} é lista vazia (RN004). */
    private static List<String> listaDeTexto(Array array) {
        if (array == null) {
            return List.of();
        }
        try {
            Object conteudo = array.getArray();
            if (conteudo instanceof String[] valores) {
                return List.of(valores);
            }
            return List.of();
        } catch (SQLException e) {
            throw new IllegalStateException("não foi possível ler a lista de texto do perfil", e);
        }
    }
}
