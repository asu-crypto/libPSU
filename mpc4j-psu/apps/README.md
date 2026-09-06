# libPSU apps

| Module | Artifact | Entry classes |
|--------|----------|---------------|
| `driver/` | `mpc4j-psu-driver` | `PsoMain`, `PsuMain`, `PsiMain`, `UpsuMain` |

Config parsing lives in `libpsu-factory`, not in the driver.

Fat JAR after build:

```text
mpc4j-psu/apps/driver/target/mpc4j-psu-driver-*-jar-with-dependencies.jar
```
