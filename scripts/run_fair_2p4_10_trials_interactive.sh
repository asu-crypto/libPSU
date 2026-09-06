#!/usr/bin/env bash
# Interactive launcher for fair trials (2p4/2p6/2p8/2p10 × LAN/WAN1/WAN2).
#
# Usage (from repo root):
#   ./scripts/run_fair_2p4_10_trials_interactive.sh
#   NETWORKS="WAN1 WAN2" FORCE=0 ./scripts/run_fair_2p4_10_trials_interactive.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

# shellcheck source=libpsu/fair_trials_interactive_lib.sh
source "${REPO_ROOT}/scripts/libpsu/fair_trials_interactive_lib.sh"

fair_trials_interactive_prep_env
fair_trials_interactive_ensure_writable 4 6 8 10
fair_trials_interactive_ensure_network

exec ./scripts/run_fair_2p4_10_trials.sh "$@"
