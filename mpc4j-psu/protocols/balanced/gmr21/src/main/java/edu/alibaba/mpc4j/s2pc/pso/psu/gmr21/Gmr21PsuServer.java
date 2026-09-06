package edu.alibaba.mpc4j.s2pc.pso.psu.gmr21;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.gmr21.Gmr21MqRpmtServer;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.*;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.psu.common.OtBenchmarkMetrics;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractOoPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PKC_GMRSS21-PSU server.
 *
 * @author Weiran Liu
 * @date 2022/02/15
 */
public class Gmr21PsuServer extends AbstractOoPsuServer {
    private static final Logger OT_METRIC_LOGGER = LoggerFactory.getLogger(Gmr21PsuServer.class);
    /**
     * PKC_GMRSS21-mqRPMT
     */
    private final Gmr21MqRpmtServer gmr21MqRpmtServer;
    /**
     * core COT
     */
    private final CoreCotSender coreCotSender;

    public Gmr21PsuServer(Rpc serverRpc, Party clientParty, Gmr21PsuConfig config) {
        super(Gmr21PsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        gmr21MqRpmtServer = new Gmr21MqRpmtServer(serverRpc, clientParty, config.getGmr21MqRpmtConfig());
        addSubPto(gmr21MqRpmtServer);
        coreCotSender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        long initSendBaseline = OtBenchmarkMetrics.sendBytesBaseline(rpc);
        stopWatch.start();
        gmr21MqRpmtServer.init(maxServerElementSize, maxClientElementSize);
        stopWatch.stop();
        long mqInitMs = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        byte[] delta = BlockUtils.randomBlock(secureRandom);
        stopWatch.start();
        coreCotSender.init(delta);
        stopWatch.stop();
        long cotInitMs = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long initTime = mqInitMs + cotInitMs;
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);
        OtBenchmarkMetrics.logInit(
            OT_METRIC_LOGGER, "PKC_GMRSS21", initTime, mqInitMs, cotInitMs,
            OtBenchmarkMetrics.sendBytesDelta(rpc, initSendBaseline)
        );

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void preCompute(int serverElementSize, int clientElementSize, int elementByteLength) throws MpcAbortException {
        checkPrecomputeInput(serverElementSize, clientElementSize, elementByteLength);

        logPhaseInfo(PtoState.PTO_BEGIN, "Pre-computation");
        stopWatch.start();
        gmr21MqRpmtServer.preCompute(serverElementSize);
        stopWatch.stop();
        long precomputeTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 1, precomputeTime);

        logPhaseInfo(PtoState.PTO_END, "Pre-computation");
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        ByteBuffer[] serverVector = gmr21MqRpmtServer.mqRpmt(serverElementSet, clientElementSize);
        stopWatch.stop();
        long mqRpmtTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, mqRpmtTime);

        stopWatch.start();
        int coreCotNum = serverVector.length;
        CotSenderOutput cotSenderOutput = coreCotSender.send(coreCotNum);
        Prg encPrg = PrgFactory.createInstance(envType, elementByteLength);
        List<byte[]> encPayload = new ArrayList<byte[]>(coreCotNum);
        for (int index = 0; index < coreCotNum; index++) {
            byte[] ciphertext = encPrg.extendToBytes(cotSenderOutput.getR0(index));
            if (serverVector[index] == null) {
                BytesUtils.xori(ciphertext, botElementByteBuffer.array());
            } else {
                BytesUtils.xori(ciphertext, serverVector[index].array());
            }
            encPayload.add(ciphertext);
        }
        DataPacketHeader encHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ENC_ELEMENTS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(encHeader, encPayload));
        stopWatch.stop();
        long encTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, encTime);

        logPhaseInfo(PtoState.PTO_END);
    }
}
