package edu.alibaba.mpc4j.s2pc.pso.psu.css25;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnReceiverOutput;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnSenderOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuPtoDesc.PtoStep;

import java.util.Arrays;
import java.util.List;

/**
 * Combine-and-Permute (CnP) from CSSW25 (ASIACCS 2025) Fig. 8–12.
 * <p>
 * ShTr (Fig. 13) is realized via Waksman-network random OSN ({@code LLL24_FLAT_NET}): the receiver
 * (PSU client) holds random {@code a, b}, the sender (PSU server) holds {@code π} and
 * {@code Δ = π(a) ⊕ b}.
 * </p>
 */
class Css25CnPUtils {

    /**
     * One byte per CnP wire; only the LSB is used so ShTr matches boolean circuit-PSI shares.
     */
    static final int SHTR_BYTE_LENGTH = 1;

    /**
     * Paper-faithful masked-element byte length for the 3-hash Cuckoo / balanced ASIACCS_CSSW25 path.
     * <p>
     * Mirrors {@code common/MP-OPRF-Parameters.h::get_mp_oprf_hash_in_bytes} in the authors'
     * {@code encryptogroup/circuitPSU} repo, restricted to the
     * {@code cuckoo_hash_num == 3 || cuckoo_hash_num == 4} balanced branch — which is the path
     * mpc4j actually runs, since both {@link edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.psty19.Psty19CcpsiConfig}
     * and {@link edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.rs21.Rs21CcpsiConfig} are wired to
     * {@link edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory.CuckooHashBinType#NO_STASH_PSZ18_3_HASH_E04}.
     * </p>
     * <p>
     * Author's table (cuckoo_hash_num ∈ {3, 4}, balanced):
     * <pre>
     *   size   | hashLengthInBytes
     *   2^8    | 8
     *   2^10   | 8
     *   2^12   | 9
     *   2^14   | 9
     *   2^16   | 10
     *   2^18   | 10
     *   2^20   | 11
     *   ≥ 2^22 | 11
     * </pre>
     * Note: a previous version of this file mistakenly used the {@code cuckoo_hash_num == 0}
     * branch (simple-hash / unbalanced), which returns 12 bytes at n=2^18. That over-allocated
     * the masked element by 2 bytes per CCPSI entry. The 3-hash branch is the correct one for
     * our PSU path.
     * </p>
     * <p>
     * Both parties apply the identical CRHF (a domain-separated MPC4J {@code HashFactory}
     * instance) so OPRF-equality on shared inputs is preserved. This is a
     * functionality-preserving post-processing step that does <b>not</b> change the underlying
     * {@code Cm20MpOprfConfig}/{@code Cm20MpOprfSenderOutput} contract.
     * </p>
     * <p>
     * The implementation is still <b>not</b> a bit-exact reproduction of the authors' MP-OPRF
     * — the inner location-generation construction and the precise matrix width {@code w}
     * still differ. The audit verdict remains
     * {@code FUNCTIONALLY_EQUIVALENT_BUT_NOT_SAME_IMPLEMENTATION}.
     * </p>
     *
     * @param setSize the (balanced) input set size for this run. For unbalanced ASIACCS_CSSW25 use
     *                {@code max(serverSize, clientSize)} (the authors' table has a separate
     *                unbalanced branch keyed on both sizes; not yet implemented here).
     * @return the paper-target OPRF output length in bytes for this set size.
     */
    static int paperMaskedElementByteLength(int setSize) {
        if (setSize <= (1 << 8)) {
            return 8;
        }
        if (setSize <= (1 << 10)) {
            return 8;
        }
        if (setSize <= (1 << 12)) {
            return 9;
        }
        if (setSize <= (1 << 14)) {
            return 9;
        }
        if (setSize <= (1 << 16)) {
            return 10;
        }
        if (setSize <= (1 << 18)) {
            return 10;
        }
        if (setSize <= (1 << 20)) {
            return 11;
        }
        return 11;
    }

    /**
     * ⊥ for empty cuckoo bins in the final OT (length matches the PSU payload element).
     */
    static byte[] botElementBytes(int byteLength) {
        byte[] bot = new byte[byteLength];
        Arrays.fill(bot, (byte) 0xFF);
        return bot;
    }

    private Css25CnPUtils() {
    }

    static final class CnPOutput {
        final boolean[] zTilde;
        final int[] pi;

        CnPOutput(boolean[] zTilde, int[] pi) {
            this.zTilde = zTilde;
            this.pi = pi;
        }
    }

    static boolean[] toBits(SquareZ2Vector share) {
        int beta = Math.toIntExact(share.getNum());
        boolean[] bits = new boolean[beta];
        for (int i = 0; i < beta; i++) {
            bits[i] = share.getBitVector().get(i);
        }
        return bits;
    }

    /**
     * Paper sender S: inputs random π, share x0, and ShTr output Δ = π(a) ⊕ b.
     */
    static CnPOutput runSenderCnP(
        Rpc rpc, PtoDesc ptoDesc, long encodeTaskId, long extraInfo,
        Party ownParty, Party otherParty, int[] pi, boolean[] x0, RosnReceiverOutput shTrOut
    ) throws MpcAbortException {
        int beta = pi.length;
        MpcAbortPreconditions.checkArgument(x0.length == beta);
        MpcAbortPreconditions.checkArgument(shTrOut.getNum() == beta);
        MpcAbortPreconditions.checkArgument(shTrOut.getByteLength() == SHTR_BYTE_LENGTH);
        boolean[] delta = lsbVector(shTrOut.getDeltas(), beta);

        DataPacketHeader mHeader = new DataPacketHeader(
            encodeTaskId, ptoDesc.getPtoId(), PtoStep.CLIENT_SEND_CNP_M.ordinal(), extraInfo,
            otherParty.getPartyId(), ownParty.getPartyId()
        );
        boolean[] m = decodeBits(rpc.receive(mHeader).getPayload().get(0), beta);

        boolean[] mTilde = xor(permuteByPi(pi, xor(m, x0)), delta);
        DataPacketHeader mTildeHeader = new DataPacketHeader(
            encodeTaskId, ptoDesc.getPtoId(), PtoStep.SERVER_SEND_CNP_M_TILDE.ordinal(), extraInfo,
            ownParty.getPartyId(), otherParty.getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(mTildeHeader, List.of(encodeBits(mTilde))));

        return new CnPOutput(null, pi);
    }

    /**
     * Paper receiver R: inputs share x1 and ShTr output (a, b).
     */
    static CnPOutput runReceiverCnP(
        Rpc rpc, PtoDesc ptoDesc, long encodeTaskId, long extraInfo,
        Party ownParty, Party otherParty, boolean[] x1, RosnSenderOutput shTrOut
    ) throws MpcAbortException {
        int beta = x1.length;
        MpcAbortPreconditions.checkArgument(shTrOut.getNum() == beta);
        MpcAbortPreconditions.checkArgument(shTrOut.getByteLength() == SHTR_BYTE_LENGTH);
        boolean[] a = lsbVector(shTrOut.getAs(), beta);
        boolean[] b = lsbVector(shTrOut.getBs(), beta);

        boolean[] m = xor(x1, a);
        DataPacketHeader mHeader = new DataPacketHeader(
            encodeTaskId, ptoDesc.getPtoId(), PtoStep.CLIENT_SEND_CNP_M.ordinal(), extraInfo,
            ownParty.getPartyId(), otherParty.getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(mHeader, List.of(encodeBits(m))));

        DataPacketHeader mTildeHeader = new DataPacketHeader(
            encodeTaskId, ptoDesc.getPtoId(), PtoStep.SERVER_SEND_CNP_M_TILDE.ordinal(), extraInfo,
            otherParty.getPartyId(), ownParty.getPartyId()
        );
        boolean[] mTilde = decodeBits(rpc.receive(mTildeHeader).getPayload().get(0), beta);
        boolean[] zTilde = xor(mTilde, b);

        return new CnPOutput(zTilde, null);
    }

    private static boolean[] lsbVector(byte[][] rows, int beta) throws MpcAbortException {
        boolean[] bits = new boolean[beta];
        for (int i = 0; i < beta; i++) {
            MpcAbortPreconditions.checkArgument(rows[i].length == SHTR_BYTE_LENGTH);
            bits[i] = (rows[i][0] & 1) != 0;
        }
        return bits;
    }

    /**
     * Applies permutation π with the repository-wide convention
     * {@code out[j] = v[pi[j]]} (same as {@code PermutationNetworkUtils.permutation} / ROSN).
     */
    private static boolean[] permuteByPi(int[] pi, boolean[] v) {
        boolean[] out = new boolean[v.length];
        for (int j = 0; j < v.length; j++) {
            out[j] = v[pi[j]];
        }
        return out;
    }

    private static boolean[] xor(boolean[] x, boolean[] y) {
        boolean[] z = new boolean[x.length];
        for (int i = 0; i < x.length; i++) {
            z[i] = x[i] ^ y[i];
        }
        return z;
    }

    private static byte[] encodeBits(boolean[] bits) {
        byte[] out = new byte[bits.length];
        for (int i = 0; i < bits.length; i++) {
            out[i] = (byte) (bits[i] ? 1 : 0);
        }
        return out;
    }

    private static boolean[] decodeBits(byte[] row, int num) throws MpcAbortException {
        MpcAbortPreconditions.checkArgument(row.length == num);
        boolean[] bits = new boolean[num];
        for (int i = 0; i < num; i++) {
            bits[i] = row[i] != 0;
        }
        return bits;
    }
}
