package com.scipath.autor;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Leitura da identidade pública do autor (RF006) — a consulta por trás de
 * {@code GET /api/autores/{slug}}. SQL explícito com {@link JdbcClient}, como o resto do
 * projeto.
 *
 * <p><b>Visibilidade (RN002, CA008).</b> Só perfil de conta {@code ATIVO} é público, e a
 * condição é constante de SQL — parâmetro nenhum pode removê-la. Consequência deliberada:
 * perfil de conta {@code PENDENTE_VERIFICACAO} ou {@code SUSPENSO} cai no <b>mesmo vazio</b>
 * que slug inexistente, sem ramo de código que os distinga. O 404 indistinguível sai da
 * consulta, não de o serviço lembrar de disfarçar.
 *
 * <p>A identidade e os materiais do autor são duas consultas: esta lê a linha de
 * {@code profile} (com {@code account} só para a regra de visibilidade) e
 * {@link com.scipath.material.MateriaisRepositorio} serve a página de materiais, dono único
 * da projeção pública de material. Duplicar a projeção aqui seria abrir a porta para o slug
 * de categoria desta tela divergir do que a listagem aceita como filtro (RN013).
 */
@Repository
public class AutorRepositorio {

    /**
     * Colunas publicadas no schema {@code AutorPublico}, e só elas — nenhuma coluna de
     * {@code account} é lida além do {@code estado} que decide a visibilidade, e o
     * {@code account_id} <b>não</b> entra na projeção (RNF013).
     */
    private static final String IDENTIDADE = """
            SELECT p.slug, p.nome_publico, p.biografia, p.afiliacao_declarada,
                   p.areas_interesse, p.links_externos
            FROM profile p
            JOIN account a ON a.id = p.account_id
            """;

    /** Visibilidade do perfil: conta ATIVO, em constante de SQL. */
    private static final String VISIVEL = " WHERE a.estado = 'ATIVO'\n";

    private static final RowMapper<PerfilPublico> MAPEADOR = (rs, linha) -> new PerfilPublico(
            rs.getString("slug"),
            rs.getString("nome_publico"),
            rs.getString("biografia"),
            rs.getString("afiliacao_declarada"),
            textos(rs.getArray("areas_interesse")),
            textos(rs.getArray("links_externos")));

    private final JdbcClient jdbc;

    public AutorRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Perfil público do autor identificado pelo {@code slug}, ou vazio quando ele não é
     * público (perfil inexistente ou de conta não ativa).
     *
     * <p>{@code p.slug} é {@code UNIQUE} (V1), então é leitura por índice e não precisa de
     * {@code LIMIT}: no máximo uma linha atende.
     *
     * @param slug slug já validado no controller (formato e tamanho do contrato)
     */
    public Optional<PerfilPublico> buscarPerfilVisivelPorSlug(String slug) {
        return jdbc.sql(IDENTIDADE + VISIVEL + "   AND p.slug = :slug\n")
                .param("slug", slug)
                .query(MAPEADOR)
                .optional();
    }

    /**
     * Converte uma coluna {@code text[]} em lista de {@code String}.
     *
     * <p>{@code NULL} na coluna é lista vazia: o contrato exige {@code areasInteresse} e
     * {@code linksExternos} sempre presentes, e "não informou" é exatamente a lista vazia.
     * Elemento {@code NULL} dentro do array some da lista — a coluna é texto livre sem
     * {@code NOT NULL} por elemento, e um item nulo viraria espaço em branco na tela.
     */
    private static List<String> textos(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object bruto = array.getArray();
        if (!(bruto instanceof String[] valores) || valores.length == 0) {
            return List.of();
        }
        return Arrays.stream(valores).filter(Objects::nonNull).toList();
    }
}
