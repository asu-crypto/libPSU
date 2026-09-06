# C:KisSon05

Kissel–Schneider 2005 Paillier polynomial PSI.

## At a glance

| | |
|--|--|
| **Protocol ID** | `C:KisSon05` |
| **Functionality** | PSI |
| **Security** | Semi-honest |
| **Output** | Client learns intersection |
| **Factory** | `PsiFactory` |
| **Driver** | `PsiMain` |
| **Config key** | `psi_pto_name = C:KisSon05` |
| **OT / cost** | NO_OT |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/psi/ks05/` |
| Maven artifact | `mpc4j-psu-protocol-ks05` |
| Classes | `Ks05PsiConfig, Ks05PsiServer/Client` |

### Init

Empty.

### Online

Paillier polynomial PSI in psi().

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psi/01_KS05/` |
| Example config | `mpc4j-psu/bench/configs/psi/01_KS05/fair_bench_2p5.conf` |
| Output files | `temp/PSI_C-KisSon05_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only C:KisSon05 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Included in fair bench as PSI baseline.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
