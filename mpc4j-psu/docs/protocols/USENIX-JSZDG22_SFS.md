# USENIX:JSZDG22_SFS

JSZ22 shuffle variant (dual DOSN/ROSN + OPRF receiver).

## At a glance

| | |
|--|--|
| **Protocol ID** | `USENIX:JSZDG22_SFS` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = USENIX:JSZDG22_SFS` |
| **OT / cost** | INCLUDED_INIT (CM20/IKNP via OPRF only) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/jsz22/` |
| Maven artifact | `mpc4j-psu-protocol-jsz22` |
| Classes | `Jsz22SfsPsuConfig, Jsz22SfsPsuServer, Jsz22SfsPsuClient` |

### Init

firstDosnSender, secondDosnReceiver, firstRosnSender, secondRosnReceiver, oprfReceiver.init.

### Online

Dual DOSN/ROSN chain + OPRF evaluation and PEQT.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/04_JSZ22_SFS/` |
| Example config | `mpc4j-psu/bench/configs/psu/04_JSZ22_SFS/fair_bench_2p5.conf` |
| Output files | `temp/PSU_USENIX-JSZDG22_SFS_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only USENIX:JSZDG22_SFS --force
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
