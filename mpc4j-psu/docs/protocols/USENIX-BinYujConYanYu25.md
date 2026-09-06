# USENIX:BinYujConYanYu25

Tu–Bai–Zhang ePSU (pnMCRG + XOR one-time pad).

## At a glance

| | |
|--|--|
| **Protocol ID** | `USENIX:BinYujConYanYu25` |
| **Functionality** | Balanced PSU / UPSU |
| **Security** | Semi-honest |
| **Output** | One-sided (receiver/client union) |
| **Factory** | `PsuFactory / UpsuFactory` |
| **Driver** | `PsuMain / UpsuMain` |
| **Config key** | `psu_pto_name / upsu_pto_name = USENIX:BinYujConYanYu25` |
| **OT / cost** | INCLUDED_INIT (RS21 + Core COT in nECRG) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/tbz25 (+ UPSU wrapper in protocols/unbalanced/tbz25)/` |
| Maven artifact | `mpc4j-psu-protocol-tbz25` |
| Classes | `Tbz25PsuServer/Client; Tbz25UpsuSender/Receiver` |

### Init

pnMCRG sub-protocol inits (RS21 MP-OPRF + Core COT).

### Online

Cuckoo + pnMCRG; sender XORs OTP ciphertexts; receiver decrypts and unions.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/08_TBZ25/` |
| UPSU configs | `mpc4j-psu/bench/configs/upsu/10_TBZ25/` |
| Example config | `mpc4j-psu/bench/configs/psu/08_TBZ25/fair_bench_2p5.conf` |
| Output files | `temp/PSU_USENIX-BinYujConYanYu25_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only USENIX:BinYujConYanYu25 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Paper FHE unbalanced MCRG (Fig. 14) is not implemented. Runnable UPSU uses the linear balanced core.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
