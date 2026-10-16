package com.scipath.material;

import com.scipath.taxonomia.ResultadoPaginado;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Leitura pública de material (RF002) — a consulta por trás de {@code GET /api/materiais}.
 * SQL explícito com {@link JdbcClient}: o projeto não usa JPA, e uma listagem paginada com
 * três filtros opcionais não precisa de ORM para ser legível.
 *
 * <p><b>Elegibilidade (RN002, CA008).</b> Duas condições são fixas, nunca parâmetro:
 * {@code material.estado_publicacao = 'PUBLICADO'} e {@code account.estado = 'ATIVO'}. Material
 * RETIRADO ou EXCLUIDO e material de conta suspensa ou ainda não verificada não aparecem —
 * nem no {@code total}, que é a contagem da mesma consulta. Deixar isso a cargo do cliente
 * seria publicar o que o §11.3 proíbe.
 *
 * <p><b>Filtros.</b> {@code categoria} e {@code tipo} chegam como {@code slug} já validado no
 * controller (formato do contrato). Cada um vira um fragmento <b>constante</b> de SQL, e o
 * valor entra sempre como parâmetro ligado ({@code :categoria}, {@code :tipo}) — nada de valor
 * de requisição concatenado na string. O fragmento existe ou não conforme o parâmetro tenha
 * sido informado, o que mantém a consulta com predicado de igualdade puro e permite ao
 * planejador usar os índices do V1 ({@code material_categoria_idx}, {@code material_tipo_idx});
 * um {@code OR :param IS NULL} deixaria o filtro como expressão e perderia o índice.
 *
 * <p><b>Ordenação.</b> Sempre explícita e sempre total (data + id, ou título + id) — ver
 * {@link OrdemMateriais} e {@link #ordenacaoDe}. O {@code LIMIT}/{@code OFFSET} só é correto
 * porque existe essa ordem total: é o que garante que paginar não repita nem omita item
 * (RN013).
 */
@Repository
public class MateriaisRepositorio {

    /** Colunas publicadas no schema {@code MaterialResumo} do contrato v0.3, e só elas. */
    private static final String CAMPOS = """
            SELECT m.slug, m.titulo, m.descricao, m.publicado_em, m.atualizado_em,
                   p.slug AS autor_slug, p.nome_publico AS autor_nome_publico,
                   c.slug AS categoria_slug, c.nome AS categoria_nome,
                   t.slug AS tipo_slug, t.nome AS tipo_nome,
                   l.slug AS licenca_slug, l.nome AS licenca_nome,
                   l.descricao_curta AS licenca_descricao_curta
            """;

    /**
     * Junções da listagem. {@code profile} e {@code account} entram por causa da regra de
     * elegibilidade (RN002) — não há material público sem autor em conta ATIVA. As três
     * tabelas de referência entram para publicar o rótulo de exibição junto do slug
     * (ADR-0006: o cliente guarda o slug, a tela mostra o nome).
     *
     * <p>A contagem usa as mesmas junções da página, de propósito: uma contagem com junções
     * diferentes da listagem é como o envelope passa a mentir sobre o próprio conteúdo.
     */
    private static final String JUNCOES = """
            FROM material m
            JOIN profile p       ON p.id = m.profile_id
            JOIN account a       ON a.id = p.account_id
            JOIN category c      ON c.id = m.category_id
            JOIN material_type t ON t.id = m.material_type_id
            JOIN license l       ON l.id = m.license_id
            """;

    /** Elegibilidade pública: estado do material e estado da conta, em constante de SQL. */
    private static final String ELEGIVEL = """
             WHERE m.estado_publicacao = 'PUBLICADO'
               AND a.estado = 'ATIVO'
            """;

    private static final RowMapper<MaterialResumoResposta> MAPEADOR = (rs, linha) -> {
        OffsetDateTime publicadoEm = rs.getObject("publicado_em", OffsetDateTime.class);
        OffsetDateTime atualizadoEm = rs.getObject("atualizado_em", OffsetDateTime.class);
        return new MaterialResumoResposta(
                rs.getString("slug"),
                rs.getString("titulo"),
                rs.getString("descricao"),
                new AutorResumoResposta(rs.getString("autor_slug"), rs.getString("autor_nome_publico")),
                new CategoriaReferencia(rs.getString("categoria_slug"), rs.getString("categoria_nome")),
                new TipoMaterialReferencia(rs.getString("tipo_slug"), rs.getString("tipo_nome")),
                new LicencaReferencia(rs.getString("licenca_slug"), rs.getString("licenca_nome"),
                        rs.getString("licenca_descricao_curta")),
                publicadoEm.toInstant(),
                // Ausente quando não houve atualização distinta da publicação (contrato v0.3).
                atualizadoEm.toInstant().equals(publicadoEm.toInstant()) ? null : atualizadoEm.toInstant());
    };

    private final JdbcClient jdbc;

    public MateriaisRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Página de materiais públicos e o total que atende à mesma consulta.
     *
     * @param categoria {@code slug} da categoria, ou {@code null} para não filtrar por ela
     * @param tipo      {@code slug} do tipo de material, ou {@code null} para não filtrar
     * @param ordem     critério de ordenação (sempre definido — o padrão é do contrato)
     * @param pagina    página pedida, começando em 0
     * @param tamanho   itens por página
     */
    public ResultadoPaginado<MaterialResumoResposta> listarPublicados(String categoria, String tipo,
            OrdemMateriais ordem, int pagina, int tamanho) {
        String filtros = ELEGIVEL
                + (categoria == null ? "" : "   AND c.slug = :categoria\n")
                + (tipo == null ? "" : "   AND t.slug = :tipo\n");

        Long total = comFiltros(jdbc.sql("SELECT count(*)" + JUNCOES + filtros), categoria, tipo)
                .query(Long.class)
                .single();

        String consulta = CAMPOS + JUNCOES + filtros + ordenacaoDe(ordem)
                + " LIMIT :limite OFFSET :deslocamento";

        List<MaterialResumoResposta> itens = comFiltros(jdbc.sql(consulta), categoria, tipo)
                .param("limite", tamanho)
                // Deslocamento em long: página * tamanho em int estoura numa página absurda e
                // devolveria a primeira página como se fosse a milésima, sem erro nenhum.
                .param("deslocamento", (long) pagina * tamanho)
                .query(MAPEADOR)
                .list();

        return new ResultadoPaginado<>(itens, total == null ? 0L : total);
    }

    /** Liga só os filtros informados: consulta sem filtro não carrega parâmetro de filtro. */
    private static JdbcClient.StatementSpec comFiltros(JdbcClient.StatementSpec spec,
            String categoria, String tipo) {
        if (categoria != null) {
            spec = spec.param("categoria", categoria);
        }
        if (tipo != null) {
            spec = spec.param("tipo", tipo);
        }
        return spec;
    }

    /**
     * Traduz o critério no {@code ORDER BY}. Os dois fragmentos são constantes de SQL: o
     * enum escolhe <i>qual</i> roda, e valor vindo da requisição nunca entra na ordenação.
     *
     * <p>Nas duas ordens o desempate é {@code m.id DESC} — o contrato pede "o mesmo desempate
     * por id" nos dois critérios, e {@code DESC} é a direção do índice do V1
     * ({@code material_estado_data_idx} = {@code estado_publicacao, publicado_em DESC, id DESC}),
     * o que permite à consulta padrão ler a ordem direto do índice, sem passo de ordenação.
     */
    private static String ordenacaoDe(OrdemMateriais ordem) {
        return switch (ordem) {
            case RECENTES -> " ORDER BY m.publicado_em DESC, m.id DESC\n";
            case TITULO -> " ORDER BY m.titulo ASC, m.id DESC\n";
        };
    }
}
