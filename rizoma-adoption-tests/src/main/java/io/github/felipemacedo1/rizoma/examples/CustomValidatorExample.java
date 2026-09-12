package io.github.felipemacedo1.rizoma.examples;

import io.github.felipemacedo1.rizoma.api.Rizoma;
import io.github.felipemacedo1.rizoma.core.Validator;
import io.github.felipemacedo1.rizoma.core.ValueTransformer;

/** Small extension example that adds one validator without rebuilding defaults. */
public final class CustomValidatorExample {
    private CustomValidatorExample() {}

    /** Builds a facade containing all defaults plus the supplied validator. */
    public static Rizoma with(Validator<?> validator) {
        return Rizoma.builder().addValidator(validator).build();
    }

    /** Builds a facade containing all defaults plus one transformer and validator. */
    public static Rizoma with(ValueTransformer<?, ?> transformer, Validator<?> validator) {
        return Rizoma.builder().addTransformer(transformer).addValidator(validator).build();
    }
}
