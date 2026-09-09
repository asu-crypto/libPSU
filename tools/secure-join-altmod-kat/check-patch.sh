#!/usr/bin/env bash
# Apply-check the KAT patch against the pinned ePSU commit (temp clone).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATCH="${SCRIPT_DIR}/reference/epsu-fast-kat.patch"
EPSU_REPO="${EPSU_REPO:-https://github.com/CryptMatrix/ePSU-from-ssPMT.git}"
EPSU_COMMIT="${EPSU_COMMIT:-255bf1e4055128dc3eeeae43d934b4d068cabe67}"

test -f "${PATCH}"
git apply --check "${PATCH}" >/dev/null 2>&1 && {
  # Patch paths are relative to ePSU_fast/; bare check against CWD is wrong.
  :
}

WORK="$(mktemp -d /tmp/haowan-kat-check.XXXXXX)"
cleanup() { rm -rf "${WORK}"; }
trap cleanup EXIT

git clone --filter=blob:none "${EPSU_REPO}" "${WORK}/ePSU"
git -C "${WORK}/ePSU" checkout "${EPSU_COMMIT}"
EPSU_FAST="${WORK}/ePSU/ePSU_fast"
if [[ ! -d "${EPSU_FAST}" ]]; then
  EPSU_FAST="${WORK}/ePSU"
fi
(
  cd "${EPSU_FAST}"
  git apply --check "${PATCH}"
  git apply "${PATCH}"
)
echo "check-patch.sh OK"
