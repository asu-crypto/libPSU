package edu.alibaba.mpc4j.s2pc.pso.psi.hn12;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psi.AbstractPsiServer;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiServer;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsiPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.balanced.Hn12BalancedAllocation;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.commit.Hn12Pedersen;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12ElementCodec;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12Transcript;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12Ciphertext;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12ElGamal;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.poly.Hn12HomomorphicPolyEval;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.prf.Hn12IdealPrf;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.prf.Hn12PrfPayload;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkCom;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkDl;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk.Hn12ZkPoly;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * HN12 PSI server (P2): set Y, no output (Protocol 5).
 */
public class Hn12PsiServer extends AbstractPsiServer implements PsiServer {
    private final Hn12PsiConfig config;
    private byte[] prfKey;

    public Hn12PsiServer(Rpc serverRpc, Party clientParty, Hn12PsiConfig config) {
        super(Hn12PsiPtoDesc.getInstance(), serverRpc, clientParty, config);
        this.config = config;
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        prfKey = Hn12PrfPayload.sampleKey(secureRandom);
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psi(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength) throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);
        byte[] domain = "HN12_ELEMENT".getBytes(StandardCharsets.UTF_8);

        // Step 1: verify P1 keys + π_DL
        Hn12MessageIO.KeysMessage keys = Hn12MessageIO.parseKeys(
            Hn12MessageIO.receiveStep(rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(),
                PtoStep.P1_SEND_KEYS.ordinal()).get(0)
        );
        Hn12DdhGroup group = keys.group;
        int sessionId = getTaskId();
        if (!config.isEnableSemiHonestDebug()) {
            Hn12Transcript dlT1 = new Hn12Transcript("JOC_HazNis12", "PI_DL", 0, sessionId, group.getGroupId());
            MpcAbortPreconditions.checkArgument(Hn12ZkDl.verify(group, group.getG(), keys.h, keys.dlH, dlT1));
            Hn12Transcript dlT2 = new Hn12Transcript("JOC_HazNis12", "PI_DL", 0, sessionId + 1, group.getGroupId());
            MpcAbortPreconditions.checkArgument(Hn12ZkDl.verify(group, group.getG(), keys.hPrime, keys.dlHp, dlT2));
        }
        Hn12Pedersen pedersen = new Hn12Pedersen(group, keys.h);
        Hn12ElGamal elGamalView = new Hn12ElGamal(group, keys.hPrime, BigInteger.ZERO);

        // Step 2: allocation params
        Hn12MessageIO.AllocMessage allocMsg = Hn12MessageIO.parseAlloc(
            Hn12MessageIO.receiveStep(rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(),
                PtoStep.P1_SEND_ALLOC.ordinal()).get(0)
        );
        Hn12BalancedAllocation alloc = new Hn12BalancedAllocation(
            allocMsg.binCount, allocMsg.maxBinSize, allocMsg.seed0, allocMsg.seed1
        );

        // Step 3: commit to Y + π_COM
        List<byte[]> commitPayload = new ArrayList<>(serverElementSize);
        List<BigInteger> yScalars = new ArrayList<>(serverElementSize);
        List<BigInteger> sScalars = new ArrayList<>(serverElementSize);
        for (ByteBuffer element : serverElementArrayList) {
            BigInteger y = Hn12ElementCodec.encodeToScalar(element, group.getQ(), domain);
            BigInteger s = group.sampleScalar(secureRandom);
            yScalars.add(y);
            sScalars.add(s);
            BigInteger com = pedersen.commit(y, s);
            int idx = commitPayload.size();
            Hn12Transcript comT = new Hn12Transcript("JOC_HazNis12", "PI_COM", 1, sessionId + idx, group.getGroupId());
            Hn12ZkCom.Proof proof = Hn12ZkCom.prove(group, pedersen, com, y, s, comT, secureRandom);
            commitPayload.add(Hn12WireCodec.encodeCommitmentRow(com, proof, group));
        }
        Hn12MessageIO.sendStep(rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(),
            PtoStep.P2_SEND_COMMITMENTS.ordinal(), commitPayload);

        // Step 4–5: encrypted polynomials + π_POLY
        List<byte[]> polyPayload = Hn12MessageIO.receiveStep(rpc, encodeTaskId, getPtoDesc(), extraInfo,
            ownParty(), otherParty(), PtoStep.P1_SEND_ENC_POLYS.ordinal());
        List<List<Hn12Ciphertext>> encMatrices = Hn12MessageIO.parseEncMatrix(polyPayload.get(0), group);
        Hn12ZkPoly.Proof polyProof = Hn12MessageIO.parsePolyProof(
            Hn12MessageIO.receiveStep(rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(),
                PtoStep.P1_SEND_POLY_PROOF.ordinal()).get(0)
        );
        if (!config.isEnableSemiHonestDebug()) {
            Hn12Transcript polyT = new Hn12Transcript("JOC_HazNis12", "PI_POLY", 0, sessionId + 2, group.getGroupId());
            MpcAbortPreconditions.checkArgument(Hn12ZkPoly.verify(
                group, clientElementSize, allocMsg.binCount, allocMsg.maxBinSize, encMatrices, polyProof, polyT
            ));
        }

        // Step 6: homomorphic evaluation + ideal-PRF masks
        List<byte[]> evalPayload = new ArrayList<>(serverElementSize);
        for (int alpha = 0; alpha < serverElementSize; alpha++) {
            BigInteger y = yScalars.get(alpha);
            BigInteger s = sScalars.get(alpha);
            int b0 = alloc.hash0(y);
            int b1 = alloc.hash1(y);
            Hn12Ciphertext e0 = Hn12HomomorphicPolyEval.evaluate(group, elGamalView, encMatrices.get(b0), y);
            Hn12Ciphertext e1 = Hn12HomomorphicPolyEval.evaluate(group, elGamalView, encMatrices.get(b1), y);
            byte[] prfSeed = Hn12IdealPrf.eval(prfKey, s, "HN12_PSI_PRF");
            Hn12PrfPayload payload = Hn12PrfPayload.parsePsi(prfSeed, group, alpha);
            e0 = elGamalView.rerandomize(e0, secureRandom);
            e1 = elGamalView.rerandomize(e1, secureRandom);
            byte[] elementBytes = elementBytes(serverElementArrayList.get(alpha), elementByteLength);
            byte[] mask = java.util.Arrays.copyOf(payload.maskXorBytes, elementByteLength);
            byte[] masked = BytesUtils.xor(elementBytes, mask);
            evalPayload.add(Hn12WireCodec.encodeEvalRow(
                e0.serialize(group), e1.serialize(group), masked, prfSeed
            ));
        }
        Hn12MessageIO.sendStep(rpc, encodeTaskId, getPtoDesc(), extraInfo, ownParty(), otherParty(),
            PtoStep.P2_SEND_EVALS.ordinal(), evalPayload);
        logPhaseInfo(PtoState.PTO_END);
    }

    private static byte[] elementBytes(ByteBuffer buf, int len) {
        byte[] raw = new byte[buf.remaining()];
        buf.duplicate().get(raw);
        byte[] out = new byte[len];
        System.arraycopy(raw, Math.max(0, raw.length - len), out, Math.max(0, len - raw.length), Math.min(len, raw.length));
        return out;
    }

    private static byte[] bigIntegerToBytes(BigInteger v, int len) {
        byte[] raw = v.toByteArray();
        byte[] out = new byte[len];
        System.arraycopy(raw, Math.max(0, raw.length - len), out, Math.max(0, len - raw.length), Math.min(len, raw.length));
        return out;
    }
}
