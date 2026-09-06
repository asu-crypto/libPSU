package edu.alibaba.mpc4j.s2pc.opf.tbz25.necrg;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;

/**
 * TBZ25 nECRG server (paper S): inputs vector s (same length as t).
 */
public class Tbz25NecRgServer extends AbstractTwoPartyPto {
    private final PeqtParty peqtSender;
    private final CoreCotSender coreCotSender;
    private int maxM;
    private int inputBitLength;

    public Tbz25NecRgServer(Rpc serverRpc, Party clientParty, Tbz25NecRgConfig config) {
        super(Tbz25NecRgPtoDesc.getInstance(), serverRpc, clientParty, config);
        peqtSender = PeqtFactory.createSender(serverRpc, clientParty, config.getPeqtConfig());
        addSubPto(peqtSender);
        coreCotSender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
    }

    public void init(int maxM, int inputBitLength) throws MpcAbortException {
        MathPreconditions.checkPositive("maxM", maxM);
        MathPreconditions.checkPositive("inputBitLength", inputBitLength);
        this.maxM = maxM;
        this.inputBitLength = inputBitLength;
        logPhaseInfo(PtoState.INIT_BEGIN);
        peqtSender.init(inputBitLength, maxM);
        byte[] delta = BlockUtils.randomBlock(secureRandom);
        coreCotSender.init(delta);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Figure 16: outputs u where u_i = r_{i,(a_i⊕1)} with a from ssPEQT.
     */
    public byte[][] necrg(byte[][] sInputs) throws MpcAbortException {
        checkInitialized();
        int m = sInputs.length;
        MathPreconditions.checkLessOrEqual("m", m, maxM);
        logPhaseInfo(PtoState.PTO_BEGIN);
        SquareZ2Vector z0 = peqtSender.peqt(inputBitLength, sInputs);
        CotSenderOutput cotOut = coreCotSender.send(m);
        byte[][] u = new byte[m][];
        for (int i = 0; i < m; i++) {
            boolean a = z0.getBitVector().get(i);
            u[i] = a ? cotOut.getR0(i) : cotOut.getR1(i);
        }
        logPhaseInfo(PtoState.PTO_END);
        return u;
    }
}
