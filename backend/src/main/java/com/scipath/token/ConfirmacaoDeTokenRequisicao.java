package com.scipath.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo da confirmação de e-mail — schema {@code ConfirmacaoToken} do contrato v0.4.
 *
 * <p><b>Um campo, e o token vem no corpo.</b> Não no caminho da URL: caminho e consulta entram
 * no log de acesso do servidor, e RNF009 proíbe registrar token. O link do e-mail aponta para
 * uma página do frontend, que manda este corpo.
 *
 * <p>O {@code @Size} máximo é higiene de entrada, não regra de negócio: o token real tem 43
 * caracteres (32 bytes URL-safe sem preenchimento). Recusar um valor absurdo com <b>422</b>
 * antes de tocar no banco é mais barato que hashear megabytes — e o cliente recebe a mesma
 * classe de erro que receberia por um token que não existe.
 *
 * <p>Nada de {@code @NotBlank} com mensagem que repita o valor: a mensagem do campo é fixa e o
 * valor enviado não é ecoado em lugar nenhum (RNF013).
 */
public record ConfirmacaoDeTokenRequisicao(

        @NotBlank(message = "informe o token")
        @Size(max = 200, message = "deve ter 200 caracteres ou menos")
        String token) {
}
