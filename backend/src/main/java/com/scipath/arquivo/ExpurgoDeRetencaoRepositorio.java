package com.scipath.arquivo;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Candidatos e atualização de estado do expurgo E5.5.
 *
 * <p>O conjunto não se sobrepõe ao coletor de órfãos: cada candidato deste serviço está ligado a um
 * material {@code EXCLUIDO}; a consulta de órfãos exige a ausência de qualquer material referenciando
 * o arquivo. A janela também é revalidada ao marcar {@code PURGADO}.
 */
@Repository
public class ExpurgoDeRetencaoRepositorio {

    private static final String CANDIDATOS = """
            SELECT f.id, f.chave_interna, f.nome_original_seguro, f.tamanho_bytes
            FROM material m
            JOIN file_asset f ON f.id = m.file_asset_id
            WHERE m.estado_publicacao = 'EXCLUIDO'
              AND m.excluido_em < now() - (:janela * interval '1 second')
              AND f.estado_processamento <> 'PURGADO'
            ORDER BY m.excluido_em, f.id
            """;

    private static final String MARCAR_PURGADO = """
            UPDATE file_asset f
            SET estado_processamento = 'PURGADO'
            WHERE f.id = :id
              AND f.estado_processamento <> 'PURGADO'
              AND EXISTS (
                  SELECT 1
                  FROM material m
                  WHERE m.file_asset_id = f.id
                    AND m.estado_publicacao = 'EXCLUIDO'
                    AND m.excluido_em < now() - (:janela * interval '1 second')
              )
            """;

    private static final RowMapper<ExpurgoDeRetencaoCandidato> MAPEADOR = (rs, linha) ->
            new ExpurgoDeRetencaoCandidato(rs.getObject("id", UUID.class), rs.getString("chave_interna"),
                    rs.getString("nome_original_seguro"), rs.getLong("tamanho_bytes"));

    private final JdbcClient jdbc;

    public ExpurgoDeRetencaoRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Usa o relógio do banco, igual à coluna excluido_em gravada pelo PostgreSQL. */
    public List<ExpurgoDeRetencaoCandidato> candidatos(Duration janela) {
        return jdbc.sql(CANDIDATOS)
                .param("janela", janela.toMillis() / 1000.0)
                .query(MAPEADOR)
                .list();
    }

    /** Marca como PURGADO somente se o material continuar elegível. */
    public int marcarPurgado(UUID id, Duration janela) {
        return jdbc.sql(MARCAR_PURGADO)
                .param("id", id)
                .param("janela", janela.toMillis() / 1000.0)
                .update();
    }
}
