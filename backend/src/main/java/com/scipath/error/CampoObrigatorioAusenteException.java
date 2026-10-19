package com.scipath.error;

/**
 * Campo obrigatório ausente do formulário — <b>400</b> {@code urn:scipath:erro:validacao}
 * (contrato v0.5: "campo obrigatório ausente no formulário devolve 400 — o servidor não
 * conseguiu ler o pedido como veio").
 *
 * <p>Existe separado do 422 de propósito. Campo <b>presente</b> com valor inválido é 422
 * (o corpo foi lido e um campo não passou); campo que <b>não veio</b> não é erro de valor —
 * é um pedido que o servidor não conseguiu ler, como um parâmetro de consulta ausente. O
 * frontend decide pelo {@code type}, e por isso a distinção vive no código e no contrato.
 *
 * <p>O caso do upload que a lança é a parte {@code arquivo} presente mas com <b>conteúdo
 * vazio</b>: não há arquivo nenhum para validar. A parte completamente ausente nem chega ao
 * serviço — o Spring falha antes, e o tratamento de {@code MissingServletRequestPartException}
 * responde a mesma coisa.
 */
public class CampoObrigatorioAusenteException extends RuntimeException {

    private final String campo;

    public CampoObrigatorioAusenteException(String campo) {
        super("Falta o campo obrigatório '" + campo + "' no formulário.");
        this.campo = campo;
    }

    /** Nome do campo ausente, publicado em {@code errors[].campo}. */
    public String campo() {
        return campo;
    }
}
