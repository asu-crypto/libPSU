package edu.alibaba.mpc4j.s2pc.pso.psi.hn12;

import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12Ciphertext;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkCom;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkDl;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkPoly;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * RPC payload codec for HN12 PSI steps.
 */
final class Hn12WireCodec {
    private Hn12WireCodec() {
    }

    static byte[] encodeKeys(byte[] groupParams, BigInteger h, BigInteger hPrime, Hn12ZkDl.Proof dlH, Hn12ZkDl.Proof dlHp, Hn12DdhGroup group) {
        List<byte[]> parts = new ArrayList<>();
        parts.add(groupParams);
        parts.add(group.encodeElement(h));
        parts.add(group.encodeElement(hPrime));
        parts.add(encodeDl(dlH, group));
        parts.add(encodeDl(dlHp, group));
        return join(parts);
    }

    static byte[] encodeAlloc(int binCount, int maxBinSize, byte[] seed0, byte[] seed1) {
        List<byte[]> parts = new ArrayList<>();
        parts.add(IntUtils.intToByteArray(binCount));
        parts.add(IntUtils.intToByteArray(maxBinSize));
        parts.add(seed0);
        parts.add(seed1);
        return join(parts);
    }

    static byte[] encodeCommitmentRow(BigInteger com, Hn12ZkCom.Proof proof, Hn12DdhGroup group) {
        List<byte[]> parts = new ArrayList<>();
        parts.add(group.encodeElement(com));
        parts.add(group.encodeElement(proof.t));
        parts.add(proof.sm.toByteArray());
        parts.add(proof.sr.toByteArray());
        return join(parts);
    }

    static byte[] encodeEncPolyMatrix(List<List<Hn12Ciphertext>> matrix, Hn12DdhGroup group) {
        int len = Integer.BYTES;
        for (List<Hn12Ciphertext> row : matrix) {
            len += Integer.BYTES;
            for (Hn12Ciphertext ct : row) {
                len += Integer.BYTES + ct.serialize(group).length;
            }
        }
        byte[] out = new byte[len];
        int o = 0;
        System.arraycopy(IntUtils.intToByteArray(matrix.size()), 0, out, o, Integer.BYTES);
        o += Integer.BYTES;
        for (List<Hn12Ciphertext> row : matrix) {
            System.arraycopy(IntUtils.intToByteArray(row.size()), 0, out, o, Integer.BYTES);
            o += Integer.BYTES;
            for (Hn12Ciphertext ct : row) {
                byte[] enc = ct.serialize(group);
                System.arraycopy(IntUtils.intToByteArray(enc.length), 0, out, o, Integer.BYTES);
                o += Integer.BYTES;
                System.arraycopy(enc, 0, out, o, enc.length);
                o += enc.length;
            }
        }
        return out;
    }

    static byte[] encodePolyProof(Hn12ZkPoly.Proof proof) {
        List<byte[]> parts = new ArrayList<>();
        parts.add(IntUtils.intToByteArray(proof.totalDegree));
        parts.add(proof.bindingDigest);
        return join(parts);
    }

    static byte[] encodeEvalRow(byte[] ct0, byte[] ct1, byte[] maskedItem, byte[] prfOut) {
        List<byte[]> parts = new ArrayList<>();
        parts.add(ct0);
        parts.add(ct1);
        parts.add(maskedItem);
        parts.add(prfOut);
        return join(parts);
    }

    private static byte[] encodeDl(Hn12ZkDl.Proof proof, Hn12DdhGroup group) {
        List<byte[]> parts = new ArrayList<>();
        parts.add(group.encodeElement(proof.t));
        parts.add(proof.s.toByteArray());
        return join(parts);
    }

    private static byte[] join(List<byte[]> parts) {
        int len = Integer.BYTES;
        for (byte[] p : parts) {
            len += Integer.BYTES + p.length;
        }
        byte[] out = new byte[len];
        int o = 0;
        System.arraycopy(IntUtils.intToByteArray(parts.size()), 0, out, o, Integer.BYTES);
        o += Integer.BYTES;
        for (byte[] p : parts) {
            System.arraycopy(IntUtils.intToByteArray(p.length), 0, out, o, Integer.BYTES);
            o += Integer.BYTES;
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}
