# ACISP:DavCid17

Davidson–Cid Bloom-filter + Paillier AHE.

## At a glance

| | |
|--|--|
| **Protocol ID** | `ACISP:DavCid17` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = ACISP:DavCid17` |
| **OT / cost** | NO_OT |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/dc17/` |
| Maven artifact | `mpc4j-psu-protocol-dc17` |
| Classes | `Dc17PsuConfig, Dc17PsuServer, Dc17PsuClient` |

### Init

Minimal or empty.

### Online

PHE operations in psu().

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/15_DC17/` |
| Example config | `mpc4j-psu/bench/configs/psu/15_DC17/fair_bench_2p5.conf` |
| Output files | `temp/PSU_ACISP-DavCid17_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only ACISP:DavCid17 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
