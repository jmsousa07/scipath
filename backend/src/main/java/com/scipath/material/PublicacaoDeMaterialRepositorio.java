package com.scipath.material;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Escrita e resolução da publicação de material (RF012, RF018) — o lado de <b>gravação</b> do
 * material, que o {@link MateriaisRepositorio} não cobre: aquele é a leitura pública da descoberta
 * (RF002–RF005) e não muda neste card.
 *
 * <p><b>Por que as consultas de taxonomia moram aqui, e não no {@code TaxonomiaRepositorio}.</b>
 * Lá é a <i>leitura pública das listas</i> (RF018: ordenar por nome, paginar, filtrar por ativo) e
 * é ela que o frontend do formulário consome. A pergunta desta classe é outra — "qual o
 * identificador interno deste slug, se ele existir <b>e</b> estiver ativo?" —, e ela só existe
 * porque a publicação precisa traduzir slug em FK. Separá-las mantém a leitura de referência
 * intocada e a regra da tradução perto de quem a usa.
 *
 * <p><b>SQL explícito com {@link JdbcClient}</b>, como o resto do projeto. Nenhum valor vindo da
 * requisição entra na string do SQL: os slugs são parâmetro ligado e os <b>nomes de tabela</b> são
 * constantes desta classe (nunca chegam do cliente) — é o que torna a concatenação de
 * {@link #idAtivo} segura por construção, não por sorte.
 */
@Repository
public class PublicacaoDeMaterialRepositorio {

    private final JdbcClient jdbc;

    public PublicacaoDeMaterialRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /* ------------------------------------------------------------------ escrita */

    /**
     * Grava o material e devolve a linha <b>como ela ficou no banco</b>
     * ({@code RETURNING id, slug, estado_publicacao}).
     *
     * <p><b>{@code ON CONFLICT (slug) DO NOTHING} é a segunda metade da unicidade do slug, e a
     * única que não corre risco de corrida.</b> Quem chama já tentou o slug base e os sufixos
     * numéricos consultando o banco, mas duas publicações simultâneas com o mesmo título passariam
     * pela consulta e chegariam juntas ao {@code INSERT}. Com a cláusula, a segunda <b>não</b>
     * grava e o resultado é vazio — estado normal, tratado pelo chamador como "tente o próximo
     * sufixo", dentro da mesma transação. Sem ela, a violação da {@code UNIQUE
     * material_slug_unico} abortaria a transação e o cliente receberia um 409 que o contrato não
     * publica para esta rota (e que não tem o que o usuário possa corrigir: o slug não foi ele que
     * escolheu).
     *
     * <p>O alvo do conflito é <b>nomeado</b> ({@code (slug)}): só a unicidade do slug é absorvida.
     * As outras invariantes do V1 ({@code material_arquivo_unico}, as FK, o formato e o estado)
     * continuam falhando alto — uma publicação que violasse qualquer uma delas é defeito, e
     * defeito tem de virar erro, não linha faltando em silêncio.
     *
     * @param slug       candidato gerado no servidor, já no formato do contrato
     * @param metadados  título, descrição e os identificadores da taxonomia (RF018)
     * @param perfilId   perfil da <b>sessão</b> — a autoria não é parâmetro do formulário
     * @param arquivoId  {@code file_asset.id} do arquivo já recebido, validado e promovido (RN008)
     * @return a linha gravada, ou vazio quando o slug já pertencia a outro material
     */
    public Optional<MaterialGravado> inserir(String slug, MetadadosDaPublicacao metadados,
            UUID perfilId, UUID arquivoId) {
        return jdbc.sql("""
                INSERT INTO material (slug, titulo, descricao, profile_id, category_id,
                                      material_type_id, license_id, file_asset_id, estado_publicacao)
                VALUES (:slug, :titulo, :descricao, :perfilId, :categoriaId,
                        :tipoId, :licencaId, :arquivoId, :estado)
                ON CONFLICT (slug) DO NOTHING
                RETURNING id, slug, estado_publicacao
                """)
                .param("slug", slug)
                .param("titulo", metadados.titulo())
                .param("descricao", metadados.descricao())
                .param("perfilId", perfilId)
                .param("categoriaId", metadados.categoriaId())
                .param("tipoId", metadados.tipoId())
                .param("licencaId", metadados.licencaId())
                .param("arquivoId", arquivoId)
                // O estado inicial vem do enum, nunca de literal: um só lugar declara qual é
                // (EstadoPublicacao.INICIAL, RF019).
                .param("estado", EstadoPublicacao.INICIAL.name())
                .query((rs, linha) -> new MaterialGravado(
                        rs.getObject("id", UUID.class),
                        rs.getString("slug"),
                        EstadoPublicacao.valueOf(rs.getString("estado_publicacao"))))
                .optional();
    }

    /** {@code category.id} do slug, se a categoria existir <b>e</b> estiver ativa (RF018). */
    public Optional<UUID> categoriaAtiva(String slug) {
        return idAtivo("category", slug);
    }

    /** {@code material_type.id} do slug, se o tipo existir <b>e</b> estiver ativo (RF018). */
    public Optional<UUID> tipoAtivo(String slug) {
        return idAtivo("material_type", slug);
    }

    /**
     * {@code license.id} do slug, se a licença existir <b>e</b> estiver ativa (RF018). Nenhuma
     * licença é escolhida pelo servidor: sem slug válido enviado pelo autor, não há publicação
     * (RN006).
     */
    public Optional<UUID> licencaAtiva(String slug) {
        return idAtivo("license", slug);
    }

    /**
     * Identificador interno do item de referência ativo, pela chave estável.
     *
     * <p>{@code ativo = true} é constante de SQL, e não parâmetro: a publicação precisa de item
     * ativo, e um parâmetro aqui só serviria para alguém, um dia, publicar com item desativado. O
     * filtro do estado ativo é o mesmo das listas públicas de taxonomia.
     *
     * <p>{@code tabela} é sempre uma das três constantes acima — o método é privado e o parâmetro
     * não tem origem em requisição (é a única razão de a concatenação ser aceitável aqui).
     */
    private Optional<UUID> idAtivo(String tabela, String slug) {
        return jdbc.sql("SELECT id FROM " + tabela + " WHERE slug = :slug AND ativo = true")
                .param("slug", slug)
                .query(UUID.class)
                .optional();
    }
}
