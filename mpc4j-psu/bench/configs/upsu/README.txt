# Unbalanced fair-bench configs (bench/configs/upsu/)

## Layout

| Subfolder | `pto_type` | Driver | Role |
|-----------|------------|--------|------|
| `01_AC:KRTW19`, `02_PKC:GMRSS21`, … (mirrored from `psu/`) | `PSU` | `PsoMain` → `PsuMain` | **Harness experiment**: balanced protocol at unequal sizes |
| `01_C:KisSon05`, `19_JOC:HazNis12` (mirrored from `psi/`) | `PSI` | `PsoMain` → `PsiMain` | PSI harness at unequal sizes |
| `01_ASIACCS:BlaAgu12` (mirrored from `ba12/`) | `ASIACCS:BlaAgu12` | `PsoMain` → `Ba12Main` | ASIACCS:BlaAgu12 harness at unequal sizes |
| `09_CCS:TCLZ23`, `10_USENIX:BinYujConYanYu25` | `UPSU` | `UpsoMain` | Native unbalanced UPSU |

`gen_fair_bench_unbalanced.py` mirrors every `psu/`, `psi/`, and `ba12/` folder here and writes
`fair_bench_unbalanced_2p<S>x2p<C>.conf` with unequal `server_log_set_size` / `client_log_set_size`.

Harness configs may abort or yield non-standard results; they are not paper-faithful UPSU.

Run:

```bash
./scripts/run_psu_fair.sh --unbalanced 20 10 --network none --suite fair --force
```
