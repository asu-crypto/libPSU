package edu.alibaba.mpc4j.s2pc.opf.tbz25.pecrg;

import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteEccFactory;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteFullEcc;
import edu.alibaba.mpc4j.common.tool.network.PermutationNetworkUtils;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static edu.alibaba.mpc4j.s2pc.opf.tbz25.pecrg.Tbz25PecRgDdhPtoDesc.PtoStep;

/**
 * TBZ25 DDH-based pECRG — server (paper S): inputs permutation π and vector s (Fig. 13 notation e).
 */
public class Tbz25PecRgDdhServer extends AbstractTwoPartyPto {
    private final ByteFullEcc ecc;
    private int maxM;

    public Tbz25PecRgDdhServer(Rpc serverRpc, Party clientParty, Tbz25PecRgDdhConfig config) {
        super(Tbz25PecRgDdhPtoDesc.getInstance(), serverRpc, clientParty, config);
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
     * Runs Figure 11 with sender vector {@code sMessages} (field elements / arbitrary bytes hashed to the curve).
     *
     * @param pi          permutation over [m].
     * @param sMessages   s-vector, length m.
     * @return permuted masked points u (compressed), length m.
     */
    public byte[][] pecRg(int[] pi, byte[][] sMessages) throws MpcAbortException {
        checkInitialized();
        int m = sMessages.length;
        MathPreconditions.checkEqual("pi.length", "m", pi.length, m);
        MathPreconditions.checkLessOrEqual("m", m, maxM);
        MpcAbortPreconditions.checkArgument(PermutationNetworkUtils.validPermutation(pi));
        logPhaseInfo(PtoState.PTO_BEGIN);

        DataPacketHeader tHatHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_T_HAT.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> tHatPayload = rpc.receive(tHatHeader).getPayload();
        MathPreconditions.checkEqual("|t_hat|", "m", tHatPayload.size(), m);

        stopWatch.start();
        byte[] a = ecc.randomScalar(secureRandom);
        byte[][] tPrime = IntStream.range(0, m)
            .mapToObj(i -> ecc.mul(tHatPayload.get(i), a))
            .toArray(byte[][]::new);
        byte[][] sPrime = Arrays.stream(sMessages)
            .map(si -> ecc.mul(ecc.hashToCurve(si), a))
            .toArray(byte[][]::new);
        byte[][] permutedSPrime = PermutationNetworkUtils.permutation(pi, sPrime);
        byte[][] uOut = PermutationNetworkUtils.permutation(pi, tPrime);
        stopWatch.stop();
        long t = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, t, "pECRG server exponent + permute");

        DataPacketHeader sPrimeHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_PERMUTED_S_PRIME.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(sPrimeHeader, Arrays.asList(permutedSPrime)));

        logPhaseInfo(PtoState.PTO_END);
        return uOut;
    }
}
