# JOC:HazNis12

Hazay–Nissim malicious PSI (DDH, ElGamal, ZK).

## At a glance

| | |
|--|--|
| **Protocol ID** | `JOC:HazNis12` |
| **Functionality** | PSI |
| **Security** | Malicious |
| **Output** | Client learns intersection |
| **Factory** | `PsiFactory` |
| **Driver** | `PsiMain` |
| **Config key** | `psi_pto_name = JOC:HazNis12` |
| **OT / cost** | NO_OT |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/psi/hn12/` |
| Maven artifact | `mpc4j-psu-protocol-hn12` |
| Classes | `Hn12PsiConfig, Hn12PsiServer/Client` |

### Init

Minimal.

### Online

DDH/ElGamal PSI with ZK proofs.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psi/19_HN12_PSI/` |
| Example config | `mpc4j-psu/bench/configs/psi/19_HN12_PSI/fair_bench_2p5.conf` |
| Output files | `temp/PSI_JOC-HazNis12_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only JOC:HazNis12 --force
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
