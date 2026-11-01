package com.scipath.material;

import com.scipath.taxonomia.ResultadoPaginado;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Leitura dos materiais do <b>dono</b> (RF015, contrato v0.6, tag {@code gerenciamento}): a página
 * de {@code GET /api/perfil/atual/materiais} e o detalhe de
 * {@code GET /api/perfil/atual/materiais/{slug}}, em qualquer dos três estados.
 *
 * <p><b>Por que um repositório próprio, e não um método a mais no {@link MateriaisRepositorio}.</b>
 * Os dois respondem perguntas diferentes com a mesma tabela: o de lá responde "o que o
 * <i>público</i> pode ver" — a elegibilidade de RN002 (estado {@code PUBLICADO} e conta
 * {@code ATIVO}) é dele e continua sendo —, e este responde "o que <i>este</i> perfil publicou".
 * Reusar as constantes de elegibilidade aqui seria o defeito clássico: alguém acrescenta um filtro
 * de estado na visão do dono "para ficar igual" e a lista do dono passa a esconder o que ele
 * excluiu (ADR-0011 §2). Aqui a condição de visibilidade é <b>uma só</b>, e é a propriedade.
 *
 * <p><b>A propriedade é o {@code profile_id} da sessão</b> (RN001, CA007): não há parâmetro de
 * conta, de perfil ou de autor em nenhuma consulta desta classe. Material de outra conta não é
 * "negado" — ele simplesmente não é devolvido, e o 404 indistinguível sai do mesmo vazio que o
 * slug inexistente (ADR-0011 §3).
 *
 * <p><b>O estado da conta não entra.</b> A leitura do dono <b>não</b> exige conta {@code ATIVO}
 * (ADR-0003 (b)): quem está em {@code PENDENTE_VERIFICACAO} continua lendo o que publicou — e a
 * consulta não junta {@code account} nem filtra por {@code estado}, porque isso não é pergunta
 * desta superfície.
 *
 * <p><b>Ordenação total, sempre.</b> {@code atualizado_em DESC, id ASC}: a ordem é determinística
 * porque o desempate é único por linha, e é isso que faz {@code LIMIT}/{@code OFFSET} não repetir
 * nem omitir item entre páginas (RN013, RNF021). Vários materiais do mesmo perfil podem
 * compartilhar o {@code atualizado_em} (nada no domínio o torna único), então o desempate não é
 * decorativo.
 */
@Repository
public class MateriaisPropriosRepositorio {

    /**
     * Colunas publicadas no schema {@code MaterialProprio} — e as que o serviço precisa para
     * decidir {@code arquivo.disponivel}, que ficam no servidor (RF014, RNF014): as mesmas do
     * detalhe público ({@code CAMPOS_DETALHE} de {@link MateriaisRepositorio}) com o {@code autor}
     * removido. Nenhuma coluna de {@code account}, nenhum {@code id} interno, nenhuma chave de
     * armazenamento publicada (RNF013).
     */
    private static final String CAMPOS = """
            SELECT m.slug, m.titulo, m.descricao, m.estado_publicacao, m.publicado_em, m.atualizado_em,
                   c.slug AS categoria_slug, c.nome AS categoria_nome,
                   t.slug AS tipo_slug, t.nome AS tipo_nome,
                   l.slug AS licenca_slug, l.nome AS licenca_nome,
                   l.descricao_curta AS licenca_descricao_curta,
                   f.nome_original_seguro, f.chave_interna, f.tamanho_bytes,
                   f.checksum, f.estado_processamento
            """;

    /**
     * Junções das duas leituras. Sem {@code profile} e sem {@code account}: a posse é o
     * {@code profile_id} da linha do material, e a visibilidade do dono não depende do estado da
     * conta (ADR-0003 (b)) — juntar as duas tabelas só para repetir a posse seria custo sem
     * pergunta. As três tabelas de referência entram pelo motivo de sempre (ADR-0006: o cliente
     * guarda o slug, a tela mostra o nome), e {@code file_asset} porque a resposta publica o
     * metadado do arquivo.
     */
    private static final String JUNCOES = """
            FROM material m
            JOIN category c      ON c.id = m.category_id
            JOIN material_type t ON t.id = m.material_type_id
            JOIN license l       ON l.id = m.license_id
            JOIN file_asset f    ON f.id = m.file_asset_id
            """;

    /**
     * A condição de visibilidade desta superfície: <b>a posse</b>. Sem estado de publicação — é a
     * visão do dono, que inclui {@code PUBLICADO}, {@code RETIRADO} e {@code EXCLUIDO} (RF015,
     * ADR-0011 §2) — e sem estado de conta.
     */
    private static final String DO_DONO = " WHERE m.profile_id = :perfil\n";

    /**
     * A ordem da tela de gerenciamento: "o que mexi por último, primeiro" (contrato v0.6,
     * {@code atualizadoEm} DESC), com desempate por {@code id} ASC.
     *
     * <p>A direção do desempate é <b>ASC</b> aqui — o inverso do desempate da listagem pública, que
     * acompanha o índice {@code material_estado_data_idx} ({@code publicado_em DESC, id DESC}) e
     * ordena "mais novo primeiro" nas duas chaves. Não há índice de {@code atualizado_em} na V1: a
     * consulta filtra pelo {@code profile_id} ({@code material_autor_idx}) e ordena o resultado, que
     * é o de um perfil só. O que o contrato promete — ordem determinística e estável ao paginar —
     * vale nas duas direções; o que não pode é a ordem depender do plano do banco.
     */
    private static final String ORDEM = " ORDER BY m.atualizado_em DESC, m.id ASC\n";

    /**
     * Linha mapeada para a projeção interna ({@link MaterialDoDono}) — sem {@code id} interno e sem
     * autor. O {@code disponivel} não é decidido aqui: o mapeamento não consulta o armazenamento
     * (essa pergunta é do serviço, uma vez por leitura).
     */
    private static final RowMapper<MaterialDoDono> MAPEADOR = (rs, linha) -> new MaterialDoDono(
            rs.getString("slug"),
            rs.getString("titulo"),
            rs.getString("descricao"),
            new CategoriaReferencia(rs.getString("categoria_slug"), rs.getString("categoria_nome")),
            new TipoMaterialReferencia(rs.getString("tipo_slug"), rs.getString("tipo_nome")),
            new LicencaReferencia(rs.getString("licenca_slug"), rs.getString("licenca_nome"),
                    rs.getString("licenca_descricao_curta")),
            rs.getString("estado_publicacao"),
            rs.getObject("publicado_em", OffsetDateTime.class).toInstant(),
            // O schema MaterialProprio lista atualizadoEm em `required`: ao contrário do item da
            // listagem pública, a data de atualização é publicada sempre (ADR-0011 §4).
            rs.getObject("atualizado_em", OffsetDateTime.class).toInstant(),
            new ArquivoDoMaterial(
                    rs.getString("slug"),
                    rs.getString("nome_original_seguro"),
                    rs.getString("chave_interna"),
                    rs.getLong("tamanho_bytes"),
                    rs.getString("checksum"),
                    rs.getString("estado_processamento")));

    private final JdbcClient jdbc;

    public MateriaisPropriosRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Página dos materiais do perfil da sessão, e o total que atende à mesma consulta.
     *
     * <p>A contagem usa o <b>mesmo</b> predicado da página — só sem {@code LIMIT}/{@code OFFSET} —,
     * de propósito: uma contagem com filtro diferente do conteúdo é como o envelope passa a mentir
     * sobre si mesmo ({@link com.scipath.taxonomia.Pagina#total()}).
     *
     * @param perfilId {@code profile_id} da sessão resolvida no interceptor (RN001) — a única
     *                 origem possível da propriedade, nunca um identificador vindo do cliente
     * @param pagina   página pedida, começando em 0
     * @param tamanho  itens por página, já validado pelo teto do contrato (RNF021)
     */
    public ResultadoPaginado<MaterialDoDono> listarDoPerfil(UUID perfilId, int pagina, int tamanho) {
        Long total = jdbc.sql("SELECT count(*)" + JUNCOES + DO_DONO)
                .param("perfil", perfilId)
                .query(Long.class)
                .single();

        String consulta = CAMPOS + JUNCOES + DO_DONO + ORDEM + " LIMIT :limite OFFSET :deslocamento";

        List<MaterialDoDono> itens = jdbc.sql(consulta)
                .param("perfil", perfilId)
                .param("limite", tamanho)
                // Deslocamento em long: página * tamanho em int estoura numa página absurda e
                // devolveria a primeira página como se fosse a milésima, sem erro nenhum.
                .param("deslocamento", (long) pagina * tamanho)
                .query(MAPEADOR)
                .list();

        return new ResultadoPaginado<>(itens, total == null ? 0L : total);
    }

    /**
     * O material do perfil da sessão identificado pelo {@code slug}, em <b>qualquer</b> estado, ou
     * vazio quando ele não é deste perfil.
     *
     * <p><b>Material de outra conta cai no mesmo vazio de material inexistente</b> — sem caminho
     * separado no código, porque a consulta já restringe a posse. É o que torna o 404 indistinguível
     * sem que o serviço precise lembrar de disfarçá-lo: não existe informação diferente para vazar
     * (ADR-0011 §3, CA007).
     *
     * <p>{@code m.slug} é {@code UNIQUE} (V1), então a leitura é por índice e no máximo uma linha
     * atende.
     *
     * @param perfilId {@code profile_id} da sessão (RN001)
     * @param slug     slug já validado no controller (formato e tamanho do contrato)
     */
    public Optional<MaterialDoDono> buscarDoPerfil(UUID perfilId, String slug) {
        String consulta = CAMPOS + JUNCOES + DO_DONO + "   AND m.slug = :slug\n";
        return jdbc.sql(consulta)
                .param("perfil", perfilId)
                .param("slug", slug)
                .query(MAPEADOR)
                .optional();
    }
}
