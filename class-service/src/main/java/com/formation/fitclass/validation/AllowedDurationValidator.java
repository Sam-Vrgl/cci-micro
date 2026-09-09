package com.formation.fitclass.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;

public class AllowedDurationValidator implements ConstraintValidator<AllowedDuration, Integer> {

    private int[] allowedValues;

    @Override
    public void initialize(AllowedDuration constraint) {
        this.allowedValues = constraint.value();
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        // null est laisse a @NotNull : une contrainte ne valide qu'une seule regle
        return value == null || Arrays.stream(allowedValues).anyMatch(allowed -> allowed == value);
    }
}
