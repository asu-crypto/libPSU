# ACNS:Frikken07

Legacy polynomial + Paillier AHE.

## At a glance

| | |
|--|--|
| **Protocol ID** | `ACNS:Frikken07` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = ACNS:Frikken07` |
| **OT / cost** | NO_OT |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/f07/` |
| Maven artifact | `mpc4j-psu-protocol-f07` |
| Classes | `ACNS:Frikken07PsuConfig, ACNS:Frikken07PsuServer, ACNS:Frikken07PsuClient` |

### Init

Minimal.

### Online

Polynomial + PHE in psu().

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/16_F07/` |
| Example config | `mpc4j-psu/bench/configs/psu/16_F07/fair_bench_2p5.conf` |
| Output files | `temp/PSU_ACNS-Frikken07_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only ACNS:Frikken07 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Distinct from ACISP:DavCid17 (Bloom vs polynomial).

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
