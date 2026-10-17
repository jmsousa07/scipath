package com.scipath.token;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Corpo do pedido de recuperação de acesso — schema {@code RecuperacaoSolicitacao} do contrato
 * v0.4 (RF010).
 *
 * <p><b>Normalização igual à do cadastro</b> (mesmo construtor compacto de
 * {@code CadastroContaRequisicao}): minúsculas e sem espaços nas pontas, antes da validação de
 * formato. A mesma normalização é o que faz {@code "  Maria.Silva@Exemplo.BR  "} encontrar a
 * conta que o cadastro gravou — e é a coluna {@code account_email_normalizado} do V1 que
 * garante que nada foi gravado fora disso.
 *
 * <p>O campo existe e é validado porque o <b>pedido</b> precisa saber para quem mandar; o que
 * não existe em lugar nenhum da resposta é a existência da conta. E-mail válido e conta
 * inexistente devolvem 202 exatamente como conta existente (RNF013), e nenhuma mensagem de erro
 * deste fluxo repete o endereço enviado.
 */
public record RecuperacaoSolicitacaoRequisicao(

        @NotBlank(message = "informe o e-mail")
        @Size(max = 254, message = "deve ter 254 caracteres ou menos")
        @Email(message = "deve ser um endereço de e-mail válido")
        String email) {

    public RecuperacaoSolicitacaoRequisicao {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
