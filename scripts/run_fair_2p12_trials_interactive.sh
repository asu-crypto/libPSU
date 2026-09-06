#!/usr/bin/env bash
# Interactive launcher for 2p12 fair trials.
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run_fair_log_trials_interactive.sh" 12 "$@"
