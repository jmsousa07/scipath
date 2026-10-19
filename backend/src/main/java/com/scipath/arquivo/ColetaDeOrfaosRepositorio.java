package com.scipath.arquivo;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * As duas consultas do coletor de órfãos (card E4.5): quais arquivos são candidatos e a remoção do
 * registro. SQL explícito com {@link JdbcClient}, como o resto do projeto.
 *
 * <p><b>O que é um órfão, dito em SQL.</b> Uma linha de {@code file_asset} que
 * <i>nenhum</i> {@code material} referencia, mais antiga que a janela de segurança. As duas
 * condições são constantes desta classe, nunca parâmetro de requisição:
 *
 * <ul>
 *   <li><b>{@code NOT EXISTS (material)}</b> — e não {@code estado_processamento = 'ORFAO'}. A
 *       marcação do E4.4 é o caminho <i>normal</i> para um arquivo que ficou sem material, mas ela
 *       é melhor esforço de propósito (precisa sobreviver ao rollback da publicação): um processo
 *       interrompido entre receber o arquivo e gravar o material deixa a linha em {@code VALIDADO}
 *       sem material nenhum. Procurar a ausência de material — e não a etiqueta — é o que faz o
 *       coletor encontrar os dois casos, e é o que o javadoc do {@link ArquivosRepositorio} já
 *       prometia ("o coletor ainda o encontra pela ausência de material"). A etiqueta continua útil
 *       como sinal de diagnóstico; ela não é a regra de coleta.</li>
 *   <li><b>{@code enviado_em <= limite}</b> — a janela de segurança do RN012. É o único freio entre
 *       "arquivo sem material" e "arquivo apagado", e ele é configuração
 *       ({@link PropriedadesDeColeta#getJanela()}).</li>
 * </ul>
 *
 * <p><b>{@code PURGADO} fica de fora.</b> Nesse estado o binário já foi removido pelo coletor
 * (ADR-0005 §3) e a linha permanece como rastro; recoletá-la seria reescrever a mesma decisão e
 * apagar o registro de que a remoção aconteceu. Também é o que a marcação de órfão do E4.4
 * preserva, pela mesma razão.
 *
 * <p><b>A remoção repete as duas condições.</b> O {@code DELETE} não confia no {@code SELECT}: ele
 * recusa a linha que ganhou material entre a leitura e a escrita, e recusa a que já virou
 * {@code PURGADO}. Nenhum caminho do produto liga um material a um {@code file_asset} antigo — a
 * publicação (E4.4) sempre grava o material apontando para a linha que ela mesma acabou de receber
 * —, mas escrever a condição no {@code DELETE} custa uma linha e transforma "não acontece" em "não
 * pode acontecer por este caminho".
 */
@Repository
public class ColetaDeOrfaosRepositorio {

    /**
     * A janela: sem material nenhum apontando para a linha, mais antiga que o limite, e não
     * {@code PURGADO}. A ordem é por {@code enviado_em} e {@code id} — a leitura é determinística,
     * então duas execuções sobre o mesmo conjunto examinam os mesmos arquivos na mesma ordem (é o
     * que torna o log de uma varredura comparável com o da anterior).
     */
    private static final String CANDIDATOS = """
            SELECT f.id, f.chave_interna, f.nome_original_seguro, f.tamanho_bytes, f.enviado_em
            FROM file_asset f
            WHERE f.estado_processamento <> 'PURGADO'
              AND f.enviado_em <= now() - (:janela * interval '1 second')
              AND NOT EXISTS (SELECT 1 FROM material m WHERE m.file_asset_id = f.id)
            ORDER BY f.enviado_em, f.id
            """;

    /**
     * Remove o registro do arquivo — as mesmas duas condições da janela, reavaliadas no momento da
     * escrita. {@code RETURNING} não é usado: o que interessa é quantas linhas mudaram, e zero
     * linhas é um desfecho previsto (a linha ganhou material ou já foi purgada), não um erro.
     */
    private static final String REMOVER_REGISTRO = """
            DELETE FROM file_asset AS f
            WHERE f.id = :id
              AND f.estado_processamento <> 'PURGADO'
              AND NOT EXISTS (SELECT 1 FROM material m WHERE m.file_asset_id = f.id)
            """;

    private static final RowMapper<OrfaoCandidato> MAPEADOR = (rs, linha) -> new OrfaoCandidato(
            rs.getObject("id", UUID.class),
            rs.getString("chave_interna"),
            rs.getString("nome_original_seguro"),
            rs.getLong("tamanho_bytes"),
            rs.getObject("enviado_em", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public ColetaDeOrfaosRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Arquivos sem material, mais antigos que a janela de segurança.
     *
     * <p><b>O corte é calculado no banco</b> ({@code now()}), e não no relógio da aplicação: a
     * coluna comparada ({@code enviado_em}) é escrita pelo relógio do banco ({@code DEFAULT now()}),
     * e comparar dois relógios diferentes abriria uma janela de falso positivo — apagando cedo
     * demais ou tarde demais — no dia em que a aplicação e o banco estiverem em máquinas distintas.
     * Um só relógio na comparação é o que mantém a janela sendo a janela configurada.
     *
     * @param janela prazo de segurança; o corte é {@code agora - janela}, no relógio do banco
     */
    public List<OrfaoCandidato> candidatos(Duration janela) {
        return jdbc.sql(CANDIDATOS)
                .param("janela", janela.toMillis() / 1000.0)
                .query(MAPEADOR)
                .list();
    }

    /**
     * Remove a linha de {@code file_asset}, se ela ainda for um órfão.
     *
     * <p><b>Chamado depois da remoção do binário, nunca antes</b> — a ordem é decisão do coletor
     * (ver {@link ColetorDeOrfaosService}). Aqui só se responde "quantas linhas saíram".
     *
     * @return 1 quando a linha saiu; 0 quando ela não existe mais, virou {@code PURGADO} ou ganhou
     *         material entre a leitura e a escrita
     */
    public int removerRegistro(UUID id) {
        return jdbc.sql(REMOVER_REGISTRO)
                .param("id", id)
                .update();
    }
}
