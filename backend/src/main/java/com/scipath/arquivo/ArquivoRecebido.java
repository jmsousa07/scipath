package com.scipath.arquivo;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Resultado do recebimento de um arquivo válido (RF013, ADR-0005 §2) — o que o passo 2 da
 * ordem do RN008 (mover para o storage definitivo, E4.3) e o passo 3 (gravar material na mesma
 * transação, E4.4) precisam para continuar.
 *
 * <p><b>Não é o corpo de nenhuma resposta do contrato.</b> É objeto de dentro do servidor:
 * carrega a chave interna e o caminho do arquivo na área temporária, e nada disso é publicado
 * (RF014 — o contrato não expõe caminho nem chave de storage). Quem monta a resposta pública
 * escolhe o que mostrar; o {@code POST /materiais} do E4.4 publica apenas o slug e o estado.
 *
 * @param id                identificador da linha de {@code file_asset} já registrada
 * @param chaveInterna      chave gerada pelo sistema; é ela — nunca o nome do usuário — que
 *                          decide o nome físico e o caminho do arquivo
 * @param nomeOriginalSeguro nome do cliente limpo (sem diretório, sem {@code ..}, sem
 *                          controle), guardado só como metadado
 * @param tipoDetectado     tipo conferido por conteúdo, não pela extensão declarada
 * @param tamanhoBytes      tamanho medido no servidor, byte a byte, durante a gravação
 * @param checksum          SHA-256 do conteúdo recebido, calculado enquanto o arquivo era
 *                          gravado (a integridade do RN008 é provada por ele no download, E4.3)
 * @param arquivoTemporario onde o arquivo está agora: área temporária de recebimento, fora de
 *                          qualquer diretório servido
 */
public record ArquivoRecebido(
        UUID id,
        String chaveInterna,
        String nomeOriginalSeguro,
        String tipoDetectado,
        long tamanhoBytes,
        String checksum,
        Path arquivoTemporario) {
}
