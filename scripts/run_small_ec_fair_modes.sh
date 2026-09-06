#!/usr/bin/env bash
# Wrapper — ./scripts/run_psu_fair.sh --suite small-ec
set -euo pipefail
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run_psu_fair.sh" --suite small-ec "$@"
