package com.scipath.conta;

import com.scipath.autor.PerfilPublico;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
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

    /** Linha de {@code profile} como {@link Perfil} — um mapeador só para as três leituras. */
    private static final RowMapper<Perfil> MAPEADOR = (rs, linha) -> new Perfil(
            rs.getObject("id", UUID.class),
            rs.getObject("account_id", UUID.class),
            rs.getString("nome_publico"),
            rs.getString("slug"),
            rs.getString("biografia"),
            rs.getString("afiliacao_declarada"),
            listaDeTexto(rs.getArray("areas_interesse")),
            listaDeTexto(rs.getArray("links_externos")));

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
                .query(MAPEADOR)
                .optional();
    }

    /**
     * Perfil da conta já na forma <b>pública</b> do contrato ({@code PerfilPublico}) — o que
     * as respostas de identidade publicam (estado da sessão, login, cadastro).
     *
     * <p>Existe para que os serviços de identidade não precisem do record interno
     * {@link Perfil} (que é do pacote {@code conta}): a projeção pública fica onde o perfil é
     * lido, e quem consome recebe exatamente o schema que pode sair na resposta — sem
     * {@code id}, sem {@code account_id} (RNF013).
     */
    public Optional<PerfilPublico> buscarPublicoPorConta(UUID contaId) {
        return buscarPorConta(contaId).map(PerfilRepositorio::publico);
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

    // ------------------------------------------------------------------ edição (E3.5)

    /**
     * Perfil identificado pelo próprio {@code id}, se existir — a releitura que fecha a edição.
     *
     * <p>É por este id que a edição grava, e o id vem da sessão (ADR-0003 (j)): a leitura
     * depois da escrita usa a mesma chave, e é por isso que o 200 devolve o que o <b>SQL</b>
     * guardou, não o que o cliente enviou.
     */
    public Optional<Perfil> buscarPorId(UUID perfilId) {
        return jdbc.sql("SELECT " + CAMPOS + " FROM profile WHERE id = :perfilId")
                .param("perfilId", perfilId)
                .query(MAPEADOR)
                .optional();
    }

    /**
     * Perfil identificado pelo {@code id} já na forma pública do contrato
     * ({@code PerfilPublico}) — a resposta de {@code PUT /perfil/atual}.
     *
     * <p>Mesma projeção de {@link #buscarPublicoPorConta}: sem {@code id} e sem
     * {@code account_id}, e é por não existir campo de conta no tipo que o vazamento por
     * engano é impossível (RNF013).
     */
    public Optional<PerfilPublico> buscarPublicoPorId(UUID perfilId) {
        return buscarPorId(perfilId).map(PerfilRepositorio::publico);
    }

    /**
     * O slug já pertence a <b>outro</b> perfil? — a checagem que vira 409 antes de tentar
     * gravar (RN009).
     *
     * <p>O {@code id <> :perfilId} é a razão de o método existir em vez de uma consulta por
     * slug: repetir o próprio slug numa edição (mudar só a biografia, por exemplo) é legítimo e
     * não pode ser recusado como colisão. A {@code UNIQUE profile_slug_unico} continua sendo o
     * juiz final da unicidade — esta consulta é conveniência para o formulário receber o campo
     * culpado, não a garantia (RNF011).
     */
    public boolean slugEmUsoPorOutro(String slug, UUID perfilId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM profile WHERE slug = :slug AND id <> :perfilId)")
                .param("slug", slug)
                .param("perfilId", perfilId)
                .query(Boolean.class)
                .single();
    }

    /**
     * Substitui os campos públicos do perfil — a escrita de {@code PUT /perfil/atual}.
     *
     * <p><b>Substitui, não mescla:</b> as seis colunas entram sempre, e valor {@code null}
     * grava {@code NULL} — "não informado", o mesmo estado de um perfil que nunca passou pela
     * edição (o contrato v0.4 diz que o corpo substitui o conjunto de campos). O
     * {@code WHERE id = :perfilId} é a fronteira de propriedade: o id vem da sessão e o
     * {@code UPDATE} não alcança nenhuma outra linha (ADR-0003 (j), CA007).
     *
     * <p>{@code atualizado_em} é escrito pela aplicação, como o V1 documenta ("não há trigger:
     * a regra de negócio fica no serviço") — o timestamp do banco é o do momento da escrita.
     *
     * <p>As duas colunas {@code text[]} recebem {@code String[]} ligado como parâmetro: o
     * driver do PostgreSQL monta o array, e nenhum valor de requisição entra na string do SQL.
     */
    public void atualizar(UUID perfilId, String nomePublico, String slug, String biografia,
            String afiliacaoDeclarada, List<String> areasInteresse, List<String> linksExternos) {
        jdbc.sql("UPDATE profile SET nome_publico = :nomePublico, slug = :slug,"
                        + " biografia = :biografia, afiliacao_declarada = :afiliacaoDeclarada,"
                        + " areas_interesse = :areasInteresse, links_externos = :linksExternos,"
                        + " atualizado_em = now() WHERE id = :perfilId")
                .param("nomePublico", nomePublico)
                .param("slug", slug)
                .param("biografia", biografia)
                .param("afiliacaoDeclarada", afiliacaoDeclarada)
                .param("areasInteresse", arrayOuNulo(areasInteresse))
                .param("linksExternos", arrayOuNulo(linksExternos))
                .param("perfilId", perfilId)
                .update();
    }

    /** Lista como {@code String[]} para a coluna {@code text[]}; lista vazia é {@code NULL}. */
    private static String[] arrayOuNulo(List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return null;
        }
        return valores.toArray(new String[0]);
    }

    /** Projeção pública do perfil — a única forma pela qual ele sai daqui (RNF013). */
    private static PerfilPublico publico(Perfil perfil) {
        return new PerfilPublico(perfil.slug(), perfil.nomePublico(), perfil.biografia(),
                perfil.afiliacaoDeclarada(), perfil.areasInteresse(), perfil.linksExternos());
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
