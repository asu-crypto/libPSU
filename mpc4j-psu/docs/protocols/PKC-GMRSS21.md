# PKC:GMRSS21

PKC:GMRSS21 mqRPMT (cuckoo + OKVS + DOSN/ROSN + KKRT OPRF ×2) + Core COT.

## At a glance

| | |
|--|--|
| **Protocol ID** | `PKC:GMRSS21` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = PKC:GMRSS21` |
| **OT / cost** | INCLUDED_INIT (KKRT ×2 + union Core COT; may be 3× Base OT) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/gmr21/` |
| Maven artifact | `mpc4j-psu-protocol-gmr21` |
| Classes | `Gmr21PsuConfig, Gmr21PsuServer, Gmr21PsuClient` |

### Init

gmr21MqRpmtServer.init (nested OPRF/DOSN/ROSN inits) + coreCotSender.init.

### Online

mqRPMT → serverVector; coreCotSender.send + XOR ciphertexts.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/02_GMR21/` |
| Example config | `mpc4j-psu/bench/configs/psu/02_GMR21/fair_bench_2p5.conf` |
| Output files | `temp/PSU_PKC-GMRSS21_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only PKC:GMRSS21 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Supports preCompute() via OoPsuMain only (not in fair PsuMain).

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
