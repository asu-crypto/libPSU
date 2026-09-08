# EUROCRYPT:PisTri26

Piske–Trieu 2026 IBLT peel + MP-OPRF + dual Core COT.

## At a glance

| | |
|--|--|
| **Protocol ID** | `EUROCRYPT:PisTri26` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | Two-sided (both parties learn `X0 ∪ X1`) |
| **Factory** | `PsuFactory.createTwoSidedServer` / `createTwoSidedClient` (one-sided factory rejects PT26) |
| **Driver** | `PsuMain` (two-sided routing) |
| **Config key** | `psu_pto_name = EUROCRYPT:PisTri26` |
| **OT / cost** | INCLUDED_INIT (OT12 + OT3 + MP-OPRF init) |
| **Init order** | Server: `init(maxServerElementSize, maxClientElementSize)`; Client: `init(maxClientElementSize, maxServerElementSize)` |

## Parameters

* IBLT hash count `k = 5`
* Ring `M = 2^(8·(elementByteLength+1))` (power-of-two wraparound; tagged BOT on OT/peel wire)
* Expansion from total threshold `τ = n0 + n1`:
  * `τ < 2^16` → `e = 4.5`
  * `2^16 ≤ τ < 2^18` → `e = 3.5`
  * `2^18 ≤ τ < 2^20` → `e = 2.0`
  * `τ ≥ 2^20` → `e = 1.5`
* These are empirical/extrapolated listing-failure parameters targeting roughly `2^-40` for evaluated balanced profiles; ordinary PSU tests do not prove that bound.
* After peel termination every local bin must have `cnt == 0` and `sum == 0` or both parties abort.

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/pt26/` |
| Maven artifact | `mpc4j-psu-protocol-pt26` |
| Classes | `Pt26PsuConfig, Pt26PsuServer, Pt26PsuClient, Pt26UnionPeel` (two-sided bases) |

### Init

IBLT keys RPC; ot12Receiver + ot3Sender + mpOprfSender init.

### Online

MP-OPRF in psu(), iterative peel with ot12/ot3 channels.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/14_PT26/` |
| Example config | `mpc4j-psu/bench/configs/psu/14_PT26/fair_bench_2p5.conf` |
| Output files | `temp/PSU_EUROCRYPT-PisTri26_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only EUROCRYPT:PisTri26 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Fidelity status

* OT polarity (`m0=BOT`, `m1=sum1` iff `cnt1==1`), two-sided output, power-of-two `Z_M`, residual IBLT abort: matched to the paper/reference behavior exercised by Java tests.
* Authors: **Piske–Trieu** (not “Pan–Tian”).
* Cross-language C++ IBLT peel/hash KAT against `IBLT-based-PSU` @ `ccfb9b3…` is **not yet claimed**.

## Notes

Skipped by run_psu_fair.sh when LOG >= 18 unless --no-skip-pt26.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
