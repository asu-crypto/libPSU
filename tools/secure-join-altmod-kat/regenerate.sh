#!/usr/bin/env bash
# Build the original ePSU_fast/ePSU target twice from clean trees and dump AltMod KATs.
# Does NOT use a standalone secureJoin frontend as the authoritative vector source.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIBPSU_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PATCH="${SCRIPT_DIR}/reference/epsu-fast-kat.patch"
DUMP_INC="${SCRIPT_DIR}/dump_altmod_kat.inc"

EPSU_REPO="${EPSU_REPO:-https://github.com/CryptMatrix/ePSU-from-ssPMT.git}"
EPSU_COMMIT="${EPSU_COMMIT:-255bf1e4055128dc3eeeae43d934b4d068cabe67}"
SECURE_JOIN_COMMIT="${SECURE_JOIN_COMMIT:-4a23526f4b3a8432f7fb12d54b9865e95faedcf4}"
LIBOTE_COMMIT="${LIBOTE_COMMIT:-d0e499206d1d4d16c6b4ca6c0e712490e0632f80}"
CRYPTOTOOLS_COMMIT="${CRYPTOTOOLS_COMMIT:-0cf6986873e2b83966d5110398dca99172d63c20}"

KEEP_WORK=0
OUT_DIR=""
WORK_ROOT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep-work) KEEP_WORK=1; shift ;;
    --out) OUT_DIR="$2"; shift 2 ;;
    --work-root) WORK_ROOT="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--out DIR] [--work-root DIR] [--keep-work]"
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

OUT_DIR="${OUT_DIR:-${LIBPSU_ROOT}/tools/secure-join-altmod-kat/out}"
mkdir -p "${OUT_DIR}"

if [[ ! -f "${PATCH}" || ! -f "${DUMP_INC}" ]]; then
  echo "missing patch or dump include under ${SCRIPT_DIR}" >&2
  exit 1
fi

if [[ -z "${WORK_ROOT}" ]]; then
  WORK_ROOT="$(mktemp -d /tmp/haowan-epsu-kat.XXXXXX)"
fi
mkdir -p "${WORK_ROOT}"

cleanup() {
  if [[ "${KEEP_WORK}" -eq 0 ]]; then
    rm -rf "${WORK_ROOT}"
  else
    echo "Keeping work root: ${WORK_ROOT}"
  fi
}
trap cleanup EXIT

sha256_file() {
  sha256sum "$1" | awk '{print $1}'
}

build_and_dump() {
  local tag="$1"
  local work="$2"
  local dump_out="$3"
  mkdir -p "${work}" "${dump_out}"

  echo "===== [${tag}] clone ePSU-from-ssPMT@${EPSU_COMMIT} ====="
  git clone --filter=blob:none "${EPSU_REPO}" "${work}/ePSU"
  git -C "${work}/ePSU" checkout "${EPSU_COMMIT}"

  local epsu_fast="${work}/ePSU/ePSU_fast"
  if [[ ! -d "${epsu_fast}" ]]; then
    # Some checkouts place sources at repo root.
    if [[ -f "${work}/ePSU/CMakeLists.txt" && -d "${work}/ePSU/src" ]]; then
      epsu_fast="${work}/ePSU"
    else
      echo "cannot locate ePSU_fast under ${work}/ePSU" >&2
      exit 1
    fi
  fi

  cp "${DUMP_INC}" "${epsu_fast}/include/dump_altmod_kat.inc"
  (
    cd "${epsu_fast}"
    if ! patch -p1 --forward --dry-run < "${PATCH}" >/dev/null 2>&1; then
      echo "unified patch dry-run failed; applying surgical edits" >&2
      if ! grep -q 'dump_altmod_kat.inc' src/SoOPPRF.cpp; then
        printf '\n// Reference-only AltMod KAT dump (libPSU tools/secure-join-altmod-kat).\n#include "dump_altmod_kat.inc"\n' >> src/SoOPPRF.cpp
      fi
      python3 - <<'PY'
from pathlib import Path
p = Path("test/test_ePSU.cpp")
t = p.read_text()
if "HAOWAN_ALT_MOD_KAT_OUT" not in t:
    if "#include <cstdlib>" not in t:
        t = t.replace('#include <vector>\n', '#include <vector>\n#include <cstdlib>\n')
    if 'int dumpAltModKat(const char* outDir);' not in t:
        t = t.replace('#include "ePSU.h"\n', '#include "ePSU.h"\n\nint dumpAltModKat(const char* outDir);\n')
    needle = "int main(int argc, char **argv)\n{\n"
    insert = needle + "    const char* katOut = std::getenv(\"HAOWAN_ALT_MOD_KAT_OUT\");\n    if (katOut != nullptr) {\n        return dumpAltModKat(katOut);\n    }\n\n"
    if needle not in t:
        raise SystemExit("test_ePSU.cpp main() pattern not found")
    p.write_text(t.replace(needle, insert, 1))
print("surgical patch ok")
PY
    else
      patch -p1 < "${PATCH}"
    fi
  )

  echo "===== [${tag}] build thirdparty (secure-join + volePSI) ====="
  (
    cd "${epsu_fast}"
    mkdir -p thirdparty
    cd thirdparty

    git clone https://github.com/Th0masAndy/secure-join
    cd secure-join
    git checkout "${SECURE_JOIN_COMMIT}"
    # Record Prf tree hash and nested pins when present.
    if [[ -d Prf ]]; then
      (cd Prf && git rev-parse HEAD >/dev/null 2>&1 || true)
      find Prf -type f -print0 | sort -z | xargs -0 sha256sum | sha256sum | awk '{print $1}' > "${dump_out}/secure-join-Prf.tree.sha256"
    fi
    python3 build.py --install=../out/install -DFETCH_ALL=ON \
      -DSECUREJOIN_ENABLE_BOOST=ON \
      -DSODIUM_MONTGOMERY=false \
      -DENABLE_BITPOLYMUL=false
    # Fail loud if libsecureJoin.a did not install.
    if [[ ! -f ../out/install/lib/libsecureJoin.a ]]; then
      echo "secure-join install missing libsecureJoin.a" >&2
      find . -name 'libsecureJoin.a' -print >&2 || true
      exit 1
    fi
    cd ..

    # Disclose: original setup.sh does not pin volePSI.
    git clone https://github.com/Th0masAndy/volepsi.git
    cd volepsi
    VOLEPSI_COMMIT="$(git rev-parse HEAD)"
    echo "${VOLEPSI_COMMIT}" > "${dump_out}/volepsi.commit"
    echo "NOTE: volePSI is unpinned by ePSU_fast/setup.sh; observed commit=${VOLEPSI_COMMIT}" | tee "${dump_out}/volepsi.UNPINNED.txt"
    if [[ -f volePSI/GMW/Gmw.cpp ]]; then
      sed -i '157s/co_await(generateTriple(1 << 20, 2, chl));/co_await(generateTriple(1 << 18, 2, chl));/' volePSI/GMW/Gmw.cpp || true
    fi
    # Prefer the fetched/install tree Boost over mismatched /usr/local Boost configs.
    python3 build.py --install=../out/install \
      -DVOLE_PSI_ENABLE_BOOST=true \
      -DVOLE_PSI_ENABLE_BITPOLYMUL=false \
      -DVOLE_PSI_SODIUM_MONTGOMERY=false \
      -DCMAKE_PREFIX_PATH="$(pwd)/../out/install" \
      -DVOLE_PSI_NO_SYSTEM_PATH=true || true
    # build.py may fail on sparsehash install even after a successful compile; salvage.
    if [[ ! -e thirdparty/sparsehash-c11/sparsehash && -d /usr/local/include/sparsehash ]]; then
      mkdir -p thirdparty/sparsehash-c11
      ln -sfn /usr/local/include/sparsehash thirdparty/sparsehash-c11/sparsehash
    fi
    cmake --install ./out/build/linux --prefix ../out/install || true
    mkdir -p ../out/install/include/volePSI ../out/install/lib
    if [[ -f ./out/build/linux/volePSI/config.h ]]; then
      cp ./out/build/linux/volePSI/config.h ../out/install/include/volePSI/
    fi
    if [[ -d ./out/install/linux ]]; then
      cp -a ./out/install/linux/. ../out/install/ || true
    fi
    find ./out/build/linux -name 'libvolePSI*.a' -exec cp -n {} ../out/install/lib/ \; || true
    if [[ ! -f ../out/install/lib/cmake/volePSI/volePSIConfig.cmake ]]; then
      echo "volePSIConfig.cmake missing after salvage" >&2
      exit 1
    fi
    cd ../..
  )

  local prefix="${epsu_fast}/thirdparty/out/install"
  if [[ ! -f "${prefix}/lib/libsecureJoin.a" ]]; then
    echo "missing ${prefix}/lib/libsecureJoin.a after thirdparty build" >&2
    exit 1
  fi


  # Verify secure-join nested dependency pins when FETCH_ALL materializes them.
  local sj_out="${epsu_fast}/thirdparty/secure-join"
  if [[ -d "${sj_out}/out/libOTe" ]]; then
    git -C "${sj_out}/out/libOTe" rev-parse HEAD > "${dump_out}/libOTe.commit" || true
  fi
  if [[ -d "${sj_out}/out/libOTe/cryptoTools" ]]; then
    git -C "${sj_out}/out/libOTe/cryptoTools" rev-parse HEAD > "${dump_out}/cryptoTools.commit" || true
  elif [[ -d "${sj_out}/out/cryptoTools" ]]; then
    git -C "${sj_out}/out/cryptoTools" rev-parse HEAD > "${dump_out}/cryptoTools.commit" || true
  fi

  # Forward declaration for dumpAltModKat
  if ! grep -q 'int dumpAltModKat' "${epsu_fast}/test/test_ePSU.cpp"; then
    python3 - <<PY
from pathlib import Path
p = Path("${epsu_fast}/test/test_ePSU.cpp")
t = p.read_text()
t = t.replace('#include "ePSU.h"\n', '#include "ePSU.h"\n\nint dumpAltModKat(const char* outDir);\n')
p.write_text(t)
PY
  fi

  echo "===== [${tag}] configure + link ePSU ====="
  (
    cd "${epsu_fast}"
    mkdir -p build
    cd build
    cmake .. \
      -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_VERBOSE_MAKEFILE=ON \
      -DCMAKE_PREFIX_PATH="${prefix};/usr/local" \
      -DBoost_DIR="${prefix}/lib/cmake/Boost-1.86.0" \
      -DBoost_NO_SYSTEM_PATHS=ON \
      -DvolePSI_DIR="${prefix}/lib/cmake/volePSI" \
      -DlibOTe_DIR="${prefix}/lib/cmake/libOTe" \
      -DcryptoTools_DIR="${prefix}/lib/cmake/cryptoTools"
    cmake --build . --target ePSU -j"$(nproc)" --verbose 2>&1 | tee "${dump_out}/ePSU.build.log"
  )

  # Extract the final link command for provenance.
  if rg -n "libsecureJoin\\.a|ePSU " "${dump_out}/ePSU.build.log" | tail -n 40 > "${dump_out}/ePSU.link.snippet.txt"; then
    :
  fi
  # Prefer the actual linker invocation containing libsecureJoin.a
  if rg -n "libsecureJoin\\.a" "${dump_out}/ePSU.build.log" | tail -n 5 > "${dump_out}/ePSU.link.line.txt"; then
    :
  fi

  local epsu_bin
  if [[ -x "${epsu_fast}/build/ePSU" ]]; then
    epsu_bin="${epsu_fast}/build/ePSU"
  elif [[ -x "${epsu_fast}/build/ePSU/ePSU" ]]; then
    epsu_bin="${epsu_fast}/build/ePSU/ePSU"
  else
    epsu_bin="$(find "${epsu_fast}/build" -type f -name ePSU -perm -111 | head -n 1)"
  fi
  if [[ -z "${epsu_bin}" || ! -x "${epsu_bin}" ]]; then
    echo "ePSU binary not found under ${epsu_fast}/build" >&2
    exit 1
  fi

  # Guard: refuse if this somehow linked only a secureJoin frontend dump tool.
  if ! ldd "${epsu_bin}" >/dev/null 2>&1; then
    # static-ish; ok
    :
  fi
  if ! rg -q "libsecureJoin\\.a|secureJoin" "${dump_out}/ePSU.build.log"; then
    echo "refusing: build log does not mention libsecureJoin.a / secureJoin" >&2
    exit 1
  fi
  if rg -q "dump_altmod_basis" "${dump_out}/ePSU.build.log" && ! rg -q "test_ePSU|SoOPPRF" "${dump_out}/ePSU.build.log"; then
    echo "refusing: looks like a standalone dump_altmod_basis target" >&2
    exit 1
  fi

  echo "===== [${tag}] dump KAT via HAOWAN_ALT_MOD_KAT_OUT ====="
  mkdir -p "${dump_out}/vectors"
  HAOWAN_ALT_MOD_KAT_OUT="${dump_out}/vectors" "${epsu_bin}"

  if [[ ! -f "${dump_out}/vectors/A_SEED_CLASS.txt" ]]; then
    echo "missing A_SEED_CLASS.txt" >&2
    exit 1
  fi
  cat "${dump_out}/vectors/A_SEED_CLASS.txt"

  # Provenance blob
  {
    echo "epsu_repo=${EPSU_REPO}"
    echo "epsu_commit=${EPSU_COMMIT}"
    echo "secure_join_commit=${SECURE_JOIN_COMMIT}"
    echo "libOTe_expected=${LIBOTE_COMMIT}"
    echo "cryptoTools_expected=${CRYPTOTOOLS_COMMIT}"
    echo -n "libOTe_observed="; cat "${dump_out}/libOTe.commit" 2>/dev/null || echo "UNRESOLVED"
    echo -n "cryptoTools_observed="; cat "${dump_out}/cryptoTools.commit" 2>/dev/null || echo "UNRESOLVED"
    echo -n "volepsi_observed="; cat "${dump_out}/volepsi.commit"
    echo "volepsi_pin_status=UNPINNED_BY_UPSTREAM_SETUP_SH"
    echo "compiler=$(${CXX:-g++} --version | head -n 1)"
    echo "cmake=$(cmake --version | head -n 1)"
    echo "os=$(uname -srmo 2>/dev/null || uname -a)"
    echo "generator_inc_sha256=$(sha256_file "${DUMP_INC}")"
    echo "patch_sha256=$(sha256_file "${PATCH}")"
    echo -n "A_SEED_CLASS="; cat "${dump_out}/vectors/A_SEED_CLASS.txt" | sed 's/^A_SEED_CLASS=//'
    for f in g_basis_images.txt g_representative.txt a_basis_images.txt b_basis_images.txt b_parity_rows.txt f_fixed_key.txt; do
      echo "$(sha256_file "${dump_out}/vectors/${f}")  ${f}"
    done
  } | tee "${dump_out}/PROVENANCE.txt"
}

RUN1="${WORK_ROOT}/run1"
RUN2="${WORK_ROOT}/run2"
build_and_dump run1 "${RUN1}" "${OUT_DIR}/run1"
build_and_dump run2 "${RUN2}" "${OUT_DIR}/run2"

echo "===== compare run1 vs run2 ====="
DIFF=0
for f in g_basis_images.txt g_representative.txt a_basis_images.txt b_basis_images.txt b_parity_rows.txt f_fixed_key.txt A_SEED_CLASS.txt; do
  if ! cmp -s "${OUT_DIR}/run1/vectors/${f}" "${OUT_DIR}/run2/vectors/${f}"; then
    echo "MISMATCH: ${f}" >&2
    DIFF=1
  else
    echo "OK ${f} $(sha256_file "${OUT_DIR}/run1/vectors/${f}")"
  fi
done
if [[ "${DIFF}" -ne 0 ]]; then
  echo "Two clean builds disagreed; leaving Java resources unchanged." >&2
  exit 4
fi

# Publish canonical copy under OUT_DIR/canonical
rm -rf "${OUT_DIR}/canonical"
mkdir -p "${OUT_DIR}/canonical"
cp -a "${OUT_DIR}/run1/vectors/." "${OUT_DIR}/canonical/"
cp "${OUT_DIR}/run1/PROVENANCE.txt" "${OUT_DIR}/canonical/PROVENANCE.txt"
cp "${OUT_DIR}/run1/ePSU.link.line.txt" "${OUT_DIR}/canonical/ePSU.link.line.txt" 2>/dev/null || true
cp "${OUT_DIR}/run1/volepsi.UNPINNED.txt" "${OUT_DIR}/canonical/volepsi.UNPINNED.txt" 2>/dev/null || true

echo "Canonical vectors: ${OUT_DIR}/canonical"
cat "${OUT_DIR}/canonical/A_SEED_CLASS.txt"
echo "DONE"
