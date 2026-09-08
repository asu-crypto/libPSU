# ASIACCS:CSSW25

MP-OPRF + CCPSI + ROSN + Core COT.

## At a glance

| | |
|--|--|
| **Protocol ID** | `ASIACCS:CSSW25` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = ASIACCS:CSSW25` |
| **OT / cost** | INCLUDED_INIT (setupOprf/setupCcpsi/setupShtr/setupOt buckets) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/cssw25/` |
| Maven artifact | `mpc4j-psu-protocol-css25` |
| Classes | `Css25PsuConfig, Css25PsuServer, Css25PsuClient` |

### Init

mpOprfSender, ccpsiClient, rosnReceiver, coreCotSender inits.

### Online

MP-OPRF, CCPSI, ROSN, final coreCot.send(beta).

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/07_CSS25/` |
| Example config | `mpc4j-psu/bench/configs/psu/07_CSS25/fair_bench_2p5.conf` |
| Output files | `temp/PSU_ASIACCS-CSSW25_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only ASIACCS:CSSW25 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Experimental runnable proxy; `css25_paper_exact=true` reserved. RS21 comparison is opt-in.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
