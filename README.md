# libPSU — Private Set Union Library

**libPSU** is a Java library for **Private Set Union (PSU)** and related private set operations, built on [mpc4j](https://github.com/alibaba-edu/mpc4j). It provides balanced PSU, unbalanced UPSU, PSI baselines, and the ASIACCS:BlaAgu12 garbled-circuit construction, with a unified fair-benchmark harness for reproducible comparisons.

The Maven reactor lives under [`mpc4j-psu/`](mpc4j-psu/) (artifact name unchanged for compatibility).

## What is included

| Family | Description | Factory / driver |
|--------|-------------|------------------|
| **Balanced PSU** | Two parties with equal set sizes; receiver learns the union | `PsuFactory`, `PsuMain` |
| **Unbalanced UPSU** | Sender and receiver with different set sizes | `UpsuFactory`, `UpsuMain` |
| **PSI** | Private set intersection baselines (C:KisSon05, HN12) | `PsiFactory`, `PsiMain` |
| **ASIACCS:BlaAgu12** | Bea91 garbled-circuit MPC union (separate `pto_type`) | `Ba12Main` |

Protocols are **research-oriented**: semi-honest by default unless noted (e.g. `EUROCRYPT:PuGaoTri26` is malicious two-sided). The library assumes synchronized, non-crashing parties over a reliable network.

## Requirements

- **JDK 17** (required for Vector API and aarch64 support). JDK 26 is not supported for benchmarks.
- **Native tool library** (`libmpc4j-native-tool`) — required for most protocols (OT, OPRF, LowMC, etc.).
- **Native FHE library** (`libmpc4j-native-fhe`) — required only for **CCS:TCLZ23** UPSU.

Build native libraries per [`mpc4j-native-tool/README.md`](mpc4j-native-tool/README.md) and [`mpc4j-native-fhe/README.md`](mpc4j-native-fhe/README.md).

## Project layout

```text
mpc4j-psu/
├── libpsu-api/           # Protocol metadata (ProtocolInfo, SoK registry)
├── libpsu-spi/           # PsuType, PsiType, UpsuType, protocol interfaces
├── libpsu-core/          # Set elements, validation, bench helpers
├── libpsu-factory/       # ProtocolRegistry, config parsing
├── plugins/              # Shared building blocks (COT, CCPSI, …)
├── protocols/            # Implementations (balanced / malicious / psi / unbalanced)
├── apps/driver/          # PsuMain, PsiMain, UpsuMain, Ba12Main (fat JAR)
├── bench/configs/        # Fair-benchmark configs (psu/, psi/, upsu/, ba12/)
└── tests/                # Integration tests

scripts/libpsu/           # Benchmark drivers and summarizers
docs/                     # BENCHMARKS.md, ARCHITECTURE.md
mpc4j-psu/docs/           # Protocol mapping and design docs
```

Layering and dependency rules: [`mpc4j-psu/docs/ARCHITECTURE.md`](mpc4j-psu/docs/ARCHITECTURE.md) /
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
Benchmark scripts and multi-trial workflow: [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md).

## Build

```bash
# JDK 17 (example on Ubuntu)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

mvn -f mpc4j-psu/pom.xml install -DskipTests
```

Driver JAR:

```text
mpc4j-psu/apps/driver/target/mpc4j-psu-driver-*-jar-with-dependencies.jar
```

## Run a single protocol

Example config: `mpc4j-psu/tests/src/test/resources/conf_psu_example.conf`.

**Start the server before the client.**

```bash
export MPC4J_NATIVE_TOOL_DIR=$PWD/mpc4j-native-tool/cmake-build-release
export MPC4J_NATIVE_FHE_DIR=$PWD/mpc4j-native-fhe/cmake-build-release   # CCS:TCLZ23 only

JAR=mpc4j-psu/apps/driver/target/mpc4j-psu-driver-*-jar-with-dependencies.jar

java -Djava.library.path=$MPC4J_NATIVE_TOOL_DIR:$MPC4J_NATIVE_FHE_DIR \
  -jar $JAR mpc4j-psu/tests/src/test/resources/conf_psu_example.conf server

java -Djava.library.path=$MPC4J_NATIVE_TOOL_DIR:$MPC4J_NATIVE_FHE_DIR \
  -jar $JAR mpc4j-psu/tests/src/test/resources/conf_psu_example.conf client
```

## Fair benchmark

Configs: `mpc4j-psu/bench/configs/{psu,psi,upsu,ba12}/`.  
Full script reference: [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md).

```bash
export MPC4J_JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export MPC4J_NATIVE_TOOL_DIR=$PWD/mpc4j-native-tool/cmake-build-release
export MPC4J_NATIVE_FHE_DIR=$PWD/mpc4j-native-fhe/cmake-build-release

# Default: balanced fair suite at 2^5 (32 × 32)
./scripts/run_psu_fair.sh 5

# Specific size and protocol
./scripts/run_psu_fair.sh 12 --only AC:KRTW19 --network none --no-build

# Full matrix (small + large balanced, optional UPSU)
./scripts/run_psu_fair_matrix.sh --small-only
./scripts/run_psu_fair_matrix.sh --large-only    # 2^12, 2^16, 2^20

# Unbalanced UPSU only (CCS:TCLZ23, USENIX:BinYujConYanYu25)
./scripts/run_psu_fair.sh --unbalanced 20 10 --only CCS:TCLZ23

# Rebuild CSV summaries from existing .output files (no re-run)
./scripts/run_psu_fair_matrix.sh --summarize-only --large-only --network LAN
```

### Multi-trial LAN/WAN runs (preferred)

Use a **tmux** (or other interactive) session so `sudo` can prompt for `tc netem`. Do **not** run the whole script under `sudo` — only `tc` needs root.

```bash
# Small set: 2^4, 2^6, 2^8, 2^10 × LAN/WAN1/WAN2 × 3 trials
./scripts/run_fair_2p4_10_trials_interactive.sh

# Single large size (shared driver: scripts/libpsu/run_fair_log_trials.sh)
./scripts/run_fair_2p12_trials_interactive.sh   # 14 protocols
./scripts/run_fair_2p16_trials_interactive.sh   # 14 protocols
./scripts/run_fair_2p20_trials_interactive.sh   # 12 protocols (no HN12 / ACISP:DavCid17)
# Equivalent: ./scripts/run_fair_log_trials_interactive.sh 20
```

Defaults for large-size trials: `TRIALS=3`, `FORCE=1`, `PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS=10800`, `PSU_FAIR_KEEP_GOING=1`.  
Override with env vars (`NETWORKS=WAN1`, `FORCE=0`, `ONLY=...`, `ARCHIVE_ROOT=...`).

Averaged CSVs land under `temp/bench/trials_2p<LOG>_<timestamp>/averaged/`.  
Re-average an existing archive:

```bash
./scripts/average_trials_archive.sh temp/bench/trials_2p12_<stamp> 12 LAN WAN1 WAN2
```

### Network profiles (`--network`)

**Single-host** Linux `tc netem` on loopback (`${PSU_FAIR_NET_DEV:-lo}`). Parties still use `127.0.0.1`. Approx. RTT ≈ 2 × one-way netem delay.

| Profile | `netem rate` | `netem delay` | Approx. RTT |
|---------|-------------:|--------------:|-----------------:|
| `none` | — | — | unshaped localhost |
| `LAN` | 10 Gbps | 0.1 ms | ~0.2 ms |
| `WAN1` | 200 Mbps | 40 ms | ~80 ms |
| `WAN2` | 50 Mbps | 40 ms | ~80 ms |

```bash
./scripts/network_profiles.sh show
sudo ./scripts/network_profiles.sh apply WAN1
ping -c 5 127.0.0.1   # ~80 ms for WAN; not a hard pass/fail
```

### Outputs

Under `temp/bench/<network>/` (often symlinked to `/dev/shm` on full disks):

| Path | Contents |
|------|----------|
| `PSU_*.output`, `PSI_*.output`, `UPSU_*.output` | Raw benchmark rows |
| `psu_fair/2p<LOG>/summary_2p<LOG>.csv` | Per-size full summary |
| `psu_fair/2p<LOG>/summary_2p<LOG>_short.csv` | Per-size ranked short summary |
| `matrix/summary_{small,large,combined}_short.csv` | Cross-protocol matrix |
| `trials_2p<LOG>_*/averaged/summary_2p*_short_avg_<NET>.csv` | Multi-trial averages |

Useful options / env:

- `--force` — re-run even when complete outputs exist
- `--only PAT,...` — filter by config path or `psu_pto_name` substring
- `--no-upsu` — balanced PSU + PSI + ASIACCS:BlaAgu12 only
- `--no-skip-pt26` — keep EUROCRYPT:PisTri26 when log ≥ 18 (enabled automatically for 2p20 trials)
- `PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS` — per-protocol wall limit (default 1800 s; trials use 10800)
- `PSU_FAIR_C:KisSon05_MAX_LOG_SIZE` — max log₂ set size for C:KisSon05 (default 8)
- `PSU_FAIR_KEEP_GOING=1` — continue after individual protocol/network failures

## Runnable protocols (summary)

Full mapping: [`mpc4j-psu/docs/PROTOCOL_MAPPING.md`](mpc4j-psu/docs/PROTOCOL_MAPPING.md).

### Balanced PSU (`PsuType`)

AC:KRTW19, PKC:GMRSS21, USENIX:JSZDG22, USENIX:JSZDG22_SFS, USENIX:ConYuWeiminDon23_PKE, USENIX:ConYuWeiminDon23_SKE, PKC:CheZhaZha24, ASIACCS:CSSW25, EUROCRYPT:PisTri26, ACISP:DavCid17, ACNS:Frikken07, USENIX:BinYujConYanYu25, USENIX:YanShiHonDaw24, EUROCRYPT:PuGaoTri26, Ours.

Deprecated enum ids (`PKC:GMRSS21_PROXY`, `JSZ22_*_PROXY`, `EUROCRYPT:PuGaoTri26`) are not runnable; use the non-`_PROXY` names above.

### Unbalanced UPSU (`UpsuType`)

CCS:TCLZ23 (needs FHE native), USENIX:BinYujConYanYu25 (linear pnMCRG path; paper FHE sublinear variant not implemented).

### PSI (`PsiType`)

C:KisSon05, JOC:HazNis12.

### ASIACCS:BlaAgu12

Garbled-circuit union via `Ba12Main`; configs under `bench/configs/ba12/`.

## Use as a Java library

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>edu.alibaba</groupId>
      <artifactId>mpc4j-psu-bom</artifactId>
      <version>1.1.5</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

```java
ProtocolInfo info = ProtocolMetadataRegistry.findByName("AC:KRTW19").orElseThrow();
PsuConfig config = ProtocolRegistry.createPsuConfig("AC:KRTW19", new Properties());
```

Details: [`mpc4j-psu/docs/LIBRARY_USAGE.md`](mpc4j-psu/docs/LIBRARY_USAGE.md).

## Documentation

| Document | Topic |
|----------|--------|
| [docs/BENCHMARKS.md](docs/BENCHMARKS.md) | Fair bench, network profiles, multi-trial runners |
| [mpc4j-psu/docs/ARCHITECTURE.md](mpc4j-psu/docs/ARCHITECTURE.md) | Module layers |
| [mpc4j-psu/README.md](mpc4j-psu/README.md) | Reactor quick reference |
| [mpc4j-psu/docs/PROTOCOL_MAPPING.md](mpc4j-psu/docs/PROTOCOL_MAPPING.md) | Enum → code path |
| [mpc4j-psu/docs/PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](mpc4j-psu/docs/PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md) | Phase accounting, primitives |
| [mpc4j-psu/docs/ADD_NEW_PROTOCOL.md](mpc4j-psu/docs/ADD_NEW_PROTOCOL.md) | Adding a protocol |
| [mpc4j-psu/bench/README.md](mpc4j-psu/bench/README.md) | Benchmark configs and scripts |
| [mpc4j-psu/protocols/README.md](mpc4j-psu/protocols/README.md) | Per-protocol notes |

## Related papers in this tree

Implementations tied to published work include AC:KRTW19, PKC:GMRSS21, JSZ22, ZCL23, ASIACCS:CSSW25, USENIX:BinYujConYanYu25, CCS:TCLZ23 (UPSU), ASIACCS:BlaAgu12, and others — see `PAPERS.md` and per-protocol docs under `mpc4j-psu/protocols/`.

## License

Apache License 2.0.
