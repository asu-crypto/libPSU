package edu.alibaba.mpc4j.s2pc.pso.psu.dc17;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.crypto.phe.PheEngine;
import edu.alibaba.mpc4j.crypto.phe.PheFactory;
import edu.alibaba.mpc4j.crypto.phe.params.PheKeyGenParams;
import edu.alibaba.mpc4j.crypto.phe.params.PhePrivateKey;
import edu.alibaba.mpc4j.crypto.phe.params.PhePublicKey;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuPtoDesc.PtoStep;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
/**
 * ACISP_DavCid17 EIBF-based PSU client (output party).
 */
public class Dc17PsuClient extends AbstractPsuClient implements PsuClient {
    private final Dc17PsuConfig config;
    private final PheEngine pheEngine;
    private PhePrivateKey sk;
    private PhePublicKey pk;

    private int bfBinNum;
    private int bfHashNum;
    private byte[][] bfHashSeeds;
    private Dc17BloomFilterHash bfHash;

    public Dc17PsuClient(Rpc clientRpc, Party serverParty, Dc17PsuConfig config) {
        super(Dc17PsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        this.config = config;
        pheEngine = PheFactory.createInstance(config.getPheType(), secureRandom);
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();

        // keygen
        PheKeyGenParams keyGenParams = new PheKeyGenParams(config.getPheSecLevel(), false, 64);
        sk = pheEngine.keyGen(keyGenParams);
        pk = sk.getPublicKey();

        // bloom filter params based on max client size (|S1| = n)
        bfHashNum = Dc17PsuUtils.optimalK(config.getEpsilon());
        bfBinNum = Dc17PsuUtils.optimalB(maxClientElementSize, bfHashNum);
        bfHashSeeds = new byte[bfHashNum][];
        for (int i = 0; i < bfHashNum; i++) {
            bfHashSeeds[i] = new byte[16];
            secureRandom.nextBytes(bfHashSeeds[i]);
        }
        bfHash = new Dc17BloomFilterHash(bfBinNum, bfHashSeeds);

        stopWatch.stop();
        logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        sendSetup();
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 3, stopWatch.getTime(TimeUnit.MILLISECONDS), "send setup");
        stopWatch.reset();

        stopWatch.start();
        List<byte[]> eibfPayload = buildEncryptedInvertedBloomFilter();
        DataPacketHeader eibfHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_EIBF.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(eibfHeader, eibfPayload));
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 2, 3, stopWatch.getTime(TimeUnit.MILLISECONDS), "send EIBF");
        stopWatch.reset();

        stopWatch.start();
        DataPacketHeader pairsHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_PAIRS.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> pairsPayload = rpc.receive(pairsHeader).getPayload();
        MpcAbortPreconditions.checkArgument(pairsPayload.size() == 2 * serverElementSize);
        Set<ByteBuffer> union = new HashSet<ByteBuffer>(clientElementArrayList);
        BigInteger modulus = pk.getPlaintextModulus();
        int recoveredServerOnly = 0;
        for (int j = 0; j < serverElementSize; j++) {
            BigInteger pTilde = new BigInteger(pairsPayload.get(2 * j));
            BigInteger cTilde = new BigInteger(pairsPayload.get(2 * j + 1));
            BigInteger q = pheEngine.rawDecrypt(sk, cTilde).mod(modulus);
            if (q.signum() == 0) {
                continue;
            }
            // recover y = (q * y) * q^{-1} mod N
            MpcAbortPreconditions.checkArgument(
                q.gcd(modulus).equals(BigInteger.ONE), "ACISP_DavCid17 Bloom-filter sum is not invertible"
            );
            BigInteger p = pheEngine.rawDecrypt(sk, pTilde).mod(modulus);
            BigInteger invQ = q.modInverse(modulus);
            BigInteger y = p.mod(modulus).multiply(invQ).mod(modulus);
            union.add(ByteBuffer.wrap(Dc17PsuUtils.decodeElement(y, elementByteLength)));
            recoveredServerOnly++;
        }
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 3, 3, stopWatch.getTime(TimeUnit.MILLISECONDS), "decrypt pairs");
        stopWatch.reset();

        logPhaseInfo(PtoState.PTO_END);
        int psica = serverElementSize - recoveredServerOnly;
        return new PsuClientOutput(union, psica);
    }

    private void sendSetup() {
        List<byte[]> pkPayload = pk.serialize();
        int seedBytes = bfHashNum * bfHashSeeds[0].length;
        int bodyLen = Integer.BYTES;
        for (byte[] part : pkPayload) {
            bodyLen += Integer.BYTES + part.length;
        }
        bodyLen += Integer.BYTES + Integer.BYTES + seedBytes;
        byte[] body = new byte[bodyLen];
        int offset = 0;
        System.arraycopy(IntUtils.intToByteArray(pkPayload.size()), 0, body, offset, Integer.BYTES);
        offset += Integer.BYTES;
        for (byte[] part : pkPayload) {
            System.arraycopy(IntUtils.intToByteArray(part.length), 0, body, offset, Integer.BYTES);
            offset += Integer.BYTES;
            System.arraycopy(part, 0, body, offset, part.length);
            offset += part.length;
        }
        System.arraycopy(IntUtils.intToByteArray(bfBinNum), 0, body, offset, Integer.BYTES);
        offset += Integer.BYTES;
        System.arraycopy(IntUtils.intToByteArray(bfHashNum), 0, body, offset, Integer.BYTES);
        offset += Integer.BYTES;
        for (byte[] seed : bfHashSeeds) {
            System.arraycopy(seed, 0, body, offset, seed.length);
            offset += seed.length;
        }
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_SETUP.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(header, Collections.singletonList(body)));
    }

    private List<byte[]> buildEncryptedInvertedBloomFilter() throws MpcAbortException {
        // Build BF bits
        boolean[] bf = new boolean[bfBinNum];
        for (ByteBuffer e : clientElementArrayList) {
            int[] pos = bfHash.positions(Dc17PsuUtils.elementBytes(e));
            for (int p : pos) {
                bf[p] = true;
            }
        }
        // Invert and encrypt (IBF[b] = 1 if BF[b]==0 else 0)
        List<byte[]> payload = new ArrayList<byte[]>(bfBinNum);
        for (int i = 0; i < bfBinNum; i++) {
            BigInteger m = bf[i] ? BigInteger.ZERO : BigInteger.ONE;
            BigInteger ct = pheEngine.rawEncrypt(pk, m);
            payload.add(ct.toByteArray());
        }
        return payload;
    }
}
