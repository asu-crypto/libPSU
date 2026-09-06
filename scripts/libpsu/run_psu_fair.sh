#!/usr/bin/env bash
# Unified PSU benchmark driver: fair protocol suite and/or SMALL_EC mode matrix.
#
# Usage (from repo root):
#   scripts/run_psu_fair.sh                    # fair suite at 2^5 = 32 x 32
#   scripts/run_psu_fair.sh 5                  # same as default
#   scripts/run_psu_fair.sh 5 --suite all      # fair + SMALL_EC modes at 2^5
#   scripts/run_psu_fair.sh 5 --suite small-ec # SMALL_EC 8-case matrix only
#   scripts/run_psu_fair.sh --size 32          # log2(32) = 5
#
# Options:
#   --suite SUITE     fair (default), small-ec, or all (fair then SMALL_EC)
#   --small-ec        alias for --suite small-ec
#   --size N          element count per party (power of two); alternative to LOG args
#   --no-upsu         skip unbalanced UPSU (balanced PSU + optional legacy PSI + ASIACCS:BlaAgu12 only)
#   --skip-pt26       skip EUROCRYPT:PisTri26
#   --no-skip-pt26    run EUROCRYPT:PisTri26 even when LOG >= 18 (default: skip EUROCRYPT:PisTri26 when LOG >= 18)
#   --no-psi          skip legacy PSI configs under bench/configs/psi/ (fair C:KisSon05 / JOC:HazNis12 are PSU)
#   --force           re-run even if complete outputs exist
#   --from LABEL      resume from config label, e.g. pso/02_PKC:GMRSS21/fair_bench_2p5.conf
#   --only PAT,...    comma-separated filter (path or pto name substring)
#   --no-build        skip mvn install if driver JAR exists
#   --unbalanced S C  unbalanced UPSU only: sender 2^S, receiver 2^C (server/client logs)
#
# Build (requires JDK 17 — Homebrew default JDK 26 will not work for benchmarks):
#   ./scripts/mvn-jdk17.sh -f mpc4j-psu/pom.xml clean install -DskipTests
#
# Environment (optional):
#   PSU_FAIR_JAVA_XMX=48g
#   MPC4J_NATIVE_TOOL_DIR / MPC4J_NATIVE_FHE_DIR  (UPSU: CCS:TCLZ23 needs FHE native)
#   PSU_FAIR_SKIP_PGT26_2M=1   skip EUROCRYPT:PuGaoTri26 configs
#   PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS=1800   kill each protocol after 30 min (CSV: --)
#   Timed-out fair protocols are recorded in matrix/protocol_timeouts.tsv and skipped at larger 2^n.
#   --network PROFILE   none (default), LAN, WAN1, or WAN2 — tc netem on loopback (Linux)
#   PSU_FAIR_NET_DEV=lo loopback interface for tc (default lo)
#
# Outputs are grouped by network profile under temp/bench/<network>/:
#   psu_fair/2p<LOG>/, small_ec/2p<LOG>/, matrix/, and raw PSU_*.output files.

set -euo pipefail

_src="${BASH_SOURCE[0]}"
while [[ -L "${_src}" ]]; do
  _dir="$(cd "$(dirname "${_src}")" && pwd)"
  _src="$(readlink "${_src}")"
  [[ "${_src}" != /* ]] && _src="${_dir}/${_src}"
done
_script_home="$(cd "$(dirname "${_src}")" && pwd)"
unset _src _dir
# shellcheck source=psu_fair_bench_lib.sh
source "${_script_home}/psu_fair_bench_lib.sh"
REPO_ROOT="$(libpsu_resolve_repo_root "${_script_home}")"
SCRIPT_DIR="${REPO_ROOT}/scripts/libpsu"
unset _script_home
cd "${REPO_ROOT}"

ensure_jdk17_home || exit 1
# shellcheck source=small_ec_fair_lib.sh
source "${SCRIPT_DIR}/small_ec_fair_lib.sh"
# shellcheck source=network_profiles.sh
source "${SCRIPT_DIR}/network_profiles.sh"

LOGS=()
UNBALANCED_SERVER_LOG=""
UNBALANCED_CLIENT_LOG=""
SIZE=""
SUITE="fair"
INCLUDE_UPSU=1
SKIP_PT26=""
NO_PSI=0
FORCE=0
FROM_LABEL=""
NO_BUILD=0
ONLY_PROTOCOLS=""
SKIP_GEN=0
NETWORK_PROFILE="none"

usage() {
  sed -n '3,26p' "$0" | sed 's/^# \?//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage 0 ;;
    --size)
      SIZE="${2:?--size needs a number}"
      shift 2
      ;;
    --no-upsu) INCLUDE_UPSU=0; shift ;;
    --skip-pt26) SKIP_PT26=1; shift ;;
    --no-skip-pt26) SKIP_PT26=0; shift ;;
    --no-psi) NO_PSI=1; shift ;;
    --force) FORCE=1; shift ;;
    --from) FROM_LABEL="${2:?--from needs a label}"; shift 2 ;;
    --no-build) NO_BUILD=1; shift ;;
    --unbalanced)
      UNBALANCED_SERVER_LOG="${2:?--unbalanced needs sender log2}"
      UNBALANCED_CLIENT_LOG="${3:?--unbalanced needs receiver log2}"
      shift 3
      ;;
    --only)
      ONLY_PROTOCOLS="${2:?--only needs a comma-separated pattern list}"
      SKIP_GEN=1
      shift 2
      ;;
    --suite)
      SUITE="${2:?--suite needs fair, small-ec, or all}"
      shift 2
      ;;
    --small-ec) SUITE="small-ec"; shift ;;
    --include-upsu) INCLUDE_UPSU=1; shift ;;
    --network)
      NETWORK_PROFILE="${2:?--network needs none, LAN, WAN1, or WAN2}"
      shift 2
      ;;
    -*)
      echo "unknown option: $1" >&2
      usage 1
      ;;
    *)
      if [[ "$1" =~ ^[0-9]+$ ]]; then
        LOGS+=("$1")
        shift
      else
        echo "unexpected argument: $1" >&2
        usage 1
      fi
      ;;
  esac
done

if [[ -n "${SIZE}" ]]; then
  if [[ ${#LOGS[@]} -gt 0 ]]; then
    echo "use either LOG argument(s) or --size, not both" >&2
    exit 1
  fi
  if ! [[ "${SIZE}" =~ ^[0-9]+$ ]] || [[ "${SIZE}" -lt 2 ]]; then
    echo "--size must be an integer >= 2" >&2
    exit 1
  fi
  n="${SIZE}"
  log=0
  while (( n > 1 )); do
    if (( n % 2 != 0 )); then
      echo "--size ${SIZE} is not a power of two" >&2
      exit 1
    fi
    n=$(( n / 2 ))
    log=$(( log + 1 ))
  done
  LOGS=( "${log}" )
fi

if [[ ${#LOGS[@]} -eq 0 ]]; then
  LOGS=( 5 )
fi

case "${SUITE}" in
  fair|small-ec|all) ;;
  *)
    echo "unknown --suite: ${SUITE} (use fair, small-ec, or all)" >&2
    exit 1
    ;;
esac

export PSU_FAIR_SKIP_PGT26_2M="${PSU_FAIR_SKIP_PGT26_2M:-0}"

# Run every fair_bench_2p<LOG>.conf (server JVM + client JVM per config).
run_psu_fair_benchmarks() {
  local CONF_ROOT="${PSU_FAIR_CONF_DIR:-$(psu_bench_resources)/psu}"
  local UPSU_CONF_ROOT="${PSU_FAIR_UPSU_CONF_DIR:-$(psu_bench_resources)/upsu}"
  local UPSU_FAIR_CONF_ROOT="${PSU_FAIR_UPSU_FAIR_CONF_DIR:-$(psu_bench_resources)/upsu}"
  local CONF_FILES=()

  collect_fair_confs() {
    local root="$1"
    [[ -d "${root}" ]] || return 0
    local find_args=(-type f ! -name 'README*' ! -path '*11_DGG25*')
    if [[ -n "${PSU_FAIR_ONLY_CONF_BASENAME:-}" ]]; then
      while IFS= read -r line; do
        CONF_FILES+=("${line}")
      done < <(find "${root}" "${find_args[@]}" -name "${PSU_FAIR_ONLY_CONF_BASENAME}" ! -name '*.disabled' -print | LC_ALL=C sort)
      return 0
    fi
    if [[ -n "${PSU_FAIR_ONLY_LOG:-}" ]]; then
      local only_name="fair_bench_2p${PSU_FAIR_ONLY_LOG}.conf"
      while IFS= read -r line; do
        CONF_FILES+=("${line}")
      done < <(find "${root}" "${find_args[@]}" -name "${only_name}" ! -name '*.disabled' -print | LC_ALL=C sort)
      return 0
    fi
    if [[ "${PSU_FAIR_ONLY_2P5_2P20:-0}" == "1" ]]; then
      while IFS= read -r line; do
        CONF_FILES+=("${line}")
      done < <(find "${root}" "${find_args[@]}" \( \
        -name 'fair_bench_2p5.conf' -o -name 'fair_bench_2p20.conf' \
        \) ! -name '*.disabled' -print | LC_ALL=C sort)
      return 0
    fi
    if [[ "${PSU_FAIR_ONLY_MAIN_CONF:-0}" == "1" ]]; then
      while IFS= read -r line; do
        CONF_FILES+=("${line}")
      done < <(find "${root}" "${find_args[@]}" -name 'fair_bench.conf' ! -name '*.disabled' -print | LC_ALL=C sort)
      return 0
    fi
    while IFS= read -r line; do
      CONF_FILES+=("${line}")
    done < <(find "${root}" "${find_args[@]}" -name '*.conf' ! -name '*.disabled' -print | LC_ALL=C sort)
  }

  if [[ -n "${PSU_FAIR_ONLY_CONF_BASENAME:-}" ]]; then
    collect_fair_confs "${UPSU_CONF_ROOT}"
    collect_fair_confs "${UPSU_FAIR_CONF_ROOT}"
  else
    collect_fair_confs "${CONF_ROOT}"
    collect_fair_confs "${UPSU_CONF_ROOT}"
    collect_fair_confs "${UPSU_FAIR_CONF_ROOT}"
  fi
  if [[ -z "${PSU_FAIR_ONLY_CONF_BASENAME:-}" && "${PSU_FAIR_INCLUDE_PSI:-0}" == "1" ]]; then
    local PSI_CONF_ROOT="${PSU_FAIR_PSI_CONF_DIR:-$(psu_bench_resources)/psi}"
    collect_fair_confs "${PSI_CONF_ROOT}"
  fi
  if [[ -z "${PSU_FAIR_ONLY_CONF_BASENAME:-}" ]]; then
    local BA12_CONF_ROOT="${PSU_FAIR_BA12_CONF_DIR:-$(psu_bench_resources)/ba12}"
    collect_fair_confs "${BA12_CONF_ROOT}"
  fi

  if [[ -n "${PSU_FAIR_PROTOCOL_ALLOWLIST:-}" ]]; then
    warn_missing_protocol_allowlist_patterns "${CONF_FILES[@]+"${CONF_FILES[@]}"}"
    local FILTERED=()
    local conf
    for conf in "${CONF_FILES[@]+"${CONF_FILES[@]}"}"; do
      if conf_matches_protocol_allowlist "${conf}"; then
        FILTERED+=("${conf}")
      fi
    done
    CONF_FILES=("${FILTERED[@]+"${FILTERED[@]}"}")
  fi

  if [[ "${#CONF_FILES[@]}" -eq 0 ]]; then
    echo "No fair bench configs under ${CONF_ROOT}" >&2
    return 1
  fi

  local NATIVE_TOOL_DIR="${MPC4J_NATIVE_TOOL_DIR:-${REPO_ROOT}/mpc4j-native-tool/cmake-build-release}"
  local NATIVE_TOOL_FILE NATIVE_FHE_LIB_NAME FHE_BUILD_DIR NATIVE_FHE_FILE JAVA_LIB_PATH
  if [[ "$(uname)" == "Darwin" ]]; then
    NATIVE_TOOL_FILE="${NATIVE_TOOL_DIR}/libmpc4j-native-tool.dylib"
    NATIVE_FHE_LIB_NAME="libmpc4j-native-fhe.dylib"
  else
    NATIVE_TOOL_FILE="${NATIVE_TOOL_DIR}/libmpc4j-native-tool.so"
    NATIVE_FHE_LIB_NAME="libmpc4j-native-fhe.so"
  fi
  FHE_BUILD_DIR="${MPC4J_NATIVE_FHE_DIR:-${REPO_ROOT}/mpc4j-native-fhe/cmake-build-release}"
  NATIVE_FHE_FILE="${FHE_BUILD_DIR}/${NATIVE_FHE_LIB_NAME}"

  if [[ ! -f "${NATIVE_TOOL_FILE}" ]]; then
    echo "Native tool library not found: ${NATIVE_TOOL_FILE}" >&2
    echo "Build: scripts/build_mpc4j_native_tool_macos.sh (macOS) or see mpc4j-native-tool/doc/" >&2
    return 1
  fi

  local FHE_AVAILABLE=0
  JAVA_LIB_PATH="${NATIVE_TOOL_DIR}"
  if [[ -f "${NATIVE_FHE_FILE}" ]]; then
    FHE_AVAILABLE=1
    JAVA_LIB_PATH="${JAVA_LIB_PATH}:${FHE_BUILD_DIR}"
  else
    echo "Note: ${NATIVE_FHE_FILE} not found; CCS:TCLZ23 (UPSU) will be skipped." >&2
  fi

  requires_native_fhe() {
    local conf="$1"
    [[ "${conf}" == *"CCS:TCLZ23"* ]] && return 0
    grep -qE '^[[:space:]]*(psu_pto_name|upsu_pto_name)[[:space:]]*=[[:space:]]*CCS:TCLZ23[[:space:]]*$' "${conf}" 2>/dev/null && return 0
    return 1
  }

  is_upsu_conf() {
    grep -qE '^[[:space:]]*pto_type[[:space:]]*=[[:space:]]*UPSU[[:space:]]*$' "$1" 2>/dev/null
  }

  local PSU_DRIVER_JAR
  PSU_DRIVER_JAR="$(ls -t "${REPO_ROOT}"/mpc4j-psu/apps/driver/target/mpc4j-psu-driver-*-jar-with-dependencies.jar 2>/dev/null | head -1 || true)"
  if [[ -z "${PSU_DRIVER_JAR}" || ! -f "${PSU_DRIVER_JAR}" ]]; then
    echo "PSU driver fat JAR not found. Build: mvn -f mpc4j-psu/pom.xml install -DskipTests" >&2
    return 1
  fi
  if ! jar tf "${PSU_DRIVER_JAR}" 2>/dev/null | grep -q 'low_mc/lowmc_128_128_20.txt'; then
    echo "Fat JAR missing LowMC resources; USENIX:ConYuWeiminDon23_SKE will fail. Rebuild: mvn -f mpc4j-psu/pom.xml install -DskipTests" >&2
    return 1
  fi

  local JAVA_BIN="${JAVA_BIN:-java}"
  local JAVA_OPTS=(
    "--add-modules" "jdk.incubator.vector"
    "-Djava.library.path=${JAVA_LIB_PATH}"
  )
  local FAIR_JAVA_XMX="${PSU_FAIR_JAVA_XMX:-32g}"
  local FAIR_JAVA_XMS="${PSU_FAIR_JAVA_XMS:-4g}"
  local FAIR_LARGE_HEAP_LOG_SIZE="${PSU_FAIR_LARGE_HEAP_LOG_SIZE:-16}"
  local JAVA_OPTS_LARGE=(
    "${JAVA_OPTS[@]}"
    "-Xms${FAIR_JAVA_XMS}"
    "-Xmx${FAIR_JAVA_XMX}"
  )
  local JAVA_OPTS_FHE=(
    "${JAVA_OPTS[@]}"
    "-Xms2g"
    "-Xmx8g"
  )

  max_log_set_exponent() {
    local conf="$1" s c
    s="$(max_log_exponent_from_conf "${conf}" server_log_set_size)"
    c="$(max_log_exponent_from_conf "${conf}" client_log_set_size)"
    if [[ "${s}" -gt "${c}" ]]; then echo "${s}"; else echo "${c}"; fi
  }

  needs_large_heap() {
    local max
    max="$(max_log_set_exponent "$1")"
    [[ "${max}" -ge "${FAIR_LARGE_HEAP_LOG_SIZE}" ]]
  }

  has_complete_fair_output() {
    has_complete_fair_output_for_conf "$1" "${TEMP_DIR}"
  }

  conf_label() {
    local conf="$1" rel
    rel="${conf#${CONF_ROOT}/}"
    if [[ "${rel}" != "${conf}" ]]; then echo "pso/${rel}"; return; fi
    rel="${conf#${UPSU_CONF_ROOT}/}"
    if [[ "${rel}" != "${conf}" ]]; then echo "upsu/${rel}"; return; fi
    rel="${conf#${UPSU_FAIR_CONF_ROOT}/}"
    if [[ "${rel}" != "${conf}" ]]; then echo "upsu_fair/${rel}"; return; fi
    if [[ "${PSU_FAIR_INCLUDE_PSI:-0}" == "1" ]]; then
      rel="${conf#${PSU_FAIR_PSI_CONF_DIR:-$(psu_bench_resources)/psi}/}"
      if [[ "${rel}" != "${conf}" ]]; then echo "psi/${rel}"; return; fi
    fi
    rel="${conf#${BA12_CONF_ROOT}/}"
    if [[ "${rel}" != "${conf}" ]]; then echo "ba12/${rel}"; return; fi
    basename "${conf}"
  }

  fair_main_class() {
    if is_upsu_conf "$1"; then
      echo "edu.alibaba.mpc4j.s2pc.upso.main.UpsoMain"
    else
      echo "edu.alibaba.mpc4j.s2pc.pso.main.PsoMain"
    fi
  }

  # setsid: own session/process group so watchdog SIGKILL stops the whole JVM.
  run_java() {
    local party="$1" target="$2" main_class opts resolved
    resolved="$(psu_fair_conf_with_save_path "${target}")"
    main_class="$(fair_main_class "${resolved}")"
    opts=("${JAVA_OPTS[@]}")
    if requires_native_fhe "${resolved}"; then opts=("${JAVA_OPTS_FHE[@]}"); fi
    if needs_large_heap "${resolved}"; then opts=("${JAVA_OPTS_LARGE[@]}"); fi
    if command -v setsid >/dev/null 2>&1; then
      ( cd "${REPO_ROOT}" && exec setsid "${JAVA_BIN}" "${opts[@]}" -cp "${PSU_DRIVER_JAR}" "${main_class}" "${resolved}" "${party}" )
    else
      ( cd "${REPO_ROOT}" && exec "${JAVA_BIN}" "${opts[@]}" -cp "${PSU_DRIVER_JAR}" "${main_class}" "${resolved}" "${party}" )
    fi
  }

  run_java_with_timeout() {
    local timeout_seconds="$1" party="$2" target="$3" pid watchdog status marker
    marker="$(psu_fair_new_timeout_marker)"
    run_java "${party}" "${target}" &
    pid=$!
    LAST_JAVA_CLIENT_PID="${pid}"
    start_timeout_watchdog "${timeout_seconds}" "${pid}" "${party} ${target}" "${marker}"
    watchdog="${LAST_TIMEOUT_WATCHDOG_PID}"
    wait "${pid}"
    status=$?
    cancel_timeout_watchdog "${watchdog}"
    if [[ -f "${marker}" ]]; then
      psu_fair_force_kill_pid "${pid}"
      rm -f "${marker}"
      return "${PSU_FAIR_TIMEOUT_EXIT}"
    fi
    return "${status}"
  }

  check_ports_free() {
    ensure_fair_bench_ports_free "$1"
  }

  wait_for_server_port() {
    local port="$1" log="$2" server_pid="$3" ready=0 _
    for _ in $(seq 1 180); do
      if ! kill -0 "${server_pid}" 2>/dev/null; then
        echo "Server exited during startup. Last lines of ${log}:" >&2
        tail -15 "${log}" >&2 || true
        return 1
      fi
      if grep -qE 'BindException|Address already in use' "${log}" 2>/dev/null; then
        kill "${server_pid}" 2>/dev/null || true
        return 1
      fi
      if command -v nc >/dev/null 2>&1; then
        if nc -z 127.0.0.1 "${port}" 2>/dev/null; then ready=1; break; fi
      else
        sleep 3
        ready=1
        break
      fi
      sleep 1
    done
    [[ "${ready}" -eq 1 ]]
  }

  local BENCH_RUN_DIR
  BENCH_RUN_DIR="$(psu_fair_bench_run_dir "${PSU_FAIR_ONLY_LOG:-5}")"
  # PsuMain writes .output files under temp/bench/<network>/ (save_path); logs/summaries go under the same tree.
  local TEMP_DIR
  TEMP_DIR="$(psu_fair_output_temp_dir)"
  mkdir -p "${TEMP_DIR}" "${BENCH_RUN_DIR}/logs"
  local OVERALL_LOG="${TMPDIR:-/tmp}/mpc4j_psu_fair_both.log"
  : >"${OVERALL_LOG}"

  local FAILURES=0 SKIP_UNTIL="${PSU_FAIR_FROM:-}" name conf pto protocol_key SERVER_PORT SERVER_LOG SERVER_PID
  local CURRENT_LOG="${PSU_FAIR_ONLY_LOG:-}" LAST_JAVA_CLIENT_PID=""
  if [[ -n "${SKIP_UNTIL}" ]]; then
    echo "Resuming from: ${SKIP_UNTIL}"
  fi

  echo "Balanced PSU configs: ${CONF_ROOT}"
  echo "Will run ${#CONF_FILES[@]} benchmark(s)"
  echo ""

  for conf in "${CONF_FILES[@]}"; do
    name="$(conf_label "${conf}")"
    if [[ -n "${SKIP_UNTIL}" ]]; then
      if [[ "${name}" != "${SKIP_UNTIL}"* ]]; then
        echo "SKIP ${name} (before ${SKIP_UNTIL})" | tee -a "${OVERALL_LOG}"
        continue
      fi
      SKIP_UNTIL=""
    fi
    echo "======== ${name} ========" | tee -a "${OVERALL_LOG}"
    pto="$(read_fair_pto_name "${conf}")"
    if should_skip_pt26_conf "${conf}"; then
      echo "SKIP ${name} (EUROCRYPT:PisTri26 disabled)" | tee -a "${OVERALL_LOG}"
      continue
    fi
    if should_skip_pgt26_2m_conf "${conf}"; then
      echo "SKIP ${name} (EUROCRYPT:PuGaoTri26 disabled)" | tee -a "${OVERALL_LOG}"
      continue
    fi
    if should_skip_ks05_oversize_conf "${conf}"; then
      echo "SKIP ${name} (C:KisSon05 small-set limit; set PSU_FAIR_KS05_MAX_LOG_SIZE to override)" | tee -a "${OVERALL_LOG}"
      continue
    fi
    if should_skip_non_paper_exact "${pto}"; then
      echo "SKIP ${name} (proxy/inspired ${pto})" | tee -a "${OVERALL_LOG}"
      continue
    fi
    protocol_key="$(fair_bench_protocol_key_from_label "${name}")"
    if [[ -n "${CURRENT_LOG}" ]] && psu_fair_should_skip_protocol_after_timeout "${protocol_key}" "${CURRENT_LOG}"; then
      echo "SKIP ${name} (timed out at 2^$(psu_fair_protocol_timeout_log_for_key "${protocol_key}"); skip larger n)" | tee -a "${OVERALL_LOG}"
      continue
    fi
    if [[ "${PSU_FAIR_FORCE:-0}" == "1" ]]; then
      remove_incomplete_fair_outputs_for_conf "${conf}" "${TEMP_DIR}"
    fi
    if [[ "${PSU_FAIR_FORCE:-0}" != "1" ]] && has_complete_fair_output "${conf}"; then
      echo "SKIP ${name} (complete output exists)" | tee -a "${OVERALL_LOG}"
      continue
    fi
    if needs_large_heap "${conf}"; then
      echo "Large heap (-Xmx${FAIR_JAVA_XMX}) for 2^$(max_log_set_exponent "${conf}")" | tee -a "${OVERALL_LOG}"
    fi
    if [[ "${FHE_AVAILABLE}" -eq 0 ]] && requires_native_fhe "${conf}"; then
      echo "SKIP ${name} (needs libmpc4j-native-fhe)" | tee -a "${OVERALL_LOG}"
      continue
    fi
    if ! check_ports_free "${conf}"; then
      echo "FAILED ${name} (ports busy)" | tee -a "${OVERALL_LOG}"
      FAILURES=$((FAILURES + 1))
      continue
    fi
    SERVER_PORT="$(fair_bench_read_server_port "${conf}")"
    SERVER_LOG="${PSU_FAIR_BENCH_DIR:-${TMPDIR:-/tmp}}/logs/server_${name//\//_}.log"
    mkdir -p "$(dirname "${SERVER_LOG}")"
    local PROTOCOL_TIMEOUT server_timeout_marker timed_out=0
    PROTOCOL_TIMEOUT="$(psu_fair_protocol_timeout_seconds)"
    local BENCH_WATCHDOG=""
    server_timeout_marker="$(psu_fair_new_timeout_marker)"

    run_java server "${conf}" >"${SERVER_LOG}" 2>&1 &
    SERVER_PID=$!

    start_timeout_watchdog "${PROTOCOL_TIMEOUT}" "${SERVER_PID}" "${name}" "${server_timeout_marker}"
    BENCH_WATCHDOG="${LAST_TIMEOUT_WATCHDOG_PID}"

    cleanup_one() {
      if kill -0 "${SERVER_PID}" 2>/dev/null; then
        kill "${SERVER_PID}" 2>/dev/null || true
        wait "${SERVER_PID}" 2>/dev/null || true
      fi
      free_fair_bench_ports "${SERVER_PORT}" "$(fair_bench_read_client_port "${conf}")" || true
    }
    trap cleanup_one EXIT INT TERM

    if ! wait_for_server_port "${SERVER_PORT}" "${SERVER_LOG}" "${SERVER_PID}"; then
      echo "FAILED ${name} (server did not start)" | tee -a "${OVERALL_LOG}"
      FAILURES=$((FAILURES + 1))
      rm -f "${server_timeout_marker}"
      cancel_timeout_watchdog "${BENCH_WATCHDOG}"
      cleanup_one
      trap - EXIT INT TERM
      continue
    fi

    local client_status=0
    set +o pipefail
    run_java_with_timeout "${PROTOCOL_TIMEOUT}" client "${conf}" 2>&1 | tee -a "${OVERALL_LOG}"
    client_status=${PIPESTATUS[0]}
    set -o pipefail

    if [[ -f "${server_timeout_marker}" ]]; then
      timed_out=1
    fi
    if [[ "${client_status}" -eq "${PSU_FAIR_TIMEOUT_EXIT}" ]]; then
      timed_out=1
    fi
    if [[ "${timed_out}" -eq 1 ]]; then
      psu_fair_force_kill_pid "${SERVER_PID}"
      psu_fair_force_kill_pid "${LAST_JAVA_CLIENT_PID}"
      psu_fair_kill_java_for_conf "${conf}"
      cleanup_one
    fi

    if [[ "${timed_out}" -eq 1 ]]; then
      if has_complete_fair_output_for_conf "${conf}" "${TEMP_DIR}"; then
        echo "OK ${name} (exceeded ${PROTOCOL_TIMEOUT}s wall limit; skip larger n)" | tee -a "${OVERALL_LOG}"
      else
        echo "FAILED ${name} (timeout)" | tee -a "${OVERALL_LOG}"
        tail -15 "${SERVER_LOG}" >&2 || true
        FAILURES=$((FAILURES + 1))
      fi
      if [[ -n "${CURRENT_LOG}" ]]; then
        psu_fair_record_protocol_timeout "${protocol_key}" "${CURRENT_LOG}"
      fi
    elif [[ "${client_status}" -eq 0 ]] || has_complete_fair_output_for_conf "${conf}" "${TEMP_DIR}"; then
      if [[ "${client_status}" -ne 0 ]]; then
        echo "OK ${name} (complete output; client exit ${client_status})" | tee -a "${OVERALL_LOG}"
      else
        echo "OK ${name}" | tee -a "${OVERALL_LOG}"
      fi
      psu_fair_clear_protocol_timeout "${protocol_key}"
    else
      echo "FAILED ${name} (client or timeout)" | tee -a "${OVERALL_LOG}"
      tail -15 "${SERVER_LOG}" >&2 || true
      psu_fair_force_kill_pid "${SERVER_PID}"
      psu_fair_force_kill_pid "${LAST_JAVA_CLIENT_PID}"
      psu_fair_kill_java_for_conf "${conf}"
      FAILURES=$((FAILURES + 1))
    fi

    rm -f "${server_timeout_marker}"
    cancel_timeout_watchdog "${BENCH_WATCHDOG}"
    cleanup_one
    trap - EXIT INT TERM
    sleep "${PSU_FAIR_BENCH_SLEEP:-1}"
  done

  echo "Full log: ${OVERALL_LOG}"
  if [[ "${FAILURES}" -gt 0 ]]; then
    echo "${FAILURES} benchmark(s) failed." >&2
    if [[ "${PSU_FAIR_KEEP_GOING:-0}" == "1" ]]; then
      echo "Continuing despite benchmark failures (PSU_FAIR_KEEP_GOING=1)." >&2
      return 0
    fi
    return 1
  fi
  echo "All ${#CONF_FILES[@]} benchmark(s) completed."
}

run_one_log() {
  local LOG="$1"
  if ! [[ "${LOG}" =~ ^[0-9]+$ ]] || [[ "${LOG}" -lt 1 ]] || [[ "${LOG}" -gt 30 ]]; then
    echo "LOG must be an integer in [1, 30] (got: ${LOG})" >&2
    return 1
  fi

  local N=$(( 1 << LOG ))
  local APPEND="fair_bench_2p${LOG}"

  local skip_pt26="${SKIP_PT26}"
  if [[ -z "${skip_pt26}" ]]; then
    if [[ "${LOG}" -ge 18 ]]; then skip_pt26=1; else skip_pt26=0; fi
  fi

  ensure_jdk17_home || return 1
  export MPC4J_NATIVE_TOOL_DIR="${MPC4J_NATIVE_TOOL_DIR:-${REPO_ROOT}/mpc4j-native-tool/cmake-build-release}"
  export MPC4J_NATIVE_FHE_DIR="${MPC4J_NATIVE_FHE_DIR:-${REPO_ROOT}/mpc4j-native-fhe/cmake-build-release}"

  export PSU_BENCH_RES="${REPO_ROOT}/mpc4j-psu/bench/configs"
  export PSU_FAIR_CONF_DIR="${PSU_BENCH_RES}/psu"
  export PSU_FAIR_BENCH_DIR="$(psu_fair_network_bench_root)/psu_fair/2p${LOG}"
  export PSU_FAIR_ONLY_LOG="${LOG}"
  export PSU_FAIR_SKIP_PT26="${skip_pt26}"
  export PSU_FAIR_FORCE="${FORCE}"
  export PSU_FAIR_FROM="${FROM_LABEL}"
  # Heap threshold for large sets (default 16 = 2^16); do not set to LOG or every bench uses -Xmx32g.
  export PSU_FAIR_LARGE_HEAP_LOG_SIZE="${PSU_FAIR_LARGE_HEAP_LOG_SIZE:-16}"

  if [[ -n "${ONLY_PROTOCOLS}" ]]; then
    export PSU_FAIR_PROTOCOL_ALLOWLIST="${ONLY_PROTOCOLS}"
  fi

  if [[ "${LOG}" -ge 16 ]]; then
    export PSU_FAIR_JAVA_XMX="${PSU_FAIR_JAVA_XMX:-48g}"
  else
    export PSU_FAIR_JAVA_XMX="${PSU_FAIR_JAVA_XMX:-32g}"
  fi

  mkdir -p "${REPO_ROOT}/temp/empty_upsu_stress"

  if [[ "${INCLUDE_UPSU}" -eq 1 ]]; then
    export PSU_FAIR_UPSU_CONF_DIR="${PSU_BENCH_RES}/upsu"
    export PSU_FAIR_UPSU_FAIR_CONF_DIR="${PSU_BENCH_RES}/upsu"
  else
    export PSU_FAIR_UPSU_CONF_DIR="${REPO_ROOT}/temp/empty_upsu_stress"
    export PSU_FAIR_UPSU_FAIR_CONF_DIR="${REPO_ROOT}/temp/empty_upsu_stress"
  fi

  if [[ "${NO_PSI}" -eq 1 ]]; then
    export PSU_FAIR_INCLUDE_PSI=0
  else
    export PSU_FAIR_INCLUDE_PSI=1
    export PSU_FAIR_PSI_CONF_DIR="${PSU_BENCH_RES}/psi"
  fi

  local GEN_ARGS=( "${LOG}" )
  if [[ "${INCLUDE_UPSU}" -eq 1 ]]; then
    GEN_ARGS+=( --include-upsu )
  fi

  echo "=== Fair benchmark at 2^${LOG} = ${N} x ${N} (${APPEND}) [network: $(psu_fair_network_slug)] ==="
  echo "  EUROCRYPT:PisTri26: $([[ "${skip_pt26}" == 1 ]] && echo skip || echo run)"
  echo "  PSI: $([[ "${NO_PSI}" == 1 ]] && echo skip || echo include)"
  echo "  UPSU: $([[ "${INCLUDE_UPSU}" == 1 ]] && echo include || echo skip)"
  if [[ -n "${ONLY_PROTOCOLS}" ]]; then
    echo "  Protocol filter: ${ONLY_PROTOCOLS}"
  fi
  echo ""

  if [[ "${SKIP_GEN}" -eq 0 ]]; then
    python3 "${SCRIPT_DIR}/gen_fair_bench_at_log.py" "${GEN_ARGS[@]}"
  else
    echo "Skipping gen_fair_bench_at_log.py (--only set)"
  fi

  if [[ "${NO_BUILD}" -ne 1 ]]; then
    ensure_psu_driver_jar >/dev/null || return 1
  fi

  local LOG_FILE="${PSU_FAIR_BENCH_DIR}/logs/psu_fair_2p${LOG}.log"
  if ! mkdir -p "${PSU_FAIR_BENCH_DIR}/logs"; then
    echo "FAILED: cannot create log dir ${PSU_FAIR_BENCH_DIR}/logs" >&2
    return 1
  fi
  : > "${LOG_FILE}" || {
    echo "FAILED: cannot write log ${LOG_FILE}" >&2
    return 1
  }

  echo "Running benchmarks $(date -Iseconds) — log: ${LOG_FILE}"
  local bench_status=0
  if ! run_psu_fair_benchmarks >> "${LOG_FILE}" 2>&1; then
    bench_status=1
    echo "benchmarks FAILED — see ${LOG_FILE}" >&2
  else
    echo "benchmarks OK $(date -Iseconds)"
  fi

  local SUMMARY="${PSU_FAIR_BENCH_DIR}/summary_2p${LOG}.csv"
  local SHORT_SUMMARY="${PSU_FAIR_BENCH_DIR}/summary_2p${LOG}_short.csv"
  if command -v python3 >/dev/null 2>&1; then
    resummarize_psu_fair_log "${LOG}" "${SCRIPT_DIR}" "$(psu_fair_output_temp_dir)" \
      2>> "${LOG_FILE}" || true
    echo "Summary: ${SUMMARY}"
    echo "Short summary (ranked): ${SHORT_SUMMARY}"
    if [[ "${INCLUDE_UPSU}" -eq 1 ]]; then
      local UPSU_SUMMARY="${PSU_FAIR_BENCH_DIR}/upsu_summary_2p${LOG}.csv"
      local UPSU_SHORT_SUMMARY="${PSU_FAIR_BENCH_DIR}/upsu_summary_2p${LOG}_short.csv"
      python3 "${SCRIPT_DIR}/summarize_upsu_fair_outputs.py" \
        --temp-dir "$(psu_fair_output_temp_dir)" \
        --out "${UPSU_SUMMARY}" 2>> "${LOG_FILE}" || true
      echo "UPSU summary: ${UPSU_SUMMARY}"
      if [[ -f "${UPSU_SUMMARY}" ]]; then
        python3 "${SCRIPT_DIR}/short_summarize_psu_fair_outputs.py" \
          "${UPSU_SUMMARY}" "${UPSU_SHORT_SUMMARY}" 2>> "${LOG_FILE}" || true
        echo "UPSU short summary (ranked): ${UPSU_SHORT_SUMMARY}"
      fi
    fi
  fi
  if [[ "${bench_status}" -ne 0 ]]; then
    if [[ "${PSU_FAIR_KEEP_GOING:-0}" == "1" ]]; then
      echo "Done 2^${LOG} with failures (keep-going) $(date -Iseconds)" >&2
      return 0
    fi
    return 1
  fi
  echo "Done 2^${LOG} $(date -Iseconds)"
}

run_one_unbalanced() {
  local S_LOG="$1" C_LOG="$2"
  if ! [[ "${S_LOG}" =~ ^[0-9]+$ && "${C_LOG}" =~ ^[0-9]+$ ]]; then
    echo "unbalanced logs must be integers" >&2
    return 1
  fi

  local SENDER_N=$(( 1 << S_LOG ))
  local RECEIVER_N=$(( 1 << C_LOG ))
  local APPEND="fair_bench_unbalanced_2p${S_LOG}x2p${C_LOG}"
  local CONF_NAME="${APPEND}.conf"

  ensure_jdk17_home || return 1
  export MPC4J_NATIVE_TOOL_DIR="${MPC4J_NATIVE_TOOL_DIR:-${REPO_ROOT}/mpc4j-native-tool/cmake-build-release}"
  export MPC4J_NATIVE_FHE_DIR="${MPC4J_NATIVE_FHE_DIR:-${REPO_ROOT}/mpc4j-native-fhe/cmake-build-release}"
  export PSU_BENCH_RES="${REPO_ROOT}/mpc4j-psu/bench/configs"
  export PSU_FAIR_BENCH_DIR="$(psu_fair_network_bench_root)/matrix/unbalanced_2p${S_LOG}x2p${C_LOG}"
  export PSU_FAIR_ONLY_CONF_BASENAME="${CONF_NAME}"
  unset PSU_FAIR_ONLY_LOG
  # Default: do not skip EUROCRYPT:PisTri26 on unbalanced (override with --skip-pt26).
  export PSU_FAIR_SKIP_PT26="${SKIP_PT26:-0}"
  export PSU_FAIR_FORCE="${FORCE}"
  export PSU_FAIR_FROM="${FROM_LABEL}"
  export PSU_FAIR_LARGE_HEAP_LOG_SIZE="${PSU_FAIR_LARGE_HEAP_LOG_SIZE:-16}"
  export PSU_FAIR_JAVA_XMX="${PSU_FAIR_JAVA_XMX:-48g}"
  export PSU_FAIR_INCLUDE_PSI=0
  export PSU_FAIR_CONF_DIR="${REPO_ROOT}/temp/empty_psu_matrix_balanced"
  mkdir -p "${PSU_FAIR_CONF_DIR}"
  export PSU_FAIR_UPSU_CONF_DIR="${PSU_BENCH_RES}/upsu"
  export PSU_FAIR_UPSU_FAIR_CONF_DIR="${PSU_BENCH_RES}/upsu"
  if [[ -n "${ONLY_PROTOCOLS}" ]]; then
    export PSU_FAIR_PROTOCOL_ALLOWLIST="${ONLY_PROTOCOLS}"
  else
    unset PSU_FAIR_PROTOCOL_ALLOWLIST
  fi

  echo "=== Unbalanced UPSU: sender 2^${S_LOG} = ${SENDER_N}, receiver 2^${C_LOG} = ${RECEIVER_N} (${APPEND}) [network: $(psu_fair_network_slug)] ==="
  if [[ -n "${ONLY_PROTOCOLS}" ]]; then
    echo "  Protocol filter: ${ONLY_PROTOCOLS}"
  fi
  echo ""

  python3 "${SCRIPT_DIR}/gen_fair_bench_unbalanced.py" "${S_LOG}" "${C_LOG}"

  if [[ "${NO_BUILD}" -ne 1 ]]; then
    ensure_psu_driver_jar >/dev/null || return 1
  fi

  local LOG_FILE="${PSU_FAIR_BENCH_DIR}/logs/upsu_unbalanced_2p${S_LOG}x2p${C_LOG}.log"
  mkdir -p "${PSU_FAIR_BENCH_DIR}/logs"
  : > "${LOG_FILE}"

  echo "Running unbalanced benchmarks $(date -Iseconds) — log: ${LOG_FILE}"
  if run_psu_fair_benchmarks >> "${LOG_FILE}" 2>&1; then
    echo "benchmarks OK $(date -Iseconds)"
  else
    echo "benchmarks FAILED — see ${LOG_FILE}" >&2
    return 1
  fi

  local SUMMARY="${PSU_FAIR_BENCH_DIR}/summary_unbalanced_2p${S_LOG}x2p${C_LOG}.csv"
  local SHORT_SUMMARY="${PSU_FAIR_BENCH_DIR}/summary_unbalanced_2p${S_LOG}x2p${C_LOG}_short.csv"
  if command -v python3 >/dev/null 2>&1; then
    local TEMP_OUT
    TEMP_OUT="$(psu_fair_output_temp_dir)"
    # Harness unequal-size runs may write PSU_* (including C:KisSon05 / JOC:HazNis12) or native UPSU_*.
    python3 "${SCRIPT_DIR}/summarize_psu_fair_outputs.py" \
      --temp-dir "${TEMP_OUT}" \
      --only-append "${APPEND}" \
      --no-small-ec-modes \
      --append-glob 'PSI_*.output' \
      --append-glob 'UPSU_*.output' \
      --out "${SUMMARY}" 2>> "${LOG_FILE}" || true
    echo "Unbalanced summary: ${SUMMARY}"
    if [[ -f "${SUMMARY}" ]]; then
      python3 "${SCRIPT_DIR}/short_summarize_psu_fair_outputs.py" \
        "${SUMMARY}" "${SHORT_SUMMARY}" 2>> "${LOG_FILE}" || true
      echo "Unbalanced short summary: ${SHORT_SUMMARY}"
    fi
  fi
  unset PSU_FAIR_ONLY_CONF_BASENAME
  echo "Done unbalanced 2^${S_LOG} x 2^${C_LOG} $(date -Iseconds)"
}

if ! network_profile_is_valid "${NETWORK_PROFILE}"; then
  echo "invalid --network: ${NETWORK_PROFILE} (expected: $(network_profile_names))" >&2
  exit 1
fi
export PSU_FAIR_NETWORK_PROFILE="${NETWORK_PROFILE}"

if [[ "${NETWORK_PROFILE}" != "none" ]]; then
  if [[ "${PSU_FAIR_SKIP_NETWORK_APPLY:-0}" == "1" ]]; then
    echo "Skipping tc apply (PSU_FAIR_SKIP_NETWORK_APPLY=1); using existing shaping on ${PSU_FAIR_NET_DEV:-lo}"
    network_profile_show
  elif ! network_profile_apply "${NETWORK_PROFILE}"; then
    echo "network profile ${NETWORK_PROFILE} not applied (use --network none for localhost)" >&2
    if [[ "${PSU_FAIR_KEEP_GOING:-0}" == "1" ]]; then
      echo "WARN: continuing without network shaping (PSU_FAIR_KEEP_GOING=1)" >&2
    else
      exit 1
    fi
  else
    network_profile_install_cleanup_trap
  fi
fi

FAILURES=0
if [[ -n "${UNBALANCED_SERVER_LOG}" && -n "${UNBALANCED_CLIENT_LOG}" ]]; then
  if ! run_one_unbalanced "${UNBALANCED_SERVER_LOG}" "${UNBALANCED_CLIENT_LOG}"; then
    FAILURES=$((FAILURES + 1))
  fi
elif [[ ${#LOGS[@]} -gt 0 ]]; then
for LOG in "${LOGS[@]}"; do
  if [[ "${SUITE}" == "fair" || "${SUITE}" == "all" ]]; then
    if ! run_one_log "${LOG}"; then
      FAILURES=$((FAILURES + 1))
    fi
  fi
  if [[ "${SUITE}" == "small-ec" || "${SUITE}" == "all" ]]; then
    if ! run_small_ec_fair_suite "${LOG}" "${FORCE}" "${NO_BUILD}"; then
      FAILURES=$((FAILURES + 1))
    fi
    # Fair suite summarizes before SMALL_EC; refresh so psu_fair CSV includes case rows.
    if [[ "${SUITE}" == "all" ]]; then
      resummarize_psu_fair_log "${LOG}" "${SCRIPT_DIR}" "$(psu_fair_output_temp_dir)" || true
    fi
  fi
done
else
  echo "No LOG arguments and no --unbalanced; nothing to run." >&2
  usage 1
fi

if [[ "${FAILURES}" -gt 0 ]]; then
  echo "COMPLETED WITH ${FAILURES} SUITE FAILURE(S) $(date -Iseconds)" >&2
  if [[ "${PSU_FAIR_KEEP_GOING:-0}" == "1" ]]; then
    echo "Exiting 0 despite failures (PSU_FAIR_KEEP_GOING=1)" >&2
    exit 0
  fi
  exit 1
fi
echo "ALL DONE $(date -Iseconds)"
