package edu.alibaba.mpc4j.s2pc.pso.psu.dc17;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.crypto.phe.PheEngine;
import edu.alibaba.mpc4j.crypto.phe.PheFactory;
import edu.alibaba.mpc4j.crypto.phe.params.PhePublicKey;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuPtoDesc.PtoStep;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * ACISP_DavCid17 EIBF-based PSU server (non-output party).
 */
public class Dc17PsuServer extends AbstractPsuServer implements PsuServer {
    private final Dc17PsuConfig config;
    private final PheEngine pheEngine;
    private PhePublicKey pk;

    private int bfBinNum;
    private int bfHashNum;
    private byte[][] bfHashSeeds;
    private Dc17BloomFilterHash bfHash;

    public Dc17PsuServer(Rpc serverRpc, Party clientParty, Dc17PsuConfig config) {
        super(Dc17PsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        this.config = config;
        pheEngine = PheFactory.createInstance(config.getPheType(), secureRandom);
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        stopWatch.stop();
        logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        receiveSetup();
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 3, stopWatch.getTime(TimeUnit.MILLISECONDS), "recv setup");
        stopWatch.reset();

        stopWatch.start();
        DataPacketHeader eibfHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_EIBF.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> eibfPayload = rpc.receive(eibfHeader).getPayload();
        MpcAbortPreconditions.checkArgument(eibfPayload.size() == bfBinNum);
        BigInteger[] eibf = new BigInteger[bfBinNum];
        for (int i = 0; i < bfBinNum; i++) {
            eibf[i] = new BigInteger(eibfPayload.get(i));
        }
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 2, 3, stopWatch.getTime(TimeUnit.MILLISECONDS), "recv EIBF");
        stopWatch.reset();

        stopWatch.start();
        List<byte[]> pairs = new ArrayList<>(2 * serverElementSize);
        List<Integer> perm = new ArrayList<>(serverElementSize);
        for (int i = 0; i < serverElementSize; i++) {
            perm.add(i);
        }
        Collections.shuffle(perm, secureRandom);

        BigInteger modulus = pk.getPlaintextModulus();
        for (int idx : perm) {
            byte[] yBytes = Dc17PsuUtils.elementBytes(serverElementArrayList.get(idx));
            BigInteger y = Dc17PsuUtils.elementToInteger(yBytes);
            MpcAbortPreconditions.checkArgument(
                y.compareTo(modulus) < 0, "server element is outside the ACISP_DavCid17 plaintext ring"
            );
            int[] pos = bfHash.positions(yBytes);
            BigInteger c = eibf[pos[0]];
            for (int i = 1; i < pos.length; i++) {
                c = pheEngine.rawAdd(pk, c, eibf[pos[i]]);
            }
            // p~ = ReRand(c * y), c~ = ReRand(c)
            BigInteger p = pheEngine.rawMultiply(pk, c, y);
            BigInteger pTilde = pheEngine.rawObfuscate(pk, p);
            BigInteger cTilde = pheEngine.rawObfuscate(pk, c);
            pairs.add(pTilde.toByteArray());
            pairs.add(cTilde.toByteArray());
        }
        DataPacketHeader pairsHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_PAIRS.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(pairsHeader, pairs));
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 3, 3, stopWatch.getTime(TimeUnit.MILLISECONDS), "send pairs");
        stopWatch.reset();

        logPhaseInfo(PtoState.PTO_END);
    }

    private void receiveSetup() throws MpcAbortException {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_SETUP.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> payload = rpc.receive(header).getPayload();
        MpcAbortPreconditions.checkArgument(payload.size() == 1, "invalid setup payload");
        byte[] body = payload.get(0);
        int offset = 0;
        int pkPartNum = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
        offset += Integer.BYTES;
        List<byte[]> pkPayload = new ArrayList<byte[]>(pkPartNum);
        for (int i = 0; i < pkPartNum; i++) {
            int partLen = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
            offset += Integer.BYTES;
            byte[] part = BytesUtils.clone(body, offset, partLen);
            offset += partLen;
            pkPayload.add(part);
        }
        pk = PheFactory.phasePhePublicKey(pkPayload);
        bfBinNum = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
        offset += Integer.BYTES;
        bfHashNum = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
        offset += Integer.BYTES;
        MathPreconditions.checkGreater("bfBinNum", bfBinNum, 0);
        MathPreconditions.checkGreater("bfHashNum", bfHashNum, 0);
        bfHashSeeds = new byte[bfHashNum][];
        for (int i = 0; i < bfHashNum; i++) {
            bfHashSeeds[i] = BytesUtils.clone(body, offset, 16);
            offset += 16;
        }
        MpcAbortPreconditions.checkArgument(offset == body.length, "invalid setup payload length");
        bfHash = new Dc17BloomFilterHash(bfBinNum, bfHashSeeds);
    }
}
