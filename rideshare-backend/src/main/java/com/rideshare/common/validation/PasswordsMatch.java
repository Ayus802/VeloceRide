package com.rideshare.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

/**
 * Class-level constraint ensuring the password and its confirmation are identical.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordsMatch.Validator.class)
public @interface PasswordsMatch {

    String message() default "Password and confirm password do not match";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Implemented by request DTOs that carry a new password and its confirmation. */
    interface Confirmable {
        String passwordToConfirm();

        String confirmPassword();
    }

    class Validator implements ConstraintValidator<PasswordsMatch, Confirmable> {
        @Override
        public boolean isValid(Confirmable value, ConstraintValidatorContext context) {
            if (value == null || value.passwordToConfirm() == null || value.confirmPassword() == null) {
                return true; // @NotBlank on the fields reports missing values
            }
            if (Objects.equals(value.passwordToConfirm(), value.confirmPassword())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
            return false;
        }
    }
}
