# C:KisSon05

Kissner–Song 2005 Paillier polynomial PSU (same algebra family as ACNS:Frikken07).

## At a glance

| | |
|--|--|
| **Protocol ID** | `C:KisSon05` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = C:KisSon05` |
| **OT / cost** | NO_OT |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/psi/ks05/` |
| Maven artifact | `mpc4j-psu-protocol-ks05` |
| Classes | `Ks05PsuConfig, Ks05PsuServer/Client` |

### Init

Paillier keygen (client).

### Online

Encrypted root polynomial; server returns randomized tuples; client recovers Y \ X and builds union.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/23_KS05/` |
| Example config | `mpc4j-psu/bench/configs/psu/23_KS05/fair_bench_2p5.conf` |
| Output files | `temp/PSU_C-KisSon05_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only C:KisSon05 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Fair-bench default: skip_warmup=true and ks05_max_set_size=256; harness skips log sizes above PSU_FAIR_KS05_MAX_LOG_SIZE (default 8). Legacy PsiType/PsiMain drivers remain for unit tests only.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
