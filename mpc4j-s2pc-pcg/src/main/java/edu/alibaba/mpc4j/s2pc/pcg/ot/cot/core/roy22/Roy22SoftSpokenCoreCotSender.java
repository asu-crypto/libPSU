package edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.roy22;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.base.BaseOtFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.base.BaseOtReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.base.BaseOtReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.AbstractCoreCotSender;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.roy22.Roy22SoftSpokenCoreCotPtoDesc.PtoStep;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Roy22 SoftSpoken core COT sender (Component 1: subspace VOLE only, semi-honest, k=2).
 *
 * <p>See the package {@code README.md} and {@link Roy22SoftSpokenCoreCotPtoDesc} for the full
 * construction and the Component-1 / Component-2 scope split. In a nutshell:</p>
 * <ol>
 *     <li><strong>Init</strong>: decomposes Δ into NUM_BLOCKS = 64 GF(4)-digits Δ_b; runs
 *         {@code 2^k * NUM_BLOCKS = 256} base OTs as the base-OT <em>receiver</em> with choice
 *         {@code (α ≠ Δ_b)}; decrypts the corrected seeds for the {@code α ≠ Δ_b} positions.</li>
 *     <li><strong>Send</strong>: for each row {@code j ∈ [num)} and block {@code b}, computes
 *         {@code Q[b][j] := XOR_{α ≠ Δ_b} (α ⊕ Δ_b) * PRG(seed_{α,b})[j]}, applies the receiver's
 *         correction {@code Q'[b][j] := Q[b][j] ⊕ Δ_b * u[b][j]}, and packs the 64 F-elements into
 *         a 16-byte block. By the subspace VOLE relation, this satisfies
 *         {@code q_j ⊕ t_j = c_j · Δ} where {@code c_j} is the receiver's COT choice bit.</li>
 * </ol>
 *
 * @author audit follow-up #5 (May 26, 2026)
 */
public class Roy22SoftSpokenCoreCotSender extends AbstractCoreCotSender {
    /**
     * base OT receiver — the SoftSpoken sender plays the base-OT <em>receiver</em>.
     */
    private final BaseOtReceiver baseOtReceiver;
    /**
     * Roy22 small-field exponent {@code k}. Must equal {@link Roy22SoftSpokenCoreCotUtils#FIELD_BITS}
     * for this Component-1 implementation.
     */
    private final int fieldBits;
    /**
     * Δ decomposed into NUM_BLOCKS = κ/k GF(4)-digits. Populated by {@link #init(byte[])}.
     */
    private int[] deltaDigits;
    /**
     * Seeds {@code seed_{α, b}} for {@code (α, b)} with {@code α ≠ Δ_b}. Stored flat at index
     * {@code b * FIELD_SIZE + α}. {@code null} for {@code α = Δ_b}.
     */
    private byte[][] seeds;

    public Roy22SoftSpokenCoreCotSender(Rpc senderRpc, Party receiverParty, Roy22SoftSpokenCoreCotConfig config) {
        super(Roy22SoftSpokenCoreCotPtoDesc.getInstance(), senderRpc, receiverParty, config);
        baseOtReceiver = BaseOtFactory.createReceiver(senderRpc, receiverParty, config.getBaseOtConfig());
        addSubPto(baseOtReceiver);
        fieldBits = config.getFieldBits();
        if (fieldBits != Roy22SoftSpokenCoreCotUtils.FIELD_BITS) {
            throw new IllegalArgumentException(
                "Roy22 SoftSpoken (Component-1 cut) hard-codes k = " + Roy22SoftSpokenCoreCotUtils.FIELD_BITS
                    + ", but config requested k = " + fieldBits + ". Generalizing to other k is a follow-up; "
                    + "see the package README.md."
            );
        }
    }

    @Override
    public void init(byte[] delta) throws MpcAbortException {
        setInitInput(delta);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        deltaDigits = Roy22SoftSpokenCoreCotUtils.decomposeBlock(delta);

        // Build base-OT choices: choice_{b, α} = (α != Δ_b). The "missing" position α = Δ_b stays 0
        // (sender knows the seed is unrecoverable; receiver still sends a ciphertext for it but
        // sender will discard).
        boolean[] choices = new boolean[Roy22SoftSpokenCoreCotUtils.TOTAL_BASE_OTS];
        for (int b = 0; b < Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS; b++) {
            int otBase = b * Roy22SoftSpokenCoreCotUtils.FIELD_SIZE;
            int deltaB = deltaDigits[b];
            for (int alpha = 0; alpha < Roy22SoftSpokenCoreCotUtils.FIELD_SIZE; alpha++) {
                choices[otBase + alpha] = (alpha != deltaB);
            }
        }
        baseOtReceiver.init();
        BaseOtReceiverOutput baseOtOutput = baseOtReceiver.receive(choices);

        // Receive the encrypted seeds: c_i = PRG(rm1_i) XOR seed_{α(i), b(i)} for every i.
        DataPacketHeader seedHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(),
            PtoStep.RECEIVER_SEND_SEED_CORRECTIONS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> seedPayload = rpc.receive(seedHeader).getPayload();
        MpcAbortPreconditions.checkArgument(
            seedPayload.size() == Roy22SoftSpokenCoreCotUtils.TOTAL_BASE_OTS,
            "expected %s seed corrections, got %s",
            Roy22SoftSpokenCoreCotUtils.TOTAL_BASE_OTS, seedPayload.size()
        );
        for (byte[] entry : seedPayload) {
            MpcAbortPreconditions.checkArgument(
                entry.length == CommonConstants.BLOCK_BYTE_LENGTH,
                "seed correction must be %s bytes; got %s",
                CommonConstants.BLOCK_BYTE_LENGTH, entry.length
            );
        }

        // Decrypt seeds for choice=1 positions. PRG expands a 16-byte base-OT key into 16 bytes
        // (the per-OT mask). For choice=0 positions (α = Δ_b) we keep seeds[i] = null and skip
        // the position in send(num).
        Prg prgKappa = PrgFactory.createInstance(envType, CommonConstants.BLOCK_BYTE_LENGTH);
        seeds = new byte[Roy22SoftSpokenCoreCotUtils.TOTAL_BASE_OTS][];
        for (int i = 0; i < Roy22SoftSpokenCoreCotUtils.TOTAL_BASE_OTS; i++) {
            if (choices[i]) {
                byte[] mask = prgKappa.extendToBytes(baseOtOutput.getRb(i));
                BytesUtils.xori(mask, seedPayload.get(i));
                seeds[i] = mask;
            }
        }
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public CotSenderOutput send(int num) throws MpcAbortException {
        setPtoInput(num);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        // Receive subspace VOLE corrections u[b] (one packed F-row per block).
        DataPacketHeader uHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(),
            PtoStep.RECEIVER_SEND_SUBSPACE_VOLE_CORRECTIONS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> uPayload = rpc.receive(uHeader).getPayload();
        MpcAbortPreconditions.checkArgument(
            uPayload.size() == Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS,
            "expected %s correction rows, got %s",
            Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS, uPayload.size()
        );
        int fRowBytes = Roy22SoftSpokenCoreCotUtils.fRowByteLength(num);
        for (byte[] row : uPayload) {
            MpcAbortPreconditions.checkArgument(
                row.length == fRowBytes,
                "correction row must be %s bytes; got %s", fRowBytes, row.length
            );
        }

        // Accumulate Q[b] := XOR_{α != Δ_b} (α XOR Δ_b) * PRG(seed_{α,b}).
        // Then Q'[b] := Q[b] XOR Δ_b * u[b], satisfying Q'[b][j] = T[b][j] XOR Δ_b * choice_j.
        Prg prgRow = PrgFactory.createInstance(envType, fRowBytes);
        byte[][] q = new byte[Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS][fRowBytes];
        for (int b = 0; b < Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS; b++) {
            int otBase = b * Roy22SoftSpokenCoreCotUtils.FIELD_SIZE;
            int deltaB = deltaDigits[b];
            for (int alpha = 0; alpha < Roy22SoftSpokenCoreCotUtils.FIELD_SIZE; alpha++) {
                if (alpha == deltaB) {
                    continue;
                }
                byte[] expanded = prgRow.extendToBytes(seeds[otBase + alpha]);
                Roy22SoftSpokenCoreCotUtils.axpyFieldRow(q[b], expanded, alpha ^ deltaB, num);
            }
            Roy22SoftSpokenCoreCotUtils.axpyFieldRow(q[b], uPayload.get(b), deltaB, num);
        }

        // Pack 64 F-elements per row j into the corresponding 16-byte κ-block. Block b lands at
        // byte index (15 - b/4), bit shift (b%4)*2 — same convention as decomposeBlock.
        byte[][] rOut = new byte[num][CommonConstants.BLOCK_BYTE_LENGTH];
        int elementsPerByte = Byte.SIZE / Roy22SoftSpokenCoreCotUtils.FIELD_BITS;
        for (int b = 0; b < Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS; b++) {
            int byteIdx = (CommonConstants.BLOCK_BYTE_LENGTH - 1) - (b / elementsPerByte);
            int shift = (b % elementsPerByte) * Roy22SoftSpokenCoreCotUtils.FIELD_BITS;
            for (int j = 0; j < num; j++) {
                int fEl = Roy22SoftSpokenCoreCotUtils.extractField(q[b], j);
                rOut[j][byteIdx] |= (byte) (fEl << shift);
            }
        }

        stopWatch.stop();
        long time = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 1, time);

        logPhaseInfo(PtoState.PTO_END);
        return CotSenderOutput.create(delta, rOut);
    }
}
