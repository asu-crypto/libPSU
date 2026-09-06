# CCS:TCLZ23

CCS:TCLZ23 sqOPRF + pm-PEQT + FHE + Core COT.

## At a glance

| | |
|--|--|
| **Protocol ID** | `CCS:TCLZ23` |
| **Functionality** | Unbalanced UPSU |
| **Security** | Semi-honest |
| **Output** | One-sided (receiver output) |
| **Factory** | `UpsuFactory` |
| **Driver** | `UpsuMain` |
| **Config key** | `upsu_pto_name = CCS:TCLZ23` |
| **OT / cost** | INCLUDED_INIT (Core COT); FHE dominates Pto |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/unbalanced/tcl23/` |
| Maven artifact | `mpc4j-psu-protocol-tcl23` |
| Classes | `Tcl23UpsuConfig, Tcl23UpsuSender/Receiver` |

### Init

sqOprf, pmPeqt, coreCot inits; FHE keygen + relin keys (large Init traffic).

### Online

OPRF, PEQT, COT, FHE ciphertexts.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/upsu/09_TCL23/` |
| Example config | `mpc4j-psu/bench/configs/upsu/09_TCL23/fair_bench_2p5.conf` |
| Output files | `temp/UPSU_CCS-TCLZ23_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only CCS:TCLZ23 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Requires libmpc4j-native-fhe. Large sets use -Xmx32g+.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
