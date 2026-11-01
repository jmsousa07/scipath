package com.scipath.material;

import com.scipath.taxonomia.ResultadoPaginado;
import com.scipath.conta.EstadoConta;
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
     *
     * <p><b>A mesma lista serve às três instruções</b> — as duas leituras (com {@code SELECT}) e a
     * escrita (com {@code RETURNING}): o recorte do que a resposta publica é escrito <b>uma vez</b>,
     * com os rótulos que {@link #MAPEADOR} lê. Duas listas para a mesma resposta divergiriam no dia
     * em que um campo entrasse no schema, e a edição passaria a responder um corpo diferente da
     * leitura. Nos três usos a tabela do material tem o apelido {@code m} e as três de referência
     * têm {@code c}, {@code t} e {@code l}, então os rótulos casam.
     */
    private static final String CAMPOS = """
            m.slug, m.titulo, m.descricao, m.estado_publicacao, m.publicado_em, m.atualizado_em,
            c.slug AS categoria_slug, c.nome AS categoria_nome,
            t.slug AS tipo_slug, t.nome AS tipo_nome,
            l.slug AS licenca_slug, l.nome AS licenca_nome,
            l.descricao_curta AS licenca_descricao_curta,
            f.nome_original_seguro, f.chave_interna, f.tamanho_bytes,
            f.checksum, f.estado_processamento
            """;

    /**
     * As tabelas do material e das suas referências, na ordem em que {@link #CAMPOS} as espera. Sem
     * {@code profile} e sem {@code account}: a posse é o {@code profile_id} da linha do material, e a
     * visibilidade do dono não depende do estado da conta (ADR-0003 (b)) — juntar as duas tabelas só
     * para repetir a posse seria custo sem pergunta. As três tabelas de referência entram pelo motivo
     * de sempre (ADR-0006: o cliente guarda o slug, a tela mostra o nome), e {@code file_asset}
     * porque a resposta publica o metadado do arquivo.
     *
     * <p><b>O {@code FROM} está fora da constante</b> porque a origem do material é {@code material}
     * nas duas leituras e a CTE da edição na escrita (E5.3): o que se repete é o <b>junto de
     * junções</b>, com os mesmos apelidos — e é isso que faz a projeção de {@link #CAMPOS} valer nos
     * três usos.
     */
    private static final String JUNCOES = """
            JOIN category c      ON c.id = m.category_id
            JOIN material_type t ON t.id = m.material_type_id
            JOIN license l       ON l.id = m.license_id
            JOIN file_asset f    ON f.id = m.file_asset_id
            """;

    /** A tabela do material na consulta de leitura — o que a CTE da edição substitui. */
    private static final String MATERIAL = "FROM material m\n";

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
        Long total = jdbc.sql("SELECT count(*)" + MATERIAL + JUNCOES + DO_DONO)
                .param("perfil", perfilId)
                .query(Long.class)
                .single();

        String consulta = "SELECT " + CAMPOS + MATERIAL + JUNCOES + DO_DONO + ORDEM
                + " LIMIT :limite OFFSET :deslocamento";

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
        String consulta = "SELECT " + CAMPOS + MATERIAL + JUNCOES + DO_DONO + "   AND m.slug = :slug\n";
        return jdbc.sql(consulta)
                .param("perfil", perfilId)
                .param("slug", slug)
                .query(MAPEADOR)
                .optional();
    }

    /**
     * Substitui os metadados editáveis do material do perfil da sessão ({@code PUT}, RF016) e
     * devolve <b>a linha como ela ficou no banco</b> — vazio em todos os casos de recusa.
     *
     * <p><b>Uma instrução, e a autorização é a própria cláusula.</b> São duas condições no
     * {@code WHERE}, e as duas são decisão de negócio:
     *
     * <ul>
     *   <li><b>A posse</b> ({@code m.profile_id = :perfil}, RN001, CA007): o {@code perfilId} vem da
     *       sessão e não existe caminho em que um identificador de outra conta entre aqui. Material
     *       alheio não é "negado" — ele não é alcançado, e o serviço responde o mesmo 404 do
     *       inexistente (ADR-0011 §3).</li>
     *   <li><b>O estado vivo</b> ({@code estado_publicacao IN ('PUBLICADO', 'RETIRADO')}): são os
     *       dois estados em que as ações do dono valem (ADR-0011 §2). {@code EXCLUIDO} é terminal e
     *       não se edita — a cláusula é uma lista <b>de permissão</b>, não uma negação de
     *       {@code EXCLUIDO}: um estado novo no {@code CHECK} do V1 nasce sem permissão de escrita,
     *       que é o lado certo para o engano cair.</li>
     * </ul>
     *
     * <p><b>Por que a resposta sai daqui, e não de uma segunda leitura.</b> A CTE devolve a linha
     * <b>que esta instrução escreveu</b>, então o {@code WHERE} abaixo é o <b>único</b> lugar que
     * decide quem pode escrever e o que volta no corpo de 200. A alternativa — gravar e reler —
     * deixaria a autorização em um lugar e a resposta em outro: apagar a posse do {@code WHERE}
     * continuaria devolvendo 404 (a releitura é escopada), o defeito só apareceria no banco, e a
     * cláusula de autorização deixaria de ser <b>exercitada</b> por qualquer teste da resposta. Com
     * uma instrução, remover a posse muda a resposta — e é isso que a prova de mutação do card mede.
     * A releitura, de quebra, custaria uma segunda ida ao banco por edição.
     *
     * <p><b>Nenhuma linha alcançada, nenhuma escrita</b> (critério do card): a instrução sem linha
     * que a atenda não grava nada — não há caminho em que a escrita aconteça e a checagem venha
     * depois, que é a ordem errada que deixaria o material alheio editado em algum caso.
     *
     * <p><b>O que esta escrita não toca, e é o contrato:</b> {@code slug} (a URL canônica pública,
     * RNF025/RN009), {@code profile_id} (a propriedade — vem da sessão, RN001), {@code
     * estado_publicacao} (é o {@code PATCH}, E5.4), {@code publicado_em} (não muda por edição nem
     * por republicação, ADR-0011 §4), {@code file_asset_id} (trocar o binário é outra operação) e as
     * colunas de retenção. Nenhuma delas aparece no {@code SET} — a lista é fechada por construção.
     *
     * <p>{@code atualizado_em = now()} é a única coluna de tempo escrita: é o instante da última
     * escrita, o campo que ordena a lista do dono e o que a resposta publica. {@code now()} é o
     * início da transação corrente — o mesmo instante que o {@code DEFAULT} da coluna usa no
     * {@code INSERT} da publicação, e a razão de a edição não precisar de relógio do lado do Java.
     *
     * <p><b>Por que uma CTE de escrita e não um {@code UPDATE … FROM … RETURNING}.</b> Nesta
     * última, as tabelas do {@code FROM} são juntadas à versão <i>anterior</i> da linha: o
     * {@code RETURNING} traria o nome da <b>categoria antiga</b> junto do material novo — a resposta
     * diria "computação" depois de a linha apontar para matemática. Com a CTE, o {@code UPDATE}
     * produz a linha nova e o {@code SELECT} de fora junta as referências <b>dela</b> — o mesmo
     * recorte ({@link #CAMPOS}, {@link #JUNCOES}) das duas leituras, sem uma segunda ida ao banco.
     *
     * @param perfilId  {@code profile_id} da sessão resolvida no interceptor (RN001) — a única
     *                  origem possível da propriedade, nunca um identificador vindo do cliente
     * @param slug      slug já validado no controller (formato e tamanho do contrato)
     * @param metadados título, descrição e identificadores da taxonomia já validados (RF018)
     * @return a linha atualizada, ou vazio quando o slug não existe, é de outra conta ou está
     *         {@code EXCLUIDO} — os três casos que a resposta trata como o mesmo 404
     */
    public Optional<MaterialDoDono> atualizarMetadados(UUID perfilId, String slug,
            MetadadosDaPublicacao metadados) {
        String consulta = """
                WITH editado AS (
                    UPDATE material m
                       SET titulo = :titulo,
                           descricao = :descricao,
                           category_id = :categoriaId,
                           material_type_id = :tipoId,
                           license_id = :licencaId,
                           atualizado_em = now()
                     WHERE m.slug = :slug
                       AND m.profile_id = :perfil
                       AND m.estado_publicacao IN ('PUBLICADO', 'RETIRADO')
                    RETURNING m.*
                )
                SELECT
                """ + CAMPOS + " FROM editado m\n" + JUNCOES;

        return jdbc.sql(consulta)
                .param("titulo", metadados.titulo())
                .param("descricao", metadados.descricao())
                .param("categoriaId", metadados.categoriaId())
                .param("tipoId", metadados.tipoId())
                .param("licencaId", metadados.licencaId())
                .param("slug", slug)
                .param("perfil", perfilId)
                .query(MAPEADOR)
                .optional();
    }

    /** Lê e bloqueia o material vivo e a conta durante a decisão; o perfil não é exposto na resposta. */
    public Optional<EstadoDoMaterialProprio> bloquearParaAcao(String slug) {
        return jdbc.sql("""
                SELECT m.profile_id, m.estado_publicacao, a.estado AS estado_conta
                  FROM material m
                  JOIN profile p ON p.id = m.profile_id
                  JOIN account a ON a.id = p.account_id
                 WHERE m.slug = :slug
                   AND m.estado_publicacao IN ('PUBLICADO', 'RETIRADO')
                 FOR UPDATE OF m, a
                """)
                .param("slug", slug)
                .query((rs, linha) -> new EstadoDoMaterialProprio(
                        rs.getObject("profile_id", UUID.class),
                        EstadoPublicacao.valueOf(rs.getString("estado_publicacao")),
                        EstadoConta.valueOf(rs.getString("estado_conta"))))
                .optional();
    }

    /** Transiciona a linha bloqueada; a resposta vem da linha gravada pela mesma instrução. */
    public Optional<MaterialDoDono> atualizarEstado(UUID perfilId, String slug,
            EstadoPublicacao origem, EstadoPublicacao destino) {
        String consulta = """
                WITH alterado AS (
                    UPDATE material m
                       SET estado_publicacao = :destino,
                           atualizado_em = now()
                     WHERE m.slug = :slug
                       AND m.profile_id = :perfil
                       AND m.estado_publicacao = :origem
                       AND m.estado_publicacao <> :destino
                    RETURNING m.*
                )
                SELECT
                """ + CAMPOS + " FROM alterado m\n" + JUNCOES;
        return jdbc.sql(consulta)
                .param("destino", destino.name())
                .param("origem", origem.name())
                .param("slug", slug)
                .param("perfil", perfilId)
                .query(MAPEADOR)
                .optional();
    }

    /** Marca exclusão lógica e sua data de retenção; não apaga o arquivo nesta rota. */
    public boolean excluirLogicamente(UUID perfilId, String slug) {
        int alteradas = jdbc.sql("""
                UPDATE material
                   SET estado_publicacao = 'EXCLUIDO',
                       excluido_em = now(),
                       atualizado_em = now()
                 WHERE slug = :slug
                   AND profile_id = :perfil
                   AND estado_publicacao IN ('PUBLICADO', 'RETIRADO')
                """)
                .param("slug", slug)
                .param("perfil", perfilId)
                .update();
        return alteradas == 1;
    }
}
