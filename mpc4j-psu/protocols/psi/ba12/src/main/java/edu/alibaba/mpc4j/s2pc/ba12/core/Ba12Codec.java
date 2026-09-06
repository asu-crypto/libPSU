package edu.alibaba.mpc4j.s2pc.ba12.core;

import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.common.circuit.z2.PlainZ2Vector;
import edu.alibaba.mpc4j.common.structure.database.Zl64Database;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;

import java.util.Arrays;

/**
 * Encode/decode integers in {@code [0, 2^ℓ - 1]} to bit-decomposed vectors.
 */
public class Ba12Codec {
  private Ba12Codec() {
    // empty
  }

  public static BitVector[] partitionPlain(int ell, long value) {
    long[] row = new long[] { value };
    Zl64Database db = Zl64Database.create(ell, row);
    return db.bitPartition(EnvType.STANDARD, false);
  }

  public static long decodePlain(int ell, BitVector[] parts) {
    return Zl64Database.create(EnvType.STANDARD, false, parts).getData()[0];
  }

  public static Ba12Share wrap(SquareZ2Vector[] bits) {
    return new Ba12Share(Arrays.stream(bits).map(v -> (MpcZ2Vector) v).toArray(MpcZ2Vector[]::new));
  }

  public static SquareZ2Vector[] unwrap(Ba12Share share) {
    return Arrays.stream(share.getBits()).map(v -> (SquareZ2Vector) v).toArray(SquareZ2Vector[]::new);
  }

}
