# HN12 (Hazay–Nissim) implementation notes

HN12 is **polynomial / ElGamal-in-exponent / Pedersen / ZK / OPRF** based. It is **not** PGT26, ASIACCS:BlaAgu12, EUROCRYPT:PisTri26, CZZ24, or ZCL23.

## Protocols

| Name | Paper | Output |
|------|-------|--------|
| `JOC:HazNis12` | Protocol 5, π∩ | P1 (client) learns `X ∩ Y`; P2 (server) no output |
| `HN12_PSU` | Protocol 8, π∪ | Both parties learn `X ∪ Y` (after PSI works) |

## Roles (MPC4J PSI mapping)

- **Client = P1**: set `X`, builds encrypted polynomials, receives intersection.
- **Server = P2**: set `Y`, Pedersen commitments, homomorphic evaluation, PRF key holder.

## Tests / benchmarks

Only **`2^5`** (correctness, malicious) and **`2^20`** (honest E2E/bench) are required.

## Config flags

- `enableSemiHonestDebug`: FNP-style baseline (no malicious ZK).
- `useIdealPrfForTesting`: ideal PRF for `2^5` tests only; production malicious mode must use real OPRF (TODO).

## Package layout

`edu.alibaba.mpc4j.s2pc.pso.psi.hn12.*` — group, ElGamal, commit, zk, balanced, poly, prf, psi.
