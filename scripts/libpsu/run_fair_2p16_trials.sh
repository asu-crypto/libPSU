#!/usr/bin/env bash
# Fair trials at 2^16 (14 paper protocols × LAN/WAN1/WAN2).
# Prefer: ./scripts/run_fair_2p16_trials_interactive.sh
_src="${BASH_SOURCE[0]}"
while [[ -L "${_src}" ]]; do
  _dir="$(cd "$(dirname "${_src}")" && pwd)"
  _src="$(readlink "${_src}")"
  [[ "${_src}" != /* ]] && _src="${_dir}/${_src}"
done
_dir="$(cd "$(dirname "${_src}")" && pwd)"
export LOG=16
exec "${_dir}/run_fair_log_trials.sh" "$@"
