# HN12 (Hazay–Nissim)

Polynomial / ElGamal-in-exponent / Pedersen / ZK / PRF based private set protocols.

**Not** PGT26, ASIACCS:BlaAgu12, EUROCRYPT:PisTri26, CZZ24, or ZCL23.

## Fair-bench protocol (PSU)

| ID | Paper | Output |
|------|-------|--------|
| `JOC:HazNis12` | Protocol 8, π∪ | Client (P1) learns `X ∪ Y`; server receives union (`Y ⊆ U` check) |

- **Classes:** `Hn12PsuConfig`, `Hn12PsuServer`, `Hn12PsuClient`
- **Factory / driver:** `PsuFactory` / `PsuMain` (`psu_pto_name = JOC:HazNis12`)
- **Bench configs:** `bench/configs/psu/24_HN12/`, unbalanced harness under `upsu/24_HN12/` (and legacy `upsu/19_HN12_PSI/` path)
- **Default bench flags:** `hn12_semi_honest_debug=true`, ideal PRF (full malicious πCOUNT/πNZ not complete)

## Legacy PSI (unit tests)

| ID | Paper | Output |
|------|-------|--------|
| Protocol 5 π∩ | `Hn12Psi*` | Client learns `X ∩ Y` |

```bash
mvn -pl mpc4j-psu-tests -Dmaven.test.skip=false -Dtest=Hn12GroupElGamal2p5Test,Hn12Psi2p5Test test
```

## Config flags

- `hn12_semi_honest_debug` / `enableSemiHonestDebug`: FNP-style baseline (skip malicious ZK verify).
- `hn12_use_ideal_prf` / `useIdealPrfForTesting`: ideal PRF for tests and fair bench; production malicious mode needs real OPRF (TODO).

## Package layout

`edu.alibaba.mpc4j.s2pc.pso.psi.hn12.*` — group, ElGamal, commit, zk, balanced, poly, prf, PSI + PSU.
