#!/usr/bin/env bash
# Run Maven with JDK 17 (required: --enable-preview + jdk.incubator.vector).
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=libpsu/psu_fair_bench_lib.sh
source "${REPO_ROOT}/scripts/libpsu/psu_fair_bench_lib.sh"
REPO_ROOT="${REPO_ROOT}"
ensure_jdk17_home
cd "${REPO_ROOT}"
exec mvn "$@"
