package com.scipath.identidade;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Corpo do login — schema {@code Credenciais} do contrato v0.4 (RF009).
 *
 * <p><b>Mesma normalização do cadastro.</b> O e-mail é aparado e passado a minúsculas no
 * construtor compacto, pelo mesmo motivo do {@link com.scipath.conta.CadastroContaRequisicao}:
 * é a coluna {@code account.email} que guarda o valor normalizado, e é isso que faz
 * {@code "  Maria.Silva@Exemplo.BR  "} entrar na <b>mesma</b> conta de
 * {@code maria.silva@exemplo.br}. Sem isso, quem digitasse a senha certa com o e-mail em
 * caixa alta receberia 401 — e o login diria, por acidente, que aquele e-mail não existe.
 *
 * <p><b>A senha não tem regra de tamanho aqui.</b> É decisão do contrato: o schema
 * {@code Credenciais} não impõe mínimo, e uma senha curta no login não é campo inválido —
 * é senha errada, e senha errada responde <b>401</b> com o mesmo corpo de e-mail inexistente
 * (RNF013). Recusar com 422 aqui devolveria um código diferente conforme o formato do que foi
 * digitado, o que só serviria para dizer ao atacante qual regra ele ainda não descobriu.
 * O mínimo de 8 caracteres continua valendo onde importa: no cadastro.
 *
 * <p>A senha existe só nesta requisição: nunca é registrada em log nem devolvida (RNF009).
 */
public record CredenciaisRequisicao(

        @NotBlank(message = "informe o e-mail")
        @Size(max = 254, message = "deve ter 254 caracteres ou menos")
        @Email(message = "deve ser um endereço de e-mail válido")
        String email,

        @NotNull(message = "informe a senha")
        String senha) {

    public CredenciaisRequisicao {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
