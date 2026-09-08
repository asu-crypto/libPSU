# Ours

Small-set EC/Elligator HashDH.

## At a glance

| | |
|--|--|
| **Protocol ID** | `Ours` |
| **Functionality** | Balanced PSU (leakage baseline) |
| **Security** | Semi-honest (leakage baseline) |
| **Output** | One-sided client union; server learns W \ V pattern |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = Ours` |
| **OT / cost** | NO_OT |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/ours/` |
| Maven artifact | `mpc4j-psu-protocol-small-ec-elligator-psu` |
| Classes | `SmallEcElligatorPsuConfig, SmallEcElligatorPsuServer/Client` |

### Init

No-op (no RPC).

### Online

Blinded X/Y, shuffled U, server filters difference, client rebuilds union.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/22_SMALL_EC_ELLIGATOR_PSU/` |
| Example config | `mpc4j-psu/bench/configs/psu/22_SMALL_EC_ELLIGATOR_PSU/fair_bench_2p5.conf` |
| Output files | `temp/PSU_Ours_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only Ours --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Not standard leakage-free PSU. Default element_byte_length = 16 (128-bit items).

## Detailed guide

Full implementation write-up: [../SMALL_EC_ELLIGATOR_PSU_IMPLEMENTATION.md](../SMALL_EC_ELLIGATOR_PSU_IMPLEMENTATION.md)

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
