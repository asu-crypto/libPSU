# Protocol mapping (libPSU)

**Per-protocol documentation:** [protocols/README.md](protocols/README.md) — one page per protocol, titled with the **bench display name** (e.g. `PKC:CheZhaZha24`).

Display names map from internal enum IDs via `scripts/libpsu/protocol_display_names.py`.

Maven **artifact IDs** are unchanged (`mpc4j-psu-protocol-*`). **Physical paths** are under `mpc4j-psu/protocols/...`. Fair-bench configs live under `mpc4j-psu/bench/configs/`.

## Balanced PSU

| Display name | Internal ID | Path | Module artifact | Family | Output | Security |
|--------------|-------------|------|-----------------|--------|--------|----------|
| AC:AC:KRTW19 | AC:KRTW19 | `protocols/balanced/krtw19` | mpc4j-psu-protocol-krtw19 | RPMT | one-sided | semi-honest |
| PKC:GMRSS21 | PKC:GMRSS21 | `protocols/balanced/gmr21` | mpc4j-psu-protocol-gmr21 | RPMT | one-sided | semi-honest |
| USENIX:JSZDG22 | USENIX:JSZDG22 | `protocols/balanced/jsz22` | mpc4j-psu-protocol-jsz22 | shuffle | one-sided | semi-honest |
| USENIX:JSZDG22_SFS | USENIX:JSZDG22_SFS | `protocols/balanced/jsz22` | mpc4j-psu-protocol-jsz22 | shuffle | one-sided | semi-honest |
| USENIX:ConYuWeiminDon23_PKE | USENIX:ConYuWeiminDon23_PKE | `protocols/balanced/zcl23` | mpc4j-psu-protocol-zcl23 | PKE | one-sided | semi-honest |
| USENIX:ConYuWeiminDon23_SKE | USENIX:ConYuWeiminDon23_SKE | `protocols/balanced/zcl23` | mpc4j-psu-protocol-zcl23 | SKE | one-sided | semi-honest |
| PKC:CheZhaZha24 | PKC:CheZhaZha24 | `protocols/balanced/czz24` | mpc4j-psu-protocol-czz24 | OPRF | one-sided | semi-honest |
| ASIACCS:CSSW25 | ASIACCS:CSSW25 | `protocols/balanced/cssw25` | mpc4j-psu-protocol-css25 | MP-OPRF + CCPSI + ROSN + COT | one-sided | semi-honest |
| USENIX:BinYujConYanYu25 | USENIX:BinYujConYanYu25 | `protocols/balanced/tbz25` | mpc4j-psu-protocol-tbz25 | pnMCRG + OTP (ePSU) | one-sided | semi-honest |
| USENIX:HaoWan26 | USENIX:HaoWan26 | `protocols/balanced/hwy26` | mpc4j-psu-protocol-haowan2026 | ssPMT-fast + ssOTd (ePSU-fast) | one-sided | semi-honest |
| EUROCRYPT:PisTri26 | EUROCRYPT:PisTri26 | `protocols/balanced/pt26` | mpc4j-psu-protocol-pt26 | IBLT | one-sided | semi-honest |
| ACISP:DavCid17 | ACISP:DavCid17 | `protocols/balanced/dc17` | mpc4j-psu-protocol-dc17 | Bloom-filter + PHE/AHE | one-sided | semi-honest |
| ACNS:Frikken07 | ACNS:Frikken07 | `protocols/balanced/fri07` | mpc4j-psu-protocol-f07 | polynomial + PHE/AHE | one-sided | semi-honest |
| C:KisSon05 | C:KisSon05 | `protocols/psi/ks05` | mpc4j-psu-protocol-ks05 | polynomial + PHE/AHE | one-sided | semi-honest |
| JOC:HazNis12 | JOC:HazNis12 | `protocols/psi/hn12` | mpc4j-psu-protocol-hn12 | DDH / ElGamal / ZK (Protocol 8 π∪) | one-sided | semi-honest (debug ZK) |
| Ours | Ours | `protocols/balanced/ours` | mpc4j-psu-protocol-small-ec-elligator-psu | EC/Elligator HashDH | client union (**leakage baseline**) | semi-honest |

### Malicious / two-sided (balanced)

| Display name | Internal ID | Path | Output | Security |
|--------------|-------------|------|--------|----------|
| USENIX:YanShiHonDaw24 | USENIX:YanShiHonDaw24 | `protocols/malicious/jszg24` | one-sided | semi-honest |
| EUROCRYPT:PuGaoTri26 | EUROCRYPT:PuGaoTri26 | `protocols/malicious/pgt26` | two-sided | malicious |

**Classes / config:** see per-protocol pages under [protocols/](protocols/README.md). `USENIX:ConYuWeiminDon23_SKE` fair bench uses 2-party `PsuMain`; optional 3-party+aider overload exists.

## Unbalanced UPSU

| Display name | Internal ID | Path | Factory | Config key |
|--------------|-------------|------|---------|------------|
| CCS:TCLZ23 | CCS:TCLZ23 | `protocols/unbalanced/tcl23` | UpsuFactory | `upsu_pto_name` |
| USENIX:BinYujConYanYu25 | USENIX:BinYujConYanYu25 | `protocols/unbalanced/tbz25` | UpsuFactory | `upsu_pto_name` |

Fair bench: `bench/configs/upsu/09_CCS:TCLZ23/`, `bench/configs/upsu/10_USENIX:BinYujConYanYu25/`.

**USENIX:BinYujConYanYu25 UPSU note:** Uses the balanced pnMCRG + OTP core (linear in sender and receiver sizes). Paper sublinear FHE MCRG (Fig. 14) is not implemented.

## Legacy PSI drivers (unit tests only)

Fair benchmarks use the PSU configs above (`psu_pto_name`). The same modules still expose
`PsiType` / `PsiFactory` / `PsiMain` for Protocol 5 (HN12) and Kissner–Song PSI unit tests
(`psi_pto_name`, configs under `bench/configs/psi/`). Prefer PSU for SoK comparisons.

## ASIACCS:BlaAgu12 (separate `pto_type`)

| Display name | Internal ID | Path | Main | Config |
|--------------|-------------|------|------|--------|
| ASIACCS:BlaAgu12 | ASIACCS:BlaAgu12 | `protocols/psi/ba12` | `Ba12Main` in `apps/driver` | `Ba12Config` |

Fair benchmark: `bench/configs/ba12/01_ASIACCS:BlaAgu12/fair_bench_*.conf`, output prefix `PSU_ASIACCS:BlaAgu12_*`. Garbled-circuit MPC (Bea91 `Z2c`), not DH/OPRF `PsuType`.

## Reserved / not runnable

| Name | Status |
|------|--------|
| ASIACCS:CSSW25 exact mode | `css25_paper_exact=true` reserved until paper-exact implementation is available |
| USENIX:BinYujConYanYu25 FHE unbalanced | Paper Fig. 14 (FHE + bOPRF pMCRG) not implemented; runnable UPSU uses linear pnMCRG path |
| PKC:GMRSS21_PROXY, JSZ22_*_PROXY | Deprecated enum aliases; use non-proxy IDs |

## Metadata registry

Canonical SoK fields: `edu.alibaba.libpsu.api.ProtocolMetadataRegistry`.

Config property names (unchanged): `psu_pto_name`, `psi_pto_name`, `upsu_pto_name`, `ba12_pto_name`.
