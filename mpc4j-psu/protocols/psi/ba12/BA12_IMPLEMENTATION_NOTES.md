# ASIACCS:BlaAgu12 implementation notes (Blanton–Aguiar)

## Model

- **Not** a 2-party PSU/PSI protocol. ASIACCS:BlaAgu12 is **multi-party set logic over secret shares**.
- Current integration uses the repo’s **2-party XOR bit sharing** (`Bea91Z2c` + `Z2IntegerCircuit`), which matches semi-honest `t < n/2` when `n = 2`.
- Elements are **positive integers** in `[1, 2^ℓ − 1]`; **`0` is the dummy / erased value**.

## Reused primitives

| ASIACCS:BlaAgu12 building block | MPC4J wrapper |
|---------------------|---------------|
| `[x] + [y]`, `[x] − [y]`, `c·[x]` | `Z2IntegerCircuit.add/sub`, local XOR on shares |
| `[z] = [x]·[y]` (integer × bit) | `MpcZ2cParty.and` with broadcast bit |
| `Eq([x],[y],ℓ)` | `Z2IntegerCircuit.eq` |
| `GE([x],[y],ℓ)` | `leq(y,x)` (unsigned `x ≥ y`) |
| `PreAND` | sequential `and` chain on shared bits |
| `Open` | `Z2cParty.revealOwn/revealOther` (tests / length-preserving only) |
| `Sort` | `Z2IntegerCircuit.sort` → **Batcher bitonic** network (`SorterTypes.BITONIC`) |
| `SortT` | `Z2IntegerCircuit.psort` (value + origin-bit payload) |

## Not used

DH, OPRF, OKVS, mqRPMT, IBLT, OT, or public-key PSU machinery.

## Package layout

- `edu.alibaba.mpc4j.s2pc.ba12` — config, types, party coordinator
- `edu.alibaba.mpc4j.s2pc.ba12.core` — `Ba12Share`, `Ba12MpcEngine`, `Ba12Plaintext`, stats
- `edu.alibaba.mpc4j.s2pc.ba12.set` — Protocols 1–5 (union, intersection, difference, …)
- `edu.alibaba.mpc4j.s2pc.ba12.relation` — subset / superset / equality
- `edu.alibaba.mpc4j.s2pc.ba12.cardinality` — cardinality + threshold variants

## Tests / benchmarks

- Correctness: `2^5` only (`Ba12*2p5Test` in `mpc4j-psu-tests`).
- Performance: `fair_bench_2p20.conf` under `mpc4j-psu-tests/src/test/resources/ba12/`.
