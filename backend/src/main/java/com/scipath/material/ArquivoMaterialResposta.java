package com.scipath.material;

/**
 * Metadado público do arquivo do material — schema {@code ArquivoMaterial} do contrato v0.5 (RF014).
 *
 * <p><b>É o que a tela precisa para oferecer o download, e nada além.</b> O nome sugerido no
 * download e o tamanho vêm do registro de {@code file_asset}; a <b>disponibilidade</b> é decidida
 * no servidor, a cada leitura, porque só ele sabe se o binário pode ser servido agora. Com ela a
 * tela deixa de anunciar um arquivo que responderia 404/500, e o visitante não clica num link
 * morto (§14.4).
 *
 * <p><b>Nada de infraestrutura.</b> Não há caminho em disco, chave de armazenamento, checksum nem
 * credencial: o endereço do arquivo é {@code GET /materiais/{slug}/arquivo}, decidido no servidor
 * (RF014, RNF014). O nome físico no armazenamento é a chave interna, e ela não tem campo aqui.
 *
 * @param nomeOriginal  nome original do arquivo, sanitizado no recebimento (E4.2) — o que o
 *                      navegador sugere ao salvar; nunca é o nome físico no armazenamento
 * @param tamanhoBytes  tamanho registrado no recebimento (E4.2)
 * @param disponivel    {@code true} quando o servidor conseguiria servir o arquivo agora; a
 *                      <b>razão</b> de um {@code false} nunca sai daqui (nem motivo, nem estado do
 *                      arquivo, nem se ele existiu um dia)
 */
public record ArquivoMaterialResposta(
        String nomeOriginal,
        long tamanhoBytes,
        boolean disponivel) {
}
