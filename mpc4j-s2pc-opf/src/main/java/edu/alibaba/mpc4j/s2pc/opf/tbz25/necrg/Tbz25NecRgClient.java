package edu.alibaba.mpc4j.s2pc.opf.tbz25.necrg;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;

/**
 * TBZ25 nECRG client (paper R): inputs vector t.
 */
public class Tbz25NecRgClient extends AbstractTwoPartyPto {
    private final PeqtParty peqtReceiver;
    private final CoreCotReceiver coreCotReceiver;
    private int maxM;
    private int inputBitLength;

    public Tbz25NecRgClient(Rpc clientRpc, Party serverParty, Tbz25NecRgConfig config) {
        super(Tbz25NecRgPtoDesc.getInstance(), clientRpc, serverParty, config);
        peqtReceiver = PeqtFactory.createReceiver(clientRpc, serverParty, config.getPeqtConfig());
        addSubPto(peqtReceiver);
        coreCotReceiver = CoreCotFactory.createReceiver(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(coreCotReceiver);
    }

    public void init(int maxM, int inputBitLength) throws MpcAbortException {
        MathPreconditions.checkPositive("maxM", maxM);
        MathPreconditions.checkPositive("inputBitLength", inputBitLength);
        this.maxM = maxM;
        this.inputBitLength = inputBitLength;
        logPhaseInfo(PtoState.INIT_BEGIN);
        peqtReceiver.init(inputBitLength, maxM);
        coreCotReceiver.init();
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Figure 16: outputs v where v_i = r_{i,b_i} and b is adjusted from ssPEQT so that a⊕b=0 iff s_i=t_i.
     */
    public byte[][] necrg(byte[][] tInputs) throws MpcAbortException {
        checkInitialized();
        int m = tInputs.length;
        MathPreconditions.checkLessOrEqual("m", m, maxM);
        logPhaseInfo(PtoState.PTO_BEGIN);
        SquareZ2Vector z1 = peqtReceiver.peqt(inputBitLength, tInputs);
        boolean[] choices = new boolean[m];
        for (int i = 0; i < m; i++) {
            choices[i] = z1.getBitVector().get(i) ^ true;
        }
        CotReceiverOutput cotOut = coreCotReceiver.receive(choices);
        byte[][] v = new byte[m][];
        for (int i = 0; i < m; i++) {
            v[i] = cotOut.getRb(i);
        }
        logPhaseInfo(PtoState.PTO_END);
        return v;
    }
}
