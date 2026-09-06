package edu.alibaba.mpc4j.s2pc.pso.psu.tbz25;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.hashbin.object.HashBinEntry;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBin;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory;
import edu.alibaba.mpc4j.common.tool.network.PermutationNetworkUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.tbz25.balanced.Tbz25BalancedPnMcrgServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * USENIX_BinYujConYanYu25 balanced ePSU sender (paper S): cuckoo side, pnMCRG, XOR one-time pad transfer.
 */
public class Tbz25PsuServer extends AbstractPsuServer {
    /**
     * Leading flag byte in OTP payloads ({@code 0x01} = real cuckoo item, {@code 0x00} = dummy bin).
     * Matches the ePSU balanced reference: only non-membership bins decrypt to a valid flag.
     */
    private static final byte PAYLOAD_VALID_FLAG = 0x01;
    private static final byte PAYLOAD_INVALID_FLAG = 0x00;
    private static final int OTP_FLAG_BYTE_LENGTH = 1;

    private final Tbz25PsuConfig config;
    private final Tbz25BalancedPnMcrgServer pnMcrgServer;
    private final int cuckooHashNum;

    public Tbz25PsuServer(Rpc serverRpc, Party clientParty, Tbz25PsuConfig config) {
        super(Tbz25PsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        this.config = config;
        cuckooHashNum = config.getCuckooHashNum();
        pnMcrgServer = new Tbz25BalancedPnMcrgServer(serverRpc, clientParty, config.getPnMcrgConfig());
        addSubPto(pnMcrgServer);
    }

    @Override
    public void setParallel(boolean parallel) {
        super.setParallel(parallel);
        pnMcrgServer.setParallel(parallel);
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        int maxBinNum = CuckooHashBinFactory.getBinNum(config.getCuckooHashBinType(), maxServerElementSize);
        int maxOkvsPairs = Math.max(1, cuckooHashNum * maxClientElementSize);
        pnMcrgServer.init(maxBinNum, maxOkvsPairs);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength) throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        CuckooHashBin<ByteBuffer> cuckooHashBin = CuckooHashBinFactory.createEnforceNoStashCuckooHashBin(
            envType, config.getCuckooHashBinType(), serverElementSize, serverElementArrayList, secureRandom
        );
        cuckooHashBin.insertPaddingItems(botElementByteBuffer);
        sendOtherPartyPayload(
            PtoStep.CUCKOO_HASH_KEYS.ordinal(),
            Arrays.stream(cuckooHashBin.getHashKeys()).collect(Collectors.toList())
        );
        int binNum = CuckooHashBinFactory.getBinNum(config.getCuckooHashBinType(), serverElementSize);
        int[] pi = PermutationNetworkUtils.randomPermutation(binNum, secureRandom);
        byte[][] serverRowKeys = buildServerRowKeys(cuckooHashBin, binNum, elementByteLength);
        stopWatch.stop();
        long hashTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 3, hashTime, "cuckoo hash + row keys");

        stopWatch.start();
        byte[][] u = pnMcrgServer.execute(serverRowKeys, pi);
        stopWatch.stop();
        long pnMcrgTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 3, pnMcrgTime, "pnMCRG");

        stopWatch.start();
        byte[][] permutedPayload = buildPermutedPayload(cuckooHashBin, binNum, pi);
        int otpByteLength = otpPayloadByteLength();
        List<byte[]> otpPayload = new ArrayList<>(binNum);
        for (int i = 0; i < binNum; i++) {
            otpPayload.add(xorPadToPayloadLength(u[i], permutedPayload[i], otpByteLength));
        }
        sendOtherPartyEqualSizePayload(PtoStep.SEND_OTP.ordinal(), otpPayload);
        stopWatch.stop();
        long otpTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 3, otpTime, "OTP send");

        logPhaseInfo(PtoState.PTO_END);
    }

    private byte[][] buildServerRowKeys(CuckooHashBin<ByteBuffer> cuckooHashBin, int binNum, int elementByteLength) {
        byte[][] serverRowKeys = new byte[binNum][];
        for (int binIndex = 0; binIndex < binNum; binIndex++) {
            HashBinEntry<ByteBuffer> entry = cuckooHashBin.getHashBinEntry(binIndex);
            if (entry.getHashIndex() == HashBinEntry.DUMMY_ITEM_HASH_INDEX) {
                serverRowKeys[binIndex] = new byte[elementByteLength + 2 * Integer.BYTES];
                secureRandom.nextBytes(serverRowKeys[binIndex]);
            } else {
                serverRowKeys[binIndex] = encodeRowKey(entry.getItemByteArray(), entry.getHashIndex(), binIndex);
            }
        }
        return serverRowKeys;
    }

    private byte[][] buildPermutedPayload(CuckooHashBin<ByteBuffer> cuckooHashBin, int binNum, int[] pi) {
        int otpByteLength = otpPayloadByteLength();
        byte[][] slotPayload = new byte[binNum][otpByteLength];
        byte[] botBytes = botElementByteBuffer.array();
        for (int binIndex = 0; binIndex < binNum; binIndex++) {
            HashBinEntry<ByteBuffer> entry = cuckooHashBin.getHashBinEntry(binIndex);
            byte[] payload = slotPayload[binIndex];
            if (entry.getHashIndex() == HashBinEntry.DUMMY_ITEM_HASH_INDEX) {
                payload[0] = PAYLOAD_INVALID_FLAG;
                System.arraycopy(botBytes, 0, payload, OTP_FLAG_BYTE_LENGTH, elementByteLength);
            } else {
                payload[0] = PAYLOAD_VALID_FLAG;
                System.arraycopy(entry.getItemByteArray(), 0, payload, OTP_FLAG_BYTE_LENGTH, elementByteLength);
            }
        }
        return PermutationNetworkUtils.permutation(pi, slotPayload);
    }

    private int otpPayloadByteLength() {
        return elementByteLength + OTP_FLAG_BYTE_LENGTH;
    }

    private byte[] xorPadToPayloadLength(byte[] pad, byte[] payload, int payloadByteLength) {
        byte[] truncatedPad = new byte[payloadByteLength];
        System.arraycopy(pad, 0, truncatedPad, 0, Math.min(pad.length, payloadByteLength));
        return BytesUtils.xor(truncatedPad, payload);
    }

    private static byte[] encodeRowKey(byte[] itemBytes, int hashIndex, int binIndex) {
        byte[] out = new byte[itemBytes.length + 2 * Integer.BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(out);
        buffer.put(itemBytes);
        buffer.putInt(hashIndex);
        buffer.putInt(binIndex);
        return out;
    }
}
