#!/usr/bin/env bash
# Shared helpers for interactive fair-trial launchers (sourced, not executed).

fair_trials_interactive_prep_env() {
  export TMPDIR="${TMPDIR:-/dev/shm}"
  export _JAVA_OPTIONS="${_JAVA_OPTIONS:--Djava.io.tmpdir=${TMPDIR}}"
  export MPC4J_JAVA_HOME="${MPC4J_JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
  mkdir -p "${TMPDIR}/libpsu_bench" "${REPO_ROOT}/temp/empty_upsu_stress"
  ln -sfn "${TMPDIR}/libpsu_bench" "${REPO_ROOT}/temp/bench"
}

# Args: log sizes to ensure writable (e.g. 12 or 4 6 8 10). Uses NETWORKS env.
fair_trials_interactive_ensure_writable() {
  local -a logs=("$@")
  local -a nets
  read -r -a nets <<< "${NETWORKS:-LAN WAN1 WAN2}"

  echo "=== Bench output ownership check (2^{${logs[*]}}) ==="
  local -a root_owned=()
  local net dir
  for net in "${nets[@]}"; do
    dir="${REPO_ROOT}/temp/bench/${net}"
    mkdir -p "${dir}" 2>/dev/null || true
    if [[ -d "${dir}" ]] && [[ ! -w "${dir}" || -n "$(find "${dir}" -user root -print -quit 2>/dev/null)" ]]; then
      root_owned+=("${dir}")
    fi
  done
  if ((${#root_owned[@]} > 0)); then
    echo "Fixing root-owned bench dirs (sudo may prompt):"
    printf '  %s\n' "${root_owned[@]}"
    sudo chown -R "${USER}:${USER}" "${root_owned[@]}"
  fi
  local log
  for net in "${nets[@]}"; do
    for log in "${logs[@]}"; do
      dir="${REPO_ROOT}/temp/bench/${net}/psu_fair/2p${log}/logs"
      if ! mkdir -p "${dir}"; then
        echo "FATAL: cannot write ${dir}" >&2
        echo "  Run: sudo chown -R ${USER}:${USER} ${REPO_ROOT}/temp/bench/${net}" >&2
        return 1
      fi
    done
  done
  echo "Bench dirs writable for: ${nets[*]}"
  echo ""
}

# Apply first shaped network profile if needed. Uses NETWORKS env.
fair_trials_interactive_ensure_network() {
  local -a nets
  read -r -a nets <<< "${NETWORKS:-LAN WAN1 WAN2}"
  local first="" net
  for net in "${nets[@]}"; do
    if [[ "${net}" != "none" ]]; then
      first="${net}"
      break
    fi
  done
  [[ -n "${first}" ]] || return 0

  echo "=== Network check (first shaped profile: ${first}) ==="
  if ! ./scripts/network_profiles.sh show 2>/dev/null | grep -qiE '(^|[[:space:]])netem([[:space:]]|$)'; then
    echo "No tc netem on lo — applying ${first} (sudo may prompt)..."
    sudo ./scripts/network_profiles.sh apply "${first}"
  else
    echo "tc netem already present on lo:"
    ./scripts/network_profiles.sh show
    echo ""
    echo "Switch profile before each network block if needed:"
    echo "  sudo ./scripts/network_profiles.sh apply WAN1"
  fi
  echo ""
}
