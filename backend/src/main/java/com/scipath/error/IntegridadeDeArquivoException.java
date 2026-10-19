package com.scipath.error;

/**
 * O arquivo registrado do material não confere com o que está armazenado — o download <b>não</b>
 * serve o conteúdo e o incidente é registrado (RN008, RF014).
 *
 * <p><b>Por que não servir é a única resposta aceitável.</b> O RN008 exige integridade entre o que
 * foi publicado e o que o visitante recebe. Servir um binário que não bate com o checksum
 * registrado é entregar como "o material do autor" um conteúdo que ninguém validou; a alternativa
 * silenciosa (servir assim mesmo) é pior que a falha visível.
 *
 * <p><b>O que o cliente vê.</b> Nada deste detalhe: a resposta é o <b>500</b> padrão do contrato
 * ({@code urn:scipath:erro:interno}) com o {@code detail} genérico do tratamento único de erro —
 * sem caminho, chave de storage, checksum esperado/encontrado ou qualquer rastro de infraestrutura
 * (RNF012, RNF014). A mensagem desta exceção existe <b>para o log</b>: é o registro do incidente que
 * o RN008 pede, e é o único lugar onde os dois checksums podem ser úteis.
 */
public class IntegridadeDeArquivoException extends RuntimeException {

    public IntegridadeDeArquivoException(String detalheParaOLog) {
        super(detalheParaOLog);
    }
}
