# AC:KRTW19

Hash-bin RPMT (polynomial PEQT + KKRT OPRF ×2) + Core COT union.

## At a glance

| | |
|--|--|
| **Protocol ID** | `AC:KRTW19` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = AC:KRTW19` |
| **OT / cost** | INCLUDED_INIT (KKRT OPRF ×2 + ALSZ13 Core COT) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/krtw19/` |
| Maven artifact | `mpc4j-psu-protocol-krtw19` |
| Classes | `Krtw19PsuConfig, Krtw19PsuServer, Krtw19PsuClient` |

### Init

rpmtOprfReceiver.init, peqtOprfSender.init, coreCotSender.init(delta); server sends hash-bin key.

### Online

Per-bin OPRF, PEQT, coreCotSender.send(binNum); client decrypts union.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/01_KRTW19/` |
| Example config | `mpc4j-psu/bench/configs/psu/01_KRTW19/fair_bench_2p5.conf` |
| Output files | `temp/PSU_AC-KRTW19_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only AC:KRTW19 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Up to three Base OT setups counted in Init Send Bytes.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
