#!/usr/bin/env bash
# Run the full fair-bench matrix: balanced PSU + unbalanced UPSU, then union CSV.
#
# Phases (balanced phases use --suite all = fair protocols + SMALL_EC 8-case matrix):
#   (1) Balanced 2^4, 2^5, 2^6, 2^8, 2^10  — PSU + PSI + ASIACCS:BlaAgu12 (all sizes) + SMALL_EC (no UPSU)
#       Includes USENIX:BinYujConYanYu25 balanced PSU (psu/08_USENIX:BinYujConYanYu25; native tool only).
#   (2) Unbalanced sender 2^20, receiver 2^10 — UPSU only (CCS:TCLZ23 + USENIX:BinYujConYanYu25; CCS:TCLZ23 needs native FHE)
#   (3) Balanced 2^12, 2^16, 2^20
#
# USENIX:BinYujConYanYu25 balanced: psu/08_USENIX:BinYujConYanYu25 (native tool). USENIX:BinYujConYanYu25 unbalanced: upsu/10_USENIX:BinYujConYanYu25 with --with-unbalanced
# on --small-only (default pair 2^10 x 2^4; override via --unbalanced-pair S,C).
#
# Usage (from repo root):
#   ./scripts/run_psu_fair_matrix.sh
#   ./scripts/run_psu_fair_matrix.sh --mini-only       # 2^4, 2^5 only (quick smoke)
#   ./scripts/run_psu_fair_matrix.sh --small-only       # 2^4, 2^5, 2^6, 2^8, 2^10 (includes 2^5 by design)
#   ./scripts/run_psu_fair_matrix.sh --small-only --skip-logs 5   # omit 2^5 (e.g. already done via --mini-only)
#   ./scripts/run_psu_fair_matrix.sh --small-only --no-2p5        # same as --skip-logs 5
#   ./scripts/run_psu_fair_matrix.sh --large-only       # 2^12, 2^16, 2^20 only
#   ./scripts/run_psu_fair_matrix.sh --no-build --force
#   ./scripts/run_psu_fair_matrix.sh --skip-pgt26-2m   # opt out of EUROCRYPT:PuGaoTri26 (included by default)
#   ./scripts/run_psu_fair_matrix.sh --summarize-only   # rebuild CSV from temp/ outputs
#   ./scripts/run_psu_fair_matrix.sh --summarize-only --mini-only
#   ./scripts/run_psu_fair_matrix.sh --summarize-only --small-only
#   ./scripts/run_psu_fair_matrix.sh --summarize-only --large-only
#   ./scripts/run_psu_fair_matrix.sh --network WAN1   # 200 Mbps, 80 ms RTT (Linux tc on lo)
#   ./scripts/run_psu_fair_matrix.sh --network LAN    # 10 Gbps, 0.2 ms RTT
#   ./scripts/run_psu_fair_matrix.sh --small-only --no-2p5 --only ASIACCS:BlaAgu12 --suite fair
#   ./scripts/run_psu_fair_matrix.sh --small-only --no-2p5 --only ASIACCS:BlaAgu12 --suite fair --all-networks
#   ./scripts/run_psu_fair_matrix.sh --small-only --only USENIX:BinYujConYanYu25 --suite fair --with-unbalanced
#   ./scripts/run_psu_fair_matrix.sh --small-only --only USENIX:BinYujConYanYu25 --suite fair --with-unbalanced --unbalanced-pair 10,4
#
# Network profiles (--network): none (default), LAN, WAN1, WAN2. Requires Linux `tc` + sudo.
#
# Each protocol is killed after PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS (default 1800 = 30 min).
# Timed-out runs appear in CSV summaries with "--" performance values.
# If a protocol exceeds the per-protocol wall limit (1800s default) at 2^n — even if
# the JVM later finishes — it is skipped at larger balanced sizes (see
# temp/bench/<network>/matrix/protocol_timeouts.tsv).
#
# Outputs (under temp/bench/<network>/ where <network> is none, LAN, WAN1, or WAN2):
#   temp/bench/<network>/psu_fair/2p<LOG>/summary_*     fair suite + SMALL_EC cases per balanced log
#   temp/bench/<network>/small_ec/2p<LOG>/summary_*     SMALL_EC 8-case matrix per log
#   temp/bench/<network>/matrix/unbalanced_2p20x2p10/   unbalanced run
#   temp/bench/<network>/matrix/summary_combined.csv      union of all phases
#   temp/bench/<network>/matrix/summary_combined_short.csv
#   temp/bench/<network>/matrix/summary_mini.csv           --mini-only combined CSV
#   temp/bench/<network>/matrix/summary_mini_short.csv
#   temp/bench/<network>/matrix/summary_small.csv         --small-only combined CSV
#   temp/bench/<network>/matrix/summary_small_short.csv
#   temp/bench/<network>/matrix/summary_large.csv         --large-only combined CSV
#   temp/bench/<network>/matrix/summary_large_short.csv
#   temp/bench/<network>/PSU_*.output                     raw benchmark outputs

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

RUNNER="${SCRIPT_DIR}/run_psu_fair.sh"
SUMMARIZE="${SCRIPT_DIR}/summarize_psu_fair_combined.py"
MATRIX_DIR=""

# shellcheck source=network_profiles.sh
source "${SCRIPT_DIR}/network_profiles.sh"

# When PSU_FAIR_KEEP_GOING=1, count failures but exit 0 so callers continue.
psu_fair_matrix_finish() {
  local failures="$1" msg="$2"
  if [[ "${failures}" -gt 0 ]]; then
    echo "${msg}" >&2
    if [[ "${PSU_FAIR_KEEP_GOING:-0}" == "1" ]]; then
      echo "Continuing despite matrix failures (PSU_FAIR_KEEP_GOING=1)" >&2
      return 0
    fi
    return 1
  fi
  return 0
}

BALANCED_MINI_LOGS=(4 5)
BALANCED_SMALL_LOGS=(4 5 6 8 10)
BALANCED_LARGE_LOGS=(12 16 20)
UNBALANCED_SENDER_LOG=20
UNBALANCED_RECEIVER_LOG=10
# Small-only unbalanced smoke (sender 2^10, receiver 2^4) when --with-unbalanced is set.
UNBALANCED_SMALL_SENDER_LOG=10
UNBALANCED_SMALL_RECEIVER_LOG=4
# fair + SMALL_EC 8-case matrix for balanced sizes; unbalanced UPSU stays fair-only.
MATRIX_SUITE="all"

EXTRA_ARGS=()
SUMMARIZE_ONLY=0
MINI_ONLY=0
SMALL_ONLY=0
LARGE_ONLY=0
SKIP_PGT26_2M=0
NETWORK_PROFILE="none"
SKIP_LOGS=()
ALL_NETWORKS=0
WITH_UNBALANCED=0

matrix_skip_logs_add() {
  local list="$1" part
  IFS=',' read -ra parts <<< "${list}"
  for part in "${parts[@]}"; do
    part="${part// /}"
    [[ -z "${part}" ]] && continue
    if [[ ! "${part}" =~ ^[0-9]+$ ]]; then
      echo "invalid --skip-logs entry: ${part} (expected log sizes like 5 or 4,5)" >&2
      exit 1
    fi
    SKIP_LOGS+=("${part}")
  done
}

matrix_log_is_skipped() {
  local log="$1" skip
  for skip in "${SKIP_LOGS[@]}"; do
    [[ "${log}" == "${skip}" ]] && return 0
  done
  return 1
}

# Populates MATRIX_RESOLVED_LOGS with logs from remaining args, excluding --skip-logs entries.
matrix_resolve_logs() {
  MATRIX_RESOLVED_LOGS=()
  local log
  for log in "$@"; do
    if matrix_log_is_skipped "${log}"; then
      continue
    fi
    MATRIX_RESOLVED_LOGS+=("${log}")
  done
}

matrix_print_skip_logs() {
  ((${#SKIP_LOGS[@]} > 0)) || return 0
  echo "Skipping log sizes: 2^{${SKIP_LOGS[*]}}"
}

matrix_size_flags_conflict() {
  local n=$((MINI_ONLY + SMALL_ONLY + LARGE_ONLY))
  if [[ "${n}" -ge 1 ]]; then
    echo "cannot combine --mini-only, --small-only, and --large-only" >&2
    exit 1
  fi
}

usage() {
  sed -n '3,21p' "$0" | sed 's/^# \?//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage 0 ;;
    --summarize-only) SUMMARIZE_ONLY=1; shift ;;
    --mini-only)
      matrix_size_flags_conflict
      MINI_ONLY=1
      shift
      ;;
    --small-only)
      matrix_size_flags_conflict
      SMALL_ONLY=1
      shift
      ;;
    --large-only)
      matrix_size_flags_conflict
      LARGE_ONLY=1
      shift
      ;;
    --skip-pgt26-2m) SKIP_PGT26_2M=1; shift ;;
    --skip-logs)
      matrix_skip_logs_add "${2:?--skip-logs needs comma-separated log sizes, e.g. 5 or 4,5}"
      shift 2
      ;;
    --no-2p5)
      SKIP_LOGS+=(5)
      shift
      ;;
    --network)
      if [[ "${ALL_NETWORKS}" -eq 1 ]]; then
        echo "cannot combine --all-networks with --network" >&2
        exit 1
      fi
      NETWORK_PROFILE="${2:?--network needs none, LAN, WAN1, or WAN2}"
      shift 2
      ;;
    --all-networks) ALL_NETWORKS=1; shift ;;
    --only)
      EXTRA_ARGS+=("$1" "${2:?--only needs a comma-separated pattern list}")
      shift 2
      ;;
    --suite)
      MATRIX_SUITE="${2:?--suite needs fair, small-ec, or all}"
      shift 2
      ;;
    --with-unbalanced) WITH_UNBALANCED=1; shift ;;
    --unbalanced-pair)
      WITH_UNBALANCED=1
      IFS=',' read -r UNBALANCED_SMALL_SENDER_LOG UNBALANCED_SMALL_RECEIVER_LOG <<< "${2:?--unbalanced-pair needs S,C e.g. 10,4}"
      UNBALANCED_SMALL_SENDER_LOG="${UNBALANCED_SMALL_SENDER_LOG// /}"
      UNBALANCED_SMALL_RECEIVER_LOG="${UNBALANCED_SMALL_RECEIVER_LOG// /}"
      if [[ ! "${UNBALANCED_SMALL_SENDER_LOG}" =~ ^[0-9]+$ || ! "${UNBALANCED_SMALL_RECEIVER_LOG}" =~ ^[0-9]+$ ]]; then
        echo "invalid --unbalanced-pair: ${2} (expected sender,receiver logs like 10,4)" >&2
        exit 1
      fi
      shift 2
      ;;
    --force|--no-build|--no-upsu|--no-psi|--skip-pt26|--no-skip-pt26)
      EXTRA_ARGS+=("$1")
      shift
      ;;
    *)
      echo "unknown option: $1" >&2
      usage 1
      ;;
  esac
done

if [[ -n "${PSU_FAIR_MATRIX_SKIP_LOGS:-}" ]]; then
  matrix_skip_logs_add "${PSU_FAIR_MATRIX_SKIP_LOGS}"
fi

# bash 3.2 + set -u treats "${EXTRA_ARGS[@]}" as unbound when the array is empty.
extra_args_has() {
  local needle="$1" arg
  ((${#EXTRA_ARGS[@]} > 0)) || return 1
  for arg in "${EXTRA_ARGS[@]}"; do
    [[ "${arg}" == "${needle}" ]] && return 0
  done
  return 1
}

psu_fair_matrix_init_network() {
  if ! network_profile_is_valid "${NETWORK_PROFILE}"; then
    echo "invalid --network: ${NETWORK_PROFILE} (expected: $(network_profile_names))" >&2
    exit 1
  fi
  export PSU_FAIR_NETWORK_PROFILE="${NETWORK_PROFILE}"
  MATRIX_DIR="$(psu_fair_network_bench_root)/matrix"
  echo "Network profile: ${NETWORK_PROFILE} — outputs under $(psu_fair_network_bench_root)/"
}

run_with_extra_args() {
  if ((${#EXTRA_ARGS[@]} > 0)); then
    "$@" "${EXTRA_ARGS[@]}"
  else
    "$@"
  fi
}

run_combined_summary() {
  local out_csv="${1:-${MATRIX_DIR}/summary_combined.csv}"
  local short_csv="${2:-${MATRIX_DIR}/summary_combined_short.csv}"
  shift 2 2>/dev/null || true
  local -a append_filters=("$@")
  local -a summarize_args=(
    --temp-dir "$(psu_fair_output_temp_dir)"
    --out "${out_csv}"
    --short-out "${short_csv}"
  )
  local log

  mkdir -p "${MATRIX_DIR}"
  echo ""
  if [[ ${#append_filters[@]} -gt 0 ]]; then
    echo "=== Combined summary (append: ${append_filters[*]}) ==="
    for log in "${append_filters[@]}"; do
      summarize_args+=(--only-append "${log}")
    done
  else
    echo "=== Combined summary (balanced PSU/PSI + unbalanced UPSU) ==="
  fi
  python3 "${SUMMARIZE}" "${summarize_args[@]}"
}

balanced_append_filters() {
  local log
  for log in "$@"; do
    echo "fair_bench_2p${log}"
    echo "small_ec_bench_2p${log}"
  done
}

small_set_append_filters() {
  balanced_append_filters "${BALANCED_SMALL_LOGS[@]}"
  if [[ "${WITH_UNBALANCED}" -eq 1 ]]; then
    echo "fair_bench_unbalanced_2p${UNBALANCED_SMALL_SENDER_LOG}x2p${UNBALANCED_SMALL_RECEIVER_LOG}"
  fi
}

mini_set_append_filters() {
  balanced_append_filters "${BALANCED_MINI_LOGS[@]}"
}

full_matrix_append_filters() {
  balanced_append_filters "${BALANCED_SMALL_LOGS[@]}" "${BALANCED_LARGE_LOGS[@]}"
  echo "fair_bench_unbalanced_2p${UNBALANCED_SENDER_LOG}x2p${UNBALANCED_RECEIVER_LOG}"
}

large_set_append_filters() {
  balanced_append_filters "${BALANCED_LARGE_LOGS[@]}"
}

run_small_set_summary() {
  local -a filters=()
  local log
  while IFS= read -r log; do filters+=("${log}"); done < <(small_set_append_filters)
  run_combined_summary \
    "${MATRIX_DIR}/summary_small.csv" \
    "${MATRIX_DIR}/summary_small_short.csv" \
    "${filters[@]}"
}

run_mini_set_summary() {
  local -a filters=()
  local log
  while IFS= read -r log; do filters+=("${log}"); done < <(mini_set_append_filters)
  run_combined_summary \
    "${MATRIX_DIR}/summary_mini.csv" \
    "${MATRIX_DIR}/summary_mini_short.csv" \
    "${filters[@]}"
}

run_large_set_summary() {
  local -a filters=()
  local log
  while IFS= read -r log; do filters+=("${log}"); done < <(large_set_append_filters)
  run_combined_summary \
    "${MATRIX_DIR}/summary_large.csv" \
    "${MATRIX_DIR}/summary_large_short.csv" \
    "${filters[@]}"
}

# Merge fair_bench + small_ec_bench_* into temp/bench/psu_fair/2p<LOG>/summary_* per log.
refresh_psu_fair_summaries_for_logs() {
  local -a logs=("$@")
  if [[ ${#logs[@]} -eq 0 ]]; then
    return 0
  fi
  echo ""
  echo "=== Refreshing psu_fair per-log summaries (fair + SMALL_EC cases) ==="
  resummarize_psu_fair_logs "${SCRIPT_DIR}" "$(psu_fair_output_temp_dir)" "${logs[@]}"
}

run_phase_mini_balanced_with() {
  local run_fn="$1"
  local failures=0
  local log idx=0 total
  matrix_resolve_logs "${BALANCED_MINI_LOGS[@]}"
  total="${#MATRIX_RESOLVED_LOGS[@]}"
  if [[ "${total}" -eq 0 ]]; then
    echo "======== Mini balanced: all log sizes skipped ========"
    return 0
  fi
  echo "======== Mini balanced 2^{${MATRIX_RESOLVED_LOGS[*]}} ========"
  matrix_print_skip_logs
  for log in "${MATRIX_RESOLVED_LOGS[@]}"; do
    idx=$((idx + 1))
    echo ""
    echo "--- [${idx}/${total}] 2^${log} ($(date -Iseconds)) ---"
    if ! PSU_FAIR_SKIP_PGT26_2M="${SKIP_PGT26_2M}" "${run_fn}" \
        "${RUNNER}" "${log}" --suite "${MATRIX_SUITE}" --no-upsu; then
      failures=$((failures + 1))
    fi
    refresh_psu_fair_summaries_for_logs "${log}"
  done
  return "${failures}"
}

run_phase_small_balanced_with() {
  local run_fn="$1"
  local failures=0
  local log idx=0 total
  matrix_resolve_logs "${BALANCED_SMALL_LOGS[@]}"
  total="${#MATRIX_RESOLVED_LOGS[@]}"
  if [[ "${total}" -eq 0 ]]; then
    echo "======== Phase 1: all small log sizes skipped ========"
    return 0
  fi
  echo "======== Phase 1: balanced 2^{${MATRIX_RESOLVED_LOGS[*]}} ========"
  matrix_print_skip_logs
  for log in "${MATRIX_RESOLVED_LOGS[@]}"; do
    idx=$((idx + 1))
    echo ""
    echo "--- [${idx}/${total}] 2^${log} ($(date -Iseconds)) ---"
    if ! PSU_FAIR_SKIP_PGT26_2M="${SKIP_PGT26_2M}" "${run_fn}" \
        "${RUNNER}" "${log}" --suite "${MATRIX_SUITE}" --no-upsu; then
      failures=$((failures + 1))
    fi
    refresh_psu_fair_summaries_for_logs "${log}"
  done
  return "${failures}"
}

run_phase_large_balanced_with() {
  local run_fn="$1"
  local failures=0
  local log idx=0 total
  matrix_resolve_logs "${BALANCED_LARGE_LOGS[@]}"
  total="${#MATRIX_RESOLVED_LOGS[@]}"
  if [[ "${total}" -eq 0 ]]; then
    echo "======== Large balanced: all log sizes skipped ========"
    return 0
  fi
  echo "======== Large balanced 2^{${MATRIX_RESOLVED_LOGS[*]}} ========"
  matrix_print_skip_logs
  echo "  Per-protocol timeout: ${PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS:-1800}s (30 min default)"
  for log in "${MATRIX_RESOLVED_LOGS[@]}"; do
    idx=$((idx + 1))
    echo ""
    echo "--- [${idx}/${total}] 2^${log} = $((1 << log)) ($(date -Iseconds)) ---"
    if ! PSU_FAIR_SKIP_PGT26_2M="${SKIP_PGT26_2M}" "${run_fn}" \
        "${RUNNER}" "${log}" --suite "${MATRIX_SUITE}" --no-upsu; then
      failures=$((failures + 1))
    fi
    refresh_psu_fair_summaries_for_logs "${log}"
  done
  return "${failures}"
}

run_matrix_for_network() {
  local net="$1"
  NETWORK_PROFILE="${net}"
  psu_fair_matrix_init_network

  local -a runner_extra=()
  runner_extra=("${EXTRA_ARGS[@]}")
  if [[ "${NETWORK_PROFILE}" != "none" ]]; then
    runner_extra+=(--network "${NETWORK_PROFILE}")
  fi

  run_with_runner_extra() {
    if ((${#runner_extra[@]} > 0)); then
      "$@" "${runner_extra[@]}"
    else
      "$@"
    fi
  }

  local FAILURES=0

  if [[ "${MINI_ONLY}" -eq 1 ]]; then
    if ! run_phase_mini_balanced_with run_with_runner_extra; then
      FAILURES=$?
    fi
    refresh_psu_fair_summaries_for_logs "${BALANCED_MINI_LOGS[@]}"
    run_mini_set_summary
    if [[ "${FAILURES}" -gt 0 ]]; then
      psu_fair_matrix_finish "${FAILURES}" "MATRIX MINI SET COMPLETED WITH ${FAILURES} FAILURE(S) [${net}]"
      return $?
    fi
    echo "MATRIX MINI SET DONE [${net}] $(date -Iseconds)"
    return 0
  fi

  if [[ "${LARGE_ONLY}" -eq 1 ]]; then
    if ! run_phase_large_balanced_with run_with_runner_extra; then
      FAILURES=$?
    fi
    refresh_psu_fair_summaries_for_logs "${BALANCED_LARGE_LOGS[@]}"
    run_large_set_summary
    if [[ "${FAILURES}" -gt 0 ]]; then
      psu_fair_matrix_finish "${FAILURES}" "MATRIX LARGE SET COMPLETED WITH ${FAILURES} FAILURE(S) [${net}]"
      return $?
    fi
    echo "MATRIX LARGE SET DONE [${net}] $(date -Iseconds)"
    return 0
  fi

  if ! run_phase_small_balanced_with run_with_runner_extra; then
    FAILURES=$?
  fi

  if [[ "${SMALL_ONLY}" -eq 1 ]]; then
    if [[ "${WITH_UNBALANCED}" -eq 1 ]]; then
      echo ""
      echo "======== Small unbalanced UPSU: sender 2^${UNBALANCED_SMALL_SENDER_LOG}, receiver 2^${UNBALANCED_SMALL_RECEIVER_LOG} [${net}] ========"
      if ! PSU_FAIR_SKIP_PGT26_2M="${SKIP_PGT26_2M}" run_with_runner_extra \
          "${RUNNER}" --unbalanced "${UNBALANCED_SMALL_SENDER_LOG}" "${UNBALANCED_SMALL_RECEIVER_LOG}" \
          --suite fair; then
        FAILURES=$((FAILURES + 1))
      fi
    fi
    refresh_psu_fair_summaries_for_logs "${BALANCED_SMALL_LOGS[@]}"
    run_small_set_summary
    if [[ "${FAILURES}" -gt 0 ]]; then
      psu_fair_matrix_finish "${FAILURES}" "MATRIX SMALL SET COMPLETED WITH ${FAILURES} FAILURE(S) [${net}]"
      return $?
    fi
    echo "MATRIX SMALL SET DONE [${net}] $(date -Iseconds)"
    return 0
  fi

  echo ""
  echo "======== Phase 2: unbalanced UPSU sender 2^${UNBALANCED_SENDER_LOG}, receiver 2^${UNBALANCED_RECEIVER_LOG} (CCS:TCLZ23 + USENIX:BinYujConYanYu25) [${net}] ========"
  if ! PSU_FAIR_SKIP_PGT26_2M="${SKIP_PGT26_2M}" run_with_runner_extra \
      "${RUNNER}" --unbalanced "${UNBALANCED_SENDER_LOG}" "${UNBALANCED_RECEIVER_LOG}" \
      --suite fair; then
    FAILURES=$((FAILURES + 1))
  fi

  if ! run_phase_large_balanced_with run_with_runner_extra; then
    FAILURES=$((FAILURES + $?))
  fi

  refresh_psu_fair_summaries_for_logs \
    "${BALANCED_SMALL_LOGS[@]}" "${BALANCED_LARGE_LOGS[@]}"

  full_matrix_filters=()
  while IFS= read -r _append; do full_matrix_filters+=("${_append}"); done < <(full_matrix_append_filters)
  run_combined_summary \
    "${MATRIX_DIR}/summary_combined.csv" \
    "${MATRIX_DIR}/summary_combined_short.csv" \
    "${full_matrix_filters[@]}"

  if [[ "${FAILURES}" -gt 0 ]]; then
    psu_fair_matrix_finish "${FAILURES}" "MATRIX COMPLETED WITH ${FAILURES} PHASE FAILURE(S) [${net}]"
    return $?
  fi
  echo "MATRIX ALL DONE [${net}] $(date -Iseconds)"
  return 0
}

if [[ "${SUMMARIZE_ONLY}" -eq 1 ]]; then
  psu_fair_matrix_init_network
  if [[ "${MINI_ONLY}" -eq 1 ]]; then
    refresh_psu_fair_summaries_for_logs "${BALANCED_MINI_LOGS[@]}"
    run_mini_set_summary
  elif [[ "${SMALL_ONLY}" -eq 1 ]]; then
    refresh_psu_fair_summaries_for_logs "${BALANCED_SMALL_LOGS[@]}"
    run_small_set_summary
  elif [[ "${LARGE_ONLY}" -eq 1 ]]; then
    refresh_psu_fair_summaries_for_logs "${BALANCED_LARGE_LOGS[@]}"
    run_large_set_summary
  else
    refresh_psu_fair_summaries_for_logs \
      "${BALANCED_SMALL_LOGS[@]}" "${BALANCED_LARGE_LOGS[@]}"
    run_combined_summary
  fi
  exit 0
fi

ensure_jdk17_home || exit 1

if ! extra_args_has --no-build; then
  if driver_jar="$(NO_BUILD=1 ensure_psu_driver_jar 2>/dev/null)"; then
    echo "=== Using existing PSU driver JAR ==="
    echo "  ${driver_jar}"
  else
    echo "=== Building PSU driver once for matrix (pass --no-build if JAR already exists) ==="
    if ! driver_jar="$(ensure_psu_driver_jar)"; then
      echo "Driver build failed. Fix the Maven error above, or run with --no-build if the JAR is already built." >&2
      exit 1
    fi
    echo "  ${driver_jar}"
  fi
  EXTRA_ARGS+=(--no-build)
fi

case "${MATRIX_SUITE}" in
  fair|small-ec|all) ;;
  *)
    echo "unknown --suite: ${MATRIX_SUITE} (use fair, small-ec, or all)" >&2
    exit 1
    ;;
esac

if [[ "${ALL_NETWORKS}" -eq 1 ]]; then
  FAILURES=0
  for net in none LAN WAN1 WAN2; do
    echo ""
    echo "################################################################"
    echo "######## Matrix network profile: ${net} ########"
    echo "################################################################"
    if ! run_matrix_for_network "${net}"; then
      FAILURES=$((FAILURES + 1))
    fi
  done
  if [[ "${FAILURES}" -gt 0 ]]; then
    psu_fair_matrix_finish "${FAILURES}" "ALL-NETWORKS MATRIX COMPLETED WITH ${FAILURES} PROFILE FAILURE(S)"
    exit $?
  fi
  echo "ALL-NETWORKS MATRIX DONE $(date -Iseconds)"
  exit 0
fi

if ! run_matrix_for_network "${NETWORK_PROFILE}"; then
  if [[ "${PSU_FAIR_KEEP_GOING:-0}" == "1" ]]; then
    echo "Matrix failed for ${NETWORK_PROFILE}; continuing (PSU_FAIR_KEEP_GOING=1)" >&2
    exit 0
  fi
  exit 1
fi
