package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26AdaptedShuffleProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26BatchedRddhProof;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * On-the-wire encoding for PGT26-2M messages (proof blobs + index lists).
 */
final class Pgt26_2mWire {
  private static final int SCALAR = Ed25519ByteEccUtils.SCALAR_BYTES;
  private static final int POINT = Ed25519ByteEccUtils.POINT_BYTES;

  private Pgt26_2mWire() {
    // empty
  }

  static byte[] packIndices(int[] indices) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bos);
    dos.writeInt(indices.length);
    for (int i : indices) {
      dos.writeInt(i);
    }
    return bos.toByteArray();
  }

  static int[] unpackIndices(byte[] data) throws IOException {
    if (data == null || data.length < Integer.BYTES) {
      throw new IOException("bad index payload length");
    }
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
    int n = dis.readInt();
    long expectedLength = Integer.BYTES + (long) n * Integer.BYTES;
    if (n < 0 || expectedLength != data.length) {
      throw new IOException("bad index payload length");
    }
    int[] out = new int[n];
    for (int i = 0; i < n; i++) {
      out[i] = dis.readInt();
    }
    return out;
  }

  static byte[] packBatched(Pgt26BatchedRddhProof proof) {
    byte[] out = new byte[POINT + SCALAR];
    System.arraycopy(proof.commitment, 0, out, 0, POINT);
    System.arraycopy(proof.z, 0, out, POINT, SCALAR);
    return out;
  }

  static Pgt26BatchedRddhProof unpackBatched(byte[] data) throws IOException {
    if (data == null || data.length != POINT + SCALAR) {
      throw new IOException("bad RDDH proof length");
    }
    byte[] c = new byte[POINT];
    byte[] z = new byte[SCALAR];
    System.arraycopy(data, 0, c, 0, POINT);
    System.arraycopy(data, POINT, z, 0, SCALAR);
    return new Pgt26BatchedRddhProof(c, z);
  }

  static byte[] packAdapted(Pgt26AdaptedShuffleProof proof, int n) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bos);
    dos.writeInt(n);
    writePoint(dos, proof.cPi);
    writePoint(dos, proof.cD);
    writePoint(dos, proof.gD);
    writePoint(dos, proof.cZ);
    writePoint(dos, proof.gU);
    writePoint(dos, proof.cU);
    for (int i = 0; i < n; i++) {
      writeScalar(dos, proof.x[i]);
    }
    writeScalar(dos, proof.v);
    writeScalar(dos, proof.v2);
    writeKnown(dos, proof.psi, n);
    return bos.toByteArray();
  }

  static Pgt26AdaptedShuffleProof unpackAdapted(byte[] data) throws IOException {
    return unpackAdapted(data, -1);
  }

  static Pgt26AdaptedShuffleProof unpackAdapted(byte[] data, int expectedN) throws IOException {
    if (data == null || data.length < Integer.BYTES) {
      throw new IOException("bad adapted proof length");
    }
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
    int n = dis.readInt();
    if (n < 0 || (expectedN >= 0 && n != expectedN) || adaptedProofLength(n) != data.length) {
      throw new IOException("bad adapted proof length");
    }
    byte[] cPi = readPoint(dis);
    byte[] cD = readPoint(dis);
    byte[] gD = readPoint(dis);
    byte[] cZ = readPoint(dis);
    byte[] gU = readPoint(dis);
    byte[] cU = readPoint(dis);
    byte[][] x = new byte[n][];
    for (int i = 0; i < n; i++) {
      x[i] = readScalar(dis);
    }
    byte[] v = readScalar(dis);
    byte[] v2 = readScalar(dis);
    Pgt26AdaptedShuffleProof.KnownContentProof psi = readKnown(dis, n);
    return new Pgt26AdaptedShuffleProof(cPi, cD, gD, cZ, gU, cU, x, v, v2, psi);
  }

  static List<byte[]> round2Payload(byte[] pk, byte[][] points) {
    List<byte[]> list = new ArrayList<>(1 + points.length);
    list.add(pk);
    for (byte[] p : points) {
      list.add(p);
    }
    return list;
  }

  static byte[][] clonePoints(byte[][] points) {
    byte[][] out = new byte[points.length][];
    for (int i = 0; i < points.length; i++) {
      out[i] = Arrays.copyOf(points[i], points[i].length);
    }
    return out;
  }

  /** Defensive copy before RPC send (matches {@link edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpc}). */
  static List<byte[]> copyPayload(List<byte[]> payload) {
    return payload.stream().map(each -> Arrays.copyOf(each, each.length)).collect(Collectors.toList());
  }

  private static void writeKnown(DataOutputStream dos, Pgt26AdaptedShuffleProof.KnownContentProof psi, int n)
      throws IOException {
    writePoint(dos, psi.cD);
    writePoint(dos, psi.cDelta);
    writePoint(dos, psi.cA);
    for (int i = 0; i < n; i++) {
      writeScalar(dos, psi.f[i]);
    }
    for (int i = 0; i < n; i++) {
      writeScalar(dos, psi.fDelta[i]);
    }
    writeScalar(dos, psi.z);
    writeScalar(dos, psi.zDelta);
  }

  private static Pgt26AdaptedShuffleProof.KnownContentProof readKnown(DataInputStream dis, int n) throws IOException {
    byte[] cD = readPoint(dis);
    byte[] cDelta = readPoint(dis);
    byte[] cA = readPoint(dis);
    byte[][] f = new byte[n][];
    byte[][] fDelta = new byte[n][];
    for (int i = 0; i < n; i++) {
      f[i] = readScalar(dis);
    }
    for (int i = 0; i < n; i++) {
      fDelta[i] = readScalar(dis);
    }
    byte[] z = readScalar(dis);
    byte[] zDelta = readScalar(dis);
    return new Pgt26AdaptedShuffleProof.KnownContentProof(cD, cDelta, cA, f, fDelta, z, zDelta);
  }

  private static void writePoint(DataOutputStream dos, byte[] p) throws IOException {
    dos.write(p);
  }

  private static byte[] readPoint(DataInputStream dis) throws IOException {
    byte[] p = new byte[POINT];
    dis.readFully(p);
    return p;
  }

  private static void writeScalar(DataOutputStream dos, byte[] s) throws IOException {
    dos.write(s);
  }

  private static byte[] readScalar(DataInputStream dis) throws IOException {
    byte[] s = new byte[SCALAR];
    dis.readFully(s);
    return s;
  }

  private static long adaptedProofLength(int n) {
    return Integer.BYTES + 9L * POINT + (3L * n + 4L) * SCALAR;
  }
}
