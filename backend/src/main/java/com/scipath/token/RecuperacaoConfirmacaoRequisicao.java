package com.scipath.token;

import com.scipath.conta.SenhaBcryptValida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo da conclusão da recuperação de acesso — schema {@code RecuperacaoConfirmacao} do
 * contrato v0.4 (RF010, ADR-0003 (d)).
 *
 * <p>Dois campos: o token de uso único (validade de 30 min) e a senha nova. O intervalo da senha
 * é o <b>mesmo do cadastro</b> — 8 a 72 caracteres e no máximo 72 bytes UTF-8, limite do bcrypt
 * (ADR-0003 (a)). Uma senha fora disso para no <b>422</b> do campo {@code novaSenha}, antes de o
 * token ser tocado, para que o formulário aponte o campo sem gastar o link.
 *
 * <p><b>A senha nunca sai daqui.</b> Não é registrada em log, não entra em exceção e não existe
 * em DTO de resposta (RNF009): o que a API devolve é 204 sem corpo.
 */
public record RecuperacaoConfirmacaoRequisicao(

        @NotBlank(message = "informe o token")
        @Size(max = 200, message = "deve ter 200 caracteres ou menos")
        String token,

        @NotNull(message = "informe a nova senha")
        @Size(min = 8, max = 72, message = "deve ter entre 8 e 72 caracteres")
        @SenhaBcryptValida
        String novaSenha) {
}
