package com.scipath.email;

/**
 * O canal de e-mail recusou a mensagem — <b>nada foi entregue</b>.
 *
 * <p>Não carrega o token, o link nem o destinatário: é uma exceção que atravessa o tratamento
 * único de erro (vira <b>500</b> no contrato) e que pode acabar em log de falha inesperada —
 * o lugar exato onde um token em claro não pode estar (RNF009, ADR-0003 (f) e (g)).
 *
 * <p>O que a mensagem descreve é <b>o que o canal fez</b> (gravar em disco, conectar ao
 * provedor), nunca o conteúdo: "não foi possível gravar a mensagem no outbox de
 * desenvolvimento" descreve a falha e não entrega nada.
 *
 * <p>É lançada pela implementação de {@link EmailSender} e é o sinal que os serviços de
 * identidade usam para decidir o que fazer — propagar (reenvio de verificação, 500) ou manter a
 * resposta inalterada (solicitação de recuperação, 202 sempre).
 */
public class FalhaDeEnvioDeEmailException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FalhaDeEnvioDeEmailException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
