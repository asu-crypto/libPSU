package edu.alibaba.mpc4j.s2pc.pso.psu.tbz25;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.crypto.prf.Prf;
import edu.alibaba.mpc4j.common.tool.crypto.prf.PrfFactory;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.tbz25.balanced.Tbz25BalancedPnMcrgClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * USENIX_BinYujConYanYu25 balanced ePSU receiver (paper R): simple hashing, pnMCRG, OTP decrypt, union output.
 */
public class Tbz25PsuClient extends AbstractPsuClient {
    private static final byte PAYLOAD_VALID_FLAG = 0x01;
    private static final int OTP_FLAG_BYTE_LENGTH = 1;

    private final Tbz25PsuConfig config;
    private final Tbz25BalancedPnMcrgClient pnMcrgClient;
    private final int cuckooHashNum;

    public Tbz25PsuClient(Rpc clientRpc, Party serverParty, Tbz25PsuConfig config) {
        super(Tbz25PsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        this.config = config;
        cuckooHashNum = config.getCuckooHashNum();
        pnMcrgClient = new Tbz25BalancedPnMcrgClient(clientRpc, serverParty, config.getPnMcrgConfig());
        addSubPto(pnMcrgClient);
    }

    @Override
    public void setParallel(boolean parallel) {
        super.setParallel(parallel);
        pnMcrgClient.setParallel(parallel);
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        int maxBinNum = CuckooHashBinFactory.getBinNum(config.getCuckooHashBinType(), maxServerElementSize);
        int maxOkvsPairs = Math.max(1, cuckooHashNum * maxClientElementSize);
        pnMcrgClient.init(maxBinNum, maxOkvsPairs);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        List<byte[]> hashKeyPayload = receiveOtherPartyPayload(PtoStep.CUCKOO_HASH_KEYS.ordinal());
        MpcAbortPreconditions.checkArgument(hashKeyPayload.size() == cuckooHashNum);
        Prf[] binHashes = new Prf[cuckooHashNum];
        for (int i = 0; i < cuckooHashNum; i++) {
            binHashes[i] = PrfFactory.createInstance(envType, Integer.BYTES);
            binHashes[i].setKey(hashKeyPayload.get(i));
        }
        int binNum = CuckooHashBinFactory.getBinNum(config.getCuckooHashBinType(), serverElementSize);
        stopWatch.stop();
        long hashTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 3, hashTime, "receive cuckoo hash keys");

        stopWatch.start();
        byte[][] v = pnMcrgClient.execute(
            binNum, clientElementArrayList, clientElementSize, binHashes, cuckooHashNum, binNum
        );
        stopWatch.stop();
        long pnMcrgTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 3, pnMcrgTime, "pnMCRG");

        stopWatch.start();
        int otpByteLength = elementByteLength + OTP_FLAG_BYTE_LENGTH;
        List<byte[]> otpPayload = receiveOtherPartyEqualSizePayload(
            PtoStep.SEND_OTP.ordinal(), binNum, otpByteLength
        );
        Set<ByteBuffer> union = new HashSet<>(clientElementSet);
        for (int i = 0; i < binNum; i++) {
            byte[] decrypted = xorPadToPayloadLength(v[i], otpPayload.get(i), otpByteLength);
            if (decrypted[0] == PAYLOAD_VALID_FLAG) {
                union.add(ByteBuffer.wrap(decrypted, OTP_FLAG_BYTE_LENGTH, elementByteLength));
            }
        }
        stopWatch.stop();
        long outTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 3, outTime, "OTP decrypt + union");

        logPhaseInfo(PtoState.PTO_END);
        long psiCaLong = (long) clientElementSize + (long) serverElementSize - union.size();
        MpcAbortPreconditions.checkArgument(
            psiCaLong >= 0 && psiCaLong <= Math.min(clientElementSize, serverElementSize),
            "invalid PSI-CA: |X|=" + clientElementSize + " |Y|=" + serverElementSize
                + " |U|=" + union.size() + " psiCa=" + psiCaLong
        );
        return new PsuClientOutput(union, Math.toIntExact(psiCaLong));
    }

    private byte[] xorPadToPayloadLength(byte[] pad, byte[] ciphertext, int payloadByteLength) {
        byte[] truncatedPad = new byte[payloadByteLength];
        System.arraycopy(pad, 0, truncatedPad, 0, Math.min(pad.length, payloadByteLength));
        return BytesUtils.xor(truncatedPad, ciphertext);
    }
}
