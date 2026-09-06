Fair PSU comparison configs (one subfolder per protocol)
=========================================================

Layout: mpc4j-psu/bench/configs/psu/<NN_PROTOCOL>/fair_bench.conf

Archived/disabled tiers: bench/configs/_archive/

Numbering: 05 is shared by ZCL23 PKE and SKE (same paper); later protocols are numbered
one lower than the old layout (e.g. CZZ24 was 07, now 06).

PsoMain and scripts/libpsu/run_psu_fair.sh discover every *.conf under this tree (for the selected 2^LOG)
in sorted path order. Each fair_bench.conf sets psu_pto_name.

Active balanced sizes: generated per run as fair_bench_2p<LOG>.conf (e.g. 2^4…2^20)
via scripts/libpsu/gen_fair_bench_at_log.py when you run scripts/run_psu_fair.sh.

Outputs: temp/bench/<network>/psu_fair/2p<LOG>/ — summarize via
scripts/summarize_psu_fair_outputs.py (symlink → scripts/libpsu/).
Multi-trial LAN/WAN: see docs/BENCHMARKS.md.

---

Removed from mpc4j-psu reactor (not in current fair-bench suite; reference/native code may remain):

  LBL26 (13_LBL26): cwPSU — JNI only in mpc4j-native-fhe/lbl26/; no Java module in reactor.
  DGG25 (11_DGG25): not in UpsuType; configs archived under bench/configs/_archive/ if present.
  ZCL24 (10_ZCL24_PEQT, 12_ZCL24_PKE): not in current reactor.
  USENIX:BinYujConYanYu25 (08_USENIX:BinYujConYanYu25): balanced PSU (PsuType.USENIX:BinYujConYanYu25, pnMCRG + OTP). Unbalanced UPSU: upsu/10_USENIX:BinYujConYanYu25/.
  ZLP24_*: not present.

Historical notes for LBL26/DGG25/ZCL24 are kept in docs/ and _archive/ for maintainers re-enabling FHE protocols.

---

CCS:TCLZ23 (upsu/09_CCS:TCLZ23): active UPSU protocol. Requires libmpc4j-native-fhe.
  Unbalanced fair variants: fair_bench_unbalanced_*.conf in upsu/09_CCS:TCLZ23/

ASIACCS:CSSW25 (07_ASIACCS:CSSW25): default active configs use the runnable PSTY19 proxy. RS21 is an explicit
  css25_paper_comparison=true mode and is too slow for routine smoke tests.

14_EUROCRYPT:PisTri26: OT + OPRF only (libmpc4j-native-tool, not FHE).

Ours (22_Ours): ports 19206/19207.
  scripts/run_psu_fair.sh --only small_ec
  scripts/run_small_ec_fair_modes.sh 5

Set sizes: server_log_set_size and client_log_set_size are base-2 exponents (log2).
Warmup (unless skip_warmup = true) uses 2^10 x 2^10 for most protocols.

Run (after mvn -f mpc4j-psu/pom.xml install -DskipTests and native libs):

  export MPC4J_NATIVE_TOOL_DIR=$REPO/mpc4j-native-tool/cmake-build-release
  export MPC4J_NATIVE_FHE_DIR=$REPO/mpc4j-native-fhe/cmake-build-release
  scripts/run_psu_fair.sh 5
  ./scripts/run_fair_2p12_trials_interactive.sh   # see docs/BENCHMARKS.md

Environment: MPC4J_NATIVE_TOOL_DIR, MPC4J_NATIVE_FHE_DIR (CCS:TCLZ23), PSU_FAIR_CONF_DIR.
