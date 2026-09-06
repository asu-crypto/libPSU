# USENIX:ConYuWeiminDon23_PKE

ZCL23 PKE mqRPMT + Core COT union.

## At a glance

| | |
|--|--|
| **Protocol ID** | `USENIX:ConYuWeiminDon23_PKE` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = USENIX:ConYuWeiminDon23_PKE` |
| **OT / cost** | INCLUDED_INIT |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/zcl23/` |
| Maven artifact | `mpc4j-psu-protocol-zcl23` |
| Classes | `Zcl23PkePsuConfig, Zcl23PkeMqRpmtServer/Client, Zcl23PkePsuServer/Client` |

### Init

zcl23PkeMqRpmtServer.init + coreCotSender.init(delta).

### Online

mqRPMT → vector; coreCotSender.send; XOR ciphertexts.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/05_ZCL23_PKE/` |
| Example config | `mpc4j-psu/bench/configs/psu/05_ZCL23_PKE/fair_bench_2p5.conf` |
| Output files | `temp/PSU_USENIX-ConYuWeiminDon23_PKE_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only USENIX:ConYuWeiminDon23_PKE --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Same OT accounting shape as PKC:CheZhaZha24.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
