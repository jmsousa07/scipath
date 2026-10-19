package com.scipath.arquivo;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Registro do arquivo recebido na tabela {@code file_asset} (V1, ADR-0005 §2), com o SQL
 * explícito do resto do projeto.
 *
 * <p><b>O arquivo existe antes do material.</b> A relação é dirigida por
 * {@code material.file_asset_id} (decisão de modelagem registrada no V1): o arquivo precisa
 * existir sozinho enquanto é validado e enquanto o material ainda não foi gravado. É por isso
 * que este card (E4.2) já grava a linha — o E4.4 grava o material apontando para ela, na mesma
 * transação, sem reinserir nada. A {@code chave_interna} é {@code UNIQUE}, então uma chave
 * repetida não é aceita em silêncio por este caminho.
 *
 * <p><b>Estado {@code VALIDADO}.</b> A linha só nasce <b>depois</b> de o conteúdo passar por
 * todas as conferências: recusa não deixa rastro no banco (nem arquivo na área temporária).
 * {@code PENDENTE} fica reservado ao intervalo entre receber e validar de quem precisar dele
 * depois; {@code ORFAO} é o que o coletor de limpeza marca (E4.5) e {@code PURGADO}, o que ele
 * deixa para trás depois de remover o binário (ADR-0005 §3).
 */
@Repository
public class ArquivosRepositorio {

    private final JdbcClient jdbc;

    public ArquivosRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Grava a linha de {@code file_asset} de um arquivo <b>já validado</b> e devolve o
     * {@code id} gerado pelo banco.
     *
     * <p>Todos os valores são parâmetro ligado — o nome original, em especial, nunca entra na
     * string do SQL, ainda que já venha limpo de {@link NomesDeArquivo}.
     *
     * @param chaveInterna       chave UUID gerada pelo sistema (nome físico do arquivo)
     * @param nomeOriginalSeguro nome do cliente já sanitizado, guardado só como metadado
     * @param tipoDetectado      tipo conferido por conteúdo ({@code application/pdf})
     * @param tamanhoBytes       tamanho medido no recebimento
     * @param checksum           SHA-256 do conteúdo, em hexadecimal minúsculo
     */
    public UUID registrar(String chaveInterna, String nomeOriginalSeguro, String tipoDetectado,
            long tamanhoBytes, String checksum) {
        return jdbc.sql("INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,"
                        + " tamanho_bytes, checksum, estado_processamento)"
                        + " VALUES (:chaveInterna, :nomeOriginalSeguro, :tipoDetectado,"
                        + " :tamanhoBytes, :checksum, 'VALIDADO') RETURNING id")
                .param("chaveInterna", chaveInterna)
                .param("nomeOriginalSeguro", nomeOriginalSeguro)
                .param("tipoDetectado", tipoDetectado)
                .param("tamanhoBytes", tamanhoBytes)
                .param("checksum", checksum)
                .query(UUID.class)
                .single();
    }

    /**
     * Marca a linha como {@code ORFAO} — arquivo gravado <b>sem material</b> (ADR-0005 §2) — e
     * devolve quantas linhas mudaram.
     *
     * <p>É o que a publicação de material (E4.4) chama quando a gravação do material falha depois
     * de o arquivo já ter sido promovido: o binário existe no armazenamento e nenhum material
     * aponta para ele. Sem esta marcação o registro continuaria dizendo {@code VALIDADO}, que é o
     * estado de um arquivo que a publicação pode servir — e o coletor (E4.5) teria de descobrir a
     * situação só pela ausência de material. A marcação é <b>sempre fora da transação da
     * publicação</b>, de propósito: é justamente o que precisa sobreviver ao rollback dela.
     *
     * <p>{@code PURGADO} é preservado: nesse estado o binário já foi removido depois da retenção
     * (ADR-0005 §3) e dizer {@code ORFAO} sobre ele seria pedir ao coletor que procurasse um
     * arquivo que não existe mais. A identidade é a {@code chave_interna} (a coluna que o
     * armazenamento usa e a que o material não vê), não o nome original — o nome é metadado de
     * exibição e pode repetir entre arquivos diferentes.
     *
     * <p>A condição por chave é uma leitura por índice ({@code UNIQUE file_asset_chave_interna_unica}),
     * então a marcação não toca linha de outro arquivo nem em caso de nome repetido.
     *
     * @param chaveInterna chave gerada pelo sistema que o recebimento registrou (E4.2)
     * @return 1 quando a linha passou a órfã; 0 quando não há arquivo com essa chave ou ele já está
     *         {@code PURGADO}
     */
    public int marcarOrfao(String chaveInterna) {
        return jdbc.sql("UPDATE file_asset SET estado_processamento = 'ORFAO'"
                        + " WHERE chave_interna = :chaveInterna AND estado_processamento <> 'PURGADO'")
                .param("chaveInterna", chaveInterna)
                .update();
    }
}
