# Network emulation profiles for fair PSU benchmarks (loopback traffic).
# Sourced by run_psu_fair.sh / run_psu_fair_matrix.sh — do not execute directly.
#
# Single-host Linux tc netem on ${PSU_FAIR_NET_DEV:-lo} (default lo).
# Both parties use 127.0.0.1; this is not a physical LAN/WAN.
#
# netem delay is applied on loopback egress. Request and response each traverse
# that qdisc, so approximate RTT ≈ 2 × netem delay.
#
#   Profile  rate      netem delay   approx. RTT
#   LAN      10gbit    0.1ms         ~0.2ms
#   WAN1     200mbit   40ms          ~80ms
#   WAN2     50mbit    40ms          ~80ms
#   none     (clear)   —             unshaped localhost
#
# macOS: tc/netem is unavailable; set profiles only on Linux hosts / VMs.

PSU_FAIR_NETWORK_PROFILE="${PSU_FAIR_NETWORK_PROFILE:-none}"

network_profile_names() {
  echo "none LAN WAN1 WAN2"
}

network_profile_is_valid() {
  local profile="$1"
  case "${profile}" in
    none|LAN|WAN1|WAN2) return 0 ;;
    *) return 1 ;;
  esac
}

# Prints: <rate> <netem_delay> <target_rtt>
# netem_delay is the tc "delay" argument (one-way on this lo qdisc), not RTT.
network_profile_params() {
  local profile="$1" rate="" netem_delay="" target_rtt=""
  case "${profile}" in
    LAN)
      rate="10gbit"
      netem_delay="0.1ms"
      target_rtt="0.2ms"
      ;;
    WAN1)
      rate="200mbit"
      netem_delay="40ms"
      target_rtt="80ms"
      ;;
    WAN2)
      rate="50mbit"
      netem_delay="40ms"
      target_rtt="80ms"
      ;;
    none) return 0 ;;
    *)
      echo "unknown network profile: ${profile}" >&2
      return 1
      ;;
  esac
  echo "${rate} ${netem_delay} ${target_rtt}"
}

network_profile_tc_supported() {
  command -v tc >/dev/null 2>&1
}

network_profile_qdisc_show() {
  local dev="${PSU_FAIR_NET_DEV:-lo}"
  tc qdisc show dev "${dev}" 2>/dev/null || sudo tc qdisc show dev "${dev}" 2>/dev/null
}

network_profile_show() {
  local dev="${PSU_FAIR_NET_DEV:-lo}"
  if ! network_profile_tc_supported; then
    echo "tc not found — network emulation unavailable on this host (use Linux for WAN/LAN profiles)"
    return 0
  fi
  echo "=== tc qdisc on ${dev} ==="
  network_profile_qdisc_show || true
}

network_profile_clear() {
  local dev="${PSU_FAIR_NET_DEV:-lo}" qdisc
  if ! network_profile_tc_supported; then
    return 0
  fi

  if ! qdisc="$(network_profile_qdisc_show 2>/dev/null)"; then
    echo "failed to inspect tc qdisc on ${dev}" >&2
    return 1
  fi
  if grep -qiE '(^|[[:space:]])netem([[:space:]]|$)' <<< "${qdisc}"; then
    echo "Clearing network emulation on ${dev}..."
    if ! sudo tc qdisc del dev "${dev}" root 2>/dev/null; then
      echo "WARN: failed to clear tc netem on ${dev} (sudo required)" >&2
      return 1
    fi
  fi
  return 0
}

# Kernel tc output varies (delay99us vs delay 99us, 10Gbit vs 10gbit, 0.1ms -> 99us).
network_profile_qdisc_has_delay_us() {
  local qdisc="$1" target_us="$2"
  grep -qiE "(^|[[:space:]])delay[[:space:]]*${target_us}us([[:space:]]|$)" <<< "${qdisc}" && return 0
  grep -qiE "(^|[[:space:]])delay${target_us}us([[:space:]]|$)" <<< "${qdisc}" && return 0
  return 1
}

network_profile_qdisc_has_delay_ms() {
  local qdisc="$1" target_ms="$2" pat
  # tc may print 40ms as 40.0ms; escape dots in targets like 0.1.
  pat="${target_ms//./\\.}"
  grep -qiE "(^|[[:space:]])delay[[:space:]]*${pat}(\\.0+)?ms([[:space:]]|$)" <<< "${qdisc}" && return 0
  grep -qiE "(^|[[:space:]])delay[[:space:]]*${pat}\\.[0-9]+ms([[:space:]]|$)" <<< "${qdisc}" && return 0
  grep -qiE "(^|[[:space:]])delay${pat}(\\.0+)?ms([[:space:]]|$)" <<< "${qdisc}" && return 0
  grep -qiE "(^|[[:space:]])delay${pat}\\.[0-9]+ms([[:space:]]|$)" <<< "${qdisc}" && return 0
  return 1
}

network_profile_qdisc_has_rate_gbit() {
  local qdisc="$1" target_gbit="$2"
  grep -qiE "(^|[[:space:]])rate[[:space:]]*${target_gbit}(\\.0+)?[[:space:]]*gbit" <<< "${qdisc}" && return 0
  grep -qiE "(^|[[:space:]])rate[[:space:]]*${target_gbit}\\.[0-9]+[[:space:]]*gbit" <<< "${qdisc}" && return 0
  return 1
}

network_profile_qdisc_has_rate_mbit() {
  local qdisc="$1" target_mbit="$2"
  grep -qiE "(^|[[:space:]])rate[[:space:]]*${target_mbit}(\\.0+)?[[:space:]]*mbit" <<< "${qdisc}" && return 0
  grep -qiE "(^|[[:space:]])rate[[:space:]]*${target_mbit}\\.[0-9]+[[:space:]]*mbit" <<< "${qdisc}" && return 0
  return 1
}

network_profile_verify() {
  local profile="${1:-none}" dev="${PSU_FAIR_NET_DEV:-lo}" qdisc
  if [[ "${profile}" == "none" || -z "${profile}" ]]; then
    if ! qdisc="$(network_profile_qdisc_show 2>/dev/null)"; then
      return 0
    fi
    if grep -qiE '(^|[[:space:]])netem([[:space:]]|$)' <<< "${qdisc}"; then
      echo "network verification failed: expected no netem qdisc on ${dev}" >&2
      echo "${qdisc}" >&2
      return 1
    fi
    return 0
  fi

  if ! network_profile_is_valid "${profile}"; then
    echo "network verification failed: invalid profile ${profile}" >&2
    return 1
  fi

  if ! qdisc="$(network_profile_qdisc_show 2>/dev/null)"; then
    echo "network verification failed: cannot read tc qdisc on ${dev}" >&2
    return 1
  fi
  if ! grep -qiE '(^|[[:space:]])netem([[:space:]]|$)' <<< "${qdisc}"; then
    echo "network verification failed: no netem qdisc on ${dev}" >&2
    echo "${qdisc}" >&2
    return 1
  fi

  case "${profile}" in
    LAN)
      network_profile_qdisc_has_rate_gbit "${qdisc}" 10 || \
        network_profile_qdisc_has_rate_mbit "${qdisc}" 10000 || {
        echo "network verification failed: LAN rate is not 10gbit/10000mbit" >&2
        echo "${qdisc}" >&2
        return 1
      }
      network_profile_qdisc_has_delay_ms "${qdisc}" 0.1 || \
        network_profile_qdisc_has_delay_ms "${qdisc}" "0.1" || \
        network_profile_qdisc_has_delay_us "${qdisc}" 99 || \
        network_profile_qdisc_has_delay_us "${qdisc}" 100 || {
        echo "network verification failed: LAN delay is not ~0.1ms (99-100us)" >&2
        echo "${qdisc}" >&2
        return 1
      }
      ;;
    WAN1)
      network_profile_qdisc_has_rate_mbit "${qdisc}" 200 || {
        echo "network verification failed: WAN1 rate is not 200mbit" >&2
        echo "${qdisc}" >&2
        return 1
      }
      network_profile_qdisc_has_delay_ms "${qdisc}" 40 || {
        echo "network verification failed: WAN1 delay is not 40ms" >&2
        echo "${qdisc}" >&2
        return 1
      }
      ;;
    WAN2)
      network_profile_qdisc_has_rate_mbit "${qdisc}" 50 || {
        echo "network verification failed: WAN2 rate is not 50mbit" >&2
        echo "${qdisc}" >&2
        return 1
      }
      network_profile_qdisc_has_delay_ms "${qdisc}" 40 || {
        echo "network verification failed: WAN2 delay is not 40ms" >&2
        echo "${qdisc}" >&2
        return 1
      }
      ;;
    *)
      echo "network verification failed: unknown profile ${profile}" >&2
      return 1
      ;;
  esac

  return 0
}

network_profile_apply() {
  local profile="${1:-none}"
  local dev="${PSU_FAIR_NET_DEV:-lo}"
  local rate netem_delay target_rtt parts

  if ! network_profile_is_valid "${profile}"; then
    echo "invalid network profile: ${profile} (expected: $(network_profile_names))" >&2
    return 1
  fi

  if [[ "${profile}" == "none" || -z "${profile}" ]]; then
    network_profile_clear || return 1
    network_profile_verify none
    return $?
  fi

  if ! network_profile_tc_supported; then
    echo "cannot apply profile ${profile}: tc/netem requires Linux (current host has no tc)" >&2
    return 1
  fi

  if network_profile_verify "${profile}" 2>/dev/null; then
    echo "Network profile ${profile} already active (verified)."
    network_profile_show
    return 0
  fi

  parts="$(network_profile_params "${profile}")" || return 1
  read -r rate netem_delay target_rtt <<< "${parts}"

  network_profile_clear || return 1
  echo "Applying network profile ${profile}:"
  echo "  device: ${dev}"
  echo "  rate: ${rate}"
  echo "  netem delay: ${netem_delay}"
  echo "  approximate RTT: ${target_rtt}"
  if ! sudo tc qdisc add dev "${dev}" root netem rate "${rate}" delay "${netem_delay}"; then
    echo "failed to apply tc netem on ${dev} (sudo required)" >&2
    return 1
  fi

  network_profile_show
  if ! network_profile_verify "${profile}"; then
    echo "WARN: ${profile} applied but verification reported a mismatch; leaving qdisc in place" >&2
    echo "  If benchmarks look wrong, run: sudo ./scripts/network_profiles.sh clear" >&2
    return 1
  fi
  echo "Network profile ${profile} verified."
  return 0
}

network_profile_install_cleanup_trap() {
  trap 'network_profile_clear || true' EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
}
