package dev.skergan.calcurve.api.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The number must be finite: not NaN and not ±Infinity (e.g. from an overflowing literal like 1e400). */
@Documented
@Constraint(validatedBy = Finite.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Finite {

    String message() default "must be a finite number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Finite, Double> {
        @Override
        public boolean isValid(Double value, ConstraintValidatorContext context) {
            return value == null || Double.isFinite(value);
        }
    }
}
