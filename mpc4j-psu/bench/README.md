# libPSU benchmarks

Fair-benchmark **configs** live in `configs/{psu,psi,upsu,ba12}/`.

Archived/disabled tiers: `configs/_archive/`.

Full workflow (multi-trial LAN/WAN, network profiles, averaging):
[docs/BENCHMARKS.md](../../docs/BENCHMARKS.md).

**Scripts** (repo root; implementations under `scripts/libpsu/`):

| Script | Role |
|--------|------|
| `scripts/run_psu_fair.sh` | Unified driver: `--suite fair`, `small-ec`, or `all`; `--unbalanced S C` |
| `scripts/run_psu_fair_matrix.sh` | Matrix over small / large sizes; `--summarize-only` |
| `scripts/network_profiles.sh` | Apply / show / clear LAN·WAN1·WAN2 `tc netem` |
| `scripts/run_fair_2p4_10_trials_interactive.sh` | 3 trials × 2^{4,6,8,10} × LAN/WAN |
| `scripts/run_fair_2p{12,16,20}_trials_interactive.sh` | 3 trials × one large size × LAN/WAN |
| `scripts/run_fair_log_trials_interactive.sh <LOG>` | Shared interactive launcher |
| `scripts/libpsu/run_fair_log_trials.sh` | Shared single-log multi-trial driver (`LOG=`) |
| `scripts/average_trials_archive.sh` | Average short CSVs in a trials archive |
| `scripts/libpsu/psu_fair_bench_lib.sh` | Shared shell helpers |
| `scripts/libpsu/small_ec_fair_lib.sh` | SMALL_EC 8-case matrix |
| `scripts/summarize_*.py` | Symlinks → `libpsu/` summarizers |
| `scripts/run_small_ec_fair_modes.sh` | Wrapper → `run_psu_fair.sh --suite small-ec` |

## Matrix examples

```bash
./scripts/run_psu_fair_matrix.sh
./scripts/run_psu_fair_matrix.sh --small-only   # 2^4…2^10 (includes EUROCRYPT:PuGaoTri26)
./scripts/run_psu_fair_matrix.sh --small-only --no-2p5   # 2^4, 2^6, 2^8, 2^10
./scripts/run_psu_fair_matrix.sh --small-only --skip-logs 5,4
./scripts/run_psu_fair_matrix.sh --large-only   # 2^12, 2^16, 2^20
./scripts/run_psu_fair_matrix.sh --large-only --skip-logs 16,20   # only 2^12
./scripts/run_psu_fair_matrix.sh --skip-pgt26-2m
./scripts/run_psu_fair_matrix.sh --summarize-only --large-only --network LAN
```

Default per-protocol wall limit for matrix/driver is **30 minutes**
(`PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS=1800`). Multi-trial scripts default to
**3 hours** (`10800`). Timed-out rows appear as `--` in CSV summaries.

## Multi-trial example

```bash
# tmux recommended (sudo for tc)
./scripts/run_fair_2p12_trials_interactive.sh
./scripts/average_trials_archive.sh temp/bench/trials_2p12_<stamp> 12 LAN WAN1 WAN2
```

Outputs: `temp/bench/<network>/psu_fair/2p<LOG>/`, `temp/bench/<network>/matrix/`,
and `temp/bench/trials_2p<LOG>_*/` (gitignored).
