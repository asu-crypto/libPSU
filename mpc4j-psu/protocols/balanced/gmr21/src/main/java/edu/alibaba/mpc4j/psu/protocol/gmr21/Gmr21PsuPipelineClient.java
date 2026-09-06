package edu.alibaba.mpc4j.psu.protocol.gmr21;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.psu.api.PsuUnionOutput;
import edu.alibaba.mpc4j.psu.api.plugin.UnionDeliveryInput;
import edu.alibaba.mpc4j.psu.core.union.CotXorUnionDelivery;
import edu.alibaba.mpc4j.psu.plugin.cot.CotXorUnionDeliveryPlugin;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.gmr21.Gmr21MqRpmtClient;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractOoPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuPtoDesc;

import java.nio.ByteBuffer;
import java.util.Set;

import static edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuPtoDesc.PtoStep;

/**
 * PKC_GMRSS21 balanced PSU composed from mqRPMT + shared COT XOR union delivery (reference implementation).
 */
public class Gmr21PsuPipelineClient extends AbstractOoPsuClient {
    private final Gmr21MqRpmtClient gmr21MqRpmtClient;
    private final CotXorUnionDeliveryPlugin unionPlugin;

    public Gmr21PsuPipelineClient(Rpc clientRpc, Party serverParty, Gmr21PsuConfig config) {
        super(Gmr21PsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        gmr21MqRpmtClient = new Gmr21MqRpmtClient(clientRpc, serverParty, config.getGmr21MqRpmtConfig());
        unionPlugin = new CotXorUnionDeliveryPlugin(clientRpc, serverParty, config.getCoreCotConfig(), envType, null);
        addSubPto(gmr21MqRpmtClient);
        addSubPto(unionPlugin.getCoreCotReceiver());
    }

    @Override
    public void preCompute(int clientElementSize, int serverElementSize, int elementByteLength) throws MpcAbortException {
        checkPrecomputeInput(clientElementSize, serverElementSize, elementByteLength);
        gmr21MqRpmtClient.preCompute(serverElementSize);
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        gmr21MqRpmtClient.init(maxClientElementSize, maxServerElementSize);
        unionPlugin.getCoreCotReceiver().init();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        boolean[] choices = gmr21MqRpmtClient.mqRpmt(clientElementSet, serverElementSize);
        CotReceiverOutput cotOut = unionPlugin.getCoreCotReceiver().receive(choices);

        DataPacketHeader encHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ENC_ELEMENTS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        var encPayload = rpc.receive(encHeader).getPayload();

        PsuUnionOutput unionOut = CotXorUnionDelivery.unionFromCotXor(
            envType, elementByteLength, botElementByteBuffer, cotOut,
            new UnionDeliveryInput(clientElementSet, serverElementSize, elementByteLength, choices, encPayload)
        );
        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(unionOut.getUnionSet(), unionOut.getPsica());
    }
}
