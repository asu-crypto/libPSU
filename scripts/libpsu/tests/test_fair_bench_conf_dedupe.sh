#!/usr/bin/env bash
# Unit tests for fair-bench config collection / UPSU root deduplication.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
# shellcheck source=../psu_fair_bench_lib.sh
source "${SCRIPT_DIR}/../psu_fair_bench_lib.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

mkdir -p "${TMP}/shared/upsu/a" "${TMP}/other/upsu/b" "${TMP}/space dir/upsu/c"

cat > "${TMP}/shared/upsu/a/fair_bench_2p5.conf" <<'EOF'
pto_type = UPSU
EOF
cat > "${TMP}/other/upsu/b/fair_bench_2p5.conf" <<'EOF'
pto_type = UPSU
EOF
cat > "${TMP}/space dir/upsu/c/fair_bench_2p5.conf" <<'EOF'
pto_type = UPSU
EOF

# Mirror the driver's collect + realpath dedupe logic without running JVMs.
collect_and_dedupe() {
  local root1="$1"
  local root2="$2"
  local CONF_FILES=()
  local find_args=(-type f)
  while IFS= read -r line; do
    CONF_FILES+=("${line}")
  done < <(find "${root1}" "${find_args[@]}" -name 'fair_bench_2p5.conf' ! -name '*.disabled' -print | LC_ALL=C sort)
  while IFS= read -r line; do
    CONF_FILES+=("${line}")
  done < <(find "${root2}" "${find_args[@]}" -name 'fair_bench_2p5.conf' ! -name '*.disabled' -print | LC_ALL=C sort)

  declare -A _seen=()
  local out=()
  local conf canon
  for conf in "${CONF_FILES[@]}"; do
    canon="$(realpath "${conf}")"
    if [[ -n "${_seen[${canon}]+x}" ]]; then
      continue
    fi
    _seen["${canon}"]=1
    out+=("${conf}")
  done
  printf '%s\n' "${out[@]}"
}

same_root_once="$(collect_and_dedupe "${TMP}/shared/upsu" "${TMP}/shared/upsu" | wc -l | tr -d ' ')"
test "${same_root_once}" = "1"

distinct="$(collect_and_dedupe "${TMP}/shared/upsu" "${TMP}/other/upsu" | wc -l | tr -d ' ')"
test "${distinct}" = "2"

space_ok="$(collect_and_dedupe "${TMP}/space dir/upsu" "${TMP}/space dir/upsu" | wc -l | tr -d ' ')"
test "${space_ok}" = "1"
space_path="$(collect_and_dedupe "${TMP}/space dir/upsu" "${TMP}/space dir/upsu")"
test -f "${space_path}"

echo "test_fair_bench_conf_dedupe.sh OK"
