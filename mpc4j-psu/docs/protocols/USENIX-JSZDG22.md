# USENIX:JSZDG22

JSZ22 shuffle PSU (DOSN, ROSN, CM20 OPRF + standalone Core COT).

## At a glance

| | |
|--|--|
| **Protocol ID** | `USENIX:JSZDG22` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = USENIX:JSZDG22` |
| **OT / cost** | INCLUDED_INIT (IKNP in OPRF + ALSZ13 union COT) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/jsz22/` |
| Maven artifact | `mpc4j-psu-protocol-jsz22` |
| Classes | `Jsz22SfcPsuConfig, Jsz22SfcPsuServer, Jsz22SfcPsuClient` |

### Init

dosnReceiver.init, rosnReceiver.init, oprfSender.init, coreCotSender.init.

### Online

Permutation/OSN, OPRF, PEQT, coreCotSender.send for encrypted payloads.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/03_JSZ22_SFC/` |
| Example config | `mpc4j-psu/bench/configs/psu/03_JSZ22_SFC/fair_bench_2p5.conf` |
| Output files | `temp/PSU_USENIX-JSZDG22_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only USENIX:JSZDG22 --force
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
