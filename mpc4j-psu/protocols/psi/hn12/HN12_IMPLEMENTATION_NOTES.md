# HN12 (Hazay–Nissim) implementation notes

HN12 is **polynomial / ElGamal-in-exponent / Pedersen / ZK / OPRF** based. It is **not** PGT26, ASIACCS:BlaAgu12, EUROCRYPT:PisTri26, CZZ24, or ZCL23.

## Protocols

| Name | Paper | Status | Output |
|------|-------|--------|--------|
| `JOC:HazNis12` (fair bench) | Protocol 8, π∪ | **Experimental semi-honest/debug** (`Hn12Psu*`) | **Two-sided**: P1 learns `X ∪ Y`; P2 also receives the full union |
| Protocol 5, π∩ | Legacy PSI | Unit tests (`Hn12Psi*`) | P1 learns `X ∩ Y`; P2 no output |

Protocol 8 differs from Protocol 5 after polynomial evaluations: recover server elements when **both** evaluations are nonzero (`Y \ X`), form the union, and send it to P2. Fair bench uses the semi-honest/debug path only.

**Malicious security is not implemented.** Setting `enableSemiHonestDebug=false` fails closed at config `build()` because πCOUNT / πNZ verification and the production OPRF are incomplete. Do not advertise this stack as maliciously secure.

## Roles (MPC4J mapping)

- **Client = P1**: set `X`, builds encrypted polynomials, recovers difference / union.
- **Server = P2**: set `Y`, Pedersen commitments, homomorphic evaluation, PRF key holder; receives the full union from P1.

## Tests / benchmarks

- **PSU fair bench:** `psu_pto_name = JOC:HazNis12` under `bench/configs/psu/24_HN12/`.
- **PSI unit tests:** `2^5` correctness (`Hn12Psi2p5Test`, group/ElGamal tests).

## Config flags

- `enableSemiHonestDebug` (default `true`): required for the runnable path. `false` is rejected.
- `useIdealPrfForTesting` / `hn12_use_ideal_prf` (default `true`): **test/benchmark-only** ideal PRF; not a production OPRF.

## Package layout

`edu.alibaba.mpc4j.s2pc.pso.psi.hn12.*` — group, ElGamal, commit, zk, balanced, poly, prf, psi, psu.
