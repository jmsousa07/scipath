package com.scipath.material;

/**
 * O arquivo de um material <b>público</b>, lido junto com a decisão de visibilidade (RF014, RN002).
 *
 * <p><b>Não é schema do contrato.</b> É a projeção interna que o download ({@code GET
 * /materiais/{slug}/arquivo}, E4.3) precisa: o metadado que a resposta publica — nome original
 * sanitizado e tamanho — e o que <b>não</b> pode ser publicado — a chave interna do armazenamento,
 * que é endereço, e o checksum registrado, que é instrumento de integridade. Nada disto vira campo
 * de resposta: o contrato v0.5 não tem caminho, chave nem checksum em lugar nenhum (RNF014).
 *
 * <p><b>Por que a leitura mora no repositório de material, e não no de arquivo.</b> A regra "o que
 * é material público" ({@code estado_publicacao = 'PUBLICADO'} e conta {@code ATIVO} — RN002,
 * RN003) já existe em um só lugar, e é o {@link MateriaisRepositorio}. Escrever a mesma condição
 * numa segunda consulta seria a divergência clássica: nada quebra, e um dia o download passa a
 * servir material retirado sem ninguém notar. Aqui a elegibilidade é a <b>mesma constante</b> da
 * listagem e do detalhe.
 *
 * @param slug               identificador público do material (para o log de incidente — o registro
 *                           de integridade precisa dizer de qual material se trata)
 * @param nomeOriginalSeguro nome sanitizado no recebimento (E4.2), sugerido no download
 * @param chaveInterna       chave do arquivo no armazenamento; decide o caminho, nunca é publicada
 * @param tamanhoBytes       tamanho medido no recebimento (E4.2); base do {@code Content-Length}
 * @param checksum           SHA-256 registrado no recebimento; conferido contra o disco no download
 * @param estadoProcessamento estado do {@code file_asset} no momento da leitura ({@code VALIDADO}
 *                           é o único que pode ser servido — RN008)
 */
public record ArquivoDoMaterial(
        String slug,
        String nomeOriginalSeguro,
        String chaveInterna,
        long tamanhoBytes,
        String checksum,
        String estadoProcessamento) {
}
