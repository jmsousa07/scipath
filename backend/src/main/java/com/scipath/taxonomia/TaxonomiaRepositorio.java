package com.scipath.taxonomia;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Leitura das três tabelas de taxonomia de referência (RF018): {@code category},
 * {@code material_type} e {@code license}. SQL explícito com {@link JdbcTemplate} — o
 * projeto não usa JPA, e listagem de referência não precisa de ORM.
 *
 * <p>Duas regras que valem para as três leituras:
 * <ul>
 *   <li><b>Ordenação explícita e estável:</b> {@code ORDER BY nome, slug}. O nome é a
 *       ordem publicada no contrato; o slug entra só como desempate, porque
 *       {@code nome} não é único no banco e sem desempate o PostgreSQL não promete
 *       ordem entre páginas — o cliente veria item repetido ou sumido ao paginar
 *       (RNF021, RN009).</li>
 *   <li><b>{@code LIMIT}/{@code OFFSET} sempre com {@code total} separado:</b> a contagem
 *       ignora a página, é o que o envelope publica como {@code total}.</li>
 * </ul>
 *
 * <p>O filtro de estado ativo é montado aqui como constante de SQL ({@code WHERE ativo =
 * true}) e nunca por concatenação de valor vindo da requisição — o parâmetro só escolhe
 * <i>qual</i> das duas consultas fixas roda.
 */
@Repository
public class TaxonomiaRepositorio {

    /** Ordem publicada no contrato ("ordenada por nome"), com desempate determinístico. */
    private static final String ORDEM = " ORDER BY nome, slug";

    private static final RowMapper<CategoriaResposta> MAPEADOR_CATEGORIA = (rs, linha) ->
            new CategoriaResposta(
                    rs.getObject("id", UUID.class),
                    rs.getString("slug"),
                    rs.getString("nome"),
                    rs.getString("descricao"),
                    rs.getBoolean("ativo"));

    private static final RowMapper<TipoMaterialResposta> MAPEADOR_TIPO = (rs, linha) ->
            new TipoMaterialResposta(
                    rs.getObject("id", UUID.class),
                    rs.getString("slug"),
                    rs.getString("nome"),
                    rs.getBoolean("ativo"));

    private static final RowMapper<LicencaResposta> MAPEADOR_LICENCA = (rs, linha) ->
            new LicencaResposta(
                    rs.getObject("id", UUID.class),
                    rs.getString("slug"),
                    rs.getString("nome"),
                    rs.getString("identificador"),
                    rs.getString("url_referencia"),
                    rs.getString("descricao_curta"),
                    rs.getBoolean("ativo"));

    private final JdbcTemplate jdbc;

    public TaxonomiaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param incluirDesativadas {@code false} devolve só {@code ativo = true} (estado
     *                           padrão do contrato); {@code true} devolve também os itens
     *                           desativados, que só existem quando uma migração de
     *                           correção os desativa (ADR-0006 — não há tela de edição).
     */
    public ResultadoPaginado<CategoriaResposta> listarCategorias(boolean incluirDesativadas, int pagina, int tamanho) {
        return paginar("category", "SELECT id, slug, nome, descricao, ativo FROM category",
                incluirDesativadas, MAPEADOR_CATEGORIA, pagina, tamanho);
    }

    public ResultadoPaginado<TipoMaterialResposta> listarTiposMaterial(boolean incluirDesativadas, int pagina,
            int tamanho) {
        return paginar("material_type", "SELECT id, slug, nome, ativo FROM material_type",
                incluirDesativadas, MAPEADOR_TIPO, pagina, tamanho);
    }

    public ResultadoPaginado<LicencaResposta> listarLicencas(boolean incluirDesativadas, int pagina, int tamanho) {
        return paginar("license", "SELECT id, slug, nome, identificador, url_referencia, descricao_curta, ativo"
                + " FROM license", incluirDesativadas, MAPEADOR_LICENCA, pagina, tamanho);
    }

    /**
     * Total da consulta + itens da página corrente. O {@code OFFSET} é calculado em
     * {@code long} de propósito: {@code pagina * tamanho} em {@code int} estouraria
     * silenciosamente numa página absurda e devolveria a primeira página como se fosse a
     * décima — erro difícil de ver em tela.
     */
    private <T> ResultadoPaginado<T> paginar(String tabela, String consulta, boolean incluirDesativadas,
            RowMapper<T> mapeador, int pagina, int tamanho) {
        String filtro = incluirDesativadas ? "" : " WHERE ativo = true";
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + tabela + filtro, Long.class);
        List<T> itens = jdbc.query(consulta + filtro + ORDEM + " LIMIT ? OFFSET ?",
                mapeador, tamanho, (long) pagina * tamanho);
        return new ResultadoPaginado<>(itens, total == null ? 0L : total);
    }
}
