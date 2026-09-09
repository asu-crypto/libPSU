# Benchmark config layout notes

## KS05 / HN12

| Location | Role |
|----------|------|
| `bench/configs/psu/23_KS05/` | Fair-bench **PSU** for `C:KisSon05` |
| `bench/configs/psu/24_HN12/` | Fair-bench **PSU** for `JOC:HazNis12` (two-sided, experimental) |
| `bench/configs/psi/01_KS05/` | Legacy **PSI** unit/bench configs (Protocol 5), not the fair PSU suite |
| `bench/configs/psi/19_HN12_PSI/` | Legacy **PSI** configs; fair suite uses `psu/24_HN12` |

Canonical fair-bench IDs are under `psu/`. PSI directories remain for Protocol 5 testing only (`--no-psi` is the fair default).
