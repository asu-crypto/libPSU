# HN12 (Hazay–Nissim) implementation notes

HN12 is **polynomial / ElGamal-in-exponent / Pedersen / ZK / OPRF** based. It is **not** PGT26, ASIACCS:BlaAgu12, EUROCRYPT:PisTri26, CZZ24, or ZCL23.

## Protocols

| Name | Paper | Status | Output |
|------|-------|--------|--------|
| `JOC:HazNis12` (fair bench) | Protocol 8, π∪ | **Implemented** (`Hn12Psu*`) | P1 learns `X ∪ Y`; P2 receives union |
| Protocol 5, π∩ | Legacy PSI | Unit tests (`Hn12Psi*`) | P1 learns `X ∩ Y`; P2 no output |

Protocol 8 differs from Protocol 5 after polynomial evaluations: recover server elements when **both** evaluations are nonzero (`Y \ X`), form the union, and send it to P2. Fair bench uses the semi-honest/debug path; full malicious πCOUNT / πNZ checks are incomplete.

## Roles (MPC4J mapping)

- **Client = P1**: set `X`, builds encrypted polynomials, recovers difference / union.
- **Server = P2**: set `Y`, Pedersen commitments, homomorphic evaluation, PRF key holder.

## Tests / benchmarks

- **PSU fair bench:** `psu_pto_name = JOC:HazNis12` under `bench/configs/psu/24_HN12/`.
- **PSI unit tests:** `2^5` correctness (`Hn12Psi2p5Test`, group/ElGamal tests).

## Config flags

- `enableSemiHonestDebug` / `hn12_semi_honest_debug`: FNP-style baseline (no malicious ZK verify).
- `useIdealPrfForTesting` / `hn12_use_ideal_prf`: ideal PRF for tests and fair bench; production malicious mode must use real OPRF (TODO).

## Package layout

`edu.alibaba.mpc4j.s2pc.pso.psi.hn12.*` — group, ElGamal, commit, zk, balanced, poly, prf, psi, psu.
