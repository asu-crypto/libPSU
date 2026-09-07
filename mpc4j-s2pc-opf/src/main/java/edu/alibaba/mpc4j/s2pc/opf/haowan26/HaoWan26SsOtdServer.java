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
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsOtdPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSender;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.RotSenderOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hao–Wan 2026 ssOTd server (paper S): silent/direct COT → ROT + masked element transfer (Figure 15).
 * <p>
 * Transfers {@code x_i} when reconstructed membership is zero. Completes with finish confirm after client ack.
 * </p>
 */
public class HaoWan26SsOtdServer extends AbstractTwoPartyPto {
    private final CotSender cotSender;
    private int maxN;

    public HaoWan26SsOtdServer(Rpc serverRpc, Party clientParty, HaoWan26SsOtdConfig config) {
        super(HaoWan26SsOtdPtoDesc.getInstance(), serverRpc, clientParty, config);
        cotSender = CotFactory.createSender(serverRpc, clientParty, config.getCotConfig());
        addSubPto(cotSender);
    }

    public void init(int maxN) throws MpcAbortException {
        MathPreconditions.checkPositive("maxN", maxN);
        this.maxN = maxN;
        logPhaseInfo(PtoState.INIT_BEGIN);
        byte[] delta = BlockUtils.randomBlock(secureRandom);
        cotSender.init(delta, maxN);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void setTaskId(int taskId) {
        super.setTaskId(taskId);
        cotSender.setTaskId(taskId);
    }

    /**
     * Transfers server elements to the client when the reconstructed membership bit is zero.
     *
     * @param serverElements    server elements in execution order.
     * @param membershipShare0  {@code [b_i]_0} from ssPMT.
     * @param elementByteLength element byte length.
     */
    public void execute(byte[][] serverElements, SquareZ2Vector membershipShare0, int elementByteLength)
        throws MpcAbortException {
        checkInitialized();
        int n = serverElements.length;
        MathPreconditions.checkPositiveInRangeClosed("n", n, maxN);
        MathPreconditions.checkPositive("elementByteLength", elementByteLength);
        MathPreconditions.checkEqual("n", "membershipShare0.num", n, membershipShare0.getNum());
        logPhaseInfo(PtoState.PTO_BEGIN);

        CotSenderOutput cotSenderOutput = cotSender.send(n);
        RotSenderOutput rotSenderOutput = new RotSenderOutput(envType, CrhfType.MMO, cotSenderOutput);
        Prg padPrg = PrgFactory.createInstance(envType, elementByteLength);
        List<byte[]> payload = new ArrayList<>(n * 2);
        for (int i = 0; i < n; i++) {
            MathPreconditions.checkEqual(
                "serverElements[i].length", "elementByteLength", serverElements[i].length, elementByteLength
            );
            boolean share0 = membershipShare0.getBitVector().get(i);
            byte[] padSeed = share0 ? rotSenderOutput.getR1(i) : rotSenderOutput.getR0(i);
            byte[] pad = padPrg.extendToBytes(padSeed);
            byte[] masked = BytesUtils.xor(pad, serverElements[i]);
            payload.add(masked);
            payload.add(new byte[]{share0 ? (byte) 1 : (byte) 0});
        }
        sendOtherPartyPayload(PtoStep.SERVER_SEND_TRANSFER.ordinal(), payload);

        List<byte[]> ack = receiveOtherPartyPayload(PtoStep.CLIENT_SEND_FINISH_ACK.ordinal());
        HaoWan26SsOtdPayloadChecks.checkFinishToken(ack);
        sendOtherPartyPayload(PtoStep.SERVER_SEND_FINISH_CONFIRM.ordinal(), Collections.singletonList(new byte[]{1}));

        logPhaseInfo(PtoState.PTO_END);
    }
}
