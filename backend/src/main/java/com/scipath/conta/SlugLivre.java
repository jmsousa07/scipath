package com.scipath.conta;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * O slug não pertence a nenhuma rota da aplicação ({@link SlugsReservados}).
 *
 * <p>É uma restrição de Bean Validation e não uma checagem no serviço por um motivo concreto:
 * o contrato publica <b>422 {@code validacao_campo}</b> com {@code errors[]} apontando o campo
 * para slug fora do que é aceito, e é exatamente o que o {@code @Valid} já produz no
 * {@code ApiExceptionHandler} único. Uma checagem no serviço precisaria de uma exceção nova e
 * de um segundo caminho até o mesmo 422 — o vocabulário de erro do contrato é fechado e não
 * ganha classe nova por causa de uma palavra reservada.
 *
 * <p>{@code null} passa aqui de propósito: "não informado" é recusado por {@code @NotBlank},
 * que é quem sabe dizer isso com a mensagem certa. Cada restrição responde por uma coisa só, e
 * o formulário recebe o campo culpado uma vez.
 */
@Documented
@Constraint(validatedBy = SlugLivre.Validador.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT,
        ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SlugLivre {

    String message() default "este identificador é reservado pela aplicação; escolha outro";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** A restrição em si — uma linha, porque a lista reservada mora em {@link SlugsReservados}. */
    class Validador implements ConstraintValidator<SlugLivre, String> {

        @Override
        public boolean isValid(String slug, ConstraintValidatorContext contexto) {
            return slug == null || !SlugsReservados.contem(slug);
        }
    }
}
