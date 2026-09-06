package edu.alibaba.mpc4j.s2pc.pso.psi.hn12;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12Ciphertext;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkCom;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkDl;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkPoly;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Message parsing for HN12 PSI RPC payloads.
 */
final class Hn12MessageIO {
    private Hn12MessageIO() {
    }

    static void sendStep(
        Rpc rpc, long encodeTaskId, PtoDesc desc, long extraInfo, Party own, Party other,
        int stepOrdinal, List<byte[]> payload
    ) {
        DataPacketHeader header = new DataPacketHeader(encodeTaskId, desc.getPtoId(), stepOrdinal, extraInfo,
            own.getPartyId(), other.getPartyId());
        rpc.send(DataPacket.fromByteArrayList(header, payload));
    }

    static List<byte[]> receiveStep(
        Rpc rpc, long encodeTaskId, PtoDesc desc, long extraInfo, Party own, Party other, int stepOrdinal
    ) throws MpcAbortException {
        DataPacketHeader header = new DataPacketHeader(encodeTaskId, desc.getPtoId(), stepOrdinal, extraInfo,
            other.getPartyId(), own.getPartyId());
        return rpc.receive(header).getPayload();
    }

    static KeysMessage parseKeys(byte[] body) throws MpcAbortException {
        List<byte[]> parts = split(body);
        MpcAbortPreconditions.checkArgument(parts.size() == 5);
        Hn12DdhGroup group = Hn12DdhGroup.deserializeParameters(parts.get(0));
        BigInteger h = group.decodeElement(parts.get(1));
        BigInteger hPrime = group.decodeElement(parts.get(2));
        Hn12ZkDl.Proof dlH = parseDl(parts.get(3), group);
        Hn12ZkDl.Proof dlHp = parseDl(parts.get(4), group);
        return new KeysMessage(group, h, hPrime, dlH, dlHp);
    }

    static AllocMessage parseAlloc(byte[] body) throws MpcAbortException {
        List<byte[]> parts = split(body);
        MpcAbortPreconditions.checkArgument(parts.size() == 4);
        return new AllocMessage(
            IntUtils.byteArrayToInt(parts.get(0)),
            IntUtils.byteArrayToInt(parts.get(1)),
            parts.get(2),
            parts.get(3)
        );
    }

    static CommitmentMessage parseCommitment(byte[] body, Hn12DdhGroup group) throws MpcAbortException {
        List<byte[]> parts = split(body);
        MpcAbortPreconditions.checkArgument(parts.size() == 4);
        return new CommitmentMessage(
            group.decodeElement(parts.get(0)),
            new Hn12ZkCom.Proof(group.decodeElement(parts.get(1)), new BigInteger(1, parts.get(2)), new BigInteger(1, parts.get(3)))
        );
    }

    static List<List<Hn12Ciphertext>> parseEncMatrix(byte[] body, Hn12DdhGroup group) throws MpcAbortException {
        int offset = 0;
        int rows = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
        offset += Integer.BYTES;
        List<List<Hn12Ciphertext>> matrix = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            int cols = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
            offset += Integer.BYTES;
            List<Hn12Ciphertext> row = new ArrayList<>(cols);
            for (int j = 0; j < cols; j++) {
                int len = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
                offset += Integer.BYTES;
                row.add(Hn12Ciphertext.deserialize(BytesUtils.clone(body, offset, len), group));
                offset += len;
            }
            matrix.add(row);
        }
        return matrix;
    }

    static Hn12ZkPoly.Proof parsePolyProof(byte[] body) throws MpcAbortException {
        List<byte[]> parts = split(body);
        MpcAbortPreconditions.checkArgument(parts.size() == 2);
        return new Hn12ZkPoly.Proof(IntUtils.byteArrayToInt(parts.get(0)), parts.get(1));
    }

    static EvalRow parseEvalRow(byte[] body, Hn12DdhGroup group) throws MpcAbortException {
        List<byte[]> parts = split(body);
        MpcAbortPreconditions.checkArgument(parts.size() == 4);
        return new EvalRow(
            Hn12Ciphertext.deserialize(parts.get(0), group),
            Hn12Ciphertext.deserialize(parts.get(1), group),
            parts.get(2),
            parts.get(3)
        );
    }

    private static Hn12ZkDl.Proof parseDl(byte[] body, Hn12DdhGroup group) {
        List<byte[]> parts = split(body);
        return new Hn12ZkDl.Proof(group.decodeElement(parts.get(0)), new BigInteger(1, parts.get(1)));
    }

    private static List<byte[]> split(byte[] body) {
        int offset = 0;
        int n = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
        offset += Integer.BYTES;
        List<byte[]> parts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int len = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
            offset += Integer.BYTES;
            parts.add(BytesUtils.clone(body, offset, len));
            offset += len;
        }
        return parts;
    }

    static final class KeysMessage {
        final Hn12DdhGroup group;
        final BigInteger h;
        final BigInteger hPrime;
        final Hn12ZkDl.Proof dlH;
        final Hn12ZkDl.Proof dlHp;

        KeysMessage(Hn12DdhGroup group, BigInteger h, BigInteger hPrime, Hn12ZkDl.Proof dlH, Hn12ZkDl.Proof dlHp) {
            this.group = group;
            this.h = h;
            this.hPrime = hPrime;
            this.dlH = dlH;
            this.dlHp = dlHp;
        }
    }

    static final class AllocMessage {
        final int binCount;
        final int maxBinSize;
        final byte[] seed0;
        final byte[] seed1;

        AllocMessage(int binCount, int maxBinSize, byte[] seed0, byte[] seed1) {
            this.binCount = binCount;
            this.maxBinSize = maxBinSize;
            this.seed0 = seed0;
            this.seed1 = seed1;
        }
    }

    static final class CommitmentMessage {
        final BigInteger commitment;
        final Hn12ZkCom.Proof proof;

        CommitmentMessage(BigInteger commitment, Hn12ZkCom.Proof proof) {
            this.commitment = commitment;
            this.proof = proof;
        }
    }

    static final class EvalRow {
        final Hn12Ciphertext eval0;
        final Hn12Ciphertext eval1;
        final byte[] maskedItem;
        final byte[] prfOut;

        EvalRow(Hn12Ciphertext eval0, Hn12Ciphertext eval1, byte[] maskedItem, byte[] prfOut) {
            this.eval0 = eval0;
            this.eval1 = eval1;
            this.maskedItem = maskedItem;
            this.prfOut = prfOut;
        }
    }
}
