# mpc4j-psu — libPSU reactor

Private Set Union / PSI / UPSU library layout:

```text
mpc4j-psu/
├── libpsu-api/          # SoK metadata (ProtocolInfo, ProtocolMetadataRegistry)
├── libpsu-core/         # SetElementUtils, bench metrics, validation
├── libpsu-spi/          # SPI aggregator + ProtocolDescriptor
├── libpsu-factory/      # PsuConfigUtils, ProtocolRegistry, config parsing
├── plugins/             # cot-union, mqrpmt, ccpsi, aon-output
├── protocols/           # balanced / unbalanced / psi / malicious implementations
├── apps/driver/         # PsoMain, PsuMain, PsiMain, UpsuMain (fat JAR)
├── bench/               # Fair-benchmark configs (bench/configs/)
├── libpsu-test-fixtures/ # Shared integration-test helpers
├── tests/               # Integration and interop tests only
└── docs/                # PROTOCOL_MAPPING, ADD_NEW_PROTOCOL
```

Full architecture: [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) and [docs/PROTOCOL_MAPPING.md](docs/PROTOCOL_MAPPING.md).

## Build

```bash
mvn -f mpc4j-psu/pom.xml install -DskipTests
mvn -f mpc4j-psu/pom.xml -pl tests test
```

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
