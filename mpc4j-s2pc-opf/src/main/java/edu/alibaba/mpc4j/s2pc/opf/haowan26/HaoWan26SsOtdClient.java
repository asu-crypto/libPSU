package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.crhf.CrhfFactory.CrhfType;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsOtdPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.RotReceiverOutput;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

/**
 * Hao–Wan 2026 ssOTd client (paper R): silent/direct COT → ROT + conditional decrypt (Figure 15).
 * <p>
 * Reveals {@code x_i} exactly when {@code [b_i]_0 XOR [b_i]_1 == 0}. Completes with a finish ack / confirm barrier.
 * </p>
 */
public class HaoWan26SsOtdClient extends AbstractTwoPartyPto {
    private final CotReceiver cotReceiver;
    private int maxN;

    public HaoWan26SsOtdClient(Rpc clientRpc, Party serverParty, HaoWan26SsOtdConfig config) {
        super(HaoWan26SsOtdPtoDesc.getInstance(), clientRpc, serverParty, config);
        cotReceiver = CotFactory.createReceiver(clientRpc, serverParty, config.getCotConfig());
        addSubPto(cotReceiver);
    }

    public void init(int maxN) throws MpcAbortException {
        MathPreconditions.checkPositive("maxN", maxN);
        this.maxN = maxN;
        logPhaseInfo(PtoState.INIT_BEGIN);
        cotReceiver.init(maxN);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void setTaskId(int taskId) {
        super.setTaskId(taskId);
        cotReceiver.setTaskId(taskId);
    }

    /**
     * Receives server elements for indices where membership is zero.
     *
     * @param membershipShare1  {@code [b_i]_1} from ssPMT.
     * @param elementByteLength element byte length.
     * @return {@code z_i}, or {@code null} when the server element is already in the client set.
     */
    public ByteBuffer[] execute(SquareZ2Vector membershipShare1, int elementByteLength) throws MpcAbortException {
        checkInitialized();
        int n = membershipShare1.getNum();
        MathPreconditions.checkPositiveInRangeClosed("n", n, maxN);
        MathPreconditions.checkPositive("elementByteLength", elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        boolean[] choices = new boolean[n];
        for (int i = 0; i < n; i++) {
            choices[i] = membershipShare1.getBitVector().get(i);
        }
        CotReceiverOutput cotReceiverOutput = cotReceiver.receive(choices);
        RotReceiverOutput rotReceiverOutput = new RotReceiverOutput(envType, CrhfType.MMO, cotReceiverOutput);

        List<byte[]> payload = receiveOtherPartyPayload(PtoStep.SERVER_SEND_TRANSFER.ordinal());
        HaoWan26SsOtdPayloadChecks.checkTransferPayload(payload, n, elementByteLength);

        Prg padPrg = PrgFactory.createInstance(envType, elementByteLength);
        ByteBuffer[] outputs = new ByteBuffer[n];
        for (int i = 0; i < n; i++) {
            byte[] masked = payload.get(2 * i);
            boolean share0 = payload.get(2 * i + 1)[0] == 1;
            boolean share1 = choices[i];
            boolean membership = share0 ^ share1;
            if (membership) {
                outputs[i] = null;
            } else {
                byte[] pad = padPrg.extendToBytes(rotReceiverOutput.getRb(i));
                byte[] element = BytesUtils.xor(pad, masked);
                outputs[i] = ByteBuffer.wrap(element);
            }
        }

        // Completion barrier: ack decrypt, wait for server confirm before returning.
        sendOtherPartyPayload(PtoStep.CLIENT_SEND_FINISH_ACK.ordinal(), Collections.singletonList(new byte[]{1}));
        List<byte[]> confirm = receiveOtherPartyPayload(PtoStep.SERVER_SEND_FINISH_CONFIRM.ordinal());
        HaoWan26SsOtdPayloadChecks.checkFinishToken(confirm);

        logPhaseInfo(PtoState.PTO_END);
        return outputs;
    }
}
