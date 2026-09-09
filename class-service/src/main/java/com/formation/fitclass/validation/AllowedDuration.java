package com.formation.fitclass.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contraint la duree d'un cours aux seules valeurs autorisees par le cahier des charges.
 */
@Documented
@Constraint(validatedBy = AllowedDurationValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedDuration {

    int[] value() default {30, 45, 60, 90};

    String message() default "La duree doit valoir 30, 45, 60 ou 90 minutes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
