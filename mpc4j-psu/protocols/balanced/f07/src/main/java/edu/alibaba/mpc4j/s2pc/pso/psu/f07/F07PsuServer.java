package edu.alibaba.mpc4j.s2pc.pso.psu.f07;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.crypto.phe.PheEngine;
import edu.alibaba.mpc4j.crypto.phe.PheFactory;
import edu.alibaba.mpc4j.crypto.phe.params.PhePublicKey;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.f07.F07PsuPtoDesc.PtoStep;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ACNS_Frikken07 polynomial-based PSU server (sender).
 */
public class F07PsuServer extends AbstractPsuServer implements PsuServer {
    private final F07PsuConfig config;
    private final PheEngine pheEngine;

    private PhePublicKey pk;
    /**
     * encrypted polynomial coefficients [Enc(a0), ..., Enc(an)]
     */
    private List<BigInteger> encCoeffs;

    public F07PsuServer(Rpc serverRpc, Party clientParty, F07PsuConfig config) {
        super(F07PsuPtoDesc.getInstance(), serverRpc, clientParty, config);
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
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength) throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        receivePkAndPoly();
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 2, stopWatch.getTime(TimeUnit.MILLISECONDS), "recv pk+poly");
        stopWatch.reset();

        stopWatch.start();
        MathPreconditions.checkGreaterOrEqual("encCoeffs.size()", encCoeffs.size(), 2);
        int degree = encCoeffs.size() - 1;
        BigInteger modulus = pk.getPlaintextModulus();
        List<byte[][]> tuples = new ArrayList<byte[][]>(serverElementSize);
        for (ByteBuffer element : serverElementArrayList) {
            BigInteger s = F07PsuUtils.elementToInteger(F07PsuUtils.elementBytes(element));
            MpcAbortPreconditions.checkArgument(
                s.compareTo(modulus) < 0, "server element is outside the ACNS_Frikken07 plaintext ring"
            );
            // Horner: f(s) = a_n; for i=n-1..0: f = f*s + a_i
            BigInteger c = encCoeffs.get(degree);
            for (int i = degree - 1; i >= 0; i--) {
                c = pheEngine.rawMultiply(pk, c, s);
                c = pheEngine.rawAdd(pk, c, encCoeffs.get(i));
            }
            BigInteger r = F07PsuUtils.sampleInvertibleScalar(modulus, secureRandom);
            BigInteger sr = s.multiply(r).mod(modulus);
            BigInteger xCt = pheEngine.rawMultiply(pk, c, sr);
            BigInteger yCt = pheEngine.rawMultiply(pk, c, r);
            // re-randomize
            xCt = pheEngine.rawObfuscate(pk, xCt);
            yCt = pheEngine.rawObfuscate(pk, yCt);
            tuples.add(new byte[][]{xCt.toByteArray(), yCt.toByteArray()});
        }
        Collections.shuffle(tuples, secureRandom);
        List<byte[]> tuplesPayload = new ArrayList<byte[]>(2 * serverElementSize);
        for (byte[][] t : tuples) {
            tuplesPayload.add(t[0]);
            tuplesPayload.add(t[1]);
        }
        DataPacketHeader tuplesHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_TUPLES.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(tuplesHeader, tuplesPayload));

        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 2, 2, stopWatch.getTime(TimeUnit.MILLISECONDS), "send tuples");
        stopWatch.reset();

        logPhaseInfo(PtoState.PTO_END);
    }

    private void receivePkAndPoly() throws MpcAbortException {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_PK_EPOLY.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> payload = rpc.receive(header).getPayload();
        MpcAbortPreconditions.checkArgument(payload.size() == 1, "invalid pk+poly payload");
        byte[] body = payload.get(0);
        int offset = 0;
        int pkPartNum = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
        offset += Integer.BYTES;
        MpcAbortPreconditions.checkArgument(pkPartNum > 0);
        List<byte[]> pkPayload = new ArrayList<byte[]>(pkPartNum);
        for (int i = 0; i < pkPartNum; i++) {
            int partLen = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
            offset += Integer.BYTES;
            MpcAbortPreconditions.checkArgument(partLen > 0);
            pkPayload.add(BytesUtils.clone(body, offset, partLen));
            offset += partLen;
        }
        pk = PheFactory.phasePhePublicKey(pkPayload);
        int coeffNum = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
        offset += Integer.BYTES;
        MpcAbortPreconditions.checkArgument(coeffNum >= 2, "invalid encrypted polynomial size: %s", coeffNum);
        encCoeffs = new ArrayList<BigInteger>(coeffNum);
        for (int i = 0; i < coeffNum; i++) {
            int ctLen = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
            offset += Integer.BYTES;
            MpcAbortPreconditions.checkArgument(ctLen > 0);
            encCoeffs.add(new BigInteger(BytesUtils.clone(body, offset, ctLen)));
            offset += ctLen;
        }
        MpcAbortPreconditions.checkArgument(offset == body.length, "invalid pk+poly payload length");
    }
}
