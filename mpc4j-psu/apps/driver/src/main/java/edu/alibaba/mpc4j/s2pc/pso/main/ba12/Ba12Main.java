package edu.alibaba.mpc4j.s2pc.pso.main.ba12;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.main.AbstractMainTwoPartyPto;
import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.ba12.Ba12Config;
import edu.alibaba.mpc4j.s2pc.ba12.Ba12Operation;
import edu.alibaba.mpc4j.s2pc.ba12.Ba12SetOpsParty;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12Share;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12Stats;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Fair-benchmark driver for BA12 secret-shared set union (Blanton–Aguiar).
 * <p>
 * Uses the same I/O and output format as {@link edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuMain}, but runs
 * {@link Ba12Operation#BA12_UNION} over XOR-shared integers (not DH/OPRF PSU).
 * Output files: {@code PSU_BA12_<append>_<element_bits>_<party>_<threads>.output}.
 */
public class Ba12Main extends AbstractMainTwoPartyPto {
  private static final Logger LOGGER = LoggerFactory.getLogger(Ba12Main.class);

  public static final String PTO_TYPE_NAME = "BA12";
  /** Summarizer / fair-bench family prefix (see {@code scripts/summarize_psu_fair_outputs.py}). */
  public static final String OUTPUT_FAMILY = "PSU";

  private static final int WARMUP_ELEMENT_BYTE_LENGTH = 16;
  private static final int WARMUP_LOG_SET_SIZE = 5;

  private final String ba12PtoName;
  private final Ba12Operation operation;
  private final int elementByteLength;
  private final int ell;
  private final int setSizeNum;
  private final int[] serverSetSizes;
  private final int[] clientSetSizes;
  private final boolean parallel;
  private final boolean skipWarmup;
  private final boolean skipGc;
  private final boolean logBa12Stats;
  private final Ba12Config ba12Config;

  public Ba12Main(Properties properties, String ownName) {
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
    serverSetSizes = Arrays.stream(serverLogSetSizes).map(s -> 1 << s).toArray();
    clientSetSizes = Arrays.stream(clientLogSetSizes).map(s -> 1 << s).toArray();
    parallel = PropertiesUtils.readBoolean(properties, "parallel", false);
    skipWarmup = PropertiesUtils.readBoolean(properties, "skip_warmup", false);
    skipGc = PropertiesUtils.readBoolean(properties, "skip_gc", false);
    logBa12Stats = PropertiesUtils.readBoolean(properties, "ba12_log_stats", false);
    ba12PtoName = Ba12ConfigUtils.readBa12PtoName(properties);
    operation = Ba12ConfigUtils.readOperation(properties);
    ba12Config = Ba12ConfigUtils.createConfig(properties, elementByteLength);
    ell = ba12Config.getEll();
  }

  private void runBenchmarkGc() {
    if (!skipGc) {
      System.gc();
    }
  }

  private String outputFilePath(Rpc rpc) {
    return filePathString + OUTPUT_FAMILY
      + "_" + ba12PtoName
      + "_" + appendString
      + "_" + ell
      + "_" + rpc.ownParty().getPartyId()
      + "_" + ForkJoinPool.getCommonPoolParallelism()
      + ".output";
  }

  @Override
  public void runParty1(Rpc serverRpc, Party clientParty) throws IOException, MpcAbortException {
    if (!skipWarmup) {
      PsuBenchmarkUtils.generateBytesInputFiles(1 << WARMUP_LOG_SET_SIZE, WARMUP_ELEMENT_BYTE_LENGTH);
    }
    for (int i = 0; i < setSizeNum; i++) {
      PsuBenchmarkUtils.generateBytesInputFiles(serverSetSizes[i], clientSetSizes[i], elementByteLength);
    }
    PrintWriter printWriter = new PrintWriter(new FileWriter(outputFilePath(serverRpc)), true);
    printHeader(printWriter);
    serverRpc.connect();
    int taskId = 0;
    if (!skipWarmup) {
      runServerOnce(serverRpc, clientParty, taskId, 1 << WARMUP_LOG_SET_SIZE, 1 << WARMUP_LOG_SET_SIZE, null);
      runBenchmarkGc();
      taskId++;
    }
    for (int i = 0; i < setSizeNum; i++) {
      runServerOnce(
        serverRpc, clientParty, taskId, serverSetSizes[i], clientSetSizes[i], printWriter
      );
      runBenchmarkGc();
      taskId++;
    }
    serverRpc.disconnect();
    printWriter.close();
  }

  @Override
  public void runParty2(Rpc clientRpc, Party serverParty) throws IOException, MpcAbortException {
    if (!skipWarmup) {
      PsuBenchmarkUtils.generateBytesInputFiles(1 << WARMUP_LOG_SET_SIZE, WARMUP_ELEMENT_BYTE_LENGTH);
    }
    for (int i = 0; i < setSizeNum; i++) {
      PsuBenchmarkUtils.generateBytesInputFiles(serverSetSizes[i], clientSetSizes[i], elementByteLength);
    }
    PrintWriter printWriter = new PrintWriter(new FileWriter(outputFilePath(clientRpc)), true);
    printHeader(printWriter);
    clientRpc.connect();
    int taskId = 0;
    if (!skipWarmup) {
      runClientOnce(clientRpc, serverParty, taskId, 1 << WARMUP_LOG_SET_SIZE, 1 << WARMUP_LOG_SET_SIZE, null);
      runBenchmarkGc();
      taskId++;
    }
    for (int i = 0; i < setSizeNum; i++) {
      runClientOnce(
        clientRpc, serverParty, taskId, serverSetSizes[i], clientSetSizes[i], printWriter
      );
      runBenchmarkGc();
      taskId++;
    }
    clientRpc.disconnect();
    printWriter.close();
  }

  private static void printHeader(PrintWriter printWriter) {
    printWriter.println(
      "Party ID\tServer Set Size\tClient Set Size\tIs Parallel\tThread Num"
        + "\tInit Time(ms)\tInit DataPacket Num\tInit Payload Bytes(B)\tInit Send Bytes(B)"
        + "\tPto  Time(ms)\tPto  DataPacket Num\tPto  Payload Bytes(B)\tPto  Send Bytes(B)"
    );
  }

  private void runServerOnce(
    Rpc serverRpc, Party clientParty, int taskId, int serverSetSize, int clientSetSize, PrintWriter printWriter
  ) throws IOException, MpcAbortException {
    long[] serverElements = readElementArray(serverSetSize, elementByteLength, ell, true);
    Ba12SetOpsParty party = new Ba12SetOpsParty(serverRpc, clientParty, ba12Config);
    party.setTaskId(taskId);
    party.setParallel(parallel);
    party.getRpc().synchronize();
    party.getRpc().reset();
    stopWatch.start();
    party.init();
    stopWatch.stop();
    long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
    stopWatch.reset();
    long initPackets = party.getRpc().getSendDataPacketNum();
    long initPayload = party.getRpc().getPayloadByteLength();
    long initSend = party.getRpc().getSendByteLength();
    party.getRpc().synchronize();
    party.getRpc().reset();
    stopWatch.start();
    Ba12Share[] a = party.inputOwn(serverElements);
    Ba12Share[] b = party.inputOther(clientSetSize);
    party.runBinary(operation, a, b);
    stopWatch.stop();
    long ptoTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
    stopWatch.reset();
    long ptoPackets = party.getRpc().getSendDataPacketNum();
    long ptoPayload = party.getRpc().getPayloadByteLength();
    long ptoSend = party.getRpc().getSendByteLength();
    logStatsIfEnabled(party, serverSetSize, clientSetSize);
    party.getRpc().synchronize();
    party.destroy();
    if (printWriter != null) {
      printWriter.println(
        serverRpc.ownParty().getPartyId() + "\t" + serverSetSize + "\t" + clientSetSize + "\t" + parallel
          + "\t" + ForkJoinPool.getCommonPoolParallelism()
          + "\t" + initTime + "\t" + initPackets + "\t" + initPayload + "\t" + initSend
          + "\t" + ptoTime + "\t" + ptoPackets + "\t" + ptoPayload + "\t" + ptoSend
      );
    }
  }

  private void runClientOnce(
    Rpc clientRpc, Party serverParty, int taskId, int serverSetSize, int clientSetSize, PrintWriter printWriter
  ) throws IOException, MpcAbortException {
    long[] clientElements = readElementArray(clientSetSize, elementByteLength, ell, false);
    Ba12SetOpsParty party = new Ba12SetOpsParty(clientRpc, serverParty, ba12Config);
    party.setTaskId(taskId);
    party.setParallel(parallel);
    party.getRpc().synchronize();
    party.getRpc().reset();
    stopWatch.start();
    party.init();
    stopWatch.stop();
    long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
    stopWatch.reset();
    long initPackets = party.getRpc().getSendDataPacketNum();
    long initPayload = party.getRpc().getPayloadByteLength();
    long initSend = party.getRpc().getSendByteLength();
    party.getRpc().synchronize();
    party.getRpc().reset();
    stopWatch.start();
    Ba12Share[] b = party.inputOwn(clientElements);
    Ba12Share[] a = party.inputOther(serverSetSize);
    party.runBinary(operation, a, b);
    stopWatch.stop();
    long ptoTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
    stopWatch.reset();
    long ptoPackets = party.getRpc().getSendDataPacketNum();
    long ptoPayload = party.getRpc().getPayloadByteLength();
    long ptoSend = party.getRpc().getSendByteLength();
    logStatsIfEnabled(party, serverSetSize, clientSetSize);
    party.getRpc().synchronize();
    party.destroy();
    if (printWriter != null) {
      printWriter.println(
        clientRpc.ownParty().getPartyId() + "\t" + serverSetSize + "\t" + clientSetSize + "\t" + parallel
          + "\t" + ForkJoinPool.getCommonPoolParallelism()
          + "\t" + initTime + "\t" + initPackets + "\t" + initPayload + "\t" + initSend
          + "\t" + ptoTime + "\t" + ptoPackets + "\t" + ptoPayload + "\t" + ptoSend
      );
    }
  }

  private void logStatsIfEnabled(Ba12SetOpsParty party, int serverSetSize, int clientSetSize) {
    if (!logBa12Stats) {
      return;
    }
    Ba12Stats stats = party.getEngine().getStats();
    LOGGER.info(
      "BA12 stats ({}): m1={}, m2={}, ell={}, eq={}, ge={}, mul={}, open={}",
      operation.name(), serverSetSize, clientSetSize, ell,
      stats.getEqCount(), stats.getGeCount(), stats.getMulCount(), stats.getOpenCount()
    );
  }

  static long[] readElementArray(int setSize, int elementByteLength, int ell, boolean server) throws IOException {
    String path = PsuBenchmarkUtils.getBytesFileName(
      server ? PsuBenchmarkUtils.BYTES_SERVER_PREFIX : PsuBenchmarkUtils.BYTES_CLIENT_PREFIX,
      setSize,
      elementByteLength
    );
    try (
      BufferedReader reader = new BufferedReader(new InputStreamReader(
        Files.newInputStream(Paths.get(path)), CommonConstants.DEFAULT_CHARSET
      ))
    ) {
      List<Long> values = new ArrayList<>(setSize);
      reader.lines().forEach(line -> values.add(encodeElement(Hex.decode(line), elementByteLength, ell)));
      Preconditions.checkArgument(values.size() == setSize, "expected %s lines in %s, got %s", setSize, path, values.size());
      return values.stream().mapToLong(Long::longValue).toArray();
    }
  }

  static long encodeElement(byte[] bytes, int byteLen, int ell) {
    int useLen = Math.min(byteLen, bytes.length);
    long v = 0L;
    for (int i = 0; i < useLen; i++) {
      v = (v << 8) | (bytes[i] & 0xffL);
    }
    long max = (1L << ell) - 1;
    v = v & max;
    if (v == 0) {
      v = 1L;
    }
    return v;
  }
}
