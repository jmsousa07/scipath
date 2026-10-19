package com.scipath.arquivo;

/**
 * Arquivo já existe no armazenamento sob a chave pedida (RF013, ADR-0005 §2) — o movimento do
 * passo 2 do RN008 tentou ocupar uma chave em uso.
 *
 * <p><b>É defeito de servidor, não erro de cliente.</b> A chave é gerada pelo sistema
 * ({@code UUID.randomUUID()} no recebimento) e é {@code UNIQUE} em {@code file_asset}: encontrá-la
 * ocupada significa chave repetida, arquivo de outra requisição ou promovido duas vezes. Sobrescrever
 * apagaria o arquivo de um material possivelmente vivo, e é justamente o que o ADR-0005 §3 proíbe —
 * por isso a operação falha alto em vez de "resolver" em silêncio.
 *
 * <p>Não é exceção de fronteira: nenhuma rota do contrato a publica. A mensagem carrega a chave e o
 * caminho <b>para o log</b>; se algum dia chegar a uma resposta, o tratamento único de erro a
 * entrega como 500 genérico, sem detalhe interno (RNF012).
 */
public class ArquivoJaArmazenadoException extends RuntimeException {

    public ArquivoJaArmazenadoException(String mensagem) {
        super(mensagem);
    }
}
