# libPSU implementation reactor

This directory contains the implementation of **libPSU**. Applications depend on
`edu.alibaba:libpsu` and use `edu.alibaba.libpsu.LibPsu` for all protocol families.
The `mpc4j-psu` directory name, internal artifacts, and legacy Java packages remain
for compatibility. See [library usage](docs/LIBRARY_USAGE.md).

Implementation layout:

```text
mpc4j-psu/
├── libpsu/              # Public application artifact and LibPsu facade
├── libpsu-api/          # SoK metadata (ProtocolInfo, ProtocolMetadataRegistry)
├── libpsu-core/         # SetElementUtils, bench metrics, validation
├── libpsu-spi/          # SPI aggregator + ProtocolDescriptor
├── libpsu-factory/      # PsuConfigUtils, ProtocolRegistry, config parsing
├── plugins/             # cot-union, mqrpmt, ccpsi, aon-output
├── protocols/           # balanced / unbalanced / psi / malicious implementations
├── apps/driver/         # Unified LibPsuMain CLI (fat JAR)
├── bench/               # Fair-benchmark configs (bench/configs/)
├── libpsu-test-fixtures/ # Shared integration-test helpers
├── tests/               # Integration and interop tests only
└── docs/                # PROTOCOL_MAPPING, ADD_NEW_PROTOCOL
```

Full architecture: [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) and [docs/PROTOCOL_MAPPING.md](docs/PROTOCOL_MAPPING.md).

## Build

Run from the repository root so Maven includes the required MPC4J primitives:

```bash
./scripts/mvn-jdk17.sh -pl :libpsu -am install -DskipTests
./scripts/mvn-jdk17.sh -pl :mpc4j-psu-driver -am package -DskipTests
```

The wrapper selects JDK 17. See [TESTING.md](docs/TESTING.md) for the facade and
protocol contract checks.

## Fair benchmark

```bash
export MPC4J_NATIVE_TOOL_DIR=$PWD/mpc4j-native-tool/cmake-build-release
export MPC4J_NATIVE_FHE_DIR=$PWD/mpc4j-native-fhe/cmake-build-release   # CCS:TCLZ23 UPSU
./scripts/run_psu_fair.sh 5
./scripts/run_fair_2p12_trials_interactive.sh   # multi-trial LAN/WAN (tmux + sudo)
```

Driver JAR: `mpc4j-psu/apps/driver/target/mpc4j-psu-driver-*-jar-with-dependencies.jar`

Configs: `mpc4j-psu/bench/configs/{psu,psi,upsu,ba12}/`  
Scripts: `scripts/libpsu/` (see [docs/BENCHMARKS.md](../docs/BENCHMARKS.md)).
