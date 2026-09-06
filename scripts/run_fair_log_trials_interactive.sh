#!/usr/bin/env bash
# Shared interactive prep for fair trial runners (sudo for tc + writable bench dirs).
#
# Usage (from repo root):
#   ./scripts/run_fair_log_trials_interactive.sh 12
#   BENCH_LOG=16 ./scripts/run_fair_log_trials_interactive.sh
#   ./scripts/run_fair_2p20_trials_interactive.sh   # thin wrapper

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

# shellcheck source=libpsu/fair_trials_interactive_lib.sh
source "${REPO_ROOT}/scripts/libpsu/fair_trials_interactive_lib.sh"

BENCH_LOG="${1:-${BENCH_LOG:-}}"
if [[ -z "${BENCH_LOG}" ]]; then
  echo "usage: $0 <log>   or set BENCH_LOG=12|16|20" >&2
  exit 1
fi
if ! [[ "${BENCH_LOG}" =~ ^[0-9]+$ ]]; then
  echo "BENCH_LOG must be an integer (got: ${BENCH_LOG})" >&2
  exit 1
fi
if [[ $# -gt 0 ]]; then
  shift
fi

fair_trials_interactive_prep_env
fair_trials_interactive_ensure_writable "${BENCH_LOG}"
fair_trials_interactive_ensure_network

wrapper="./scripts/run_fair_2p${BENCH_LOG}_trials.sh"
if [[ -x "${wrapper}" || -L "${wrapper}" ]]; then
  exec "${wrapper}" "$@"
fi
export LOG="${BENCH_LOG}"
exec ./scripts/libpsu/run_fair_log_trials.sh "$@"
