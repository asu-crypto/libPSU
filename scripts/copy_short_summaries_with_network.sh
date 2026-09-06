#!/usr/bin/env bash
# Copy every *_short.csv under temp/bench/<network>/ to new files whose names
# include the network profile. Originals are never modified or overwritten.
#
# Usage (from repo root):
#   ./scripts/copy_short_summaries_with_network.sh
#   ./scripts/copy_short_summaries_with_network.sh --out-dir temp/bench/short_summaries
#   ./scripts/copy_short_summaries_with_network.sh --beside-source
#
# Default destination: temp/bench/short_summaries/
#   summary_2p4_short.csv  (LAN)  ->  summary_2p4_short_LAN.csv

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

BENCH_ROOT="${REPO_ROOT}/temp/bench"
OUT_DIR="${BENCH_ROOT}/short_summaries"
BESIDE_SOURCE=0

usage() {
  sed -n '2,12p' "$0" | sed 's/^# \?//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage 0 ;;
    --out-dir)
      OUT_DIR="${2:?--out-dir needs a path}"
      shift 2
      ;;
    --beside-source)
      BESIDE_SOURCE=1
      shift
      ;;
    -*)
      echo "unknown option: $1" >&2
      usage 1
      ;;
    *)
      echo "unexpected argument: $1" >&2
      usage 1
      ;;
  esac
done

if [[ ! -d "${BENCH_ROOT}" ]]; then
  echo "bench root not found: ${BENCH_ROOT}" >&2
  exit 1
fi

if [[ "${BESIDE_SOURCE}" -eq 0 ]]; then
  mkdir -p "${OUT_DIR}"
fi

copied=0
skipped=0
missing=0

while IFS= read -r -d '' src; do
  rel="${src#${BENCH_ROOT}/}"
  network="${rel%%/*}"
  if [[ -z "${network}" || "${network}" == "${rel}" ]]; then
    continue
  fi
  case "${network}" in
    LAN|WAN1|WAN2|none) ;;
    short_summaries) continue ;;
    *) continue ;;
  esac

  base="$(basename "${src}" .csv)"
  dest_name="${base}_${network}.csv"
  if [[ "${BESIDE_SOURCE}" -eq 1 ]]; then
    dest="$(dirname "${src}")/${dest_name}"
  else
    dest="${OUT_DIR}/${dest_name}"
  fi

  if [[ "${src}" -ef "${dest}" ]]; then
    skipped=$((skipped + 1))
    continue
  fi
  if [[ -e "${dest}" ]]; then
    echo "SKIP exists ${dest}"
    skipped=$((skipped + 1))
    continue
  fi
  if [[ ! -f "${src}" ]]; then
    missing=$((missing + 1))
    continue
  fi

  cp -n -- "${src}" "${dest}"
  echo "CREATED ${dest}"
  copied=$((copied + 1))
done < <(find "${BENCH_ROOT}" -type f -name '*_short.csv' -print0 | LC_ALL=C sort -z)

echo "Copied ${copied}, skipped ${skipped}, missing ${missing}"
if [[ "${BESIDE_SOURCE}" -eq 0 ]]; then
  echo "Output dir: ${OUT_DIR}"
fi
