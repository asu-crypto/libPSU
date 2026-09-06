#!/usr/bin/env bash
# Shared fair-bench trial runner for a single log size (2^LOG).
#
# Intended entry points (thin wrappers):
#   ./scripts/run_fair_2p12_trials.sh
#   ./scripts/run_fair_2p16_trials.sh
#   ./scripts/run_fair_2p20_trials.sh
#   LOG=12 ./scripts/libpsu/run_fair_log_trials.sh
#
# Preferred interactive launcher (sudo for tc):
#   ./scripts/run_fair_2p12_trials_interactive.sh
#
# Env overrides:
#   LOG=12|16|20   TRIALS=3   NETWORKS="LAN WAN1 WAN2"
#   ONLY=...       FORCE=0|1  ARCHIVE_ROOT=...
#   PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS  PSU_FAIR_JAVA_XMX  PSU_FAIR_KEEP_GOING

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

RUNNER="${SCRIPT_DIR}/run_psu_fair.sh"
AVERAGE_PY="${SCRIPT_DIR}/average_psu_fair_short_summaries.py"

LOG="${LOG:?LOG must be set (e.g. 12, 16, or 20)}"
if ! [[ "${LOG}" =~ ^[0-9]+$ ]] || [[ "${LOG}" -lt 1 || "${LOG}" -gt 30 ]]; then
  echo "LOG must be an integer in [1, 30] (got: ${LOG})" >&2
  exit 1
fi

export MPC4J_JAVA_HOME="${MPC4J_JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export MPC4J_NATIVE_TOOL_DIR="${MPC4J_NATIVE_TOOL_DIR:-${REPO_ROOT}/mpc4j-native-tool/cmake-build-release}"
export MPC4J_NATIVE_FHE_DIR="${MPC4J_NATIVE_FHE_DIR:-${REPO_ROOT}/mpc4j-native-fhe/cmake-build-release}"
export PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS="${PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS:-10800}"
export PSU_FAIR_JAVA_XMX="${PSU_FAIR_JAVA_XMX:-48g}"
export PSU_FAIR_KEEP_GOING="${PSU_FAIR_KEEP_GOING:-1}"

TRIALS="${TRIALS:-3}"
read -r -a NETWORKS <<< "${NETWORKS:-LAN WAN1 WAN2}"
FORCE="${FORCE:-1}"

# Default allowlists by size (override with ONLY=...).
# 14 protocols for smaller large-sets; 12 for 2p20 (no HN12 / ACISP:DavCid17).
DEFAULT_ONLY_14="AC:KRTW19,PKC:GMRSS21,USENIX:JSZDG22,USENIX:ConYuWeiminDon23_SKE,ASIACCS:CSSW25,ACISP:DavCid17,PKC:CheZhaZha24,USENIX:ConYuWeiminDon23_PKE,Ours,USENIX:YanShiHonDaw24,EUROCRYPT:PisTri26,USENIX:BinYujConYanYu25,JOC:HazNis12,EUROCRYPT:PuGaoTri26"
DEFAULT_ONLY_12="PKC:CheZhaZha24,USENIX:ConYuWeiminDon23_PKE,PKC:GMRSS21,USENIX:JSZDG22,EUROCRYPT:PisTri26,ASIACCS:CSSW25,USENIX:YanShiHonDaw24,AC:KRTW19,USENIX:ConYuWeiminDon23_SKE,USENIX:BinYujConYanYu25,Ours,EUROCRYPT:PuGaoTri26"
if [[ -z "${ONLY:-}" ]]; then
  if [[ "${LOG}" -ge 20 ]]; then
    ONLY="${DEFAULT_ONLY_12}"
  else
    ONLY="${DEFAULT_ONLY_14}"
  fi
fi

STAMP="$(date -Iseconds | tr ':' '-')"
ARCHIVE_ROOT="${ARCHIVE_ROOT:-${REPO_ROOT}/temp/bench/trials_2p${LOG}_${STAMP}}"
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

  if [[ -d "${src}/psu_fair/2p${LOG}" ]]; then
    mkdir -p "${dest}/psu_fair"
    cp -a "${src}/psu_fair/2p${LOG}" "${dest}/psu_fair/" \
      || echo "WARN: partial archive for ${net}/psu_fair/2p${LOG}" >&2
  fi
  shopt -s nullglob
  local outs=( "${src}"/*.output )
  if ((${#outs[@]} > 0)); then
    mkdir -p "${dest}/outputs"
    cp -a "${outs[@]}" "${dest}/outputs/" || echo "WARN: partial archive for ${net} outputs" >&2
  fi
  shopt -u nullglob
  echo "Archived -> ${dest}"
}

echo "Archive root: ${ARCHIVE_ROOT:-<disabled>}"
echo "Log size:     2^${LOG} = $((1 << LOG))"
echo "Trials:       ${TRIALS}"
echo "Networks:     ${NETWORKS[*]}"
echo "Timeout:      ${PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS}s per protocol"
echo "Java heap:    ${PSU_FAIR_JAVA_XMX}"
echo "Force re-run: ${FORCE}"
echo "Keep going:   ${PSU_FAIR_KEEP_GOING}"
echo "Protocols:    ${ONLY}"
echo ""

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
    echo "WARN: ${net} not shaped yet — will retry at benchmark start (sudo required)" >&2
    echo "  Run: sudo ${REPO_ROOT}/scripts/network_profiles.sh apply ${net}" >&2
  fi
done

if [[ "${PREFLIGHT_WARN}" -eq 1 ]]; then
  echo ""
  echo "NOTE: LAN/WAN profiles need sudo for tc netem." >&2
  echo "  Prefer: ./scripts/run_fair_2p${LOG}_trials_interactive.sh" >&2
  echo "  or:     sudo ${REPO_ROOT}/scripts/network_profiles.sh apply LAN" >&2
  echo ""
fi
echo "Networks to run: ${RUN_NETWORKS[*]}"
echo ""

runner_args=( "${LOG}" --no-build --suite fair --no-upsu --only "${ONLY}" )
# EUROCRYPT:PisTri26 is skipped by default when LOG >= 18; keep it when it is in the allowlist.
if [[ "${LOG}" -ge 18 ]]; then
  runner_args+=( --no-skip-pt26 )
fi
if [[ "${FORCE}" == "1" ]]; then
  runner_args+=( --force )
fi

for trial in $(seq 1 "${TRIALS}"); do
  echo "################ TRIAL ${trial}/${TRIALS} ################"
  for net in "${RUN_NETWORKS[@]}"; do
    echo ""
    echo "======== trial=${trial} network=${net} $(date -Iseconds) ========"
    mkdir -p "${REPO_ROOT}/temp/bench/${net}/matrix" "${REPO_ROOT}/temp/bench/${net}/psu_fair/2p${LOG}/logs" || {
      echo "WARN: cannot create bench dir for ${net}; skipping" >&2
      continue
    }
    rm -f "${REPO_ROOT}/temp/bench/${net}/matrix/protocol_timeouts.tsv"

    if [[ "${net}" != "none" ]]; then
      if ! network_profile_apply "${net}"; then
        echo "WARN: could not apply network ${net}; continuing unshaped (KEEP_GOING)" >&2
      fi
    fi

    if ! "${RUNNER}" "${runner_args[@]}" --network "${net}"; then
      echo "WARN: trial ${trial} network ${net} completed with failures" >&2
    fi

    archive_network_results "${trial}" "${net}"
  done
done

if [[ -n "${ARCHIVE_ROOT}" && -f "${AVERAGE_PY}" ]]; then
  echo ""
  echo "=== Averaging short summaries across trials (2^${LOG}) ==="
  avg_dir="${ARCHIVE_ROOT}/averaged"
  mkdir -p "${avg_dir}" || avg_dir=""

  if [[ -n "${avg_dir}" ]]; then
    for net in "${RUN_NETWORKS[@]}"; do
      inputs=()
      for trial in $(seq 1 "${TRIALS}"); do
        path="${ARCHIVE_ROOT}/trial${trial}/${net}/psu_fair/2p${LOG}/summary_2p${LOG}_short.csv"
        if [[ -f "${path}" ]]; then
          inputs+=("${path}")
        else
          echo "WARN: missing ${path}" >&2
        fi
      done
      if ((${#inputs[@]} == 0)); then
        echo "skip ${net} 2p${LOG}: no short CSVs"
        continue
      fi
      if ((${#inputs[@]} < TRIALS)); then
        echo "WARN: averaging ${net} from ${#inputs[@]}/${TRIALS} trial(s)" >&2
      fi
      python3 "${AVERAGE_PY}" "${inputs[@]}" \
        -o "${avg_dir}/summary_2p${LOG}_short_avg_${net}.csv" \
        || echo "WARN: averaging failed for ${net} 2p${LOG}" >&2
    done
  fi
fi

echo ""
echo "ALL DONE $(date -Iseconds)"
if [[ -n "${ARCHIVE_ROOT}" ]]; then
  echo "Per-trial archives: ${ARCHIVE_ROOT}/trial*/"
  echo "Averaged CSVs:       ${ARCHIVE_ROOT}/averaged/"
fi
