# JOC:HazNis12

Hazay–Nissim Protocol 8 π∪ (DDH, ElGamal, Pedersen, ZK; semi-honest/debug path).

## At a glance

| | |
|--|--|
| **Protocol ID** | `JOC:HazNis12` |
| **Functionality** | Balanced PSU |
| **Security** | Experimental semi-honest/debug only (malicious path explicitly unsupported) |
| **Output** | Two-sided (client computes union; server also receives the full union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = JOC:HazNis12` |
| **OT / cost** | NO_OT |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/psi/hn12/` |
| Maven artifact | `mpc4j-psu-protocol-hn12` |
| Classes | `Hn12PsuConfig, Hn12PsuServer/Client` |

### Init

DDH group + ElGamal/Pedersen key setup (client).

### Online

Protocol 5 stack through evals; recover Y \ X when both poly evaluations are nonzero; send union to P2.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/24_HN12/` |
| Example config | `mpc4j-psu/bench/configs/psu/24_HN12/fair_bench_2p5.conf` |
| Output files | `temp/PSU_JOC-HazNis12_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only JOC:HazNis12 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Bench configs use hn12_semi_honest_debug / ideal PRF by default. Legacy Protocol 5 PSI (Hn12Psi*) remains for unit tests; fair bench uses PSU.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
