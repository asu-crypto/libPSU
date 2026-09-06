#!/usr/bin/env bash
# Apply / clear / show fair-bench network emulation profiles (Linux tc netem on loopback).
#
# Usage (from repo root):
#   ./scripts/network_profiles.sh show
#   ./scripts/network_profiles.sh clear
#   ./scripts/network_profiles.sh apply LAN    # 10gbit, delay 0.1ms (~0.2ms RTT)
#   ./scripts/network_profiles.sh apply WAN1   # 200mbit, delay 40ms (~80ms RTT)
#   ./scripts/network_profiles.sh apply WAN2   # 50mbit, delay 40ms (~80ms RTT)
#
# Manual RTT check after apply (do not use as a hard pass/fail):
#   ping -c 5 127.0.0.1
#
# Benchmark driver:
#   ./scripts/run_psu_fair_matrix.sh --network WAN1 --mini-only --no-build

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=libpsu/network_profiles.sh
source "${SCRIPT_DIR}/libpsu/network_profiles.sh"

cmd="${1:-show}"
profile="${2:-}"

case "${cmd}" in
  show)
    network_profile_show
    ;;
  clear|none)
    network_profile_clear
    echo "Network emulation cleared (raw localhost)."
    ;;
  apply)
    profile="${profile:-LAN}"
    network_profile_apply "${profile}"
    ;;
  -h|--help)
    sed -n '3,16p' "$0" | sed 's/^# \?//'
    echo ""
    echo "Single-host tc netem on \${PSU_FAIR_NET_DEV:-lo}. Parties still use 127.0.0.1."
    echo "netem delay is one-way on loopback egress; approx. RTT ≈ 2 × delay."
    echo ""
    echo "Profiles:"
    echo "  LAN  — rate 10gbit,  netem delay 0.1ms,  approx. RTT ~0.2ms"
    echo "  WAN1 — rate 200mbit, netem delay 40ms,   approx. RTT ~80ms"
    echo "  WAN2 — rate 50mbit,  netem delay 40ms,   approx. RTT ~80ms"
    echo "  none — remove shaping (default for benchmarks)"
    echo ""
    echo "After WAN1/WAN2: ping -c 5 127.0.0.1  (expect ~80 ms, not exact)."
    echo "After LAN:        ping -c 5 127.0.0.1  (expect ~0.2 ms)."
    ;;
  *)
    echo "unknown command: ${cmd} (use show, clear, or apply)" >&2
    exit 1
    ;;
esac
