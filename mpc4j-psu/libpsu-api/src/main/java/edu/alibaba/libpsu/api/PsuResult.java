package edu.alibaba.libpsu.api;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Consumer-facing PSU result.
 */
public final class PsuResult {
    private final PsuInput union;
    private final OptionalInt psiCa;

    private PsuResult(PsuInput union, OptionalInt psiCa) {
        this.union = Objects.requireNonNull(union, "union");
        this.psiCa = Objects.requireNonNull(psiCa, "psiCa");
    }

    public static PsuResult union(PsuInput union) {
        return new PsuResult(union, OptionalInt.empty());
    }

    public static PsuResult unionWithPsiCa(PsuInput union, int psiCa) {
        if (psiCa < 0) {
            throw new IllegalArgumentException("psiCa must be non-negative");
        }
        return new PsuResult(union, OptionalInt.of(psiCa));
    }

    public PsuInput union() {
        return union;
    }

    public OptionalInt psiCa() {
        return psiCa;
    }
}
