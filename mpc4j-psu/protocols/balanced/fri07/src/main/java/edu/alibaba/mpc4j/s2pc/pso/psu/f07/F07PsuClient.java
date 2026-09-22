package edu.alibaba.mpc4j.s2pc.pso.psu.f07;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.crypto.phe.PheEngine;
import edu.alibaba.mpc4j.crypto.phe.PheFactory;
import edu.alibaba.mpc4j.crypto.phe.params.PheKeyGenParams;
import edu.alibaba.mpc4j.crypto.phe.params.PhePrivateKey;
import edu.alibaba.mpc4j.crypto.phe.params.PhePublicKey;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.f07.F07PsuPtoDesc.PtoStep;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ACNS_Frikken07 polynomial-based PSU client (output party).
 */
public class F07PsuClient extends AbstractPsuClient implements PsuClient {
    private final F07PsuConfig config;
    private final PheEngine pheEngine;

    private PhePrivateKey sk;
    private PhePublicKey pk;

    public F07PsuClient(Rpc clientRpc, Party serverParty, F07PsuConfig config) {
        super(F07PsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        this.config = config;
        pheEngine = PheFactory.createInstance(config.getPheType(), secureRandom);
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();

        PheKeyGenParams keyGenParams = new PheKeyGenParams(config.getPheSecLevel(), false, 64);
        sk = pheEngine.keyGen(keyGenParams);
        pk = sk.getPublicKey();

        stopWatch.stop();
        logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS), "keygen");
        stopWatch.reset();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        BigInteger modulus = pk.getPlaintextModulus();
        List<BigInteger> roots = new ArrayList<BigInteger>(clientElementSize);
        for (ByteBuffer e : clientElementArrayList) {
            BigInteger root = F07PsuUtils.elementToInteger(F07PsuUtils.elementBytes(e));
            MpcAbortPreconditions.checkArgument(
                root.compareTo(modulus) < 0, "client element is outside the ACNS_Frikken07 plaintext ring"
            );
            roots.add(root);
        }
        List<BigInteger> coeffs = F07PsuUtils.buildRootPolynomial(roots, modulus);
        List<byte[]> pkPayload = pk.serialize();
        List<byte[]> encCoeffBytes = new ArrayList<byte[]>(coeffs.size());
        for (BigInteger a : coeffs) {
            encCoeffBytes.add(pheEngine.rawEncrypt(pk, a.mod(modulus)).toByteArray());
        }
        int bodyLen = Integer.BYTES;
        for (byte[] part : pkPayload) {
            bodyLen += Integer.BYTES + part.length;
        }
        bodyLen += Integer.BYTES;
        for (byte[] ct : encCoeffBytes) {
            bodyLen += Integer.BYTES + ct.length;
        }
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
        System.arraycopy(IntUtils.intToByteArray(encCoeffBytes.size()), 0, body, offset, Integer.BYTES);
        offset += Integer.BYTES;
        for (byte[] ct : encCoeffBytes) {
            System.arraycopy(IntUtils.intToByteArray(ct.length), 0, body, offset, Integer.BYTES);
            offset += Integer.BYTES;
            System.arraycopy(ct, 0, body, offset, ct.length);
            offset += ct.length;
        }
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_PK_EPOLY.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(header, Collections.singletonList(body)));
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 2, stopWatch.getTime(TimeUnit.MILLISECONDS), "send pk+poly");
        stopWatch.reset();

        stopWatch.start();
        DataPacketHeader tuplesHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_TUPLES.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> tuplesPayload = rpc.receive(tuplesHeader).getPayload();
        MpcAbortPreconditions.checkArgument(tuplesPayload.size() == 2 * serverElementSize);
        Set<ByteBuffer> union = new HashSet<ByteBuffer>(clientElementArrayList);
        int intersectionSize = 0;
        for (int j = 0; j < serverElementSize; j++) {
            BigInteger xCt = new BigInteger(tuplesPayload.get(2 * j));
            BigInteger yCt = new BigInteger(tuplesPayload.get(2 * j + 1));
            BigInteger x = pheEngine.rawDecrypt(sk, xCt).mod(modulus);
            BigInteger y = pheEngine.rawDecrypt(sk, yCt).mod(modulus);
            if (x.signum() == 0 && y.signum() == 0) {
                intersectionSize++;
                continue;
            }
            MpcAbortPreconditions.checkArgument(y.signum() > 0, "invalid nonzero ACNS_Frikken07 tuple");
            MpcAbortPreconditions.checkArgument(
                y.gcd(modulus).equals(BigInteger.ONE), "non-invertible ACNS_Frikken07 tuple denominator"
            );
            BigInteger s = F07PsuUtils.recoverServerElement(x, y, modulus);
            union.add(ByteBuffer.wrap(F07PsuUtils.decodeElement(s, elementByteLength)));
        }
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 2, 2, stopWatch.getTime(TimeUnit.MILLISECONDS), "decrypt tuples");
        stopWatch.reset();

        logPhaseInfo(PtoState.PTO_END);
        MathPreconditions.checkGreaterOrEqual("union.size()", union.size(), clientElementSize);
        return new PsuClientOutput(union, intersectionSize);
    }
}
