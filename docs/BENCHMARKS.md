# Fair benchmarks

How to run and summarize libPSU fair benchmarks. Configs live under
`mpc4j-psu/bench/configs/{psu,psi,upsu,ba12}/`. Script sources live in
`scripts/libpsu/`; thin wrappers under `scripts/` call them.

## Prerequisites

```bash
export MPC4J_JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # JDK 17 required
export MPC4J_NATIVE_TOOL_DIR=$PWD/mpc4j-native-tool/cmake-build-release
export MPC4J_NATIVE_FHE_DIR=$PWD/mpc4j-native-fhe/cmake-build-release   # CCS:TCLZ23 only

# Fat JAR (or let the driver build it):
ls mpc4j-psu/apps/driver/target/mpc4j-psu-driver-*-jar-with-dependencies.jar
```

On disk-full hosts, redirect Java / bench temps:

```bash
export TMPDIR=/dev/shm
export _JAVA_OPTIONS="-Djava.io.tmpdir=/dev/shm"
# Interactive trial launchers also symlink temp/bench → $TMPDIR/libpsu_bench
```

## Drivers

| Entry point | Role |
|-------------|------|
| `scripts/run_psu_fair.sh` | Single log / unbalanced UPSU (`--suite fair\|small-ec\|all`) |
| `scripts/run_psu_fair_matrix.sh` | Small / large / full matrix over many sizes |
| `scripts/network_profiles.sh` | Apply / show / clear `tc netem` profiles |
| `scripts/run_fair_2p4_10_trials*.sh` | Multi-trial small set (matrix-based) |
| `scripts/run_fair_2p{12,16,20}_trials*.sh` | Multi-trial single large size |
| `scripts/run_fair_log_trials_interactive.sh <LOG>` | Shared interactive launcher for one log |
| `scripts/average_trials_archive.sh` | Average short CSVs inside a trials archive |
| `scripts/summarize_*.py` | Symlinks → `scripts/libpsu/` |

Shared implementation for 2^12 / 2^16 / 2^20 trials:
`scripts/libpsu/run_fair_log_trials.sh` (set `LOG=`).

## One-shot runs

```bash
./scripts/run_psu_fair.sh 5
./scripts/run_psu_fair.sh 12 --only AC:KRTW19 --network none --no-build
./scripts/run_psu_fair.sh --unbalanced 20 10 --only CCS:TCLZ23

./scripts/run_psu_fair_matrix.sh --small-only --no-2p5
./scripts/run_psu_fair_matrix.sh --large-only --skip-logs 16,20   # only 2^12
./scripts/run_psu_fair_matrix.sh --summarize-only --large-only --network LAN
```

## Multi-trial LAN / WAN1 / WAN2

Run from **tmux** (or any interactive shell) so `sudo` can configure `tc`.
Do **not** `sudo` the whole benchmark — only network shaping needs root.
Root-owned trees under `temp/bench/<NET>/` break later writes; interactive
launchers fix ownership when needed.

```bash
# 2^4, 2^6, 2^8, 2^10 (matrix --small-only --no-2p5)
./scripts/run_fair_2p4_10_trials_interactive.sh

# Single large size → shared run_fair_log_trials.sh
./scripts/run_fair_2p12_trials_interactive.sh   # default 14 protocols
./scripts/run_fair_2p16_trials_interactive.sh
./scripts/run_fair_2p20_trials_interactive.sh   # default 12 protocols
./scripts/run_fair_log_trials_interactive.sh 20 # same as 2p20
```

### Defaults (large-size trials)

| Variable | Default | Notes |
|----------|---------|--------|
| `TRIALS` | `3` | |
| `NETWORKS` | `LAN WAN1 WAN2` | Space-separated |
| `FORCE` | `1` | Set `FORCE=0` to resume |
| `PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS` | `10800` | 3 h / protocol |
| `PSU_FAIR_JAVA_XMX` | `48g` | |
| `PSU_FAIR_KEEP_GOING` | `1` | Continue after failures |
| `ONLY` | size-dependent | See below |
| `ARCHIVE_ROOT` | `temp/bench/trials_2p<LOG>_<stamp>` | |

Protocol allowlists (`ONLY` override):

- **2^12 / 2^16 (14):** AC:KRTW19, PKC:GMRSS21, USENIX:JSZDG22, USENIX:ConYuWeiminDon23_SKE, ASIACCS:CSSW25, ACISP:DavCid17,
  PKC:CheZhaZha24, USENIX:ConYuWeiminDon23_PKE, Ours, USENIX:YanShiHonDaw24, EUROCRYPT:PisTri26,
  USENIX:BinYujConYanYu25, JOC:HazNis12, EUROCRYPT:PuGaoTri26
- **2^20 (12):** same without ACISP:DavCid17 / JOC:HazNis12

For log ≥ 18 the driver skips EUROCRYPT:PisTri26 unless `--no-skip-pt26` is set; the shared
trial runner adds that flag automatically.

Resume WAN only into an existing archive:

```bash
ARCHIVE_ROOT=temp/bench/trials_2p12_2026-08-23T15-54-38+00-00 \
NETWORKS="WAN1 WAN2" TRIALS=3 FORCE=1 \
./scripts/run_fair_2p12_trials_interactive.sh
```

### Outputs and averaging

Per trial:

```text
temp/bench/trials_2p<LOG>_<stamp>/trial{N}/<NET>/psu_fair/2p<LOG>/summary_2p<LOG>_short.csv
```

Averages (written at end of the trial script):

```text
temp/bench/trials_2p<LOG>_<stamp>/averaged/summary_2p<LOG>_short_avg_{LAN,WAN1,WAN2}.csv
```

Re-average later:

```bash
./scripts/average_trials_archive.sh \
  temp/bench/trials_2p12_<stamp> 12 LAN WAN1 WAN2
```

## Network profiles

Single-host `tc netem` on `${PSU_FAIR_NET_DEV:-lo}`. Parties still bind
`127.0.0.1`. Approx. RTT ≈ 2 × one-way delay. Not a physical full-duplex WAN.

| Profile | Rate | Delay | Approx. RTT |
|---------|-----:|------:|------------:|
| `none` | — | — | unshaped |
| `LAN` | 10 Gbit | 0.1 ms | ~0.2 ms |
| `WAN1` | 200 Mbit | 40 ms | ~80 ms |
| `WAN2` | 50 Mbit | 40 ms | ~80 ms |

```bash
./scripts/network_profiles.sh show
sudo ./scripts/network_profiles.sh apply LAN
sudo ./scripts/network_profiles.sh apply WAN1
sudo ./scripts/network_profiles.sh clear
```

Kernel `tc` may print `delay 40.0ms` / `delay 99us`; verification accepts those forms.

## Layout of `scripts/`

```text
scripts/
├── run_psu_fair.sh                    → libpsu/run_psu_fair.sh
├── run_psu_fair_matrix.sh             → libpsu/run_psu_fair_matrix.sh
├── network_profiles.sh                # CLI wrapper → libpsu/network_profiles.sh
├── run_fair_2p4_10_trials.sh          → libpsu/run_fair_2p4_10_trials.sh
├── run_fair_2p{12,16,20}_trials.sh    → thin wrappers → run_fair_log_trials.sh
├── run_fair_*_trials_interactive.sh   # ownership + tc prep, then trials
├── run_fair_log_trials_interactive.sh # shared interactive (arg = LOG)
├── average_trials_archive.sh
├── summarize_*.py                     → libpsu/*.py
└── libpsu/
    ├── run_fair_log_trials.sh         # shared single-log multi-trial driver
    ├── fair_trials_interactive_lib.sh
    ├── network_profiles.sh
    ├── psu_fair_bench_lib.sh
    └── …
```
