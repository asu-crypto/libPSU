package edu.alibaba.mpc4j.s2pc.pso.psi.ks05;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.crypto.phe.PheEngine;
import edu.alibaba.mpc4j.crypto.phe.PheFactory;
import edu.alibaba.mpc4j.crypto.phe.params.PheKeyGenParams;
import edu.alibaba.mpc4j.crypto.phe.params.PhePrivateKey;
import edu.alibaba.mpc4j.crypto.phe.params.PhePublicKey;
import edu.alibaba.mpc4j.s2pc.pso.psi.AbstractPsiClient;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiClient;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psi.ks05.Ks05PsiPtoDesc.PtoStep;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * C_KisSon05 PSI client (learns intersection on the client set).
 */
public class Ks05PsiClient extends AbstractPsiClient implements PsiClient {
    private final Ks05PsiConfig config;
    private final PheEngine pheEngine;

    private PhePrivateKey sk;
    private PhePublicKey pk;

    public Ks05PsiClient(Rpc clientRpc, Party serverParty, Ks05PsiConfig config) {
        super(Ks05PsiPtoDesc.getInstance(), clientRpc, serverParty, config);
        this.config = config;
        pheEngine = PheFactory.createInstance(config.getPheType(), secureRandom);
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        Ks05PsiUtils.checkSetSizeLimit(maxServerElementSize, maxClientElementSize, config.getMaxSetSize());
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
    public PsiClientOutput psi(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        Ks05PsiUtils.checkSetSizeLimit(serverElementSize, clientElementSize, config.getMaxSetSize());
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        BigInteger modulus = pk.getPlaintextModulus();
        List<BigInteger> roots = new ArrayList<>(clientElementSize);
        for (ByteBuffer e : clientElementArrayList) {
            roots.add(Ks05PsiUtils.encodeElement(e, modulus));
        }
        List<BigInteger> coeffs = Ks05PsiUtils.buildRootPolynomial(roots, modulus);
        List<byte[]> pkPayload = pk.serialize();
        List<byte[]> encCoeffBytes = new ArrayList<>(coeffs.size());
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
        DataPacketHeader maskedPolyHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_MASKED_POLY.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> maskedPolyPayload = rpc.receive(maskedPolyHeader).getPayload();
        MpcAbortPreconditions.checkArgument(!maskedPolyPayload.isEmpty(), "empty masked polynomial payload");
        List<BigInteger> maskedPoly = new ArrayList<BigInteger>(maskedPolyPayload.size());
        for (byte[] coeffCt : maskedPolyPayload) {
            maskedPoly.add(pheEngine.rawDecrypt(sk, new BigInteger(coeffCt)).mod(modulus));
        }
        Set<ByteBuffer> intersection = new HashSet<>();
        for (ByteBuffer element : clientElementArrayList) {
            BigInteger encoded = Ks05PsiUtils.encodeElement(element, modulus);
            BigInteger value = Ks05PsiUtils.evaluatePlainPolynomial(maskedPoly, encoded, modulus);
            if (value.signum() == 0) {
                intersection.add(element);
            }
        }
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 2, 2, stopWatch.getTime(TimeUnit.MILLISECONDS), "decrypt masked polynomial");
        stopWatch.reset();

        logPhaseInfo(PtoState.PTO_END);
        return new PsiClientOutput(intersection);
    }
}
