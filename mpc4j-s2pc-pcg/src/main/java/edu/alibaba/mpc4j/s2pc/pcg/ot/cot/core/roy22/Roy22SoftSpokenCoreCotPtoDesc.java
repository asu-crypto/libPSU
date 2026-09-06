package edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.roy22;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Roy22 SoftSpoken core COT protocol description. Paper:
 * <p>
 * Lawrence Roy. "SoftSpokenOT: Quieter OT Extension From Small-Field Silent VOLE in the Minicrypt
 * Model." EUROCRYPT 2022, pp. 657-687. Reference implementation:
 * {@code libOTe::SoftSpokenShOtSender} / {@code libOTe::SoftSpokenShOtReceiver}
 * (see {@code https://github.com/osu-crypto/libOTe/tree/master/libOTe/Tools/SoftSpokenOT}).
 * </p>
 *
 * <h3>Why we want this in mpc4j</h3>
 * <p>
 * Multiple PSU protocols we benchmark cite SoftSpoken as their base OT extension:
 * </p>
 * <ul>
 *     <li><strong>PT26</strong> (Piske &amp; Trieu, EUROCRYPT 2026 / {@code asu-crypto/IBLT-based-PSU})
 *         uses {@code SoftSpokenShOt} with {@code FIELD_BITS = 2} as the base of every peel-round OT
 *         batch.</li>
 *     <li><strong>TBZ25</strong> (Tu, Bai, Zhang, USENIX Security 2025) uses {@code libOTe} SoftSpoken
 *         / SilentOT inside the nECRG sub-protocol (Section 4.1, Fig. 11).</li>
 *     <li><strong>CSS25</strong> (Chandran et al., ASIACCS 2025 / {@code encryptogroup/circuitPSU})
 *         uses silent-OT-precomputed final OT, which in turn bootstraps from a SoftSpoken base.</li>
 * </ul>
 * <p>
 * Today all three benchmarks run with ALSZ13 (the mpc4j default semi-honest core COT) as a labelled
 * proxy — see {@code temp/readme.md}. Implementing Roy22 SoftSpoken here lets us swap the proxy out
 * uniformly across every consumer that calls {@code CoreCotFactory.createDefaultConfig}.
 * </p>
 *
 * <h3>Planned wire steps (semi-honest, k = {@code FIELD_BITS})</h3>
 * <p>
 * Roy22's "Quiet OT" (semi-honest variant, Fig. 12 of the EUROCRYPT version) at parameter k = 2 looks
 * roughly like this:
 * </p>
 * <ol>
 *     <li><strong>{@link PtoStep#SENDER_SEND_VOLE_SEEDS}</strong>: the sender (= subspace VOLE receiver
 *         in Roy22's flipped notation) runs (2^k - 1) base OTs as the receiver to obtain PRF seeds for
 *         every non-zero subspace direction. With k = 2 this is 3 base OTs per {@code kappa}-block, so
 *         {@code 3 * 128} base OTs to set up a 128-bit Δ (vs ALSZ13's {@code 128}). The cost amortizes
 *         because each extended row costs only {@code 1 / k} = 0.5 base-OT-bandwidth-worth of bytes per
 *         row at k = 2.</li>
 *     <li><strong>{@link PtoStep#RECEIVER_SEND_SUBSPACE_VOLE_CORRECTIONS}</strong>: the receiver
 *         (= subspace VOLE sender) sends the digit-decomposed selection vector minus the subspace VOLE
 *         output. Total payload is {@code ceil(num / 8) * (2^k - 1)} bytes per extension batch (vs
 *         ALSZ13's {@code ceil(num / 8) * 128}).</li>
 *     <li>(Malicious-only, NOT in scope for the first cut) Roy22 §6.2 consistency check:
 *         {@code RECEIVER_SEND_HASH_CHECK} + {@code SENDER_SEND_HASH_CHECK} via a 4-batch sublinear
 *         universal hash; gives the malicious-security upgrade. The mpc4j-side cut should land
 *         semi-honest first, then bolt this on as a separate {@code Builder.setMalicious(true)} toggle
 *         that mirrors how KOS15 layers a Δ-consistency check on top of IKNP03.</li>
 * </ol>
 *
 * <h3>Status</h3>
 * <p>
 * <strong>SCAFFOLDING ONLY.</strong> The cryptographic body (subspace VOLE expansion, Walsh–Hadamard
 * decoding, PRF tree) lives in
 * {@code mpc4j-s2pc-pcg/src/main/java/edu/alibaba/mpc4j/s2pc/pcg/ot/cot/core/roy22/README.md} as a
 * step-by-step implementation plan. {@link Roy22SoftSpokenCoreCotSender#send(int)} and
 * {@link Roy22SoftSpokenCoreCotReceiver#receive(boolean[])} currently throw
 * {@link UnsupportedOperationException}. Wiring this protocol into a consumer (e.g. PT26 / TBZ25
 * nECRG) is unsafe until the body is filled in.
 * </p>
 *
 * @author audit follow-up #5 (May 26, 2026)
 */
class Roy22SoftSpokenCoreCotPtoDesc implements PtoDesc {
    /**
     * protocol ID
     */
    private static final int PTO_ID = Math.abs((int) 8147251902436711032L);
    /**
     * protocol name
     */
    private static final String PTO_NAME = "ROY22_SOFT_SPOKEN_CORE_COT";

    /**
     * protocol step
     */
    enum PtoStep {
        /**
         * <strong>Init only.</strong> Receiver sends the encrypted seeds for all
         * {@code FIELD_SIZE * NUM_BLOCKS = 2^k * (κ/k)} positions. Per-OT payload is a single
         * κ-bit ciphertext {@code c_i := PRG(rm1_i) ⊕ seed_{α,b}}; sender at positions with
         * {@code α ≠ Δ_b} decrypts via its base-OT output, sender at positions with
         * {@code α = Δ_b} discards (it knows Δ_b). Total init payload: {@code 16 * 2^k * (κ/k)}
         * bytes (4096 B at k=2).
         */
        RECEIVER_SEND_SEED_CORRECTIONS,
        /**
         * <strong>Per-extension batch.</strong> Receiver sends the subspace VOLE corrections
         * {@code u[b][j] := S[b][j] ⊕ choice_j} for every block {@code b ∈ [0, κ/k)} and row
         * {@code j ∈ [0, num)}. Payload: one packed F-row per block = {@code (κ/k) * fRowByteLength(num)}
         * bytes per extension batch.
         *
         * <p><em>Component-2 follow-up:</em> with the subspace-code linear amortization (Roy22 §4),
         * this matrix is replaced by a tall-thin {@code v × num} F-matrix where {@code v ≪ κ/k}, cutting
         * the per-row wire cost from κ bits to {@code v · k} bits. Without that optimization (today's
         * Component 1), the wire bandwidth here matches IKNP03 exactly.</p>
         */
        RECEIVER_SEND_SUBSPACE_VOLE_CORRECTIONS,
        /**
         * (Malicious-only, future work, Roy22 §6.2) receiver hash of the universal-hash check value.
         */
        RECEIVER_SEND_HASH_CHECK,
        /**
         * (Malicious-only, future work, Roy22 §6.2) sender hash response.
         */
        SENDER_SEND_HASH_CHECK,
    }

    /**
     * singleton
     */
    private static final Roy22SoftSpokenCoreCotPtoDesc INSTANCE = new Roy22SoftSpokenCoreCotPtoDesc();

    private Roy22SoftSpokenCoreCotPtoDesc() {
        // empty
    }

    public static PtoDesc getInstance() {
        return INSTANCE;
    }

    static {
        PtoDescManager.registerPtoDesc(getInstance());
    }

    @Override
    public int getPtoId() {
        return PTO_ID;
    }

    @Override
    public String getPtoName() {
        return PTO_NAME;
    }
}
