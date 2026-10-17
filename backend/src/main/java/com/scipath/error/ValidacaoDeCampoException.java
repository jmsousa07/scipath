package com.scipath.error;

/**
 * Campo de <b>corpo</b> recusado por uma regra que só o serviço conhece — vira <b>422</b>
 * {@code urn:scipath:erro:validacao_campo} com o campo culpado em {@code errors[]}.
 *
 * <p>Existe porque o vocabulário do contrato distingue dois 4xx que nascem em lugares
 * diferentes: valor recusado em <b>consulta ou caminho</b>, ou corpo que o servidor não
 * conseguiu ler, é <b>400</b> {@code validacao} (é o {@code ProblemDetail} padrão, sem campo a
 * apontar); corpo lido com <b>campo</b> de valor inválido é <b>422</b> {@code validacao_campo},
 * com {@code errors[]}. O Bean Validation cobre o formato do corpo antes do controller; este
 * 422 existe para o que <b>só o banco pode julgar</b> — o caso que o card E3.4 traz é o token de
 * confirmação: um valor bem formado, que nada no formato tem de errado, e que não corresponde a
 * nenhum token válido.
 *
 * <p><b>A mensagem não revela nada</b> (RNF013): inválido, expirado e já consumido produzem o
 * mesmo texto, e nada do valor apresentado é repetido.
 */
public class ValidacaoDeCampoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String campo;
    private final transient String motivoDoCampo;

    /**
     * @param campo         nome do campo como o cliente o enviou (ex.: {@code token})
     * @param motivoDoCampo mensagem curta para {@code errors[].mensagem}
     */
    public ValidacaoDeCampoException(String campo, String motivoDoCampo) {
        super(motivoDoCampo);
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
