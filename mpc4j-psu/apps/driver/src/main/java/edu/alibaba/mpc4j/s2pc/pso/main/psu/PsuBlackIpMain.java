package edu.alibaba.mpc4j.s2pc.pso.main.psu;

import edu.alibaba.libpsu.LibPsu;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.main.AbstractMainTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * PSU_BLACK_IP main: IPv4 sets encoded as 16-byte domain-separated elements.
 */
public class PsuBlackIpMain extends AbstractMainTwoPartyPto {
    private static final Logger LOGGER = LoggerFactory.getLogger(PsuBlackIpMain.class);
    public static final String PTO_TYPE_NAME = "PSU_BLACK_IP";

    private final PsuConfig psuConfig;
    private final Set<ByteBuffer> serverElementSet;
    private final int serverSetSize;
    private final Set<ByteBuffer> clientElementSet;
    private final int clientSetSize;

    public PsuBlackIpMain(Properties properties, String ownName) throws IOException {
        super(properties, ownName);
        LOGGER.info("{} read PTO config", ownRpc.ownParty().getPartyName());
        String serverInputPath = PropertiesUtils.readString(properties, "psu_black_ip_server_input");
        LOGGER.info("Read server input file from: {}", serverInputPath);
        serverElementSet = PsuBlackIpConfigUtils.readBlackIpSet(serverInputPath);
        serverSetSize = serverElementSet.size();
        LOGGER.info("Server contains {} IPs", serverSetSize);
        String clientInputPath = PropertiesUtils.readString(properties, "psu_black_ip_client_input");
        LOGGER.info("Read client input file from: {}", clientInputPath);
        clientElementSet = PsuBlackIpConfigUtils.readBlackIpSet(clientInputPath);
        clientSetSize = clientElementSet.size();
        LOGGER.info("Client contains {} IPs", clientSetSize);
        LOGGER.info("{} read PSU config", ownRpc.ownParty().getPartyName());
        psuConfig = LibPsu.createPsuConfig(properties);
    }

    @Override
    public void runParty1(Rpc serverRpc, Party clientParty) throws IOException, MpcAbortException {
        try (FileWriter fileWriter = new FileWriter(resultPath(serverRpc));
             PrintWriter printWriter = new PrintWriter(fileWriter, true)) {
            printWriter.println(header());
            LOGGER.info("{} ready for run", serverRpc.ownParty().getPartyName());
            serverRpc.connect();
            int taskId = 0;
            LOGGER.info(
                "{}: serverSetSize = {}, clientSetSize = {}, parallel = {}",
                serverRpc.ownParty().getPartyName(), serverSetSize, clientSetSize, true
            );
            if (LibPsu.usesTwoSidedPublicFactory(psuConfig)) {
                PsuTwoSidedServer server = LibPsu.createTwoSidedServer(serverRpc, clientParty, psuConfig);
                runTwoSidedServer(server, taskId, printWriter);
            } else {
                PsuServer server = LibPsu.createServer(serverRpc, clientParty, psuConfig);
                runOneSidedServer(server, taskId, printWriter);
            }
            serverRpc.disconnect();
        }
    }

    @Override
    public void runParty2(Rpc clientRpc, Party serverParty) throws IOException, MpcAbortException {
        try (FileWriter fileWriter = new FileWriter(resultPath(clientRpc));
             PrintWriter printWriter = new PrintWriter(fileWriter, true)) {
            printWriter.println(header());
            LOGGER.info("{} ready for run", clientRpc.ownParty().getPartyName());
            clientRpc.connect();
            int taskId = 0;
            LOGGER.info(
                "{}: serverSetSize = {}, clientSetSize = {}, parallel = {}",
                clientRpc.ownParty().getPartyName(), serverSetSize, clientSetSize, true
            );
            if (LibPsu.usesTwoSidedPublicFactory(psuConfig)) {
                PsuTwoSidedClient client = LibPsu.createTwoSidedClient(clientRpc, serverParty, psuConfig);
                runTwoSidedClient(client, taskId, printWriter);
            } else {
                PsuClient client = LibPsu.createClient(clientRpc, serverParty, psuConfig);
                runOneSidedClient(client, taskId, printWriter);
            }
            clientRpc.disconnect();
        }
    }

    private void runOneSidedServer(PsuServer psuServer, int taskId, PrintWriter printWriter) throws MpcAbortException {
        psuServer.setTaskId(taskId);
        psuServer.setParallel(true);
        psuServer.getRpc().synchronize();
        psuServer.getRpc().reset();
        LOGGER.info("{} init", psuServer.ownParty().getPartyName());
        stopWatch.start();
        psuServer.init(serverSetSize, clientSetSize);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long initDataPacketNum = psuServer.getRpc().getSendDataPacketNum();
        long initPayloadByteLength = psuServer.getRpc().getPayloadByteLength();
        long initSendByteLength = psuServer.getRpc().getSendByteLength();
        psuServer.getRpc().synchronize();
        psuServer.getRpc().reset();
        LOGGER.info("{} execute", psuServer.ownParty().getPartyName());
        stopWatch.start();
        psuServer.psu(serverElementSet, clientSetSize, PsuBlackIpConfigUtils.IP_BYTE_LENGTH);
        stopWatch.stop();
        writeStats(printWriter, psuServer.ownParty().getPartyId(), psuServer.getParallel(),
            initTime, initDataPacketNum, initPayloadByteLength, initSendByteLength,
            stopWatch.getTime(TimeUnit.MILLISECONDS),
            psuServer.getRpc().getSendDataPacketNum(),
            psuServer.getRpc().getPayloadByteLength(),
            psuServer.getRpc().getSendByteLength());
        stopWatch.reset();
        psuServer.getRpc().synchronize();
        psuServer.getRpc().reset();
        psuServer.destroy();
        LOGGER.info("{} finish", psuServer.ownParty().getPartyName());
    }

    private void runTwoSidedServer(PsuTwoSidedServer psuServer, int taskId, PrintWriter printWriter)
        throws MpcAbortException {
        psuServer.setTaskId(taskId);
        psuServer.setParallel(true);
        psuServer.getRpc().synchronize();
        psuServer.getRpc().reset();
        LOGGER.info("{} init", psuServer.ownParty().getPartyName());
        stopWatch.start();
        psuServer.init(serverSetSize, clientSetSize);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long initDataPacketNum = psuServer.getRpc().getSendDataPacketNum();
        long initPayloadByteLength = psuServer.getRpc().getPayloadByteLength();
        long initSendByteLength = psuServer.getRpc().getSendByteLength();
        psuServer.getRpc().synchronize();
        psuServer.getRpc().reset();
        LOGGER.info("{} execute", psuServer.ownParty().getPartyName());
        stopWatch.start();
        var out = psuServer.psu(serverElementSet, clientSetSize, PsuBlackIpConfigUtils.IP_BYTE_LENGTH);
        stopWatch.stop();
        writeStats(printWriter, psuServer.ownParty().getPartyId(), psuServer.getParallel(),
            initTime, initDataPacketNum, initPayloadByteLength, initSendByteLength,
            stopWatch.getTime(TimeUnit.MILLISECONDS),
            psuServer.getRpc().getSendDataPacketNum(),
            psuServer.getRpc().getPayloadByteLength(),
            psuServer.getRpc().getSendByteLength());
        printWriter.println("#libpsu_union_size\t" + out.getUnion().size());
        stopWatch.reset();
        psuServer.getRpc().synchronize();
        psuServer.getRpc().reset();
        psuServer.destroy();
        LOGGER.info("{} finish", psuServer.ownParty().getPartyName());
    }

    private void runOneSidedClient(PsuClient psuClient, int taskId, PrintWriter printWriter) throws MpcAbortException {
        psuClient.setTaskId(taskId);
        psuClient.setParallel(true);
        psuClient.getRpc().synchronize();
        psuClient.getRpc().reset();
        LOGGER.info("{} init", psuClient.ownParty().getPartyName());
        stopWatch.start();
        psuClient.init(clientSetSize, serverSetSize);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long initDataPacketNum = psuClient.getRpc().getSendDataPacketNum();
        long initPayloadByteLength = psuClient.getRpc().getPayloadByteLength();
        long initSendByteLength = psuClient.getRpc().getSendByteLength();
        psuClient.getRpc().synchronize();
        psuClient.getRpc().reset();
        LOGGER.info("{} execute", psuClient.ownParty().getPartyName());
        stopWatch.start();
        psuClient.psu(clientElementSet, serverSetSize, PsuBlackIpConfigUtils.IP_BYTE_LENGTH);
        stopWatch.stop();
        writeStats(printWriter, psuClient.ownParty().getPartyId(), psuClient.getParallel(),
            initTime, initDataPacketNum, initPayloadByteLength, initSendByteLength,
            stopWatch.getTime(TimeUnit.MILLISECONDS),
            psuClient.getRpc().getSendDataPacketNum(),
            psuClient.getRpc().getPayloadByteLength(),
            psuClient.getRpc().getSendByteLength());
        stopWatch.reset();
        psuClient.getRpc().synchronize();
        psuClient.getRpc().reset();
        psuClient.destroy();
        LOGGER.info("{} finish", psuClient.ownParty().getPartyName());
    }

    private void runTwoSidedClient(PsuTwoSidedClient psuClient, int taskId, PrintWriter printWriter)
        throws MpcAbortException {
        psuClient.setTaskId(taskId);
        psuClient.setParallel(true);
        psuClient.getRpc().synchronize();
        psuClient.getRpc().reset();
        LOGGER.info("{} init", psuClient.ownParty().getPartyName());
        stopWatch.start();
        psuClient.init(clientSetSize, serverSetSize);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long initDataPacketNum = psuClient.getRpc().getSendDataPacketNum();
        long initPayloadByteLength = psuClient.getRpc().getPayloadByteLength();
        long initSendByteLength = psuClient.getRpc().getSendByteLength();
        psuClient.getRpc().synchronize();
        psuClient.getRpc().reset();
        LOGGER.info("{} execute", psuClient.ownParty().getPartyName());
        stopWatch.start();
        var out = psuClient.psu(clientElementSet, serverSetSize, PsuBlackIpConfigUtils.IP_BYTE_LENGTH);
        stopWatch.stop();
        writeStats(printWriter, psuClient.ownParty().getPartyId(), psuClient.getParallel(),
            initTime, initDataPacketNum, initPayloadByteLength, initSendByteLength,
            stopWatch.getTime(TimeUnit.MILLISECONDS),
            psuClient.getRpc().getSendDataPacketNum(),
            psuClient.getRpc().getPayloadByteLength(),
            psuClient.getRpc().getSendByteLength());
        printWriter.println("#libpsu_union_size\t" + out.getUnion().size());
        stopWatch.reset();
        psuClient.getRpc().synchronize();
        psuClient.getRpc().reset();
        psuClient.destroy();
        LOGGER.info("{} finish", psuClient.ownParty().getPartyName());
    }

    private String resultPath(Rpc rpc) {
        return PTO_TYPE_NAME
            + "_" + psuConfig.getPtoType().fileToken()
            + "_" + appendString
            + "_" + rpc.ownParty().getPartyId()
            + "_" + ForkJoinPool.getCommonPoolParallelism()
            + ".output";
    }

    private static String header() {
        return "Party ID\tServer Set Size\tClient Set Size\tIs Parallel\tThread Num"
            + "\tInit Time(ms)\tInit DataPacket Num\tInit Payload Bytes(B)\tInit Send Bytes(B)"
            + "\tPto  Time(ms)\tPto  DataPacket Num\tPto  Payload Bytes(B)\tPto  Send Bytes(B)";
    }

    private void writeStats(
        PrintWriter printWriter, int partyId, boolean parallel,
        long initTime, long initDataPacketNum, long initPayloadByteLength, long initSendByteLength,
        long ptoTime, long ptoDataPacketNum, long ptoPayloadByteLength, long ptoSendByteLength
    ) {
        printWriter.println(
            partyId
                + "\t" + serverSetSize
                + "\t" + clientSetSize
                + "\t" + parallel
                + "\t" + ForkJoinPool.getCommonPoolParallelism()
                + "\t" + initTime + "\t" + initDataPacketNum + "\t" + initPayloadByteLength + "\t" + initSendByteLength
                + "\t" + ptoTime + "\t" + ptoDataPacketNum + "\t" + ptoPayloadByteLength + "\t" + ptoSendByteLength
        );
    }
}
