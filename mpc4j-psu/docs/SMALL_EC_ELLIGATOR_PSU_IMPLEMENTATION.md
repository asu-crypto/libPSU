# Ours (`Ours`) — Implementation Guide

| | |
|--|--|
| **Display name** | Ours |
| **Internal ID** | `Ours` |
| **Protocol page** | [protocols/Ours.md](protocols/Ours.md) |

The **default fast small-set engineering baseline** in this fork is EC/Elligator-based. It is **not** standard leakage-free one-sided PSU: the server learns the sender-side set-difference / membership pattern (see [Security and output model](#security-and-output-model) below).

Global PSU wiring: [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md). Fair benchmark cost: [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md).

## Module layout

| | |
|--|--|
| **Current physical module path** | `mpc4j-psu/protocols/balanced/ours/` |
| **Target libPSU Stage-2 path** | `protocols/balanced/ours/` (already under `protocols/`) |
| **Artifact ID** | `mpc4j-psu-protocol-small-ec-elligator-psu` |
| **Status** | Migrated under `protocols/`; factory in `mpc4j-psu-balanced` |

| Field | Value |
|-------|--------|
| **Enum** | `PsuType.Ours` |
| **RPC / log name** | `CUSTOM_Ours` |
| **Java package** | `edu.alibaba.mpc4j.s2pc.pso.psu.smallec` |

---

## Security and output model

| | |
|--|--|
| **Security** | Semi-honest only |
| **Output** | Client (`PsuClient`) obtains the union |
| **Leakage** | Server learns sender-side difference / membership pattern (see below) |
| **Status** | Leakage baseline — **not** standard leakage-free one-sided PSU |

This protocol is a **semi-honest engineering baseline** with **sender-side set-difference leakage**. It must **not** be claimed as malicious-secure or as leakage-free one-sided PSU.

**Why:** After the client sends shuffled `U`, the server runs `SmallEcHashDhCore.filterDifference(...)` and sends only `W \ V` entries via `sendUDiff`. The server therefore learns **which** of its own elements are outside the receiver set (and logs `|U_diff|` when `small_ec_log_stats=true`). That reveals a **membership / set-difference pattern** on the sender set with respect to the receiver set.

Do not advertise this protocol as malicious-secure or as leakage-free one-sided PSU. To obtain standard one-sided PSU, the final delivery step must be replaced by an oblivious delivery / OT / AHE-style **full-vector masked retrieval** so the server does not learn the selected difference indices.

ZK/AoK/RDDH steps from PGT26 are **not** implemented.

---

## Party roles (mpc4j convention)

| Role | mpc4j class | Set | Output |
|------|-------------|-----|--------|
| **P0 / server** | `SmallEcElligatorPsuServer` (`PsuServer`) | Sender set **W** (`serverElementSet`) | None |
| **P1 / client** | `SmallEcElligatorPsuClient` (`PsuClient`) | Receiver set **V** | **V ∪ (W \ V)** via `PsuClientOutput.getUnion()` |

Driver convention: **server = sender (P0)**, **client = receiver / output party (P1)** — same as `PsuMain` and other one-sided PSU protocols.

**Note on source Javadoc:** `SmallEcElligatorPsuClient` / `SmallEcElligatorPsuServer` class comments use inverted “P0 receiver / P1 sender” labels (paper-style). **Benchmark logs and `PsuMain` roles follow mpc4j:** server holds W, client holds V and prints union size on the client side only.

---

## Cryptographic core

| Component | Class | Role |
|-----------|--------|------|
| `EcGroupOps` | `smallec.crypto.EcGroupOps` | Ed25519 scalars/points, canonical encodings |
| `IdealPermutation` | `smallec.crypto.IdealPermutation` | Π / Π⁻¹ over γ=256-bit blocks |
| `FeistelIdealPermutation` | `smallec.crypto.FeistelIdealPermutation` | 3-round Feistel PRP; domain tag `Ours_PERM_V1` |
| `ElligatorCodec` | `smallec.crypto.ElligatorCodec` | `H_EC(x) = M2P(Π(enc(x)))` and inverse (reuses PGT26 field map) |

No Paillier/RLWE/OT/homomorphic code is used in this module (`NO_OT` in fair benchmarks).

---

## Protocol flow (4 messages)

1. **Client → Server:** `|V|` blinded points `X_i = H_EC(v_i)^{k0}`
2. **Server → Client:** `|W|` blinded points `Y_j = H_EC(w_j)^{k1}`
3. **Client → Server:** `|W|` shuffled `U_j = Y_{π(j)}^{k0}` plus permutation metadata (semi-honest engineering)
4. **Server → Client:** `|W \ V|` entries `u'_j || w_j` (48 bytes each): only **difference** rows after server-side filter — **this step leaks the difference pattern to the server**

The client decodes union elements from the difference payload plus its own set `V`. Decoding tries `inversePointToItem(R_j)` first; if empty, verifies `mapToPoint(w_j) == R_j` using paired item bytes (semi-honest binding).

---

## Configuration

```properties
psu_pto_name = Ours
element_byte_length = 16
small_ec_item_bit_length = 128
small_ec_log_stats = false
```

Fair benchmark: `mpc4j-psu/tests/src/test/resources/psu/22_Ours/` (ports **19206** / **19207**).

Driver JAR (current path): `mpc4j-psu/apps/driver/target/mpc4j-psu-driver-*-jar-with-dependencies.jar` via `PsuMain`.
