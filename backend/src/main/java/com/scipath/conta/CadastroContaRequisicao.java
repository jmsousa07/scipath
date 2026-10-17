package com.scipath.conta;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Corpo do cadastro — schema {@code CadastroConta} do contrato v0.4 (RF008, §7.2).
 *
 * <p>Três campos, e só eles: {@code email}, {@code senha} e {@code nomePublico}. O
 * {@code slug} inicial do perfil <b>não</b> é campo — quem o deriva é o servidor
 * ({@link GeradorDeSlug}). Afiliação, áreas de interesse, links e biografia também não
 * existem aqui: entram na edição de perfil (E3.5).
 *
 * <p><b>Normalização do e-mail no construtor compacto.</b> O contrato diz que o e-mail é
 * "validado e normalizado (minúsculas, sem espaços)"; normalizar <i>antes</i> de validar é o
 * que faz {@code "  Maria.Silva@Exemplo.BR  "} ser aceito e tratado como o mesmo
 * identificador de {@code maria.silva@exemplo.br} — e é o que torna o 409 de e-mail
 * duplicado independente de caixa e espaço (RN009). Validar o texto cru recusaria com 422
 * uma entrada que o próprio contrato promete aceitar. A coluna
 * {@code account_email_normalizado} do V1 é a rede de segurança: nada é gravado fora disso.
 *
 * <p>A normalização também é o que mantém o e-mail fora do valor exibido em erro: os campos
 * enviados pelo cliente não são repetidos em nenhuma mensagem de erro (RNF013).
 *
 * <p><b>Senha.</b> {@code @NotNull} + {@code @Size(8..72)}: o teto de 72 acompanha o limite
 * de 72 bytes do bcrypt (ADR-0003 (a)) — aceitar mais seria descartar o excedente em
 * silêncio. A senha existe só nesta requisição: nunca é registrada em log nem devolvida
 * (RNF009).
 */
public record CadastroContaRequisicao(

        @NotBlank(message = "informe o e-mail")
        @Size(max = 254, message = "deve ter 254 caracteres ou menos")
        @Email(message = "deve ser um endereço de e-mail válido")
        String email,

        @NotNull(message = "informe a senha")
        @Size(min = 8, max = 72, message = "deve ter entre 8 e 72 caracteres")
        String senha,

        @NotBlank(message = "informe o nome público")
        @Size(max = 120, message = "deve ter 120 caracteres ou menos")
        String nomePublico) {

    public CadastroContaRequisicao {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
