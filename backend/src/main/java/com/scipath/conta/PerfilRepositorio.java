package com.scipath.conta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Escrita e leitura da tabela {@code profile} (V1). Como {@link ContaRepositorio}: SQL
 * explícito e <b>sem migração nova</b> — {@code profile_conta_unica} (1:1 com a conta),
 * {@code profile_slug_unico} e {@code profile_slug_formato} já estão no modelo aplicado e
 * são a garantia real da unicidade de slug.
 *
 * <p>A unicidade do slug é resolvida em duas partes, de propósito:
 * {@link #slugsEmUsoComPrefixo} traz de uma vez todos os slugs que podem colidir com um
 * candidato (uma consulta, não uma por tentativa) e o serviço escolhe o sufixo; a
 * {@code UNIQUE} da coluna continua sendo o juiz final — se dois cadastros idênticos
 * correrem em paralelo, o segundo é recusado pelo banco, e não gravado com slug repetido.
 */
@Repository
public class PerfilRepositorio {

    private static final String CAMPOS = "id, account_id, nome_publico, slug, biografia,"
            + " afiliacao_declarada, areas_interesse, links_externos";

    private final JdbcClient jdbc;

    public PerfilRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Grava o perfil inicial e devolve o {@code id} gerado.
     *
     * <p>Só {@code nome_publico} e {@code slug} são preenchidos: o cadastro não recebe
     * biografia, afiliação, áreas nem links (o schema {@code CadastroConta} do contrato tem
     * três campos). As colunas ficam {@code NULL} — "não informado", que é o mesmo estado de
     * um perfil que nunca passou pela edição de perfil (E3.5).
     */
    public UUID inserir(UUID contaId, String nomePublico, String slug) {
        return jdbc.sql("INSERT INTO profile (account_id, nome_publico, slug)"
                        + " VALUES (:contaId, :nomePublico, :slug) RETURNING id")
                .param("contaId", contaId)
                .param("nomePublico", nomePublico)
                .param("slug", slug)
                .query(UUID.class)
                .single();
    }

    /**
     * Perfil da conta, se existir — a leitura que fecha o cadastro.
     *
     * <p>O serviço monta a resposta a partir <b>desta</b> leitura, e não da entrada: assim o
     * que o cliente recebe em {@code perfil} é literalmente o que ficou gravado (RN013).
     */
    public Optional<Perfil> buscarPorConta(UUID contaId) {
        return jdbc.sql("SELECT " + CAMPOS + " FROM profile WHERE account_id = :contaId")
                .param("contaId", contaId)
                .query((rs, linha) -> new Perfil(
                        rs.getObject("id", UUID.class),
                        rs.getObject("account_id", UUID.class),
                        rs.getString("nome_publico"),
                        rs.getString("slug"),
                        rs.getString("biografia"),
                        rs.getString("afiliacao_declarada"),
                        listaDeTexto(rs.getArray("areas_interesse")),
                        listaDeTexto(rs.getArray("links_externos"))))
                .optional();
    }

    /**
     * Slugs já em uso que podem colidir com o candidato derivado de {@code base}: o próprio
     * {@code base} e todos os sufixados ({@code base-2}, {@code base-3}, ...).
     *
     * <p>Uma consulta só, com o padrão ligado como parâmetro ({@code LIKE :prefixo}) — nada
     * de concatenar valor de requisição na string do SQL. O volume é pequeno por construção:
     * são slugs derivados do <i>mesmo</i> nome público.
     *
     * @param base slug derivado do nome público, já no formato do contrato
     */
    public List<String> slugsEmUsoComPrefixo(String base) {
        return jdbc.sql("SELECT slug FROM profile WHERE slug = :base OR slug LIKE :prefixo")
                .param("base", base)
                .param("prefixo", base + "-%")
                .query(String.class)
                .list();
    }

    /** {@code text[]} do PostgreSQL como lista Java; {@code NULL} é lista vazia (RN004). */
    private static List<String> listaDeTexto(java.sql.Array array) {
        if (array == null) {
            return List.of();
        }
        try {
            Object conteudo = array.getArray();
            if (conteudo instanceof String[] valores) {
                return List.of(valores);
            }
            return List.of();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("não foi possível ler a lista de texto do perfil", e);
        }
    }
}
