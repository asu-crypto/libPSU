# OT / Base OT benchmark cost audit

This document records how **Base OT** and **Core COT** setup are accounted for in fair benchmarks driven by `PsuMain`, `PsiMain`, `UpsuMain`, and `Ba12Main`.

## Benchmark accounting model

`PsuMain`, `PsiMain`, and `UpsuMain` use the same pattern (see `PsuMain.runServer`):

1. `rpc.reset()` immediately before the timed region.
2. Wall-clock **Init Time** = `protocol.init(...)`.
3. **Init Send Bytes** = `rpc.getSendByteLength()` after init (then `reset()`).
4. Wall-clock **Pto Time** = `protocol.psu(...)` / `psi(...)` / `upsu(...)`.
5. **Pto Send Bytes** = `rpc.getSendByteLength()` after the online phase.

Sub-protocols share the root `Rpc`, so **any** wire traffic during `init()` counts toward Init Send Bytes, including Base OT executed inside `CoreCot.init()` (e.g. ALSZ13 calling `BaseOtSender.send(128)` while the parent PSU is in `init()`).

**Reference pattern:** `PKC:CheZhaZha24` (`PKC:CheZhaZha24`) — `coreCotSender.init(delta)` / `coreCotReceiver.init()` in PSU `init()`; online `send`/`receive` in PSU `psu()`.

**Not counted in Init/Pto (separate columns):** `OoPsuMain` runs `preCompute()` in its own **Pre** phase (ROSN / mqRPMT offline permutation). Fair PSU scripts use `PsuMain`, which does **not** call `preCompute()` for PKC:GMRSS21/JSZ22.

**`Ba12Main`** (garbled-circuit set ops, `pto_type=ASIACCS:BlaAgu12`) follows the same measured-window pattern: `rpc.reset()`, timed `Ba12SetOpsParty.init()` (Init time/bytes), `rpc.reset()`, timed online set-op phase (`runBinary`, etc.), Pto time/bytes. ASIACCS:BlaAgu12 does **not** run through `PsuMain`.

## Summary table

| Display name | Internal ID | Category | Uses OT/COT | OT/COT type | Base OT type | Base OT counted? | Phase | Fix applied | Notes |
|--------------|-------------|----------|-------------|-------------|--------------|------------------|-------|-------------|-------|
| PKC:CheZhaZha24 | PKC:CheZhaZha24 | PSU | yes | ALSZ13 Core COT | NP01_BYTE (via ALSZ13) | INCLUDED_INIT | init() | none (reference) | mqRPMT is ECC-only; COT for union XOR |
| PKC:GMRSS21 | PKC:GMRSS21 | PSU | yes | ALSZ13 + KKRT OPRF (×2) + ALSZ13 union | NP01_BYTE | INCLUDED_INIT | init() | none | Extra KKRT COT inits inside mqRPMT OPRFs; union Core COT in PSU init |
| USENIX:JSZDG22 | USENIX:JSZDG22 | PSU | yes | IKNP (OPRF CM20) + ALSZ13 union | NP01 / IKNP base | INCLUDED_INIT | init() | none | Duplicate Core COT: inside `oprfSender.init` and `coreCotSender.init` |
| USENIX:JSZDG22_SFS | USENIX:JSZDG22_SFS | PSU | yes | IKNP (OPRF CM20 only) | NP01 / IKNP base | INCLUDED_INIT | init() | none | No standalone union COT; OT only via CM20 OPRF init |
| AC:AC:KRTW19 | AC:KRTW19 | PSU | yes | KKRT OPRF (×2) + ALSZ13 union | NP01_BYTE | INCLUDED_INIT | init() | none | Triple KKRT+COT inits in init + union Core COT |
| USENIX:ConYuWeiminDon23_PKE | USENIX:ConYuWeiminDon23_PKE | PSU | yes | ALSZ13 Core COT | NP01_BYTE | INCLUDED_INIT | init() | none | Same shape as PKC:CheZhaZha24 |
| USENIX:ConYuWeiminDon23_SKE | USENIX:ConYuWeiminDon23_SKE | PSU | yes | ALSZ13 + Z2c + OPRP | NP01_BYTE | INCLUDED_INIT | init() | none | **PsuMain = 2-party**; optional 3-party+aider overload exists; fair bench fragile (LowMC NPE) |
| ASIACCS:CSSW25 | ASIACCS:CSSW25 | PSU | yes | MP-OPRF + PSTY19 CCPSI + ROSN + Core COT (RS21 opt-in) | NP01 or Roy22 (config) | INCLUDED_INIT | init() | none | Experimental runnable proxy; exact mode remains reserved |
| EUROCRYPT:PisTri26 | EUROCRYPT:PisTri26 | PSU | yes | 2× ALSZ13 (OT12 + OT3) + MP-OPRF | NP01_BYTE default | INCLUDED_INIT | init() | none | `ot12Receiver.init` + `ot3Sender.init(delta)` in PSU init |
| EUROCRYPT:PuGaoTri26 | EUROCRYPT:PuGaoTri26 | PSU | no | — | — | NO_OT | — | none | EC/ZK only |
| USENIX:YanShiHonDaw24 | USENIX:YanShiHonDaw24 | PSU | yes | LNOT (COT-NC) + BOPPRF + PEQT + DOSN | NC-COT chain in LNOT init | INCLUDED_INIT | init() | none | `lnotSender.init(1, bMax)` in PSU init |
| CCS:TCLZ23 | CCS:TCLZ23 | UPSU | yes | ALSZ13 Core COT | NP01_BYTE | INCLUDED_INIT | init() | none | Same Core COT pattern as PKC:CheZhaZha24 |
| ACISP:DavCid17 | ACISP:DavCid17 | PSU | no | PHE / EIBF | — | NO_OT | — | none | |
| ACNS:Frikken07 | ACNS:Frikken07 | PSU | no | polynomial / legacy | — | NO_OT | — | none | |
| C:KisSon05 | C:KisSon05 | PSU | no | PHE (Paillier poly union) | — | NO_OT | — | none | Same algebra family as F07; fair bench `psu_pto_name` |
| JOC:HazNis12 | JOC:HazNis12 | PSU | no | DDH / ElGamal (Protocol 8 π∪) | — | NO_OT | — | none | Semi-honest/debug fair path; legacy PSI unit tests only |
| Ours | Ours | PSU | no | EC HashDH | — | NO_OT | — | none | **Sender-side set-diff leakage** (not standard PSU); see Ours doc |
| ASIACCS:BlaAgu12 | ASIACCS:BlaAgu12 | GC / MPC set ops | yes | Bea91 `Z2c` → Z2 triple + COT (ALSZ13 chain) | NP01 via COT | INCLUDED_INIT | `Ba12SetOpsParty.init()` | none | **Not** `PsuMain`; `Ba12Main` + `pto_type=ASIACCS:BlaAgu12`; fair scripts include ASIACCS:BlaAgu12 |
| USENIX:BinYujConYanYu25 | USENIX:BinYujConYanYu25 | PSU / UPSU | yes | RS21 MP-OPRF + Core COT (nECRG) | NP01_BYTE (via ALSZ13) | INCLUDED_INIT | init() | none | Balanced pnMCRG + OTP; linear UPSU wrapper |
| USENIX:HaoWan26 | USENIX:HaoWan26 | PSU | yes | RS21 + Core COT (ssPMT-fast + ssOTd) | NP01_BYTE (via ALSZ13) | INCLUDED_INIT | init() | none | ePSU-fast only |

**ASIACCS:BlaAgu12** (`Ba12Main`, not `PsuMain`): `Ba12SetOpsParty.init()` → `z2c.init()` → `Bea91Z2c` initializes COT sender/receiver (Base OT inside that init). Timed the same way as PSU Init columns (`rpc.reset()` then `party.init()`).

## Per-protocol init / online map (OT-bearing)

| Protocol | Base OT instantiated | Initialized in | Wire messages (typical) | preCompute? |
|----------|----------------------|----------------|-------------------------|-------------|
| CZZ24 | `Alsz13CoreCot` → `Np01ByteBaseOt` | `coreCot*.init()` in PSU `init()` | Base OT in init; ALSZ13 matrix in `psu()` | no |
| PKC:GMRSS21 | ALSZ13 union + KKRT×2 in mqRPMT | all in `init()` | Base OT in init (multiple instances) | optional, **not** in `PsuMain` fair bench |
| JSZ22 SFC | CM20/IKNP + ALSZ13 union | `init()` | Base OT in init (OPRF + union) | optional ROSN, not in fair bench |
| JSZ22 SFS | CM20/IKNP in OPRF only | `init()` | Base OT in init | optional ROSN |
| AC:KRTW19 | KKRT×2 + ALSZ13 | `init()` | Base OT in init | no |
| ZCL23 PKE/SKE | ALSZ13 (+ Z2/OPRP for SKE) | `init()` | Base OT in init | no |
| ASIACCS:CSSW25 | MP-OPRF + Core COT (+ CCPSI internals) | `init()` | Base OT in init | no |
| EUROCRYPT:PisTri26 | 2× Core COT + MP-OPRF | `init()` | Base OT in init; peel uses COT in `psu()` | no |
| JSZG24 | LNOT → NC-COT → … | `lnotSender.init` in PSU `init()` | LNOT/NC-COT setup in init; eqOTe in `psu()` | no |
| CCS:TCLZ23 | ALSZ13 | `init()` | same as CZZ24 | no |
| USENIX:BinYujConYanYu25 | RS21 + Core COT (nECRG) | `init()` | MP-OPRF + pnMCRG in `psu()` | no |
| ASIACCS:BlaAgu12 | Bea91 Z2c → COT | `Ba12SetOpsParty.init()` in `Ba12Main` | Base OT in Init; GC online in Pto | no |

## Findings

- **No protocol fix was required** for fair `PsuMain` / `PsiMain` / `UpsuMain` benchmarks: every runnable OT/COT PSU/UPSU already calls the corresponding sub-protocol `init()` inside its own `init()`, and Base OT bytes occur inside that window.
- **CZZ24** remains the canonical reference (documented above).
- **OoPsuMain** exposes a separate **Pre** phase for ROSN/mqRPMT preprocessing; that is intentional and not Base OT.
- **JSZ22 / PKC:GMRSS21** may run **multiple** Base OT setups in one init (OPRF + union). That **over**-counts versus a minimal single-COT design but does **not** exclude Base OT from totals.
- **USENIX:BinYujConYanYu25** is runnable as balanced PSU (`PsuType.USENIX:BinYujConYanYu25`) and linear UPSU (`UpsuType.USENIX:BinYujConYanYu25`). Paper FHE unbalanced MCRG (Fig. 14) is not implemented.
- **ASIACCS:BlaAgu12** is fair-benchmarked via **`Ba12Main`** (garbled-circuit MPC union), not `PsuType`; OT is via **Z2c**, not DH/OPRF PSU.
- **Ours** has **no OT** but **does** leak sender-side set difference to the server; OT audit `NO_OT` does not imply leakage-free PSU.
- Non-`_PROXY` PKC:GMRSS21/JSZ22 enum values are not part of the current fair benchmark reactor.

## Debug logging

- Shared helper: `edu.alibaba.mpc4j.psu.common.OtBenchmarkMetrics` (`OT_BASE_DEBUG` log lines).
- **CZZ24** server logs init/pto splits (`membershipInitMs` = mqRPMT init, `cotInitMs` = Core COT init including Base OT).
- **ASIACCS:CSSW25** / **EUROCRYPT:PisTri26** already log phase byte splits (`setupOtBytes`, peel channels).

## Tests

`edu.alibaba.mpc4j.psu.audit.OtBaseCostAuditTest` (physical path `tests/`, artifact ID `mpc4j-psu-tests`):

- Config scan (`OtConfigInspector`) for OT-bearing vs non-OT protocols.
- Memory-RPC init: asserts Init Send Bytes ≥ threshold for CZZ24, PKC:GMRSS21, AC:KRTW19, ZCL23 PKE, JSZ22 SFC, EUROCRYPT:PisTri26, JSZG24; asserts ACISP:DavCid17 stays minimal.

## Manual review

| Item | Status |
|------|--------|
| USENIX:BinYujConYanYu25 fair `.output` under `temp/` | Re-run with `08_USENIX:BinYujConYanYu25` / `10_USENIX:BinYujConYanYu25` configs after 2026-06 USENIX:BinYujConYanYu25 integration |
| `Ours` leakage | Documented; protocol not standard one-sided PSU |
| USENIX:ConYuWeiminDon23_SKE fair bench | 2-party in `PsuMain`; may fail at runtime (LowMC); 3-party+aider is separate API |
| ASIACCS:BlaAgu12 vs PSU OT stacks | Different primitive family (Z2c/GC); both count OT setup in their respective Init phases |

## Validation evidence

| Field | Value |
|-------|--------|
| **Date** | 2026-06-02 (documentation consistency pass) |
| **Git commit** | `ea3b9aa99389adbe6219b0b909d2cc39cacad2bd` |
| **Audit commands** | `git rev-parse HEAD`; `grep -R "mpc4j-psu-protocol-\|mpc4j-psu-driver\|mpc4j-psu-tests" mpc4j-psu/docs` (stale-path sweep); manual review of taxonomy labels and `Ba12Main` opening |
| **Test commands** | `mvn -f mpc4j-psu/pom.xml -pl tests -Dtest=OtBaseCostAuditTest test`; `mvn -f mpc4j-psu/pom.xml -pl tests -Dtest=LibPsuMetadataSmokeTest test` |
| **Test result** | **Passed** (both test classes) |
| **Build (optional)** | `mvn -f mpc4j-psu/pom.xml install -DskipTests` not re-run for this pass |

**Not automated in tests:** ASIACCS:BlaAgu12 Init OT bytes (separate `Ba12Main`); USENIX:BinYujConYanYu25 UPSU at large unbalanced sizes; SMALL_EC leakage (documented only).
