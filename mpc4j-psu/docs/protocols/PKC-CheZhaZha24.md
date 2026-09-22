# PKC:CheZhaZha24

CZZ24 cwOPRF mqRPMT + Core COT union.

## At a glance

| | |
|--|--|
| **Protocol ID** | `PKC:CheZhaZha24` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = PKC:CheZhaZha24` |
| **OT / cost** | INCLUDED_INIT (canonical OT reference) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/czz24/` |
| Maven artifact | `mpc4j-psu-protocol-czz24` |
| Classes | `Czz24CwOprfPsuConfig, Czz24CwOprfPsuServer, Czz24CwOprfPsuClient` |

### Init

mqRPMT local α/β + coreCotSender/Receiver.init (ALSZ13 + NP01 Base OT).

### Online

ECC mqRPMT, coreCot send/receive(n), encrypted union payloads.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/06_CZZ24_CW_OPRF/` |
| Example config | `mpc4j-psu/bench/configs/psu/06_CZZ24_CW_OPRF/fair_bench_2p5.conf` |
| Output files | `temp/PSU_PKC-CheZhaZha24_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only PKC:CheZhaZha24 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Reference protocol for fair-bench OT accounting.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
