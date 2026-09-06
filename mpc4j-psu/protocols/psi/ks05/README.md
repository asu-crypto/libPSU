# C:KisSon05 (Kissner–Song)

Paillier polynomial private set **union** (same algebra family as ACNS:Frikken07).

## Fair-bench protocol (PSU)

| ID | Classes | Factory |
|------|---------|---------|
| `C:KisSon05` | `Ks05PsuConfig`, `Ks05PsuServer/Client` | `PsuFactory` / `PsuMain` |

- Config key: `psu_pto_name = C:KisSon05`
- Bench folders: `bench/configs/psu/23_KS05/`, unbalanced `upsu/23_KS05/` (and legacy `upsu/01_KS05/`)
- Small-set guard: `ks05_max_set_size` (default 256); harness `PSU_FAIR_KS05_MAX_LOG_SIZE` (default 8)

## Legacy PSI

`Ks05Psi*` / `PsiFactory` remain for unit tests (`psi_pto_name`, `bench/configs/psi/01_KS05/`).
