#!/usr/bin/env bash
# Verify committed HaoWan AltMod KAT resources and tooling digests.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIBPSU_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${LIBPSU_ROOT}"

bash -n "${SCRIPT_DIR}/regenerate.sh"
bash -n "${SCRIPT_DIR}/verify.sh"

SUMS="${SCRIPT_DIR}/SHA256SUMS"
test -f "${SUMS}"

sha256sum -c "${SUMS}"

ROOTS=(
  mpc4j-s2pc-aby/src/main/resources/haowan26/secure-join-4a23526-epsu-fast
  mpc4j-s2pc-opf/src/main/resources/haowan26/secure-join-4a23526-epsu-fast
  mpc4j-s2pc-opf/src/test/resources/haowan26/secure-join-4a23526-epsu-fast
)

EXPECTED_A=f73d0d2afb43d545e005f424da480352a756e048ab1e36ba9ff399139d7e63fb
EXPECTED_F=cba98ecd3474b8fa4e2bbebe9544ae2d838ee0867d55e6d40b2424bc7781f806

for root in "${ROOTS[@]}"; do
  test -f "${root}/A_SEED_CLASS.txt"
  grep -qx 'A_SEED_CLASS=CC' "${root}/A_SEED_CLASS.txt"
  test "$(sha256sum "${root}/a_basis_images.txt" | awk '{print $1}')" = "${EXPECTED_A}"
  test "$(sha256sum "${root}/f_fixed_key.txt" | awk '{print $1}')" = "${EXPECTED_F}"
  # Record counts
  test "$(grep -cvE '^#|^$' "${root}/g_basis_images.txt")" -eq 128
  test "$(grep -cvE '^#|^$' "${root}/g_representative.txt")" -eq 4
  test "$(grep -cvE '^#|^$' "${root}/a_basis_images.txt")" -eq 512
  test "$(grep -cvE '^#|^$' "${root}/b_basis_images.txt")" -eq 256
  test "$(grep -c ' y=' "${root}/f_fixed_key.txt")" -eq 5
done

echo "verify.sh OK"
