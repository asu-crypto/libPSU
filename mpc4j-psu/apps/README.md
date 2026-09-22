# libPSU apps

| Module | Artifact | Public entry point |
|--------|----------|---------------|
| `driver/` | `mpc4j-psu-driver` | `edu.alibaba.libpsu.cli.LibPsuMain` |

The driver uses the `edu.alibaba:libpsu` library facade. Its entry point selects
the protocol family from each config's `pto_type`: `PSU`, `OO_PSU`,
`PSU_BLACK_IP`, `PSI`, `UPSU`, or `BA12`. A config path or a directory of configs
and the local party name are required arguments.

Build from the repository root:

```bash
./scripts/mvn-jdk17.sh -pl :mpc4j-psu-driver -am package -DskipTests
```

Fat JAR after build:

```text
mpc4j-psu/apps/driver/target/mpc4j-psu-driver-*-jar-with-dependencies.jar
```

Run with JDK 17 and the required native-library paths:

```bash
java --enable-preview --add-modules jdk.incubator.vector \
    -Djava.library.path="$MPC4J_NATIVE_TOOL_DIR:$MPC4J_NATIVE_FHE_DIR" \
    -jar mpc4j-psu/apps/driver/target/mpc4j-psu-driver-1.1.5-jar-with-dependencies.jar \
    mpc4j-psu/tests/src/test/resources/conf_psu_example.conf server
```

Start the other party in another terminal using `client`. UPSU configs use the
party names defined in their configs. The historical `PsoMain` and `UpsoMain`
class names delegate to `LibPsuMain` for compatibility; all families use the same JAR.
