# USENIX:HaoWan26

Hao–Wang ePSU-fast (ssPMT-fast + ssOTd).

## At a glance

| | |
|--|--|
| **Protocol ID** | `USENIX:HaoWan26` |
| **Functionality** | Balanced ePSU |
| **Security** | Semi-honest |
| **Output** | One-sided (client union); sender outputs Finished |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = USENIX:HaoWan26` |
| **OT / cost** | INCLUDED_INIT (Core COT in ssOTd; RS21 in ssPMT-fast) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/haowan2026/` |
| Maven artifact | `mpc4j-psu-protocol-haowan2026` |
| Classes | `HaoWan2026PsuConfig, HaoWan2026PsuServer, HaoWan2026PsuClient` |

### Init

ssPmtFastServer/Client.init + ssOtdServer/Client.init.

### Online

Server permutes X → ssPMT-fast → ssOTd; client outputs Y ∪ {decrypted z_i ≠ ⊥}.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/09_HaoWan2026/` |
| Example config | `mpc4j-psu/bench/configs/psu/09_HaoWan2026/fair_bench_2p5.conf` |
| Output files | `temp/PSU_USENIX-HaoWan26_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only USENIX:HaoWan26 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Implements ePSU-fast only (not ePSU-low / ssPMT-low).

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
