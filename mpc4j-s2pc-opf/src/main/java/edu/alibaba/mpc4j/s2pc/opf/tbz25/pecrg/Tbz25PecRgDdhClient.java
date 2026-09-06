package edu.alibaba.mpc4j.s2pc.opf.tbz25.pecrg;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteEccFactory;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteFullEcc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.alibaba.mpc4j.s2pc.opf.tbz25.pecrg.Tbz25PecRgDdhPtoDesc.PtoStep;

/**
 * TBZ25 DDH-based pECRG — client (paper R): inputs vector t (Fig. 13 notation d).
 */
public class Tbz25PecRgDdhClient extends AbstractTwoPartyPto {
    private final ByteFullEcc ecc;
    private int maxM;

    public Tbz25PecRgDdhClient(Rpc clientRpc, Party serverParty, Tbz25PecRgDdhConfig config) {
        super(Tbz25PecRgDdhPtoDesc.getInstance(), clientRpc, serverParty, config);
        ecc = ByteEccFactory.createFullInstance(envType);
    }

    public void init(int maxM) {
        setInitInput(maxM);
        logPhaseInfo(PtoState.INIT_BEGIN);
        logPhaseInfo(PtoState.INIT_END);
    }

    private void setInitInput(int maxM) {
        MathPreconditions.checkPositive("maxM", maxM);
        this.maxM = maxM;
        initState();
    }

    /**
     * @param tMessages receiver vector t, length m.
     * @return permuted masked points v (compressed), length m.
     */
    public byte[][] pecRg(byte[][] tMessages) throws MpcAbortException {
        checkInitialized();
        int m = tMessages.length;
        MathPreconditions.checkLessOrEqual("m", m, maxM);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        byte[] b = ecc.randomScalar(secureRandom);
        List<byte[]> tHat = IntStream.range(0, m)
            .mapToObj(i -> ecc.mul(ecc.hashToCurve(tMessages[i]), b))
            .collect(Collectors.toList());
        stopWatch.stop();
        long t0 = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, t0, "pECRG client t_hat");

        DataPacketHeader tHatHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_T_HAT.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(tHatHeader, tHat));

        DataPacketHeader sPrimeHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_PERMUTED_S_PRIME.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> permutedSPrime = rpc.receive(sPrimeHeader).getPayload();
        MathPreconditions.checkEqual("|pi(s')|", "m", permutedSPrime.size(), m);

        stopWatch.start();
        byte[][] vOut = IntStream.range(0, m)
            .mapToObj(i -> ecc.mul(permutedSPrime.get(i), b))
            .toArray(byte[][]::new);
        stopWatch.stop();
        long t1 = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, t1, "pECRG client v");

        logPhaseInfo(PtoState.PTO_END);
        return vOut;
    }
}
