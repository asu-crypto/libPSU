package edu.alibaba.mpc4j.s2pc.pso.main.psi;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.main.AbstractMainTwoPartyPto;
import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiClient;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiFactory;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiServer;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * PSI benchmark driver (C_KisSon05 and future PSI protocols).
 */
public class PsiMain extends AbstractMainTwoPartyPto {
    private static final Logger LOGGER = LoggerFactory.getLogger(PsiMain.class);
    public static final String PTO_TYPE_NAME = "PSI";
    private static final int WARMUP_ELEMENT_BYTE_LENGTH = 16;
    private static final int WARMUP_SET_SIZE = 1 << 10;

    private final int elementByteLength;
    private final int setSizeNum;
    private final int[] serverSetSizes;
    private final int[] clientSetSizes;
    private final boolean parallel;
    private final PsiConfig psiConfig;
    private final boolean skipWarmup;
    private final boolean skipGc;

    public PsiMain(Properties properties, String ownName) {
        super(properties, ownName);
        elementByteLength = PropertiesUtils.readInt(properties, "element_byte_length");
        int[] serverLogSetSizes = PropertiesUtils.readLogIntArray(properties, "server_log_set_size");
        int[] clientLogSetSizes = PropertiesUtils.readLogIntArray(properties, "client_log_set_size");
        Preconditions.checkArgument(
            serverLogSetSizes.length == clientLogSetSizes.length,
            "# of server log_set_size = %s, # of client log_set_size = %s, they must be equal",
            serverLogSetSizes.length, clientLogSetSizes.length
        );
        setSizeNum = serverLogSetSizes.length;
        serverSetSizes = Arrays.stream(serverLogSetSizes).map(logSetSize -> 1 << logSetSize).toArray();
        clientSetSizes = Arrays.stream(clientLogSetSizes).map(logSetSize -> 1 << logSetSize).toArray();
        parallel = PropertiesUtils.readBoolean(properties, "parallel", false);
        psiConfig = PsiConfigUtils.createConfig(properties);
        skipWarmup = PropertiesUtils.readBoolean(properties, "skip_warmup", false);
        skipGc = PropertiesUtils.readBoolean(properties, "skip_gc", false);
    }

    private void runBenchmarkGc() {
        if (!skipGc) {
            System.gc();
        }
    }

    @Override
    public void runParty1(Rpc serverRpc, Party clientParty) throws IOException, MpcAbortException {
        if (!skipWarmup) {
            PsuBenchmarkUtils.generateBytesInputFiles(WARMUP_SET_SIZE, WARMUP_ELEMENT_BYTE_LENGTH);
        }
        for (int setSizeIndex = 0; setSizeIndex < setSizeNum; setSizeIndex++) {
            PsuBenchmarkUtils.generateBytesInputFiles(
                serverSetSizes[setSizeIndex], clientSetSizes[setSizeIndex], elementByteLength
            );
        }
        String filePath = filePathString + PTO_TYPE_NAME
            + "_" + psiConfig.getPtoType().fileToken()
            + "_" + appendString
            + "_" + elementByteLength * Byte.SIZE
            + "_" + serverRpc.ownParty().getPartyId()
            + "_" + ForkJoinPool.getCommonPoolParallelism()
            + ".output";
        PrintWriter printWriter = new PrintWriter(new FileWriter(filePath), true);
        printWriter.println(
            "Party ID\tServer Set Size\tClient Set Size\tIs Parallel\tThread Num"
                + "\tInit Time(ms)\tInit DataPacket Num\tInit Payload Bytes(B)\tInit Send Bytes(B)"
                + "\tPto  Time(ms)\tPto  DataPacket Num\tPto  Payload Bytes(B)\tPto  Send Bytes(B)"
        );
        serverRpc.connect();
        int taskId = 0;
        if (!skipWarmup) {
            warmupServer(serverRpc, clientParty, taskId, WARMUP_SET_SIZE, WARMUP_SET_SIZE);
            runBenchmarkGc();
            taskId++;
        }
        for (int setSizeIndex = 0; setSizeIndex < setSizeNum; setSizeIndex++) {
            int serverSetSize = serverSetSizes[setSizeIndex];
            int clientSetSize = clientSetSizes[setSizeIndex];
            Set<ByteBuffer> serverElementSet = readServerElementSet(serverSetSize, elementByteLength);
            runServer(serverRpc, clientParty, taskId, serverElementSet, clientSetSize, elementByteLength, printWriter);
            runBenchmarkGc();
            taskId++;
        }
        serverRpc.disconnect();
        printWriter.close();
    }

    @Override
    public void runParty2(Rpc clientRpc, Party serverParty) throws IOException, MpcAbortException {
        if (!skipWarmup) {
            PsuBenchmarkUtils.generateBytesInputFiles(WARMUP_SET_SIZE, WARMUP_ELEMENT_BYTE_LENGTH);
        }
        for (int setSizeIndex = 0; setSizeIndex < setSizeNum; setSizeIndex++) {
            PsuBenchmarkUtils.generateBytesInputFiles(
                serverSetSizes[setSizeIndex], clientSetSizes[setSizeIndex], elementByteLength
            );
        }
        String filePath = filePathString + PTO_TYPE_NAME
            + "_" + psiConfig.getPtoType().fileToken()
            + "_" + appendString
            + "_" + elementByteLength * Byte.SIZE
            + "_" + clientRpc.ownParty().getPartyId()
            + "_" + ForkJoinPool.getCommonPoolParallelism()
            + ".output";
        PrintWriter printWriter = new PrintWriter(new FileWriter(filePath), true);
        printWriter.println(
            "Party ID\tServer Set Size\tClient Set Size\tIs Parallel\tThread Num"
                + "\tInit Time(ms)\tInit DataPacket Num\tInit Payload Bytes(B)\tInit Send Bytes(B)"
                + "\tPto  Time(ms)\tPto  DataPacket Num\tPto  Payload Bytes(B)\tPto  Send Bytes(B)"
        );
        clientRpc.connect();
        int taskId = 0;
        if (!skipWarmup) {
            warmupClient(clientRpc, serverParty, taskId, WARMUP_SET_SIZE, WARMUP_SET_SIZE);
            runBenchmarkGc();
            taskId++;
        }
        for (int setSizeIndex = 0; setSizeIndex < setSizeNum; setSizeIndex++) {
            int serverSetSize = serverSetSizes[setSizeIndex];
            int clientSetSize = clientSetSizes[setSizeIndex];
            Set<ByteBuffer> clientElementSet = readClientElementSet(clientSetSize, elementByteLength);
            runClient(clientRpc, serverParty, taskId, clientElementSet, serverSetSize, elementByteLength, printWriter);
            runBenchmarkGc();
            taskId++;
        }
        clientRpc.disconnect();
        printWriter.close();
    }

    private Set<ByteBuffer> readServerElementSet(int setSize, int elementByteLength) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(
            Files.newInputStream(Paths.get(
                PsuBenchmarkUtils.getBytesFileName(PsuBenchmarkUtils.BYTES_SERVER_PREFIX, setSize, elementByteLength)
            )),
            CommonConstants.DEFAULT_CHARSET
        );
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        Set<ByteBuffer> set = bufferedReader.lines()
            .map(Hex::decode)
            .map(ByteBuffer::wrap)
            .collect(Collectors.toSet());
        bufferedReader.close();
        inputStreamReader.close();
        return set;
    }

    private Set<ByteBuffer> readClientElementSet(int setSize, int elementByteLength) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(
            Files.newInputStream(Paths.get(
                PsuBenchmarkUtils.getBytesFileName(PsuBenchmarkUtils.BYTES_CLIENT_PREFIX, setSize, elementByteLength)
            )),
            CommonConstants.DEFAULT_CHARSET
        );
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        Set<ByteBuffer> set = bufferedReader.lines()
            .map(Hex::decode)
            .map(ByteBuffer::wrap)
            .collect(Collectors.toSet());
        bufferedReader.close();
        inputStreamReader.close();
        return set;
    }

    private void warmupServer(Rpc serverRpc, Party clientParty, int taskId, int serverSetSize, int clientSetSize)
        throws IOException, MpcAbortException {
        Set<ByteBuffer> serverElementSet = readServerElementSet(serverSetSize, WARMUP_ELEMENT_BYTE_LENGTH);
        PsiServer psiServer = PsiFactory.createServer(serverRpc, clientParty, psiConfig);
        psiServer.setTaskId(taskId);
        psiServer.setParallel(parallel);
        psiServer.getRpc().synchronize();
        psiServer.init(serverSetSize, clientSetSize);
        psiServer.getRpc().synchronize();
        psiServer.psi(serverElementSet, clientSetSize, WARMUP_ELEMENT_BYTE_LENGTH);
        psiServer.getRpc().synchronize();
        psiServer.getRpc().reset();
        psiServer.destroy();
    }

    private void warmupClient(Rpc clientRpc, Party serverParty, int taskId, int serverSetSize, int clientSetSize)
        throws IOException, MpcAbortException {
        Set<ByteBuffer> clientElementSet = readClientElementSet(clientSetSize, WARMUP_ELEMENT_BYTE_LENGTH);
        PsiClient psiClient = PsiFactory.createClient(clientRpc, serverParty, psiConfig);
        psiClient.setTaskId(taskId);
        psiClient.setParallel(parallel);
        psiClient.getRpc().synchronize();
        psiClient.init(clientSetSize, serverSetSize);
        psiClient.getRpc().synchronize();
        psiClient.psi(clientElementSet, serverSetSize, WARMUP_ELEMENT_BYTE_LENGTH);
        psiClient.getRpc().synchronize();
        psiClient.getRpc().reset();
        psiClient.destroy();
    }

    private void runServer(
        Rpc serverRpc, Party clientParty, int taskId, Set<ByteBuffer> serverElementSet,
        int clientSetSize, int elementByteLength, PrintWriter printWriter
    ) throws MpcAbortException {
        int serverSetSize = serverElementSet.size();
        PsiServer psiServer = PsiFactory.createServer(serverRpc, clientParty, psiConfig);
        psiServer.setTaskId(taskId);
        psiServer.setParallel(parallel);
        psiServer.getRpc().synchronize();
        psiServer.getRpc().reset();
        stopWatch.start();
        psiServer.init(serverSetSize, clientSetSize);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long initDataPacketNum = psiServer.getRpc().getSendDataPacketNum();
        long initPayloadByteLength = psiServer.getRpc().getPayloadByteLength();
        long initSendByteLength = psiServer.getRpc().getSendByteLength();
        psiServer.getRpc().synchronize();
        psiServer.getRpc().reset();
        stopWatch.start();
        psiServer.psi(serverElementSet, clientSetSize, elementByteLength);
        stopWatch.stop();
        long ptoTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long ptoDataPacketNum = psiServer.getRpc().getSendDataPacketNum();
        long ptoPayloadByteLength = psiServer.getRpc().getPayloadByteLength();
        long ptoSendByteLength = psiServer.getRpc().getSendByteLength();
        printWriter.println(
            psiServer.ownParty().getPartyId()
                + "\t" + serverSetSize
                + "\t" + clientSetSize
                + "\t" + psiServer.getParallel()
                + "\t" + ForkJoinPool.getCommonPoolParallelism()
                + "\t" + initTime + "\t" + initDataPacketNum + "\t" + initPayloadByteLength + "\t" + initSendByteLength
                + "\t" + ptoTime + "\t" + ptoDataPacketNum + "\t" + ptoPayloadByteLength + "\t" + ptoSendByteLength
        );
        psiServer.getRpc().synchronize();
        psiServer.getRpc().reset();
        psiServer.destroy();
    }

    private void runClient(
        Rpc clientRpc, Party serverParty, int taskId, Set<ByteBuffer> clientElementSet,
        int serverSetSize, int elementByteLength, PrintWriter printWriter
    ) throws MpcAbortException {
        int clientSetSize = clientElementSet.size();
        PsiClient psiClient = PsiFactory.createClient(clientRpc, serverParty, psiConfig);
        psiClient.setTaskId(taskId);
        psiClient.setParallel(parallel);
        psiClient.getRpc().synchronize();
        psiClient.getRpc().reset();
        stopWatch.start();
        psiClient.init(clientSetSize, serverSetSize);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long initDataPacketNum = psiClient.getRpc().getSendDataPacketNum();
        long initPayloadByteLength = psiClient.getRpc().getPayloadByteLength();
        long initSendByteLength = psiClient.getRpc().getSendByteLength();
        psiClient.getRpc().synchronize();
        psiClient.getRpc().reset();
        stopWatch.start();
        psiClient.psi(clientElementSet, serverSetSize, elementByteLength);
        stopWatch.stop();
        long ptoTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long ptoDataPacketNum = psiClient.getRpc().getSendDataPacketNum();
        long ptoPayloadByteLength = psiClient.getRpc().getPayloadByteLength();
        long ptoSendByteLength = psiClient.getRpc().getSendByteLength();
        printWriter.println(
            psiClient.ownParty().getPartyId()
                + "\t" + clientSetSize
                + "\t" + serverSetSize
                + "\t" + psiClient.getParallel()
                + "\t" + ForkJoinPool.getCommonPoolParallelism()
                + "\t" + initTime + "\t" + initDataPacketNum + "\t" + initPayloadByteLength + "\t" + initSendByteLength
                + "\t" + ptoTime + "\t" + ptoDataPacketNum + "\t" + ptoPayloadByteLength + "\t" + ptoSendByteLength
        );
        psiClient.getRpc().synchronize();
        psiClient.getRpc().reset();
        psiClient.destroy();
    }
}
