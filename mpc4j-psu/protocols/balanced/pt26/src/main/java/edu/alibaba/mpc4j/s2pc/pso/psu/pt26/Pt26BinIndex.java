package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import java.io.Serializable;
import java.util.Objects;

/**
 * Bin coordinate {@code (i, j) ∈ [k] × [ℓ]}.
 */
public final class Pt26BinIndex implements Serializable {
    private static final long serialVersionUID = 3847561029384756102L;
    private final int i;
    private final int j;

    public Pt26BinIndex(int i, int j) {
        this.i = i;
        this.j = j;
    }

    public int getI() {
        return i;
    }

    public int getJ() {
        return j;
    }

    public int linearIndex(int subtableSize) {
        return i * subtableSize + j;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pt26BinIndex)) {
            return false;
        }
        Pt26BinIndex that = (Pt26BinIndex) o;
        return i == that.i && j == that.j;
    }

    public int hashCode() {
        return Objects.hash(i, j);
    }
}
