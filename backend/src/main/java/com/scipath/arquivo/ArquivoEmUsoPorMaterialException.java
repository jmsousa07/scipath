package com.scipath.arquivo;

/**
 * Recusa de remoção: o arquivo sob a chave pedida é referenciado por um material e <b>não</b> pode
 * ser apagado (ADR-0005 §3).
 *
 * <p><b>É o guarda do RN012, não um erro de fronteira.</b> O coletor de órfãos (E4.5) e a purga da
 * Etapa 5 removem binários; entre encontrar um candidato e apagá-lo existe uma pergunta a mais —
 * "algum material ainda aponta para ele?" — e a resposta afirmativa interrompe a remoção. Material
 * {@code RETIRADO} continua vivo (o autor pode republicar) e {@code EXCLUIDO} dentro da janela de
 * retenção também; apagar o binário de qualquer um dos dois destruiria dado que ainda tem dono.
 *
 * <p>Nenhuma rota do contrato publica esta exceção: quem a encontra é a rotina de limpeza, que
 * contabiliza o motivo e segue. A mensagem carrega a chave <b>para o log</b>; numa resposta HTTP, o
 * tratamento único de erro a entrega como 500 genérico, sem detalhe interno (RNF012).
 */
public class ArquivoEmUsoPorMaterialException extends RuntimeException {

    public ArquivoEmUsoPorMaterialException(String mensagem) {
        super(mensagem);
    }
}
