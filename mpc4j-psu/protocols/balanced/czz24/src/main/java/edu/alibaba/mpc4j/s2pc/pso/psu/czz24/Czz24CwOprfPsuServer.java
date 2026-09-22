package edu.alibaba.mpc4j.s2pc.pso.psu.czz24;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.czz24.Czz24CwOprfMqRpmtServer;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.psu.common.OtBenchmarkMetrics;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.czz24.Czz24CwOprfPsuPtoDesc.PtoStep;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CZZ24-cwOPRF-PSU server.
 *
 * @author Yufei Wang
 * @date 2022/8/1
 */
public class Czz24CwOprfPsuServer extends AbstractPsuServer {
    private static final Logger OT_METRIC_LOGGER = LoggerFactory.getLogger(Czz24CwOprfPsuServer.class);
    /**
     * CZZ24-cwOPRF-mqRPMT
     */
    private final Czz24CwOprfMqRpmtServer czz24CwOprfMqRpmtServer;
    /**
     * core COT
     */
    private final CoreCotSender coreCotSender;

    public Czz24CwOprfPsuServer(Rpc serverRpc, Party clientParty, Czz24CwOprfPsuConfig config) {
        super(Czz24CwOprfPsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        czz24CwOprfMqRpmtServer = new Czz24CwOprfMqRpmtServer(serverRpc, clientParty, config.getCzz24CwOprfPsuConfig());
        addSubPto(czz24CwOprfMqRpmtServer);
        coreCotSender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        long initSendBaseline = OtBenchmarkMetrics.sendBytesBaseline(rpc);
        stopWatch.start();
        czz24CwOprfMqRpmtServer.init(maxServerElementSize, maxClientElementSize);
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
            OT_METRIC_LOGGER, "PKC_CheZhaZha24", initTime, mqInitMs, cotInitMs,
            OtBenchmarkMetrics.sendBytesDelta(rpc, initSendBaseline)
        );

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        long ptoSendBaseline = OtBenchmarkMetrics.sendBytesBaseline(rpc);
        stopWatch.start();
        ByteBuffer[] serverVector = czz24CwOprfMqRpmtServer.mqRpmt(serverElementSet, clientElementSize);
        stopWatch.stop();
        long mqRpmtTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, mqRpmtTime, "Server runs mqRPMT");

        stopWatch.start();
        int coreCotNum = serverVector.length;
        CotSenderOutput cotSenderOutput = coreCotSender.send(coreCotNum);
        stopWatch.stop();
        long cotOnlineMs = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        stopWatch.start();
        Prg encPrg = PrgFactory.createInstance(envType, elementByteLength);
        List<byte[]> encPayload = new ArrayList<byte[]>(serverElementSize);
        for (int index = 0; index < serverElementSize; index++) {
            byte[] ciphertext = encPrg.extendToBytes(cotSenderOutput.getR0(index));
            BytesUtils.xori(ciphertext, serverVector[index].array());
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
        logStepInfo(PtoState.PTO_STEP, 2, 2, encTime, "Server handles union");
        long ptoTotalMs = mqRpmtTime + cotOnlineMs + encTime;
        OtBenchmarkMetrics.logPto(
            OT_METRIC_LOGGER, "PKC_CheZhaZha24", ptoTotalMs, mqRpmtTime, cotOnlineMs, encTime, 0L,
            OtBenchmarkMetrics.sendBytesDelta(rpc, ptoSendBaseline)
        );

        logPhaseInfo(PtoState.PTO_END);
    }
}
