# MPC4J PSU/PSI/UPSU — Implementation Guide

## Per-protocol docs

**[protocols/README.md](protocols/README.md)** — index of all runnable protocols, titled with **bench display names** (e.g. `PKC:CheZhaZha24`, `USENIX:HaoWan26`).

Each protocol has its own page under `mpc4j-psu/docs/protocols/` (regenerate with `python3 scripts/gen_protocol_docs.py`). Internal enum IDs (e.g. `PKC:CheZhaZha24`) appear on each page for config and `--only` flags.

**Docs index:** [README.md](README.md)

## Aggregate references

| Topic | Document |
|-------|----------|
| Enum → path | [PROTOCOL_MAPPING.md](PROTOCOL_MAPPING.md) |
| Implementation + benchmark cost | [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md) |
| OT / Base OT audit | [OT_BASE_COST_AUDIT.md](OT_BASE_COST_AUDIT.md) |
| Reactor layout | [ARCHITECTURE.md](ARCHITECTURE.md) |

## Detailed implementation guides

- [Ours_IMPLEMENTATION.md](Ours_IMPLEMENTATION.md) — deep dive for **Ours** (`Ours`), also linked from [protocols/Ours.md](protocols/Ours.md)

**Note:** `Ours` is a semi-honest leakage baseline (sender learns \(W \setminus V\) pattern), not standard one-sided PSU. `USENIX:BinYujConYanYu25` balanced PSU and linear UPSU are runnable; paper FHE unbalanced MCRG is not. `ASIACCS:BlaAgu12` is audited via `Ba12Main` (Z2c OT in Init).
