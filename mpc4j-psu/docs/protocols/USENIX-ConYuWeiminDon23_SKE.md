# USENIX:ConYuWeiminDon23_SKE

ZCL23 SKE (Z2 circuits + OPRP + GF2K-DOKVS + Core COT).

## At a glance

| | |
|--|--|
| **Protocol ID** | `USENIX:ConYuWeiminDon23_SKE` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union); fair bench uses 2-party PsuMain |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = USENIX:ConYuWeiminDon23_SKE` |
| **OT / cost** | INCLUDED_INIT (Z2c + Core COT Base OT) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/zcl23/` |
| Maven artifact | `mpc4j-psu-protocol-zcl23` |
| Classes | `Zcl23SkePsuConfig, Zcl23SkePsuServer, Zcl23SkePsuClient` |

### Init

z2cSender.init, oprpReceiver.init, coreCotSender.init; send DOKVS keys.

### Online

PEQT shares and Core COT online delivery.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/05_ZCL23_SKE/` |
| Example config | `mpc4j-psu/bench/configs/psu/05_ZCL23_SKE/fair_bench_2p5.conf` |
| Output files | `temp/PSU_USENIX-ConYuWeiminDon23_SKE_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only USENIX:ConYuWeiminDon23_SKE --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Optional 3-party+aider API exists; fair configs use 2-party only.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
