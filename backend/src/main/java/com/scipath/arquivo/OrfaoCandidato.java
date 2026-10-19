package com.scipath.arquivo;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um arquivo candidato à coleta, como o banco o descreve (card E4.5).
 *
 * <p>É a projeção <b>interna</b> da consulta de órfãos, não um schema do contrato: nada disto é
 * publicado por endpoint nenhum. {@code chaveInterna} é a identidade que o armazenamento usa (o
 * material não a vê) e {@code id} é a chave da linha em {@code file_asset}, que é o que a remoção
 * do registro precisa.
 *
 * @param id                 chave primária da linha em {@code file_asset}
 * @param chaveInterna       chave gerada pelo sistema, nome do arquivo no armazenamento
 * @param nomeOriginalSeguro nome original sanitizado — só entra no log, como rastro de auditoria
 * @param tamanhoBytes       tamanho registrado no recebimento
 * @param enviadoEm          momento do recebimento, base do cálculo da janela de segurança
 */
public record OrfaoCandidato(UUID id, String chaveInterna, String nomeOriginalSeguro,
        long tamanhoBytes, OffsetDateTime enviadoEm) {
}
