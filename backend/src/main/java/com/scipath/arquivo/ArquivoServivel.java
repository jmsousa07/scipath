package com.scipath.arquivo;

import org.springframework.core.io.Resource;

/**
 * Arquivo <b>pronto para servir</b> (RF014) — o resultado do download depois que a visibilidade
 * foi decidida e a integridade, conferida (RN008).
 *
 * <p><b>Não é schema do contrato.</b> É objeto de dentro do servidor: carrega a chave do
 * armazenamento, que nunca é publicada. A resposta HTTP publica só o nome original sanitizado (no
 * {@code Content-Disposition}) e o conteúdo; tamanho e tipo saem de decisão do controller.
 *
 * <p>O {@code conteudo} é o <b>mesmo</b> recurso que o checksum conferiu — a checagem e a entrega
 * leem o arquivo uma vez cada, e não duas leituras arbitrárias. Nada de caminho em disco: o
 * adaptador é quem sabe onde o arquivo está.
 *
 * @param nomeOriginal nome original sanitizado no recebimento (E4.2), sugerido no download
 * @param tamanhoBytes tamanho medido no recebimento; base do {@code Content-Length}
 * @param conteudo     conteúdo armazenado, para o controller entregar
 */
public record ArquivoServivel(String nomeOriginal, long tamanhoBytes, Resource conteudo) {
}
