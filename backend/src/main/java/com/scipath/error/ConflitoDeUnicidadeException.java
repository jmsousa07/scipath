package com.scipath.error;

/**
 * Conflito de unicidade (409 do contrato): o valor enviado já pertence a outro registro —
 * e-mail já cadastrado (RF008, RN009) ou {@code slug} de perfil já em uso.
 *
 * <p>Carrega o <b>campo</b> culpado porque a classe de erro {@code conflito_de_unicidade} do
 * contrato traz {@code errors[]}: um formulário precisa dizer qual campo corrigir. Isto é
 * deliberado e é a exceção publicada — o contrato diz que o cadastro é o <b>único</b> ponto
 * em que a resposta pode revelar que existe conta com determinado e-mail; login e
 * recuperação nunca fazem essa distinção (RNF013).
 */
public class ConflitoDeUnicidadeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String campo;
    private final transient String motivoDoCampo;

    /**
     * @param campo          nome do campo em conflito, como o cliente o enviou (ex.: {@code email})
     * @param motivoDoCampo  mensagem curta para {@code errors[].mensagem} (ex.: "já está em uso")
     * @param detalhe        texto acionável do {@code detail} do {@code ProblemDetail}
     */
    public ConflitoDeUnicidadeException(String campo, String motivoDoCampo, String detalhe) {
        super(detalhe);
        this.campo = campo;
        this.motivoDoCampo = motivoDoCampo;
    }

    public String campo() {
        return campo;
    }

    public String motivoDoCampo() {
        return motivoDoCampo;
    }
}
