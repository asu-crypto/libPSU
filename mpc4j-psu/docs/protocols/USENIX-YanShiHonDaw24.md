# USENIX:YanShiHonDaw24

JSZG24 Fig.17 bECRG + batch OPPRF + PEQT + LNOT + DOSN.

## At a glance

| | |
|--|--|
| **Protocol ID** | `USENIX:YanShiHonDaw24` |
| **Functionality** | Balanced PSU |
| **Security** | Semi-honest |
| **Output** | One-sided (receiver union; sender Finished) |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = USENIX:YanShiHonDaw24` |
| **OT / cost** | INCLUDED_INIT (LNOT/NC-COT chain) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/malicious/jszg24-becrg-psu/` |
| Maven artifact | `mpc4j-psu-protocol-jszg24` |
| Classes | `Jszg24BecrgPsuConfig, Jszg24BecrgPsuServer/Client` |

### Init

bopprfReceiver, peqtSender, lnotSender, dosnReceiver inits.

### Online

Cuckoo, OPPRF, PET, lnot send, pad XOR, DOSN, ciphertexts.

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/psu/19_JSZG24_BECRG_PSU/` |
| Example config | `mpc4j-psu/bench/configs/psu/19_JSZG24_BECRG_PSU/fair_bench_2p5.conf` |
| Output files | `temp/PSU_USENIX-YanShiHonDaw24_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only USENIX:YanShiHonDaw24 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

Config keys: jszg24_item_bit_length, jszg24_lambda, silent_cot.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
