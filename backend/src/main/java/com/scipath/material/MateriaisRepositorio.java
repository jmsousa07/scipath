package com.scipath.material;

import com.scipath.taxonomia.ResultadoPaginado;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Leitura pública de material (RF002) — as consultas por trás de {@code GET /api/materiais}
 * (a listagem com filtros) e da página de materiais de {@code GET /api/autores/{slug}}
 * (RF006) — esta última servida por {@link #listarPublicadosDoAutor}, que é a mesma projeção
 * com um predicado de igualdade a mais (RN013: um só lugar decide o que é material público).
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
 * <p><b>Busca por texto (RF003, §12.1, ADR-0008).</b> {@code q} vira um predicado de
 * <i>full-text</i> sobre título e descrição — {@code tsvector @@ plainto_tsquery} — servido
 * pelo índice GIN {@code material_busca_idx} da migração V3. Três escolhas que não são
 * detalhe:
 *
 * <ul>
 *   <li>{@code plainto_tsquery}, nunca {@code to_tsquery}: o primeiro trata a entrada como
 *       TEXTO e o segundo como EXPRESSÃO, em que {@code &}, {@code |}, {@code !} e
 *       parênteses são operadores. O contrato promete "caracteres especiais são tratados
 *       como texto, nunca como operador" (§12.1) — e um {@code q=} vindo de terceiro não
 *       pode virar sintaxe de consulta.</li>
 *   <li>A dobra de acento ({@link #semAcento}) é a MESMA da expressão do índice. Índice e
 *       consulta têm de ser a mesma expressão: é o que faz o planejador reconhecer
 *       {@code material_busca_idx}, e é o que faz "matemática" e "matematica" devolverem o
 *       mesmo conjunto (RN013).</li>
 *   <li>O termo entra como parâmetro ligado ({@code :busca}); a string do predicado é
 *       compilada de constantes, como a dos outros filtros.</li>
 * </ul>
 *
 * <p>Consequência registrada: um termo cujas palavras são todas <i>stopwords</i> da
 * configuração ("de", "a", "para") gera {@code tsquery} vazio, e {@code @@} com consulta
 * vazia é falso — a resposta é 200 com lista vazia, que é o estado de "nenhum resultado"
 * que o contrato já define (RNF004, RF003). Não há exceção para esse caso: um termo sem
 * lexema não é um termo de busca.
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
     * Colunas publicadas no schema {@code MaterialDetalhe} do contrato v0.3, e só elas.
     *
     * <p>Difere do {@link #CAMPOS} da listagem em duas colunas — {@code l.identificador} e
     * {@code l.url_referencia} — e no {@code estado_publicacao}: o detalhe publica a licença
     * com o texto de referência (é onde há espaço para ele) e o estado de publicação. Nada
     * privado entra: nenhuma coluna de {@code account}, {@code file_asset} ou {@code id}
     * interno (RNF013).
     */
    private static final String CAMPOS_DETALHE = """
            SELECT m.slug, m.titulo, m.descricao, m.estado_publicacao, m.publicado_em, m.atualizado_em,
                   p.slug AS autor_slug, p.nome_publico AS autor_nome_publico,
                   c.slug AS categoria_slug, c.nome AS categoria_nome,
                   t.slug AS tipo_slug, t.nome AS tipo_nome,
                   l.slug AS licenca_slug, l.nome AS licenca_nome,
                   l.identificador AS licenca_identificador,
                   l.url_referencia AS licenca_url_referencia,
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

    /**
     * Mapa de dobra de acento da busca (ADR-0008). As duas strings têm 54 caracteres —
     * {@code translate()} exige comprimentos iguais e falha alto se divergirem.
     *
     * <p>Dobra as duas caixas de propósito, sem {@code lower()}: {@code lower()} depende da
     * collation do banco (num banco {@code LC_CTYPE=C} ele não dobra "Á"), e o que sobra
     * depois do {@code translate} é ASCII, que o analisador do {@code tsvector} rebaixa de
     * forma determinística. Assim o resultado da busca não depende de configuração da máquina.
     *
     * <p><b>A MESMA dupla está na expressão do índice</b> ({@code V3__busca_texto_material.sql}):
     * índice e consulta precisam ser a mesma expressão, senão o planejador não reconhece o
     * índice — é o que o {@code EXPLAIN} da evidência do card prova.
     */
    static final String ACENTOS = "ÀÁÂÃÄÅàáâãäåÈÉÊËèéêëÌÍÎÏìíîïÒÓÔÕÖòóôõöÙÚÛÜùúûüÇçÑñÝŸýÿ";

    /** Acento dobrado: cada caractere de {@link #ACENTOS}, na mesma ordem. */
    static final String SEM_ACENTOS = "AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNnYYyy";

    /**
     * Configuração de busca do PostgreSQL, <b>nomeada</b> em todo lugar. Nunca
     * {@code default_text_search_config}: a busca não pode mudar de comportamento por causa
     * de ajuste do servidor (ADR-0008).
     */
    private static final String CONFIG_BUSCA = "portuguese";

    /**
     * Predicado de busca por texto (RF003): o vetor de título + descrição casado contra a
     * consulta do termo, ambos com a mesma dobra de acento. {@code plainto_tsquery} — e não
     * {@code to_tsquery} — porque o contrato trata caracteres especiais como texto (§12.1);
     * o valor entra ligado em {@code :busca}, e a string do predicado é constante.
     *
     * <p>Visível ao pacote (e não privado) porque o teste de unidade
     * {@code MateriaisBuscaDobraTest} verifica que ele e o índice da migração V3 continuam
     * sendo a mesma expressão — divergir não dá erro, só perde o índice.
     */
    static final String BUSCA =
            "   AND " + vetorDe("m.titulo || ' ' || m.descricao")
            + " @@ plainto_tsquery('" + CONFIG_BUSCA + "', " + semAcento(":busca") + ")\n";

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

    /** Colunas do detalhe mapeadas para o schema público, sem id interno nem coluna de arquivo. */
    private static final RowMapper<MaterialDetalheResposta> MAPEADOR_DETALHE = (rs, linha) ->
            new MaterialDetalheResposta(
                    rs.getString("slug"),
                    rs.getString("titulo"),
                    rs.getString("descricao"),
                    new AutorResumoResposta(rs.getString("autor_slug"), rs.getString("autor_nome_publico")),
                    new CategoriaReferencia(rs.getString("categoria_slug"), rs.getString("categoria_nome")),
                    new TipoMaterialReferencia(rs.getString("tipo_slug"), rs.getString("tipo_nome")),
                    new LicencaDetalheResposta(
                            rs.getString("licenca_slug"),
                            rs.getString("licenca_nome"),
                            rs.getString("licenca_identificador"),
                            rs.getString("licenca_url_referencia"),
                            rs.getString("licenca_descricao_curta")),
                    rs.getString("estado_publicacao"),
                    rs.getObject("publicado_em", OffsetDateTime.class).toInstant(),
                    rs.getObject("atualizado_em", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    public MateriaisRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Página de materiais públicos e o total que atende à mesma consulta.
     *
     * @param busca     termo de busca por texto (RF003), ou {@code null} para não buscar;
     *                  termo vazio é convertido em {@code null} pelo serviço
     * @param categoria {@code slug} da categoria, ou {@code null} para não filtrar por ela
     * @param tipo      {@code slug} do tipo de material, ou {@code null} para não filtrar
     * @param ordem     critério de ordenação (sempre definido — o padrão é do contrato)
     * @param pagina    página pedida, começando em 0
     * @param tamanho   itens por página
     */
    public ResultadoPaginado<MaterialResumoResposta> listarPublicados(String busca, String categoria,
            String tipo, OrdemMateriais ordem, int pagina, int tamanho) {
        String filtros = ELEGIVEL
                + (busca == null ? "" : BUSCA)
                + (categoria == null ? "" : "   AND c.slug = :categoria\n")
                + (tipo == null ? "" : "   AND t.slug = :tipo\n");

        Long total = comFiltros(jdbc.sql("SELECT count(*)" + JUNCOES + filtros), busca, categoria, tipo)
                .query(Long.class)
                .single();

        String consulta = CAMPOS + JUNCOES + filtros + ordenacaoDe(ordem)
                + " LIMIT :limite OFFSET :deslocamento";

        List<MaterialResumoResposta> itens = comFiltros(jdbc.sql(consulta), busca, categoria, tipo)
                .param("limite", tamanho)
                // Deslocamento em long: página * tamanho em int estoura numa página absurda e
                // devolveria a primeira página como se fosse a milésima, sem erro nenhum.
                .param("deslocamento", (long) pagina * tamanho)
                .query(MAPEADOR)
                .list();

        return new ResultadoPaginado<>(itens, total == null ? 0L : total);
    }

    /**
     * Material público identificado pelo {@code slug} (RF005), ou vazio quando ele não é
     * público.
     *
     * <p>A elegibilidade é a <b>mesma</b> da listagem (RN002, CA008): material PUBLICADO de
     * conta ATIVA. Aqui isso tem uma consequência a mais — material inexistente, {@code
     * RETIRADO}, {@code EXCLUIDO} e material de conta não ativa caem todos no mesmo vazio, sem
     * caminho separado no código. É o que torna o 404 indistinguível sem depender de o serviço
     * lembrar de disfarçar: não existe informação diferente para vazar.
     *
     * <p>{@code m.slug} é {@code UNIQUE} (V1), então a consulta é uma leitura por índice e não
     * precisa de {@code LIMIT}: no máximo uma linha atende.
     *
     * @param slug slug já validado no controller (formato do contrato)
     */
    public Optional<MaterialDetalheResposta> buscarPublicadoPorSlug(String slug) {
        String consulta = CAMPOS_DETALHE + JUNCOES + ELEGIVEL + "   AND m.slug = :slug\n";
        return jdbc.sql(consulta)
                .param("slug", slug)
                .query(MAPEADOR_DETALHE)
                .optional();
    }

    /**
     * Página dos materiais publicados de um autor (RF006) — o que a página do perfil
     * público ({@code GET /api/autores/{slug}}) mostra abaixo da identidade.
     *
     * <p>É a <b>mesma</b> consulta da listagem pública — {@link #CAMPOS}, {@link #JUNCOES},
     * {@link #ELEGIVEL} e {@link #MAPEADOR} —, com um predicado de igualdade a mais
     * ({@code p.slug}). Reusar a projeção, e não reescrevê-la aqui, é o que garante que o
     * {@code categoria.slug} publicado na página do autor seja literalmente o valor que
     * {@code GET /api/materiais?categoria=} aceita como filtro (RN013, RF007): duas cópias
     * poderiam divergir — e divergir não dá erro, só quebra a navegação sem ninguém notar.
     *
     * <p><b>Ordenação.</b> A ordem é a padrão do contrato (§12.2, {@code recentes}: data de
     * publicação decrescente, desempate por {@code id}): o endpoint do autor
     * <b>não</b> oferece {@code ordem} — o contrato não a declara lá —, então não há decisão a
     * tomar além da padrão. A ordem total é o que torna {@code LIMIT}/{@code OFFSET} correto
     * (paginar não repete nem omite item).
     *
     * <p><b>Elegibilidade e contagem</b> são as da listagem: material PUBLICADO de conta ATIVA,
     * e o {@code total} é a contagem da <i>mesma</i> consulta — uma contagem com junções
     * diferentes do conteúdo é como o envelope passa a mentir sobre si mesmo.
     *
     * @param autorSlug {@code slug} do perfil já validado no controller; o perfil público
     *                  foi verificado antes (a página do autor só chega aqui com perfil visível)
     * @param pagina    página pedida, começando em 0
     * @param tamanho   itens por página
     */
    public ResultadoPaginado<MaterialResumoResposta> listarPublicadosDoAutor(String autorSlug,
            int pagina, int tamanho) {
        String filtros = ELEGIVEL + "   AND p.slug = :autor\n";

        Long total = jdbc.sql("SELECT count(*)" + JUNCOES + filtros)
                .param("autor", autorSlug)
                .query(Long.class)
                .single();

        String consulta = CAMPOS + JUNCOES + filtros + ordenacaoDe(OrdemMateriais.RECENTES)
                + " LIMIT :limite OFFSET :deslocamento";

        List<MaterialResumoResposta> itens = jdbc.sql(consulta)
                .param("autor", autorSlug)
                .param("limite", tamanho)
                // Deslocamento em long, como na listagem: página * tamanho em int estoura e
                // devolveria a primeira página como se fosse a milésima, sem erro nenhum.
                .param("deslocamento", (long) pagina * tamanho)
                .query(MAPEADOR)
                .list();

        return new ResultadoPaginado<>(itens, total == null ? 0L : total);
    }

    /** Liga só os filtros informados: consulta sem filtro não carrega parâmetro de filtro. */
    private static JdbcClient.StatementSpec comFiltros(JdbcClient.StatementSpec spec, String busca,
            String categoria, String tipo) {
        if (busca != null) {
            spec = spec.param("busca", busca);
        }
        if (categoria != null) {
            spec = spec.param("categoria", categoria);
        }
        if (tipo != null) {
            spec = spec.param("tipo", tipo);
        }
        return spec;
    }

    /** {@code texto} com acento dobrado, na mesma forma que o índice aplica ao documento. */
    private static String semAcento(String texto) {
        return "translate(" + texto + ", '" + ACENTOS + "', '" + SEM_ACENTOS + "')";
    }

    /** Vetor de busca do documento: título + descrição, com a dobra de acento do índice. */
    private static String vetorDe(String texto) {
        return "to_tsvector('" + CONFIG_BUSCA + "', " + semAcento(texto) + ")";
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
