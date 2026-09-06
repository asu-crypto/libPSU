package edu.alibaba.mpc4j.s2pc.pso.psi.ks05;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.crypto.phe.PheEngine;
import edu.alibaba.mpc4j.crypto.phe.PheFactory;
import edu.alibaba.mpc4j.crypto.phe.params.PhePublicKey;
import edu.alibaba.mpc4j.s2pc.pso.psi.AbstractPsiServer;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiServer;
import edu.alibaba.mpc4j.s2pc.pso.psi.ks05.Ks05PsiPtoDesc.PtoStep;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * C_KisSon05 PSI server, specialized to the two-party client-output API.
 */
public class Ks05PsiServer extends AbstractPsiServer implements PsiServer {
    private final Ks05PsiConfig config;
    private final PheEngine pheEngine;

    private PhePublicKey pk;
    private List<BigInteger> encCoeffs;

    public Ks05PsiServer(Rpc serverRpc, Party clientParty, Ks05PsiConfig config) {
        super(Ks05PsiPtoDesc.getInstance(), serverRpc, clientParty, config);
        this.config = config;
        pheEngine = PheFactory.createInstance(config.getPheType(), secureRandom);
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        Ks05PsiUtils.checkSetSizeLimit(maxServerElementSize, maxClientElementSize, config.getMaxSetSize());
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        stopWatch.stop();
        logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psi(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength) throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        Ks05PsiUtils.checkSetSizeLimit(serverElementSize, clientElementSize, config.getMaxSetSize());
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        receivePkAndPoly();
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 2, stopWatch.getTime(TimeUnit.MILLISECONDS), "recv pk+poly");
        stopWatch.reset();

        stopWatch.start();
        BigInteger modulus = pk.getPlaintextModulus();
        List<BigInteger> serverRoots = new ArrayList<>(serverElementSize);
        for (ByteBuffer element : serverElementArrayList) {
            serverRoots.add(Ks05PsiUtils.encodeElement(element, modulus));
        }
        List<BigInteger> serverPoly = Ks05PsiUtils.buildRootPolynomial(serverRoots, modulus);
        int maskDegree = Math.max(encCoeffs.size() - 1, serverPoly.size() - 1);
        List<BigInteger> serverMask = Ks05PsiUtils.randomPolynomial(maskDegree, modulus, secureRandom);
        List<BigInteger> clientMask = Ks05PsiUtils.randomPolynomial(maskDegree, modulus, secureRandom);

        // Two-party C_KisSon05 specialization of p = f_client * r_server + f_server * r_client.
        List<BigInteger> clientTerm = multiplyEncryptedByPlain(encCoeffs, serverMask, modulus);
        List<BigInteger> serverTermPlain = Ks05PsiUtils.multiplyPlainPolynomials(serverPoly, clientMask, modulus);
        List<BigInteger> serverTerm = encryptPlainPolynomial(serverTermPlain);
        List<BigInteger> maskedPoly = addEncryptedPolynomials(clientTerm, serverTerm);

        List<byte[]> maskedPolyPayload = new ArrayList<>(maskedPoly.size());
        for (BigInteger coeff : maskedPoly) {
            maskedPolyPayload.add(coeff.toByteArray());
        }
        DataPacketHeader maskedPolyHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_MASKED_POLY.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(maskedPolyHeader, maskedPolyPayload));

        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 2, 2, stopWatch.getTime(TimeUnit.MILLISECONDS), "send masked polynomial");
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
        List<byte[]> pkPayload = new ArrayList<>(pkPartNum);
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
        encCoeffs = new ArrayList<>(coeffNum);
        for (int i = 0; i < coeffNum; i++) {
            int ctLen = IntUtils.byteArrayToInt(BytesUtils.clone(body, offset, Integer.BYTES));
            offset += Integer.BYTES;
            MpcAbortPreconditions.checkArgument(ctLen > 0);
            encCoeffs.add(new BigInteger(BytesUtils.clone(body, offset, ctLen)));
            offset += ctLen;
        }
        MpcAbortPreconditions.checkArgument(offset == body.length, "invalid pk+poly payload length");
    }

    private List<BigInteger> multiplyEncryptedByPlain(List<BigInteger> encrypted, List<BigInteger> plain,
                                                      BigInteger modulus) {
        List<BigInteger> product = encryptedZeroPolynomial(encrypted.size() + plain.size() - 1);
        for (int i = 0; i < encrypted.size(); i++) {
            for (int j = 0; j < plain.size(); j++) {
                BigInteger factor = plain.get(j).mod(modulus);
                if (factor.signum() == 0) {
                    continue;
                }
                BigInteger term = pheEngine.rawMultiply(pk, encrypted.get(i), factor);
                product.set(i + j, pheEngine.rawAdd(pk, product.get(i + j), term));
            }
        }
        return obfuscatePolynomial(product);
    }

    private List<BigInteger> encryptPlainPolynomial(List<BigInteger> plain) {
        List<BigInteger> encrypted = new ArrayList<BigInteger>(plain.size());
        BigInteger modulus = pk.getPlaintextModulus();
        for (BigInteger coeff : plain) {
            encrypted.add(pheEngine.rawEncrypt(pk, coeff.mod(modulus)));
        }
        return encrypted;
    }

    private List<BigInteger> addEncryptedPolynomials(List<BigInteger> left, List<BigInteger> right) {
        int size = Math.max(left.size(), right.size());
        List<BigInteger> sum = encryptedZeroPolynomial(size);
        for (int i = 0; i < size; i++) {
            BigInteger value = sum.get(i);
            if (i < left.size()) {
                value = pheEngine.rawAdd(pk, value, left.get(i));
            }
            if (i < right.size()) {
                value = pheEngine.rawAdd(pk, value, right.get(i));
            }
            sum.set(i, pheEngine.rawObfuscate(pk, value));
        }
        return sum;
    }

    private List<BigInteger> encryptedZeroPolynomial(int size) {
        List<BigInteger> zeros = new ArrayList<BigInteger>(size);
        for (int i = 0; i < size; i++) {
            zeros.add(pheEngine.rawEncrypt(pk, BigInteger.ZERO));
        }
        return zeros;
    }

    private List<BigInteger> obfuscatePolynomial(List<BigInteger> coeffs) {
        List<BigInteger> obfuscated = new ArrayList<BigInteger>(coeffs.size());
        for (BigInteger coeff : coeffs) {
            obfuscated.add(pheEngine.rawObfuscate(pk, coeff));
        }
        return obfuscated;
    }
}
