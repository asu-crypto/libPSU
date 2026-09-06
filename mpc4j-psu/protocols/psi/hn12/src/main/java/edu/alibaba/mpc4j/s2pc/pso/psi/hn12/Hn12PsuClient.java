package edu.alibaba.mpc4j.s2pc.pso.psi.hn12;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsuPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.balanced.Hn12BalancedAllocation;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.commit.Hn12Pedersen;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12ElementCodec;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12Transcript;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12Ciphertext;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12ElGamal;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.poly.Hn12Polynomial;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.prf.Hn12PrfPayload;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkCom;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkDl;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkPoly;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JOC:HazNis12 PSU client (P1): learns {@code X ∪ Y} (Protocol 8 π∪, semi-honest path).
 * <p>
 * Differs from Protocol 5 by recovering server elements where both polynomial evaluations are
 * nonzero ({@code Y \ X}), then forwarding the union to P2.
 * </p>
 */
public class Hn12PsuClient extends AbstractPsuClient implements PsuClient {
    private final Hn12PsuConfig config;
    private Hn12DdhGroup group;
    private Hn12ElGamal elGamal;
    private BigInteger pedersenKeyH;
    private BigInteger pedersenSecret;

    public Hn12PsuClient(Rpc clientRpc, Party serverParty, Hn12PsuConfig config) {
        super(Hn12PsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        this.config = config;
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        group = Hn12DdhGroup.createForTests(config.getGroupBitLength(), secureRandom);
        elGamal = Hn12ElGamal.keyGen(group, secureRandom);
        pedersenSecret = group.sampleScalar(secureRandom);
        pedersenKeyH = group.pow(group.getG(), pedersenSecret);
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);
        byte[] domain = "HN12_ELEMENT".getBytes(StandardCharsets.UTF_8);
        int sessionId = getTaskId();

        BigInteger h = pedersenKeyH;
        BigInteger hPrime = elGamal.getPublicKey();
        Hn12Transcript dlT1 = new Hn12Transcript("JOC_HazNis12", "PI_DL", 0, sessionId, group.getGroupId());
        Hn12ZkDl.Proof dlH = Hn12ZkDl.prove(group, group.getG(), h, pedersenSecret, dlT1, secureRandom);
        Hn12Transcript dlT2 = new Hn12Transcript("JOC_HazNis12", "PI_DL", 0, sessionId + 1, group.getGroupId());
        Hn12ZkDl.Proof dlHp = Hn12ZkDl.prove(group, group.getG(), hPrime, elGamal.getSecretKey(), dlT2, secureRandom);
        Hn12MessageIO.sendStep(rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(),
            PtoStep.P1_SEND_KEYS.ordinal(),
            List.of(Hn12WireCodec.encodeKeys(group.serializeParameters(), h, hPrime, dlH, dlHp, group)));

        int binCount = Hn12BalancedAllocation.recommendBinCount(clientElementSize);
        int maxBinSize = Hn12BalancedAllocation.recommendMaxBinSize(clientElementSize, binCount);
        byte[] seed0 = new byte[32];
        byte[] seed1 = new byte[32];
        secureRandom.nextBytes(seed0);
        secureRandom.nextBytes(seed1);
        Hn12BalancedAllocation alloc = new Hn12BalancedAllocation(binCount, maxBinSize, seed0, seed1);
        Hn12MessageIO.sendStep(rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(),
            PtoStep.P1_SEND_ALLOC.ordinal(),
            List.of(Hn12WireCodec.encodeAlloc(binCount, maxBinSize, seed0, seed1)));

        Set<BigInteger> clientScalars = new HashSet<>();
        for (ByteBuffer buf : clientElementArrayList) {
            clientScalars.add(Hn12ElementCodec.encodeToScalar(buf, group.getQ(), domain));
        }
        @SuppressWarnings("unchecked")
        List<BigInteger>[] bins = alloc.allocate(new ArrayList<>(clientScalars));

        List<byte[]> commitPayload = Hn12MessageIO.receiveStep(
            rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(), PtoStep.P2_SEND_COMMITMENTS.ordinal()
        );
        MpcAbortPreconditions.checkArgument(commitPayload.size() == serverElementSize);
        List<Hn12MessageIO.CommitmentMessage> commitments = new ArrayList<>(serverElementSize);
        Hn12Pedersen pedersen = new Hn12Pedersen(group, h);
        for (int i = 0; i < serverElementSize; i++) {
            Hn12MessageIO.CommitmentMessage com = Hn12MessageIO.parseCommitment(commitPayload.get(i), group);
            if (!config.isEnableSemiHonestDebug()) {
                Hn12Transcript comT = new Hn12Transcript("JOC_HazNis12", "PI_COM", 1, sessionId + i, group.getGroupId());
                MpcAbortPreconditions.checkArgument(
                    Hn12ZkCom.verify(group, pedersen, com.commitment, com.proof, comT)
                );
            }
            commitments.add(com);
        }

        List<List<BigInteger>> coeffMatrices = new ArrayList<>(binCount);
        List<List<Hn12Ciphertext>> encMatrices = new ArrayList<>(binCount);
        for (int i = 0; i < binCount; i++) {
            List<BigInteger> coeffs = Hn12Polynomial.buildRootPolynomial(bins[i], group.getQ(), maxBinSize);
            coeffMatrices.add(coeffs);
            List<BigInteger> trimmed = Hn12Polynomial.trimTrailingZeros(coeffs);
            List<Hn12Ciphertext> encRow = new ArrayList<>(trimmed.size());
            for (BigInteger c : trimmed) {
                BigInteger gm = c.signum() == 0 ? group.identity() : group.pow(group.getG(), c);
                encRow.add(elGamal.encryptExponent(gm, secureRandom));
            }
            encMatrices.add(encRow);
        }
        Hn12MessageIO.sendStep(rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(),
            PtoStep.P1_SEND_ENC_POLYS.ordinal(),
            List.of(Hn12WireCodec.encodeEncPolyMatrix(encMatrices, group)));

        Hn12Transcript polyT = new Hn12Transcript("JOC_HazNis12", "PI_POLY", 0, sessionId + 2, group.getGroupId());
        Hn12ZkPoly.Proof polyProof = Hn12ZkPoly.prove(group, clientElementSize, coeffMatrices, encMatrices, polyT);
        Hn12MessageIO.sendStep(rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(),
            PtoStep.P1_SEND_POLY_PROOF.ordinal(),
            List.of(Hn12WireCodec.encodePolyProof(polyProof)));

        List<byte[]> evalPayload = Hn12MessageIO.receiveStep(
            rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(), PtoStep.P2_SEND_EVALS.ordinal()
        );
        Set<ByteBuffer> union = new HashSet<>(clientElementArrayList);
        int intersectionSize = 0;
        for (int alpha = 0; alpha < serverElementSize; alpha++) {
            Hn12MessageIO.EvalRow row = Hn12MessageIO.parseEvalRow(evalPayload.get(alpha), group);
            BigInteger dec0 = elGamal.decryptToExponent(row.eval0);
            BigInteger dec1 = elGamal.decryptToExponent(row.eval1);
            boolean inIntersection = dec0.equals(group.identity()) || dec1.equals(group.identity());
            if (inIntersection) {
                intersectionSize++;
                continue;
            }
            // Protocol 8: both nonzero ⇒ y ∈ Y \ X — recover via PRF mask.
            Hn12PrfPayload payload = Hn12PrfPayload.parsePsi(row.prfOut, group, alpha);
            byte[] mask = java.util.Arrays.copyOf(payload.maskXorBytes, elementByteLength);
            byte[] recoveredBytes = BytesUtils.xor(row.maskedItem, mask);
            BigInteger y = Hn12ElementCodec.encodeToScalar(recoveredBytes, group.getQ(), domain);
            if (!config.isUseIdealPrfForTesting()
                && !pedersen.commit(y, payload.openingS).equals(commitments.get(alpha).commitment)) {
                continue;
            }
            if (clientScalars.contains(y)) {
                // Should not happen for honest parties when both evals are nonzero.
                continue;
            }
            union.add(ByteBuffer.wrap(recoveredBytes));
        }

        List<byte[]> unionPayload = new ArrayList<>(union.size());
        List<ByteBuffer> shuffled = new ArrayList<>(union);
        Collections.shuffle(shuffled, secureRandom);
        for (ByteBuffer e : shuffled) {
            byte[] raw = new byte[e.remaining()];
            e.duplicate().get(raw);
            byte[] fixed = new byte[elementByteLength];
            System.arraycopy(raw, Math.max(0, raw.length - elementByteLength),
                fixed, Math.max(0, elementByteLength - raw.length),
                Math.min(elementByteLength, raw.length));
            unionPayload.add(fixed);
        }
        Hn12MessageIO.sendStep(rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(),
            PtoStep.P1_SEND_UNION.ordinal(), unionPayload);

        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(union, intersectionSize);
    }
}
