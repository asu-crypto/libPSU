#!/usr/bin/env bash
# Build Microsoft SEAL 4.0.0 (local prefix) and mpc4j-native-fhe on macOS.
#
# Usage (from repo root):
#   scripts/build_mpc4j_native_fhe_macos.sh
#
# Then run fair compare with:
#   export MPC4J_NATIVE_FHE_DIR="$PWD/mpc4j-native-fhe/cmake-build-release"
#   scripts/run_psu_fair.sh 5

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
FHE_SRC="${REPO_ROOT}/mpc4j-native-fhe"
FHE_BUILD="${FHE_SRC}/cmake-build-release"
SEAL_PREFIX="${REPO_ROOT}/.local/seal-4.0.0"
SEAL_SRC="${REPO_ROOT}/.local/SEAL-4.0.0-src"

if [[ "$(uname)" != "Darwin" ]]; then
  echo "This helper targets macOS only. See mpc4j-native-fhe/README.md for Linux." >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)"
  fi
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "Set JAVA_HOME to a JDK 17+ installation." >&2
  exit 1
fi

if [[ ! -f "${SEAL_PREFIX}/lib/cmake/SEAL/SEALConfig.cmake" ]]; then
  echo "==> Building Microsoft SEAL 4.0.0 into ${SEAL_PREFIX}"
  mkdir -p "$(dirname "${SEAL_SRC}")"
  if [[ ! -f "${SEAL_SRC}/CMakeLists.txt" ]]; then
    rm -rf "${SEAL_SRC}"
    git clone --depth 1 --branch v4.0.0 https://github.com/microsoft/SEAL.git "${SEAL_SRC}"
  fi
  cmake -S "${SEAL_SRC}" -B "${SEAL_SRC}/build" \
    -DCMAKE_INSTALL_PREFIX="${SEAL_PREFIX}" \
    -DCMAKE_CXX_COMPILER=clang++ \
    -DCMAKE_C_COMPILER=clang \
    -DBUILD_SHARED_LIBS=ON \
    -DCMAKE_BUILD_TYPE=Release \
    -DSEAL_BUILD_BENCH=OFF \
    -DSEAL_BUILD_EXAMPLES=OFF \
    -DSEAL_BUILD_TESTS=OFF \
    -DSEAL_USE_CXX17=ON \
    -DSEAL_USE_MSGSL=OFF \
    -DSEAL_USE_ZLIB=OFF \
    -DSEAL_USE_ZSTD=OFF
  cmake --build "${SEAL_SRC}/build" -j"$(sysctl -n hw.ncpu 2>/dev/null || echo 4)"
  cmake --install "${SEAL_SRC}/build"
else
  echo "==> SEAL already installed at ${SEAL_PREFIX}"
fi

echo "==> Building mpc4j-native-fhe into ${FHE_BUILD}"
cmake -S "${FHE_SRC}" -B "${FHE_BUILD}" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="${SEAL_PREFIX}"
cmake --build "${FHE_BUILD}" -j"$(sysctl -n hw.ncpu 2>/dev/null || echo 4)"

if [[ "$(uname)" == "Darwin" ]]; then
  ls -la "${FHE_BUILD}/libmpc4j-native-fhe.dylib"
else
  ls -la "${FHE_BUILD}/libmpc4j-native-fhe.so"
fi

echo ""
echo "Export before running CCS:TCLZ23 / ZCL24 / DGG25 benchmarks:"
echo "  export MPC4J_NATIVE_FHE_DIR=${FHE_BUILD}"
