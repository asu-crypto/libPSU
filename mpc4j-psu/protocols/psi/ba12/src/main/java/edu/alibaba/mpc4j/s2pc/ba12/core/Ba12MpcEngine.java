package edu.alibaba.mpc4j.s2pc.ba12.core;

import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.common.circuit.z2.PlainZ2Vector;
import edu.alibaba.mpc4j.common.circuit.z2.Z2CircuitConfig;
import edu.alibaba.mpc4j.common.circuit.z2.Z2IntegerCircuit;
import edu.alibaba.mpc4j.common.circuit.z2.sorter.SorterFactory;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.structure.database.Zl64Database;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVectorFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.s2pc.ba12.Ba12Config;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Wraps {@link Z2IntegerCircuit} + {@link Z2cParty} for BA12 shared integer/bit operations.
 */
public class Ba12MpcEngine {
  private final Ba12Config config;
  private final Z2cParty z2c;
  private final Z2IntegerCircuit circuit;
  private final Ba12Stats stats;
  private final int ell;

  public Ba12MpcEngine(Ba12Config config, Z2cParty z2c) {
    this.config = config;
    this.z2c = z2c;
    this.ell = config.getEll();
    this.stats = new Ba12Stats();
    Z2CircuitConfig circuitConfig = new Z2CircuitConfig.Builder()
      .setSorterType(SorterFactory.SorterTypes.BITONIC)
      .build();
    this.circuit = new Z2IntegerCircuit(z2c, circuitConfig);
  }

  public Ba12Stats getStats() {
    return stats;
  }

  public int getEll() {
    return ell;
  }

  public Z2cParty getZ2c() {
    return z2c;
  }

  public Ba12Share shareOwnElement(long value) {
    BitVector[] parts = partitionPlain(value);
    SquareZ2Vector[] shares = z2c.shareOwn(parts);
    return Ba12Codec.wrap(shares);
  }

  public Ba12Share shareOtherElement() throws MpcAbortException {
    int[] bitNums = new int[ell];
    Arrays.fill(bitNums, 1);
    SquareZ2Vector[] shares = z2c.shareOther(bitNums);
    return Ba12Codec.wrap(shares);
  }

  public Ba12Share[] shareOwn(long[] values) {
    return Arrays.stream(values).mapToObj(this::shareOwnElement).toArray(Ba12Share[]::new);
  }

  public Ba12Share[] shareOther(int m) throws MpcAbortException {
    Ba12Share[] out = new Ba12Share[m];
    for (int i = 0; i < m; i++) {
      out[i] = shareOtherElement();
    }
    return out;
  }

  /**
   * Reveal shared values to both parties (allowed in tests / opened-output mode).
   */
  public long[] reveal(Ba12Share[] xs) throws MpcAbortException {
    stats.incOpen();
    long[] out = new long[xs.length];
    for (int i = 0; i < xs.length; i++) {
      BitVector[] parts = z2c.open(xs[i].getBits());
      out[i] = Ba12Codec.decodePlain(ell, parts);
    }
    return out;
  }

  private BitVector[] partitionPlain(long value) {
    return Ba12Codec.partitionPlain(ell, value);
  }

  public Ba12Share add(Ba12Share a, Ba12Share b) throws MpcAbortException {
    return new Ba12Share(circuit.add(a.getBits(), b.getBits()));
  }

  public Ba12Share sub(Ba12Share a, Ba12Share b) throws MpcAbortException {
    return new Ba12Share(circuit.sub(a.getBits(), b.getBits()));
  }

  public MpcZ2Vector eq(Ba12Share x, Ba12Share y) throws MpcAbortException {
    stats.incEq();
    return circuit.eq(x.getBits(), y.getBits());
  }

  public MpcZ2Vector eq(Ba12Share x, long publicY) throws MpcAbortException {
    return eq(x, publicShare(publicY));
  }

  public MpcZ2Vector ge(Ba12Share x, Ba12Share y) throws MpcAbortException {
    stats.incGe();
    return circuit.leq(y.getBits(), x.getBits());
  }

  public MpcZ2Vector ge(Ba12Share x, long publicY) throws MpcAbortException {
    return ge(x, publicShare(publicY));
  }

  public MpcZ2Vector[] preAnd(MpcZ2Vector[] xs) throws MpcAbortException {
    int n = xs.length;
    MpcZ2Vector[] ys = new MpcZ2Vector[n];
    ys[0] = xs[0];
    for (int i = 1; i < n; i++) {
      ys[i] = z2c.and(ys[i - 1], xs[i]);
    }
    return ys;
  }

  public Ba12Share mulByBit(Ba12Share x, MpcZ2Vector bit) throws MpcAbortException {
    stats.incMul();
    MpcZ2Vector[] out = new MpcZ2Vector[ell];
    for (int i = 0; i < ell; i++) {
      out[i] = z2c.and(x.getBits()[i], bit);
    }
    return new Ba12Share(out);
  }

  public Ba12Share mulByBitComplement(Ba12Share x, MpcZ2Vector bit) throws MpcAbortException {
    return mulByBit(x, z2c.not(bit));
  }

  public Ba12Share addBit(Ba12Share acc, MpcZ2Vector bit) throws MpcAbortException {
    return add(acc, mulByBit(publicShare(1L), bit));
  }

  public Ba12Share publicShare(long value) throws MpcAbortException {
    BitVector[] parts = partitionPlain(value);
    return new Ba12Share(z2c.setPublicValues(parts));
  }

  public MpcZ2Vector publicBit(boolean value) throws MpcAbortException {
    BitVector bit = value ? BitVectorFactory.createOnes(1) : BitVectorFactory.createZeros(1);
    return z2c.setPublicValues(new BitVector[] { bit })[0];
  }

  public Ba12Share zeroShare() throws MpcAbortException {
    return publicShare(0L);
  }

  public void sort(Ba12Share[] xs) throws MpcAbortException {
    if (config.isInputAlreadySorted()) {
      throw new IllegalStateException("Merge path not enabled in MVP; disable inputAlreadySorted");
    }
    int m = xs.length;
    MpcZ2Vector[][] data = new MpcZ2Vector[m][];
    for (int j = 0; j < m; j++) {
      data[j] = xs[j].getBits();
    }
    circuit.sort(data);
    for (int j = 0; j < m; j++) {
      xs[j] = new Ba12Share(data[j]);
    }
  }

  public void sortT(Ba12TupleShare[] tuples) throws MpcAbortException {
    int m = tuples.length;
    Ba12Share[] values = new Ba12Share[m];
    MpcZ2Vector[] tags = new MpcZ2Vector[m];
    for (int i = 0; i < m; i++) {
      values[i] = tuples[i].getValue();
      tags[i] = tuples[i].getTag();
    }
    MpcZ2Vector[] valueColumns = packColumns(values);
    MpcZ2Vector tagColumn = z2c.merge(tags);
    MpcZ2Vector[][] data = new MpcZ2Vector[][] { valueColumns };
    MpcZ2Vector[][] payloads = new MpcZ2Vector[][] { new MpcZ2Vector[] { tagColumn } };
    circuit.psort(data, payloads, PlainZ2Vector.createOnes(1), false, true);
    unpackColumns(data[0], values);
    MpcZ2Vector[] tagSplit = z2c.split(tagColumn, ones(m));
    for (int i = 0; i < m; i++) {
      tuples[i] = new Ba12TupleShare(values[i], tagSplit[i]);
    }
  }

  private MpcZ2Vector[] packColumns(Ba12Share[] xs) throws MpcAbortException {
    MpcZ2Vector[] columns = new MpcZ2Vector[ell];
    for (int b = 0; b < ell; b++) {
      MpcZ2Vector[] bitShares = new MpcZ2Vector[xs.length];
      for (int j = 0; j < xs.length; j++) {
        bitShares[j] = xs[j].getBits()[b];
      }
      columns[b] = z2c.merge(bitShares);
    }
    return columns;
  }

  private void unpackColumns(MpcZ2Vector[] columns, Ba12Share[] xs) throws MpcAbortException {
    int[] bitNums = ones(xs.length);
    for (int b = 0; b < ell; b++) {
      MpcZ2Vector[] split = z2c.split(columns[b], bitNums);
      for (int j = 0; j < xs.length; j++) {
        xs[j].getBits()[b] = split[j];
      }
    }
  }

  private static int[] ones(int n) {
    return IntStream.range(0, n).map(i -> 1).toArray();
  }

  public Ba12Share[] concat(Ba12Share[] a, Ba12Share[] b) {
    Ba12Share[] out = new Ba12Share[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }
}
