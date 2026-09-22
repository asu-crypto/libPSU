#!/usr/bin/env bash
# Verify committed HaoWan AltMod KAT resources and tooling digests.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIBPSU_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${LIBPSU_ROOT}"

bash -n "${SCRIPT_DIR}/regenerate.sh"
bash -n "${SCRIPT_DIR}/verify.sh"
bash -n "${SCRIPT_DIR}/check-patch.sh"

SUMS="${SCRIPT_DIR}/SHA256SUMS"
test -f "${SUMS}"
sha256sum -c "${SUMS}"

ROOT="mpc4j-s2pc-aby/src/main/resources/haowan26/secure-join-4a23526-epsu-fast"
REQUIRED=(
  A_SEED_CLASS.txt
  a_basis_images.txt
  b_basis_images.txt
  b_parity_rows.txt
  f_fixed_key.txt
  g_basis_images.txt
  g_representative.txt
  PROVENANCE.txt
  MANIFEST.md
)
for f in "${REQUIRED[@]}"; do
  test -f "${ROOT}/${f}"
done

grep -qx 'A_SEED_CLASS=CC' "${ROOT}/A_SEED_CLASS.txt"
test "$(sha256sum "${ROOT}/a_basis_images.txt" | awk '{print $1}')" = "f73d0d2afb43d545e005f424da480352a756e048ab1e36ba9ff399139d7e63fb"
test "$(sha256sum "${ROOT}/f_fixed_key.txt" | awk '{print $1}')" = "cba98ecd3474b8fa4e2bbebe9544ae2d838ee0867d55e6d40b2424bc7781f806"
test "$(grep -cvE '^#|^$' "${ROOT}/g_basis_images.txt")" -eq 128
test "$(grep -cvE '^#|^$' "${ROOT}/g_representative.txt")" -eq 4
test "$(grep -cvE '^#|^$' "${ROOT}/a_basis_images.txt")" -eq 512
test "$(grep -cvE '^#|^$' "${ROOT}/b_basis_images.txt")" -eq 256
test "$(grep -c ' y=' "${ROOT}/f_fixed_key.txt")" -eq 5

# OPF must not keep a duplicate resource tree.
test ! -d mpc4j-s2pc-opf/src/main/resources/haowan26/secure-join-4a23526-epsu-fast
test ! -d mpc4j-s2pc-opf/src/test/resources/haowan26/secure-join-4a23526-epsu-fast

# Dead standalone generator must stay removed.
test ! -f tools/secure-join-altmod-kat/dump_altmod_basis.cpp

grep -q 'volepsi_pin_status=PINNED' "${ROOT}/PROVENANCE.txt"
grep -q 'FAIL: epsu-fast-kat.patch does not apply' tools/secure-join-altmod-kat/regenerate.sh

echo "verify.sh OK"
