# ASIACCS:BlaAgu12

Bea91 Z2c MPC set operations.

## At a glance

| | |
|--|--|
| **Protocol ID** | `ASIACCS:BlaAgu12` |
| **Functionality** | Garbled-circuit set ops |
| **Security** | Semi-honest |
| **Output** | Secret-shared set operation (e.g. ASIACCS:BlaAgu12_UNION) |
| **Factory** | `N/A` |
| **Driver** | `Ba12Main` |
| **Config key** | `ba12_pto_name = ASIACCS:BlaAgu12` |
| **OT / cost** | INCLUDED_INIT (Z2c / Bea91 COT in init) |

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/psi/ba12/` |
| Maven artifact | `mpc4j-psu-protocol-ba12` |
| Classes | `Ba12Config, Ba12SetOpsParty` |

### Init

Ba12SetOpsParty.init → z2c.init (Base OT inside).

### Online

runBinary (e.g. ASIACCS:BlaAgu12_UNION).

## Benchmarking

| | |
|--|--|
| Config folder | `mpc4j-psu/bench/configs/ba12/01_BA12/` |
| Example config | `mpc4j-psu/bench/configs/ba12/01_BA12/fair_bench_2p5.conf` |
| Output files | `temp/PSU_ASIACCS-BlaAgu12_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |

Run (example, 2^5 × 2^5):

```bash
./scripts/run_psu_fair.sh 5 --only ASIACCS:BlaAgu12 --force
```

Summarize:

```bash
python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv
```

## Notes

pto_type = ASIACCS:BlaAgu12; output prefix PSU_ASIACCS:BlaAgu12_* in fair scripts. Not a PsuType.

## See also

- [Protocol index](README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
