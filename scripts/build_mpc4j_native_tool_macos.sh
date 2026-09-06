#!/usr/bin/env bash
# Build mpc4j-native-fourq and mpc4j-native-tool on macOS (Homebrew: gmp, ntl, libsodium, openssl@3, cmake).
# CMakeLists for mpc4j-native-tool uses ENV FOURQ_ROOT_DIR to locate FourQ headers and libfourq.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [[ "$(uname)" != "Darwin" ]]; then
  echo "This helper targets macOS only. See mpc4j-native-tool/doc/ for Linux." >&2
  exit 1
fi

if [[ "$(uname -m)" == "arm64" ]]; then
  BREW_PREFIX="${BREW_PREFIX:-/opt/homebrew}"
else
  BREW_PREFIX="${BREW_PREFIX:-/usr/local}"
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

OPENSSL_ROOT="${OPENSSL_ROOT:-}"
if [[ -z "${OPENSSL_ROOT}" ]]; then
  if [[ -d "${BREW_PREFIX}/opt/openssl@3" ]]; then
    OPENSSL_ROOT="${BREW_PREFIX}/opt/openssl@3"
  elif [[ -d "${BREW_PREFIX}/opt/openssl" ]]; then
    OPENSSL_ROOT="${BREW_PREFIX}/opt/openssl"
  fi
fi
if [[ -z "${OPENSSL_ROOT}" ]]; then
  echo "Could not find OpenSSL under Homebrew. Install openssl@3 or set OPENSSL_ROOT." >&2
  exit 1
fi

export NTL_ROOT_DIR="${NTL_ROOT_DIR:-${BREW_PREFIX}}"
export GMP_ROOT_DIR="${GMP_ROOT_DIR:-${BREW_PREFIX}}"

FOURQ_SRC="${REPO_ROOT}/mpc4j-native-fourq"
FOURQ_BUILD="${FOURQ_SRC}/build"
FOURQ_DIST="${FOURQ_BUILD}/dist"

echo "==> Building FourQ into ${FOURQ_DIST}"
rm -rf "${FOURQ_BUILD}"
mkdir -p "${FOURQ_BUILD}"
(
  cd "${FOURQ_BUILD}"
  cmake .. -DCMAKE_INSTALL_PREFIX="${FOURQ_DIST}"
  cmake --build . --parallel "$(sysctl -n hw.ncpu 2>/dev/null || echo 4)"
  cmake --install . --prefix "${FOURQ_DIST}"
)

export FOURQ_ROOT_DIR="${FOURQ_DIST}"

NATIVE_SRC="${REPO_ROOT}/mpc4j-native-tool"
NATIVE_BUILD="${NATIVE_SRC}/cmake-build-release"

echo "==> Building mpc4j-native-tool into ${NATIVE_BUILD}"
rm -rf "${NATIVE_BUILD}"
mkdir -p "${NATIVE_BUILD}"
(
  cd "${NATIVE_BUILD}"
  cmake .. -DOPENSSL_ROOT_DIR="${OPENSSL_ROOT}"
  cmake --build . --parallel "$(sysctl -n hw.ncpu 2>/dev/null || echo 4)"
)

echo "Done. Native JNI library:"
ls -la "${NATIVE_BUILD}/libmpc4j-native-tool.dylib"
echo "Use: export MPC4J_NATIVE_TOOL_DIR=\"${NATIVE_BUILD}\""
echo "Or run scripts/run_psu_fair.sh (defaults to that path)."
