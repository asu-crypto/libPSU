#!/usr/bin/env bash
# Run fair bench at 2^4,2^6,2^8,2^10 across LAN/WAN1/WAN2 for multiple trials.
#
# Usage (from repo root):
#   ./scripts/run_fair_2p4_10_trials.sh
#   TRIALS=3 NETWORKS="LAN WAN1 WAN2" ./scripts/run_fair_2p4_10_trials.sh
#   NETWORKS="WAN1 WAN2" FORCE=0 ./scripts/run_fair_2p4_10_trials.sh   # resume WAN only
#   SUITE_ARGS="--suite all" ./scripts/run_fair_2p4_10_trials.sh
#
# Requires Linux tc + sudo for shaped profiles.
# Tip: do not run the whole script under sudo — only `tc` needs sudo. Root-owned
# temp/bench trees break later summarizers/archives for a normal user.

set -uo pipefail

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

mkdir -p "${REPO_ROOT}/temp/empty_upsu_stress" "${REPO_ROOT}/temp/empty_psu_matrix_balanced"
export TMPDIR="${TMPDIR:-/dev/shm}"
export _JAVA_OPTIONS="${_JAVA_OPTIONS:--Djava.io.tmpdir=${TMPDIR}}"

# shellcheck source=network_profiles.sh
source "${SCRIPT_DIR}/network_profiles.sh"

MATRIX="${SCRIPT_DIR}/run_psu_fair_matrix.sh"
AVERAGE_PY="${SCRIPT_DIR}/average_psu_fair_short_summaries.py"

export MPC4J_JAVA_HOME="${MPC4J_JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export MPC4J_NATIVE_TOOL_DIR="${MPC4J_NATIVE_TOOL_DIR:-${REPO_ROOT}/mpc4j-native-tool/cmake-build-release}"
export MPC4J_NATIVE_FHE_DIR="${MPC4J_NATIVE_FHE_DIR:-${REPO_ROOT}/mpc4j-native-fhe/cmake-build-release}"
export PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS="${PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS:-10800}"
export PSU_FAIR_KS05_MAX_LOG_SIZE="${PSU_FAIR_KS05_MAX_LOG_SIZE:-${PSU_FAIR_C_KISSON05_MAX_LOG_SIZE:-8}}"
export PSU_FAIR_JAVA_XMX="${PSU_FAIR_JAVA_XMX:-48g}"
export PSU_FAIR_KEEP_GOING="${PSU_FAIR_KEEP_GOING:-1}"

TRIALS="${TRIALS:-3}"
read -r -a NETWORKS <<< "${NETWORKS:-LAN WAN1 WAN2}"
read -r -a SUITE_ARGS <<< "${SUITE_ARGS:---suite fair --no-upsu}"
LOGS=(4 6 8 10)
FORCE="${FORCE:-0}"

STAMP="$(date -Iseconds | tr ':' '-')"
ARCHIVE_ROOT="${ARCHIVE_ROOT:-${REPO_ROOT}/temp/bench/trials_${STAMP}}"
mkdir -p "${ARCHIVE_ROOT}" || {
  echo "WARN: cannot create archive root ${ARCHIVE_ROOT}; continuing without archive" >&2
  ARCHIVE_ROOT=""
}

archive_network_results() {
  local trial="$1" net="$2"
  [[ -n "${ARCHIVE_ROOT}" ]] || return 0
  local src dest
  src="${REPO_ROOT}/temp/bench/${net}"
  dest="${ARCHIVE_ROOT}/trial${trial}/${net}"
  mkdir -p "${dest}" || {
    echo "WARN: cannot archive trial ${trial} ${net}" >&2
    return 0
  }

  if [[ -d "${src}/psu_fair" ]]; then
    cp -a "${src}/psu_fair" "${dest}/" || echo "WARN: partial archive for ${net}/psu_fair" >&2
  fi
  if [[ -d "${src}/matrix" ]]; then
    cp -a "${src}/matrix" "${dest}/" || echo "WARN: partial archive for ${net}/matrix" >&2
  fi
  mkdir -p "${dest}/outputs"
  shopt -s nullglob
  local outs=( "${src}"/*.output )
  if ((${#outs[@]} > 0)); then
    cp -a "${outs[@]}" "${dest}/outputs/" || echo "WARN: partial archive for ${net} outputs" >&2
  fi
  shopt -u nullglob
  echo "Archived -> ${dest}"
}

echo "Archive root: ${ARCHIVE_ROOT:-<disabled>}"
echo "Trials: ${TRIALS}"
echo "Networks: ${NETWORKS[*]}"
echo "Timeout: ${PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS}s per protocol"
echo "C:KisSon05 max log: ${PSU_FAIR_KS05_MAX_LOG_SIZE} (skip larger balanced sizes)"
echo "Force re-run: ${FORCE}"
echo "Keep going on errors: ${PSU_FAIR_KEEP_GOING}"
echo "Suite args: ${SUITE_ARGS[*]}"
echo ""

# Preflight is informational; benchmarks still run and retry tc apply at start (needs sudo).
declare -a RUN_NETWORKS=()
PREFLIGHT_WARN=0
for net in "${NETWORKS[@]}"; do
  RUN_NETWORKS+=("${net}")
  [[ "${net}" == "none" ]] && continue
  echo "=== Preflight network profile ${net} ==="
  if network_profile_verify "${net}" 2>/dev/null; then
    echo "Preflight OK: ${net} (already shaped)"
  elif network_profile_apply "${net}"; then
    echo "Preflight OK: ${net} (applied)"
  else
    PREFLIGHT_WARN=1
    echo "WARN: ${net} is not shaped yet — benchmarks will retry tc apply (sudo required)" >&2
    echo "  Run once in a terminal: sudo ${REPO_ROOT}/scripts/network_profiles.sh apply ${net}" >&2
  fi
done

if [[ "${PREFLIGHT_WARN}" -eq 1 ]]; then
  echo ""
  echo "NOTE: Shaped profiles (LAN/WAN1/WAN2) need sudo for tc netem on lo." >&2
  echo "  Run this script from tmux/terminal where sudo can prompt, or pre-apply:" >&2
  echo "  sudo ${REPO_ROOT}/scripts/network_profiles.sh apply LAN" >&2
  echo ""
fi
echo "Networks to run: ${RUN_NETWORKS[*]}"
echo ""

matrix_extra_args=( --small-only --no-2p5 --no-build )
if [[ "${FORCE}" == "1" ]]; then
  matrix_extra_args+=( --force )
fi

for trial in $(seq 1 "${TRIALS}"); do
  echo "################ TRIAL ${trial}/${TRIALS} ################"
  for net in "${RUN_NETWORKS[@]}"; do
    echo ""
    echo "======== trial=${trial} network=${net} $(date -Iseconds) ========"
    mkdir -p "${REPO_ROOT}/temp/bench/${net}/matrix" || {
      echo "WARN: cannot create bench dir for ${net}; skipping" >&2
      continue
    }
    rm -f "${REPO_ROOT}/temp/bench/${net}/matrix/protocol_timeouts.tsv"

    if ! "${MATRIX}" \
        "${matrix_extra_args[@]}" \
        --network "${net}" \
        "${SUITE_ARGS[@]}"; then
      echo "WARN: trial ${trial} network ${net} completed with failures" >&2
    fi

    archive_network_results "${trial}" "${net}"
  done
done

if [[ -n "${ARCHIVE_ROOT}" && -f "${AVERAGE_PY}" ]]; then
  echo ""
  echo "=== Averaging short summaries across trials ==="
  avg_dir="${ARCHIVE_ROOT}/averaged"
  mkdir -p "${avg_dir}" || avg_dir=""

  if [[ -n "${avg_dir}" ]]; then
    for net in "${RUN_NETWORKS[@]}"; do
      for log in "${LOGS[@]}"; do
        inputs=()
        for trial in $(seq 1 "${TRIALS}"); do
          path="${ARCHIVE_ROOT}/trial${trial}/${net}/psu_fair/2p${log}/summary_2p${log}_short.csv"
          if [[ -f "${path}" ]]; then
            inputs+=("${path}")
          fi
        done
        if ((${#inputs[@]} == 0)); then
          echo "skip ${net} 2p${log}: no short CSVs"
          continue
        fi
        python3 "${AVERAGE_PY}" "${inputs[@]}" \
          -o "${avg_dir}/summary_2p${log}_short_avg_${net}.csv" \
          || echo "WARN: averaging failed for ${net} 2p${log}" >&2
      done
    done
  fi
fi

echo ""
echo "ALL DONE $(date -Iseconds)"
if [[ -n "${ARCHIVE_ROOT}" ]]; then
  echo "Per-trial archives: ${ARCHIVE_ROOT}/trial*/"
  echo "Averaged CSVs:       ${ARCHIVE_ROOT}/averaged/"
fi
