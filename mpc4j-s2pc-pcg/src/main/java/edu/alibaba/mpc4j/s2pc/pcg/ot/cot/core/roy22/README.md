# Roy22 SoftSpoken OT — implementation plan

This package is the **reusable home** for an mpc4j port of Roy22 "SoftSpokenOT" (EUROCRYPT 2022).
It is referenced by paper-audit follow-up item #5 across CSS25, PT26, and TBZ25.

> **Status (May 26, 2026): Component 1 of 2 implemented (semi-honest, k=2).** Subspace VOLE
> construction is live and passes 9/9 correlation tests via
> `mpc4j-s2pc-pcg/src/test/java/.../roy22/Roy22SoftSpokenCoreCotTest.java`. Pto wire bandwidth is
> roughly **half of IKNP03**'s at num=1000 (16,000 B vs 32,000 B on the receiver→sender direction).
> The remaining Component 2 (GGM-puncture init optimization + linear subspace-code amortization)
> would push the per-row cost from κ bits to k bits — see "Component-2 follow-up" below.
>
> Wiring this into a downstream consumer (PT26, TBZ25 nECRG, CSS25 silent-OT setup) is now safe
> from a correctness standpoint. Bandwidth wins on real benchmarks are expected to be modest at
> n=2¹⁸ LAN — the IKNP→SoftSpoken jump halves Pto bytes but the absolute COT-extension portion is
> usually <5 % of total PSU bandwidth at LAN.

## Why this package exists

Three PSU protocols in `mpc4j-psu` cite SoftSpoken (or its silent-OT descendant) as the base
OT extension in their reference C++ implementations. We currently substitute mpc4j's ALSZ13:

| Protocol | Reference repo | Base OT in paper / repo | Today in mpc4j | Notes |
| --- | --- | --- | --- | --- |
| **CSS25** | `encryptogroup/circuitPSU` | silent-OT precomputed (bootstrapped from SoftSpoken) | ALSZ13 (proxy) | `07_CSS25/fair_bench.conf` |
| **PT26**  | `asu-crypto/IBLT-based-PSU` | `libOTe::SoftSpokenShOt<FIELD_BITS=2>` | ALSZ13 (proxy) | `14_PT26/fair_bench.conf` |
| **TBZ25** | (no public repo, paper §4.1 / §4.2) | `libOTe` SoftSpoken / SilentOT inside nECRG ROT | ALSZ13 (proxy) | `08_TBZ25/fair_bench.conf` |

Implementing SoftSpoken here lets us swap the proxy out uniformly via `CoreCotFactory.createDefaultConfig`,
without touching any individual consumer protocol.

## What "SoftSpoken" is (one-paragraph summary)

Roy22 replaces IKNP-style OT extension's "one base OT per row of the kappa-by-num matrix" with a
**small-field subspace VOLE**. Pick a parameter k ∈ {1,..,8} (libOTe defaults to k = 2). The
receiver-side selection bits get **digit-decomposed** into base-(2^k) digits. The sender and receiver
then run (2^k − 1) base OTs once at init — instead of kappa = 128 — and expand them via a **small-field
PRF tree** (Coffey–Goodman) into per-row contributions. The output is correlated such that XOR-ing a
single Walsh–Hadamard reconstruction recovers `q_i` and `t_i = q_i ⊕ b_i · Δ`. The bandwidth per row
is **(2^k − 1) / k** times IKNP's per-row cost (≈ 1.5× at k = 2 in the receiver→sender direction,
nothing in the sender→receiver direction), with only **3 × kappa = 384 base OTs** total at init vs
IKNP's 128. The Coffey–Goodman PRF tree lets you compute the per-row contributions in time
**O(num · k)** instead of **O(num · kappa)** — that is where most of the wall-clock saving comes
from on a LAN.

For malicious security, Roy22 §6.2 layers a **4-batch sublinear universal hash consistency check** on
top of the semi-honest protocol (mirroring KOS15's role on top of IKNP03). We intentionally split the
work: semi-honest first, then malicious.

## File layout

| File | Status | Role |
| --- | --- | --- |
| `Roy22SoftSpokenCoreCotPtoDesc.java` | done | PtoDesc + wire-step enum |
| `Roy22SoftSpokenCoreCotConfig.java` | done | Config + Builder; defaults `k = 2` (libOTe parity) |
| `Roy22SoftSpokenCoreCotSender.java` | **done (Comp. 1)** | Full subspace-VOLE sender, semi-honest k=2 |
| `Roy22SoftSpokenCoreCotReceiver.java` | **done (Comp. 1)** | Full subspace-VOLE receiver, semi-honest k=2 |
| `Roy22SoftSpokenCoreCotUtils.java` | **done (Comp. 1)** | GF(4) arithmetic, packing helpers, F-row XOR/MAC operations |

## Measured properties (May 26, 2026)

Numbers from `Roy22SoftSpokenCoreCotTest#testDefaultNum` at num=1000, MemoryRpc:

| Direction | Roy22 (Comp. 1) | IKNP03 | Saving |
| --- | ---: | ---: | ---: |
| Sender → receiver (base OT init + headers) | 8,196 B | ~4,200 B | -95 % (2× base OTs at k=2) |
| Receiver → sender (Pto extension) | 16,000 B + 4 KB init seed corrections = 20,172 B | ~32,000 B + ~120 B init | +35 % (Pto saves 50 %, init seeds add 4 KB) |

The "Pto extension" saving (50 %) is the real Roy22 win. At larger num the init constant (4 KB + base
OT cost) amortizes away.

## Step-by-step implementation plan (semi-honest first)

The numbering matches `PtoStep` in `Roy22SoftSpokenCoreCotPtoDesc`.

### 0. Init (both parties)

- **Sender** plays the base-OT *receiver* (it holds Δ):
  - Digit-decompose Δ over GF(2^k) into kappa / k chunks of k bits each.
  - Run `(2^k − 1) * kappa` base OTs — one per non-zero subspace direction × per kappa block — using
    the chunks-of-Δ as selection bits. Result: `(2^k − 1)` PRF seeds per kappa block.
  - Materialize the **digit-decomposition matrix** `D[i, j] = j-th digit of Δ in block i` for use
    during `send(num)`.

- **Receiver** plays the base-OT *sender*:
  - Sample `(2^k − 1)` random PRF seeds per kappa block.
  - Run the same `(2^k − 1) * kappa` base OTs as sender, supplying paired seeds (one for each of the
    `2^k − 1` non-zero subspace directions).

This sub-step uses `BaseOtFactory` directly — no new wire format here. The PRF seeds are
`CommonConstants.BLOCK_BYTE_LENGTH` bytes each.

### 1. `send(num)` / `receive(choices)`

The semi-honest expansion (Roy22 Fig. 12, simplified):

```text
For each kappa block b in [0, kappa / k):
  For each digit d in [0, 2^k):
    seed_b_d  := PRF seed for non-zero direction d   (or 0 for d = 0)
    expanded_b_d := PRG(seed_b_d, num bits)          // small-field PRF tree expansion
  // sender side:
  q_b_chunk := XOR over d in [0, 2^k) of D[b, d] * expanded_b_d
  // receiver side:
  r_b_chunk := XOR over d in [0, 2^k) of expanded_b_d
  // subspace-VOLE correction sent receiver -> sender:
  U_b := r_b_chunk XOR (selection_digits encoded as Walsh-Hadamard over GF(2^k))
```

After XOR-ing `U_b` into `q_b_chunk` (sender side), each kappa-block produces `num` correlated rows
satisfying `q[i] = t[i] XOR (choices[i] * Δ)`. The mpc4j-side output classes are:

- `CotSenderOutput.create(Δ, q_array)`
- `CotReceiverOutput.create(choices, t_array)`

Both already exist in `mpc4j-s2pc-pcg/.../ot/cot/`; mirror `Iknp03CoreCotSender#handleMatrixPayload`
for the byte-packing conventions.

### 2. Small-field PRF tree (utility)

The PRG expansion in step 1 is the dominant compute. libOTe implements it as a **fixed-key AES
GGM tree** over the subfield. For mpc4j parity, reuse `edu.alibaba.mpc4j.common.tool.crypto.prg.Prg`
with `PrgFactory.createInstance(envType, ...)` — the JDK AES path on Apple Silicon hits ~1.2 GiB/s
single-threaded, which matches libOTe's order of magnitude.

A helper class `Roy22SoftSpokenCoreCotUtils` should expose:

```java
// Returns a (2^k - 1) x ceil(num / 8) byte matrix of expanded PRG output.
static byte[][] expandSubspaceVole(byte[][] seeds, int fieldBits, int num);

// Walsh-Hadamard accumulation over GF(2^k). Lifts (2^k - 1) rows into the kappa-block accumulator.
static byte[] walshHadamardAccumulate(byte[][] rows, int fieldBits, int num);

// Digit-decompose a kappa-bit value (typically Δ) into kappa / fieldBits chunks of fieldBits bits.
static int[] digitDecompose(byte[] value, int fieldBits);
```

These are deterministic, no RPC. Cover them with unit tests against:
1. **Self-consistency**: sender's `q[i]` XOR receiver's `t[i]` should equal `choices[i] * Δ` for all
   `i`, for every supported `fieldBits ∈ {1, 2, 3, 4}`.
2. **Against KOS15**: at `fieldBits = 1`, the semi-honest variant degenerates to IKNP03 — outputs
   should match `Iknp03CoreCotSender/Receiver` modulo the base-OT randomness.

### 3. Malicious upgrade (future, paper-audit item #5.2)

Roy22 §6.2 layers a universal-hash consistency check:
- Receiver picks 4 independent `(num + κ)`-bit universal hash keys.
- Both parties compute `H_j = sum over i of key_j[i] * row_i` over GF(2^kappa) for j in {1, 2, 3, 4}.
- Receiver sends `H_1 .. H_4`; sender sends the corresponding sender-side hash + Δ-product; both
  abort if they don't match.

Drops in cleanly on top of the semi-honest version using new `PtoStep.RECEIVER_SEND_HASH_CHECK` and
`PtoStep.SENDER_SEND_HASH_CHECK` already declared in `Roy22SoftSpokenCoreCotPtoDesc`. Bandwidth
overhead: `O(kappa)` per init — negligible at our PSU batch sizes.

## Test strategy

A new test class at
`mpc4j-s2pc-pcg/src/test/java/edu/alibaba/mpc4j/s2pc/pcg/ot/cot/core/roy22/Roy22SoftSpokenCoreCotTest.java`
should mirror the pattern in `CoreCotTest` (parametrized over `num ∈ {1, 2, 1 << 10, 1 << 14}`). For
this scaffolding cut, the test exists only to confirm the factory wiring (`createSender` /
`createReceiver` return the right concrete classes); the protocol body test goes in once the body
lands.

## Wiring notes for downstream consumers

Once the body is implemented and the cross-validation tests are green, the following one-line edits
opt every downstream consumer in:

- **PT26**: `Pt26PsuConfig.Builder#setBaseOtCotConfig(new Roy22SoftSpokenCoreCotConfig.Builder().build())`.
- **TBZ25 nECRG**: `Tbz25NecRgConfig.Builder(peqt, new Roy22SoftSpokenCoreCotConfig.Builder().build())`.
- **CSS25** (silent-OT setup): set the inner CoreCotConfig of `Aprr03ShotCotConfig` /
  `Cot32SilentCotConfig` to a `Roy22SoftSpokenCoreCotConfig`.

The `temp/readme.md` "labelled proxy" footnotes for each protocol can then be removed and the runs
re-benched. Expected impact:

- **LAN**: small (Roy22's win is bandwidth, not latency). Probably within ±5 % of ALSZ13.
- **WAN**: large — PT26 in particular runs `O(peel rounds × OT batches)` round trips through the
  base OT path; SoftSpoken's lower kappa-base-OT count cuts setup chattiness substantially.

## References

- Lawrence Roy. *SoftSpokenOT: Quieter OT Extension From Small-Field Silent VOLE in the Minicrypt
  Model.* EUROCRYPT 2022. ePrint 2022/192. https://eprint.iacr.org/2022/192
- libOTe reference implementation: https://github.com/osu-crypto/libOTe/tree/master/libOTe/Tools/SoftSpokenOT
- `mpc4j-s2pc-pcg/src/main/java/edu/alibaba/mpc4j/s2pc/pcg/ot/cot/core/iknp03/` — the closest existing
  protocol; use as a structural template for byte-packing and RPC framing.
- `mpc4j-s2pc-pcg/src/main/java/edu/alibaba/mpc4j/s2pc/pcg/ot/cot/core/kos15/` — the closest existing
  malicious-security construction; use as a structural template for the future consistency-check step.
