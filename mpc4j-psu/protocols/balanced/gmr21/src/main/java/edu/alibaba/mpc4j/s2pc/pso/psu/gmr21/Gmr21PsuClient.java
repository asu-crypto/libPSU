package edu.alibaba.mpc4j.s2pc.pso.psu.gmr21;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.gmr21.Gmr21MqRpmtClient;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.*;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.psu.api.PsuUnionOutput;
import edu.alibaba.mpc4j.psu.api.plugin.UnionDeliveryInput;
import edu.alibaba.mpc4j.psu.core.union.CotXorUnionDelivery;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractOoPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
/**
 * PKC_GMRSS21-PSU client.
 *
 * @author Weiran Liu
 * @date 2022/02/15
 */
public class Gmr21PsuClient extends AbstractOoPsuClient {
    /**
     * PKC_GMRSS21-mqRPMT server
     */
    private final Gmr21MqRpmtClient gmr21MqRpmtClient;
    /**
     * core COT
     */
    private final CoreCotReceiver coreCotReceiver;

    public Gmr21PsuClient(Rpc clientRpc, Party serverParty, Gmr21PsuConfig config) {
        super(Gmr21PsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        gmr21MqRpmtClient = new Gmr21MqRpmtClient(clientRpc, serverParty, config.getGmr21MqRpmtConfig());
        addSubPto(gmr21MqRpmtClient);
        coreCotReceiver = CoreCotFactory.createReceiver(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(coreCotReceiver);
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        gmr21MqRpmtClient.init(maxClientElementSize, maxServerElementSize);
        coreCotReceiver.init();
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void preCompute(int clientElementSize, int serverElementSize, int elementByteLength) throws MpcAbortException {
        checkPrecomputeInput(clientElementSize, serverElementSize, elementByteLength);

        logPhaseInfo(PtoState.PTO_BEGIN, "Pre-computation");
        stopWatch.start();
        gmr21MqRpmtClient.preCompute(serverElementSize);
        stopWatch.stop();
        long precomputeTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 1, precomputeTime);

        logPhaseInfo(PtoState.PTO_END, "Pre-computation");
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        boolean[] choices = gmr21MqRpmtClient.mqRpmt(clientElementSet, serverElementSize);
        stopWatch.stop();
        long mqRpmtTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, mqRpmtTime);

        stopWatch.start();
        int coreCotNum = choices.length;
        CotReceiverOutput cotReceiverOutput = coreCotReceiver.receive(choices);
        DataPacketHeader encHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ENC_ELEMENTS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> encPayload = rpc.receive(encHeader).getPayload();
        MpcAbortPreconditions.checkArgument(encPayload.size() == coreCotNum);
        PsuUnionOutput unionOut = CotXorUnionDelivery.unionFromCotXor(
            envType,
            elementByteLength,
            cotReceiverOutput,
            new UnionDeliveryInput(clientElementSet, serverElementSize, elementByteLength, choices, encPayload)
        );
        Set<ByteBuffer> union = unionOut.getUnionSet();
        int psica = unionOut.getPsica();
        stopWatch.stop();
        long unionTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, unionTime);

        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(union, psica);
    }
}
