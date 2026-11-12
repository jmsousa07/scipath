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
import java.nio.charset.StandardCharsets;

/** Limita a senha ao tamanho que o BCrypt processa sem truncamento: 72 bytes UTF-8. */
@Documented
@Constraint(validatedBy = SenhaBcryptValida.Validador.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT,
        ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaBcryptValida {

    String message() default "deve ter no máximo 72 bytes em UTF-8 (limite do bcrypt)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<SenhaBcryptValida, String> {

        private static final int LIMITE_BYTES = 72;

        @Override
        public boolean isValid(String senha, ConstraintValidatorContext contexto) {
            return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= LIMITE_BYTES;
        }
    }
}
