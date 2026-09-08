# EUROCRYPT:PuGaoTri26

Pu–Gao–Trieu malicious two-sided (EC + AoK shuffle/RDDH).

## At a glance

| | |
|--|--|
| **Protocol ID** | `EUROCRYPT:PuGaoTri26` |
| **Functionality** | Balanced PSU |
| **Security** | Malicious |
| **Output** | Two-sided (both parties get union) |
| **Factory** | `PsuFactory (two-sided)` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = EUROCRYPT:PuGaoTri26` |
| **OT / cost** | NO_OT |
| **Init order** | Server: `init(maxServerElementSize, maxClientElementSize)`; Client: `init(maxClientElementSize, maxServerElementSize)` |
| **Public support** | **2M only** (`PGT26_1M` is rejected by `PsuFactory` / experimental quarantine) |

## Malicious checks

* Adapted shuffle and batched RDDH proofs are verified unconditionally (no production bypass).
* Proof commitments must be canonical prime-subgroup points (`isValidPrimeSubgroupPoint`); identity allowed for commitments.
* Statement points / public keys use `isValidNonIdentityPoint`.
* Figure 6 Round-4 coverage: receiver aborts if revealed index multiset ≠ expected difference indices (before RDDH verify).

Honest union tests do **not** certify malicious security.

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/malicious/pgt26/` |
| Maven artifact | `mpc4j-psu-protocol-pgt26` |
| Classes | `Pgt26_2mPsuServer/Client via PsuTwoSided* API` |

### Init

Pgt26PublicParams.setup(maxN) only.

### Online

Multi-round HashDH + proofs; no mpc4j OT layer.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/18_PGT26_2M/` |
| Example config | `mpc4j-psu/bench/configs/psu/18_PGT26_2M/fair_bench_2p5.conf` |
| Output files | `temp/PSU_EUROCRYPT-PuGaoTri26_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only EUROCRYPT:PuGaoTri26 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Fair benchmark always runs with shuffle proofs and RDDH proofs enabled.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
