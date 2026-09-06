#!/usr/bin/env bash
# Average per-trial short PSU fair summaries inside a trials archive directory.
#
# Usage (from repo root):
#   ./scripts/average_trials_archive.sh temp/bench/trials_2p12_2026-08-23T15-54-38+00-00
#   ./scripts/average_trials_archive.sh temp/bench/trials_2p12_2026-08-23T15-54-38+00-00 12 LAN WAN1 WAN2
#
# Expects:
#   <archive>/trial{N}/<NET>/psu_fair/2p<LOG>/summary_2p<LOG>_short.csv
# Writes:
#   <archive>/averaged/summary_2p<LOG>_short_avg_<NET>.csv

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <archive_dir> [log] [network ...]" >&2
  exit 1
fi

ARCHIVE="$(cd "$1" && pwd)"
shift
LOG="${1:-12}"
if [[ $# -gt 0 && "${1}" =~ ^[0-9]+$ ]]; then
  shift
fi

if ((${#@} > 0)); then
  read -r -a NETWORKS <<< "$*"
else
  NETWORKS=()
  for trial_dir in "${ARCHIVE}"/trial*; do
    [[ -d "${trial_dir}" ]] || continue
    for net_dir in "${trial_dir}"/*; do
      [[ -d "${net_dir}" ]] || continue
      net="$(basename "${net_dir}")"
      [[ " ${NETWORKS[*]:-} " == *" ${net} "* ]] && continue
      NETWORKS+=("${net}")
    done
  done
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AVERAGE_PY="${SCRIPT_DIR}/libpsu/average_psu_fair_short_summaries.py"
[[ -f "${AVERAGE_PY}" ]] || { echo "missing ${AVERAGE_PY}" >&2; exit 1; }

avg_dir="${ARCHIVE}/averaged"
mkdir -p "${avg_dir}"

trials=()
for trial_dir in "${ARCHIVE}"/trial*; do
  [[ -d "${trial_dir}" ]] || continue
  trials+=("$(basename "${trial_dir}")")
done
IFS=$'\n' trials=($(printf '%s\n' "${trials[@]}" | sort -V))
unset IFS

echo "Archive:  ${ARCHIVE}"
echo "Log size: 2^${LOG}"
echo "Trials:   ${trials[*]:-<none>}"
echo "Networks: ${NETWORKS[*]:-<none>}"
echo ""

for net in "${NETWORKS[@]}"; do
  inputs=()
  for trial in "${trials[@]}"; do
    path="${ARCHIVE}/${trial}/${net}/psu_fair/2p${LOG}/summary_2p${LOG}_short.csv"
    if [[ -f "${path}" ]]; then
      inputs+=("${path}")
    else
      echo "WARN: missing ${path}" >&2
    fi
  done
  if ((${#inputs[@]} == 0)); then
    echo "skip ${net}: no short CSVs"
    continue
  fi
  out="${avg_dir}/summary_2p${LOG}_short_avg_${net}.csv"
  echo "Averaging ${net} from ${#inputs[@]} trial(s) -> ${out}"
  python3 "${AVERAGE_PY}" "${inputs[@]}" -o "${out}"
done

echo ""
echo "Done. Averaged CSVs: ${avg_dir}/"
