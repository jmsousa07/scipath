package com.scipath.error;

/**
 * Campo obrigatório ausente do pedido — <b>400</b> {@code urn:scipath:erro:validacao}
 * (contrato v0.5: "campo obrigatório ausente no formulário devolve 400 — o servidor não
 * conseguiu ler o pedido como veio"; contrato v0.6: "campo obrigatório ausente no corpo → 400").
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
 *
 * <p><b>A exceção carrega o campo; o lugar é do tratamento de erro.</b> Os dois pedidos que a
 * lançam chegam por corpos diferentes — {@code multipart/form-data} na publicação (E4.4) e JSON
 * na edição do material próprio (E5.3) —, e o {@code detail} precisa dizer o lugar certo
 * ("no formulário" / "no corpo da requisição", RNF004). Quem sabe qual é o corpo é o
 * {@code ApiExceptionHandler}, que já tem a requisição; a mensagem desta exceção é, portanto,
 * <b>neutra</b> e serve só para quem depura no servidor.
 */
public class CampoObrigatorioAusenteException extends RuntimeException {

    private final String campo;

    public CampoObrigatorioAusenteException(String campo) {
        super("Campo obrigatório ausente: " + campo);
        this.campo = campo;
    }

    /** Nome do campo ausente, publicado em {@code errors[].campo}. */
    public String campo() {
        return campo;
    }
}
