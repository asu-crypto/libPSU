# libPSU architecture

libPSU has one application interface, `edu.alibaba.libpsu.LibPsu`, provided by
`edu.alibaba:libpsu`. The `mpc4j-psu/` source tree is its implementation reactor;
the directory name is retained for compatibility with scripts and existing builds.

## Layers

| Module | Role |
|--------|------|
| `libpsu` | Single public application dependency and `LibPsu` entry point |
| `libpsu-api` | Protocol metadata (`ProtocolInfo`, SoK registry) |
| `libpsu-spi` | `PsuType` / `PsiType` / `UpsuType`, protocol interfaces |
| `libpsu-core` | Set elements, validation, bench helpers |
| `libpsu-factory` | `ProtocolRegistry`, config parsing |
| `plugins/` | Shared building blocks (COT, CCPSI, …) |
| `protocols/` | Protocol implementations |
| `apps/driver` | Unified `edu.alibaba.libpsu.cli.LibPsuMain` CLI (fat JAR) |
| `bench/configs/` | Fair-benchmark `.conf` trees |
| `tests/` | Integration tests |

Only `libpsu` is an application-facing library layer. The other `libpsu-*` artifacts are
extension boundaries used to keep dependencies acyclic. Historical `mpc4j-psu-*-spi` artifacts
are empty compatibility adapters; they do not own source code. Protocol implementations retain
their established `edu.alibaba.mpc4j` package names for wire and source compatibility.

The facade constructs configs and runtime parties for balanced PSU, two-sided PSU,
offline/online PSU, PSI, UPSU, and the BA12 garbled-circuit protocol. Applications
continue to use the existing RPC, config, and result types returned by the facade.
Protocol authors may use the SPI and factory layers directly.

The driver depends on the public library. Its single entry point dispatches
`PSU`, `OO_PSU`, `PSU_BLACK_IP`, `PSI`, `UPSU`, and `BA12` configurations by
`pto_type`. Historical `PsoMain` and `UpsoMain` entry points delegate to it.

Lower layers must not depend on higher ones. See also
[PROTOCOL_MAPPING.md](../mpc4j-psu/docs/PROTOCOL_MAPPING.md) and
[ADD_NEW_PROTOCOL.md](../mpc4j-psu/docs/ADD_NEW_PROTOCOL.md).

## Building from source

Run Maven from the repository root so `-am` includes the MPC4J primitive modules:

```bash
./scripts/mvn-jdk17.sh -pl :libpsu -am install -DskipTests
./scripts/mvn-jdk17.sh -pl :mpc4j-psu-driver -am package -DskipTests
```

Application dependency and runtime examples are in
[LIBRARY_USAGE.md](../mpc4j-psu/docs/LIBRARY_USAGE.md).

## Benchmarking

Drivers and multi-trial LAN/WAN workflow:
**[BENCHMARKS.md](BENCHMARKS.md)**.

Configs: `mpc4j-psu/bench/configs/`. Scripts: `scripts/libpsu/` with wrappers under `scripts/`.
