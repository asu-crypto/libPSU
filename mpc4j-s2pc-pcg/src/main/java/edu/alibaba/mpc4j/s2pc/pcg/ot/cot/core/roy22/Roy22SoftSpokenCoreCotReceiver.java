package edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.roy22;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.base.BaseOtFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.base.BaseOtSender;
import edu.alibaba.mpc4j.s2pc.pcg.ot.base.BaseOtSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.AbstractCoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.roy22.Roy22SoftSpokenCoreCotPtoDesc.PtoStep;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Roy22 SoftSpoken core COT receiver (Component 1: subspace VOLE only, semi-honest, k=2).
 *
 * <p>See the package {@code README.md} and {@link Roy22SoftSpokenCoreCotPtoDesc} for the full
 * construction and the Component-1 / Component-2 scope split. In a nutshell:</p>
 * <ol>
 *     <li><strong>Init</strong>: samples {@code 2^k * NUM_BLOCKS = 256} κ-bit seeds; runs the
 *         {@code 256} base OTs as the base-OT <em>sender</em> with random (r0, r1) pairs; encrypts
 *         each seed under {@code PRG(r1)} so the sender can recover it iff its base-OT choice is
 *         1 (i.e. iff {@code α ≠ Δ_b}).</li>
 *     <li><strong>Receive</strong>: for each block {@code b} and row {@code j}, computes
 *         {@code S[b][j] = XOR_α PRG(seed_{α,b})[j]} and {@code T[b][j] = XOR_α α * PRG(seed_{α,b})[j]};
 *         sends correction {@code u[b][j] := S[b][j] ⊕ choice_j} to the sender. Packs 64 F-elements
 *         per row {@code j} into a 16-byte block as the COT output {@code t_j}.</li>
 * </ol>
 *
 * @author audit follow-up #5 (May 26, 2026)
 */
public class Roy22SoftSpokenCoreCotReceiver extends AbstractCoreCotReceiver {
    /**
     * base OT sender — the SoftSpoken receiver plays the base-OT <em>sender</em>.
     */
    private final BaseOtSender baseOtSender;
    /**
     * Roy22 small-field exponent {@code k}. Must equal {@link Roy22SoftSpokenCoreCotUtils#FIELD_BITS}
     * for this Component-1 implementation.
     */
    private final int fieldBits;
    /**
     * All {@code TOTAL_BASE_OTS} = {@code 2^k * (κ/k)} seeds {@code seed_{α, b}}, stored flat at
     * index {@code b * FIELD_SIZE + α}. Populated by {@link #init()}.
     */
    private byte[][] seeds;

    public Roy22SoftSpokenCoreCotReceiver(Rpc receiverRpc, Party senderParty, Roy22SoftSpokenCoreCotConfig config) {
        super(Roy22SoftSpokenCoreCotPtoDesc.getInstance(), receiverRpc, senderParty, config);
        baseOtSender = BaseOtFactory.createSender(receiverRpc, senderParty, config.getBaseOtConfig());
        addSubPto(baseOtSender);
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
    public void init() throws MpcAbortException {
        setInitInput();
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        baseOtSender.init();
        BaseOtSenderOutput baseOtOutput = baseOtSender.send(Roy22SoftSpokenCoreCotUtils.TOTAL_BASE_OTS);

        // Sample fresh seeds and encrypt under PRG(r1_i). Sender at choice=1 (α ≠ Δ_b) will recover
        // seed_{α,b}; sender at choice=0 (α = Δ_b) discards because it knows Δ_b.
        Prg prgKappa = PrgFactory.createInstance(envType, CommonConstants.BLOCK_BYTE_LENGTH);
        seeds = new byte[Roy22SoftSpokenCoreCotUtils.TOTAL_BASE_OTS][];
        List<byte[]> seedPayload = new ArrayList<>(Roy22SoftSpokenCoreCotUtils.TOTAL_BASE_OTS);
        for (int i = 0; i < Roy22SoftSpokenCoreCotUtils.TOTAL_BASE_OTS; i++) {
            byte[] seed = BlockUtils.randomBlock(secureRandom);
            seeds[i] = seed;
            byte[] mask = prgKappa.extendToBytes(baseOtOutput.getR1(i));
            BytesUtils.xori(mask, seed);
            seedPayload.add(mask);
        }
        DataPacketHeader seedHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(),
            PtoStep.RECEIVER_SEND_SEED_CORRECTIONS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(seedHeader, seedPayload));

        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public CotReceiverOutput receive(boolean[] choices) throws MpcAbortException {
        setPtoInput(choices);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        int fRowBytes = Roy22SoftSpokenCoreCotUtils.fRowByteLength(num);
        Prg prgRow = PrgFactory.createInstance(envType, fRowBytes);

        // Per block b: S[b] := XOR_α PRG(seed_{α,b}), T[b] := XOR_α α * PRG(seed_{α,b}).
        byte[][] s = new byte[Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS][fRowBytes];
        byte[][] t = new byte[Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS][fRowBytes];
        for (int b = 0; b < Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS; b++) {
            int otBase = b * Roy22SoftSpokenCoreCotUtils.FIELD_SIZE;
            for (int alpha = 0; alpha < Roy22SoftSpokenCoreCotUtils.FIELD_SIZE; alpha++) {
                byte[] expanded = prgRow.extendToBytes(seeds[otBase + alpha]);
                Roy22SoftSpokenCoreCotUtils.axpyFieldRow(s[b], expanded, 1, num);
                Roy22SoftSpokenCoreCotUtils.axpyFieldRow(t[b], expanded, alpha, num);
            }
        }

        // Build corrections u[b] := S[b] XOR (choices embedded as F-row). XOR over packed F-rows is
        // plain byte XOR because the F-elements are 2-bit-aligned and the field addition is XOR.
        byte[] choiceRow = Roy22SoftSpokenCoreCotUtils.embedChoicesAsFieldRow(choices);
        List<byte[]> uPayload = new ArrayList<>(Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS);
        for (int b = 0; b < Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS; b++) {
            byte[] u = new byte[fRowBytes];
            System.arraycopy(s[b], 0, u, 0, fRowBytes);
            BytesUtils.xori(u, choiceRow);
            uPayload.add(u);
        }
        DataPacketHeader uHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(),
            PtoStep.RECEIVER_SEND_SUBSPACE_VOLE_CORRECTIONS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(uHeader, uPayload));

        // Pack T into per-row κ-bit outputs (mirroring sender's packing).
        byte[][] rOut = new byte[num][CommonConstants.BLOCK_BYTE_LENGTH];
        int elementsPerByte = Byte.SIZE / Roy22SoftSpokenCoreCotUtils.FIELD_BITS;
        for (int b = 0; b < Roy22SoftSpokenCoreCotUtils.NUM_BLOCKS; b++) {
            int byteIdx = (CommonConstants.BLOCK_BYTE_LENGTH - 1) - (b / elementsPerByte);
            int shift = (b % elementsPerByte) * Roy22SoftSpokenCoreCotUtils.FIELD_BITS;
            for (int j = 0; j < num; j++) {
                int fEl = Roy22SoftSpokenCoreCotUtils.extractField(t[b], j);
                rOut[j][byteIdx] |= (byte) (fEl << shift);
            }
        }

        stopWatch.stop();
        long time = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 1, time);

        logPhaseInfo(PtoState.PTO_END);
        return CotReceiverOutput.create(choices, rOut);
    }
}
