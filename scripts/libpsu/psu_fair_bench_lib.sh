# Shared helpers for fair_bench.conf discovery and completion checks.
# Shared helpers; sourced by scripts/run_psu_fair.sh (do not execute directly).

# Resolve repo root when scripts live outside the tree (e.g. symlinked under /dev/shm).
libpsu_resolve_repo_root() {
  local script_dir="${1:-}"
  if [[ -n "${LIBPSU_REPO_ROOT:-}" && -f "${LIBPSU_REPO_ROOT}/mpc4j-psu/pom.xml" ]]; then
    printf '%s\n' "${LIBPSU_REPO_ROOT}"
    return 0
  fi
  if [[ -f "${PWD}/mpc4j-psu/pom.xml" ]]; then
    printf '%s\n' "${PWD}"
    return 0
  fi
  local probe="${script_dir}" candidate=""
  while [[ -n "${probe}" && "${probe}" != "/" ]]; do
    if [[ -f "${probe}/mpc4j-psu/pom.xml" ]]; then
      candidate="${probe}"
      break
    fi
    probe="$(dirname "${probe}")"
  done
  if [[ -n "${candidate}" ]]; then
    printf '%s\n' "${candidate}"
    return 0
  fi
  if [[ -n "${script_dir}" ]]; then
    printf '%s\n' "$(cd "${script_dir}/../.." && pwd)"
    return 0
  fi
  return 1
}

# mpc4j requires JDK 17 (--enable-preview + jdk.incubator.vector). Pin unless MPC4J_JAVA_HOME is set.
ensure_jdk17_home() {
  if [[ -n "${MPC4J_JAVA_HOME:-}" ]]; then
    export JAVA_HOME="${MPC4J_JAVA_HOME}"
  elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  else
    local candidate
    for candidate in \
      /usr/lib/jvm/java-17-openjdk-amd64 \
      /usr/lib/jvm/java-1.17.0-openjdk-amd64 \
      /usr/lib/jvm/temurin-17-jdk-amd64 \
      /usr/lib/jvm/temurin-17 \
      "${HOME}/.sdkman/candidates/java/17"* \
      ; do
      if [[ -x "${candidate}/bin/java" ]]; then
        export JAVA_HOME="${candidate}"
        break
      fi
    done
  fi
  if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
    echo "JDK 17 not found. Install Temurin 17 or set MPC4J_JAVA_HOME." >&2
    return 1
  fi
  local ver
  ver="$("${JAVA_HOME}/bin/java" -version 2>&1 | grep -E 'version \"17\.' | head -1)"
  if [[ -z "${ver}" ]]; then
    ver="$("${JAVA_HOME}/bin/java" -version 2>&1 | tail -1)"
  fi
  if [[ "${ver}" != *"17."* ]]; then
    echo "Expected JDK 17 for mpc4j build/bench, got: ${ver}" >&2
    echo "Set MPC4J_JAVA_HOME to a JDK 17 install (e.g. temurin-17)." >&2
    return 1
  fi
  export PATH="${JAVA_HOME}/bin:${PATH}"
  return 0
}

psu_bench_resources() {
  echo "${PSU_BENCH_RES:-${REPO_ROOT}/mpc4j-psu/bench/configs}"
}

# Backward-compatible alias (deprecated).
psu_test_resources() {
  psu_bench_resources
}

psu_fair_conf_root() {
  echo "${PSU_FAIR_CONF_DIR:-$(psu_bench_resources)/psu}"
}

# Network profile slug for output paths (none, LAN, WAN1, WAN2).
psu_fair_network_slug() {
  echo "${PSU_FAIR_NETWORK_PROFILE:-none}"
}

# Root for all bench artifacts for one network profile.
psu_fair_network_bench_root() {
  echo "${REPO_ROOT}/temp/bench/$(psu_fair_network_slug)"
}

# Directory where PsuMain/PsiMain write PSU_*.output (save_path).
psu_fair_output_temp_dir() {
  echo "$(psu_fair_network_bench_root)"
}

# save_path value relative to repo root (no trailing slash).
psu_fair_save_path_relative() {
  echo "temp/bench/$(psu_fair_network_slug)"
}

# Inject save_path into a bench config (staging copy under the network root).
psu_fair_conf_with_save_path() {
  local conf="$1"
  local save_path rel patched staging
  save_path="$(psu_fair_save_path_relative)"
  if grep -qE "^[[:space:]]*save_path[[:space:]]*=[[:space:]]*${save_path}[[:space:]]*$" "${conf}" 2>/dev/null; then
    echo "${conf}"
    return 0
  fi
  rel="${conf#${REPO_ROOT}/}"
  staging="$(psu_fair_network_bench_root)/staging/confs"
  mkdir -p "${staging}"
  patched="${staging}/$(echo "${rel}" | tr '/' '_')"
  if [[ -f "${patched}" && "${patched}" -nt "${conf}" ]] \
    && grep -qE "^[[:space:]]*save_path[[:space:]]*=[[:space:]]*${save_path}[[:space:]]*$" "${patched}" 2>/dev/null; then
    echo "${patched}"
    return 0
  fi
  if grep -qE '^[[:space:]]*save_path[[:space:]]*=' "${conf}" 2>/dev/null; then
    sed "s/^[[:space:]]*save_path[[:space:]]*=.*/save_path = ${save_path}/" "${conf}" >"${patched}"
  elif grep -qE '^append_string[[:space:]]*=' "${conf}" 2>/dev/null; then
    sed "/^append_string[[:space:]]*=.*/a save_path = ${save_path}" "${conf}" >"${patched}"
  else
    { echo "save_path = ${save_path}"; cat "${conf}"; } >"${patched}"
  fi
  echo "${patched}"
}

psu_fair_bench_run_dir() {
  local log="${1:-5}"
  echo "${PSU_FAIR_BENCH_DIR:-$(psu_fair_network_bench_root)/psu_fair/2p${log}}"
}

max_log_exponent_from_conf() {
  local conf="$1" key="$2"
  local raw max=0 log
  raw="$(grep -E "^[[:space:]]*${key}[[:space:]]*=" "${conf}" 2>/dev/null | head -1 | sed 's/.*=[[:space:]]*//')"
  raw="${raw//[[:space:]]/}"
  IFS=',' read -r -a logs <<< "${raw}"
  for log in "${logs[@]}"; do
    if [[ "${log}" =~ ^[0-9]+$ && "${log}" -gt "${max}" ]]; then
      max="${log}"
    fi
  done
  echo "${max}"
}

fair_bench_server_count() {
  local conf="$1"
  echo $((1 << $(max_log_exponent_from_conf "${conf}" server_log_set_size)))
}

fair_bench_client_count() {
  local conf="$1"
  echo $((1 << $(max_log_exponent_from_conf "${conf}" client_log_set_size)))
}

read_psu_pto_name() {
  local conf="$1"
  grep -E '^[[:space:]]*psu_pto_name[[:space:]]*=' "${conf}" 2>/dev/null | head -1 \
    | sed 's/.*=[[:space:]]*//' | tr -d '[:space:]' || true
}

read_psi_pto_name() {
  local conf="$1"
  grep -E '^[[:space:]]*psi_pto_name[[:space:]]*=' "${conf}" 2>/dev/null | head -1 \
    | sed 's/.*=[[:space:]]*//' | tr -d '[:space:]' || true
}

is_proxy_or_inspired_pto() {
  local pto="$1"
  [[ -z "${pto}" ]] && return 1
  case "${pto}" in
    *_PROXY|*_INSPIRED|*_PROXY_*|PKC:GMRSS21|USENIX:JSZDG22|USENIX:JSZDG22_SFS)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

should_skip_non_paper_exact() {
  local pto="$1"
  if [[ "${PAPER_EXACT_ONLY:-0}" == "1" ]] && is_proxy_or_inspired_pto "${pto}"; then
    return 0
  fi
  return 1
}

read_ba12_pto_name() {
  local conf="$1"
  grep -E '^[[:space:]]*ba12_pto_name[[:space:]]*=' "${conf}" 2>/dev/null | head -1 \
    | sed 's/.*=[[:space:]]*//' | tr -d '[:space:]' || true
}

read_fair_pto_name() {
  local conf="$1" pto
  if grep -qE '^[[:space:]]*pto_type[[:space:]]*=[[:space:]]*BA12[[:space:]]*$' "${conf}" 2>/dev/null; then
    pto="$(read_ba12_pto_name "${conf}")"
    echo "${pto:-BA12}"
    return 0
  fi
  pto="$(read_psu_pto_name "${conf}")"
  if [[ -n "${pto}" ]]; then
    echo "${pto}"
    return 0
  fi
  read_psi_pto_name "${conf}"
}

# Match config path or psu_pto_name / psi_pto_name against one allowlist token (case-insensitive).
conf_matches_protocol_pattern() {
  local conf="$1" pat="$2"
  local path_upper pto
  pat="$(echo "${pat}" | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')"
  [[ -n "${pat}" ]] || return 1
  path_upper="$(echo "${conf}" | tr '[:lower:]' '[:upper:]')"
  pto="$(read_fair_pto_name "${conf}" | tr '[:lower:]' '[:upper:]')"
  [[ "${path_upper}" == *"${pat}"* || "${pto}" == *"${pat}"* ]]
}

conf_matches_protocol_allowlist() {
  local conf="$1"
  local allowlist="${PSU_FAIR_PROTOCOL_ALLOWLIST:-}"
  [[ -z "${allowlist}" ]] && return 0
  local pat
  IFS=',' read -r -a patterns <<< "${allowlist}"
  for pat in "${patterns[@]}"; do
    if conf_matches_protocol_pattern "${conf}" "${pat}"; then
      return 0
    fi
  done
  return 1
}

warn_missing_protocol_allowlist_patterns() {
  local allowlist="${PSU_FAIR_PROTOCOL_ALLOWLIST:-}"
  [[ -z "${allowlist}" ]] && return 0
  local conf pat found
  IFS=',' read -r -a patterns <<< "${allowlist}"
  for pat in "${patterns[@]}"; do
    pat="$(echo "${pat}" | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')"
    [[ -n "${pat}" ]] || continue
    found=0
    for conf in "${@}"; do
      if conf_matches_protocol_pattern "${conf}" "${pat}"; then
        found=1
        break
      fi
    done
    if [[ "${found}" -eq 0 ]]; then
      echo "WARN: no fair bench config matched allowlist pattern '${pat}'" >&2
    fi
  done
}

read_append_string_from_conf() {
  local conf="$1"
  grep -E '^[[:space:]]*append_string[[:space:]]*=' "${conf}" 2>/dev/null | head -1 \
    | sed 's/.*=[[:space:]]*//' | tr -d '[:space:]' || true
}

read_element_byte_length_from_conf() {
  local conf="$1"
  grep -E '^[[:space:]]*element_byte_length[[:space:]]*=' "${conf}" 2>/dev/null | head -1 \
    | sed 's/.*=[[:space:]]*//' | tr -d '[:space:]' || echo 16
}

read_ks05_max_set_size_from_conf() {
  local conf="$1"
  grep -E '^[[:space:]]*ks05_max_set_size[[:space:]]*=' "${conf}" 2>/dev/null | head -1 \
    | sed 's/.*=[[:space:]]*//' | tr -d '[:space:]' || true
}

# Largest n with n <= max_size is 2^floor(log2(max_size)).
ks05_max_log_from_set_size() {
  local max_size="$1"
  local n="${max_size}"
  local log=0
  [[ "${max_size}" =~ ^[0-9]+$ && "${max_size}" -gt 0 ]] || return 1
  while (( n > 1 )); do
    log=$((log + 1))
    n=$((n / 2))
  done
  echo "${log}"
}

read_pto_type_from_conf() {
  local conf="$1"
  grep -E '^[[:space:]]*pto_type[[:space:]]*=' "${conf}" 2>/dev/null | head -1 \
    | sed 's/.*=[[:space:]]*//' | tr -d '[:space:]' || true
}

# Matches PsuMain/PsiMain: <PSU|PSI>_<pto>_<append>_<elementBits>_<partyId>_<parallelism>.output
# Parallelism is ForkJoinPool.getCommonPoolParallelism() at runtime (not fixed).
fair_bench_output_family() {
  local conf="$1"
  case "$(read_pto_type_from_conf "${conf}")" in
    PSI) echo "PSI" ;;
    UPSU) echo "UPSU" ;;
    *) echo "PSU" ;;
  esac
}

fair_bench_output_glob() {
  local conf="$1" pto="$2" party_id="$3" temp_dir="${4:-$(psu_fair_output_temp_dir)}"
  local append elem_bits family
  append="$(read_append_string_from_conf "${conf}")"
  append="${append:-fair_bench}"
  elem_bits=$(( $(read_element_byte_length_from_conf "${conf}") * 8 ))
  family="$(fair_bench_output_family "${conf}")"
  echo "${temp_dir}/${family}_${pto}_${append}_${elem_bits}_${party_id}_*.output"
}

fair_bench_output_path() {
  local conf="$1" pto="$2" party_id="$3" temp_dir="${4:-$(psu_fair_output_temp_dir)}"
  local glob match
  glob="$(fair_bench_output_glob "${conf}" "${pto}" "${party_id}" "${temp_dir}")"
  # shellcheck disable=SC2086
  for match in ${glob}; do
    if [[ -f "${match}" ]]; then
      echo "${match}"
      return 0
    fi
  done
  # Legacy fallback path (parallelism unknown before first run).
  echo "${glob%.output}9.output"
}

fair_bench_output_file_is_complete() {
  local out="$1" exp_s="$2" exp_c="$3"
  [[ -f "${out}" ]] || return 1
  awk -F'\t' -v s="${exp_s}" -v c="${exp_c}" \
    'NR == 2 && (($2 == s && $3 == c) || ($2 == c && $3 == s)) { ok = 1 } END { exit !ok }' "${out}"
}

should_skip_pt26_conf() {
  local conf="$1"
  [[ "${PSU_FAIR_SKIP_PT26:-0}" == "1" ]] || return 1
  [[ "${conf}" == *"/14_PT26/"* || "${conf}" == *"14_PT26"* ]] && return 0
  [[ "$(read_fair_pto_name "${conf}")" == "EUROCRYPT:PisTri26" ]] && return 0
  return 1
}

should_skip_pgt26_2m_conf() {
  local conf="$1"
  [[ "${PSU_FAIR_SKIP_PGT26_2M:-0}" == "1" ]] || return 1
  [[ "${conf}" == *"/18_PGT26_2M/"* || "${conf}" == *"18_PGT26_2M"* ]] && return 0
  [[ "$(read_fair_pto_name "${conf}")" == "EUROCRYPT:PuGaoTri26" ]] && return 0
  return 1
}

should_skip_ks05_oversize_conf() {
  local conf="$1" pto server_log client_log max_log max_allowed_log ks05_max cfg_max_log
  pto="$(read_fair_pto_name "${conf}")"
  [[ "${pto}" == "C:KisSon05" ]] || return 1
  max_allowed_log="${PSU_FAIR_KS05_MAX_LOG_SIZE:-${PSU_FAIR_C_KISSON05_MAX_LOG_SIZE:-8}}"
  ks05_max="$(read_ks05_max_set_size_from_conf "${conf}")"
  if [[ "${ks05_max}" =~ ^[0-9]+$ && "${ks05_max}" -gt 0 ]]; then
    cfg_max_log="$(ks05_max_log_from_set_size "${ks05_max}")"
    if [[ "${cfg_max_log}" -lt "${max_allowed_log}" ]]; then
      max_allowed_log="${cfg_max_log}"
    fi
  fi
  server_log="$(max_log_exponent_from_conf "${conf}" server_log_set_size)"
  client_log="$(max_log_exponent_from_conf "${conf}" client_log_set_size)"
  if [[ "${server_log}" -gt "${client_log}" ]]; then
    max_log="${server_log}"
  else
    max_log="${client_log}"
  fi
  [[ "${max_log}" -gt "${max_allowed_log}" ]]
}

has_complete_fair_output_for_conf() {
  local conf="$1" temp_dir="${2:-$(psu_fair_output_temp_dir)}"
  local pto exp_s exp_c pid glob match found
  pto="$(read_fair_pto_name "${conf}")"
  [[ -n "${pto}" ]] || return 1
  exp_s="$(fair_bench_server_count "${conf}")"
  exp_c="$(fair_bench_client_count "${conf}")"
  for pid in 0 1; do
    glob="$(fair_bench_output_glob "${conf}" "${pto}" "${pid}" "${temp_dir}")"
    found=0
    # shellcheck disable=SC2086
    for match in ${glob}; do
      if fair_bench_output_file_is_complete "${match}" "${exp_s}" "${exp_c}"; then
        found=1
        break
      fi
    done
    [[ "${found}" -eq 1 ]] || return 1
  done
  return 0
}

remove_incomplete_fair_outputs_for_conf() {
  local conf="$1" temp_dir="${2:-$(psu_fair_output_temp_dir)}"
  local pto pid out
  pto="$(read_fair_pto_name "${conf}")"
  [[ -n "${pto}" ]] || return 0
  for pid in 0 1; do
    out="$(fair_bench_output_path "${conf}" "${pto}" "${pid}" "${temp_dir}")"
    [[ -f "${out}" ]] || continue
    if ! awk -F'\t' 'NR == 2 { ok = 1 } END { exit !ok }' "${out}"; then
      rm -f "${out}"
      echo "Removed incomplete output ${out}" >&2
    fi
  done
}

fair_bench_conf_for_pto() {
  local pto="$1" root f
  root="$(psu_fair_conf_root)"
  while IFS= read -r f; do
    if grep -qE "^[[:space:]]*psu_pto_name[[:space:]]*=[[:space:]]*${pto}[[:space:]]*$" "${f}" 2>/dev/null; then
      echo "${f}"
      return 0
    fi
  done < <(find "${root}" -type f -name 'fair_bench.conf' ! -name '*.disabled' \
    ! -path '*11_DGG25*' 2>/dev/null | LC_ALL=C sort)
  return 1
}

fair_bench_trim_port() {
  echo "${1:-}" | tr -d '[:space:]'
}

fair_bench_read_server_port() {
  local conf="$1" default_port="${2:-19002}" port
  port="$(grep -E '^[[:space:]]*server_port[[:space:]]*=' "${conf}" 2>/dev/null | head -1 | sed 's/.*=[[:space:]]*//')"
  port="$(fair_bench_trim_port "${port}")"
  echo "${port:-${default_port}}"
}

fair_bench_read_client_port() {
  local conf="$1" default_port="${2:-19003}" port
  port="$(grep -E '^[[:space:]]*client_port[[:space:]]*=' "${conf}" 2>/dev/null | head -1 | sed 's/.*=[[:space:]]*//')"
  port="$(fair_bench_trim_port "${port}")"
  echo "${port:-${default_port}}"
}

fair_bench_port_has_listener() {
  local port="$1"
  [[ -n "${port}" ]] || return 1
  command -v lsof >/dev/null 2>&1 || return 1
  lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
}

fair_bench_kill_listeners_on_port() {
  local port="$1" pid
  [[ -n "${port}" ]] || return 0
  if ! command -v lsof >/dev/null 2>&1; then
    return 0
  fi
  while IFS= read -r pid; do
    [[ -z "${pid}" ]] && continue
    kill "${pid}" 2>/dev/null || true
  done < <(lsof -nP -t -iTCP:"${port}" -sTCP:LISTEN 2>/dev/null)
  sleep 1
  while IFS= read -r pid; do
    [[ -z "${pid}" ]] && continue
    kill -9 "${pid}" 2>/dev/null || true
  done < <(lsof -nP -t -iTCP:"${port}" -sTCP:LISTEN 2>/dev/null)
}

# Kill any process LISTENing on the planned server/client ports. Checks each port
# independently (lsof treats multiple -i flags as AND, so a lone zombie on one port
# must still be detected). Call before every protocol run.
free_fair_bench_ports() {
  local server_port client_port log="${OVERALL_LOG:-}" port had_listener=0 still_busy=0
  server_port="$(fair_bench_trim_port "${1:-19002}")"
  client_port="$(fair_bench_trim_port "${2:-19003}")"

  if ! command -v lsof >/dev/null 2>&1; then
    return 0
  fi

  for port in "${server_port}" "${client_port}"; do
    if fair_bench_port_has_listener "${port}"; then
      had_listener=1
      break
    fi
  done

  if [[ "${had_listener}" -eq 1 ]]; then
    if [[ -n "${log}" ]]; then
      echo "Freeing ports ${server_port}/${client_port} (stale listeners)..." | tee -a "${log}"
    else
      echo "Freeing ports ${server_port}/${client_port} (stale listeners)..." >&2
    fi
    for port in "${server_port}" "${client_port}"; do
      fair_bench_kill_listeners_on_port "${port}"
    done
    sleep 2
  fi

  for port in "${server_port}" "${client_port}"; do
    if fair_bench_port_has_listener "${port}"; then
      still_busy=1
      echo "Port ${port} still in use after cleanup." >&2
      lsof -nP -iTCP:"${port}" -sTCP:LISTEN >&2 || true
    fi
  done

  [[ "${still_busy}" -eq 0 ]]
}

ensure_fair_bench_ports_free() {
  local conf="$1" server_port client_port
  server_port="$(fair_bench_read_server_port "${conf}")"
  client_port="$(fair_bench_read_client_port "${conf}")"
  free_fair_bench_ports "${server_port}" "${client_port}"
}

has_complete_fair_output_for_pto() {
  local pto="$1" temp_dir="${2:-$(psu_fair_output_temp_dir)}"
  local conf exp_s exp_c pid out
  conf="$(fair_bench_conf_for_pto "${pto}")" || return 1
  exp_s="$(fair_bench_server_count "${conf}")"
  exp_c="$(fair_bench_client_count "${conf}")"
  for pid in 0 1; do
    out="$(fair_bench_output_path "${conf}" "${pto}" "${pid}" "${temp_dir}")"
    [[ -f "${out}" ]] || return 1
    if ! awk -F'\t' -v s="${exp_s}" -v c="${exp_c}" \
      'NR == 2 && (($2 == s && $3 == c) || ($2 == c && $3 == s)) { ok = 1 } END { exit !ok }' "${out}"; then
      return 1
    fi
  done
  return 0
}

# Remove header-only PSU_*.output (PsuMain creates the file before the timed run).
remove_incomplete_fair_outputs_for_pto() {
  local pto="$1" temp_dir="${2:-$(psu_fair_output_temp_dir)}"
  local conf pid out
  conf="$(fair_bench_conf_for_pto "${pto}")" || return 0
  for pid in 0 1; do
    out="$(fair_bench_output_path "${conf}" "${pto}" "${pid}" "${temp_dir}")"
    [[ -f "${out}" ]] || continue
    if ! awk -F'\t' 'NR == 2 { ok = 1 } END { exit !ok }' "${out}"; then
      rm -f "${out}"
      echo "Removed incomplete output ${out}" >&2
    fi
  done
}

# Build or locate the PSU driver fat JAR. Set NO_BUILD=1 to skip when JAR exists.
ensure_psu_driver_jar() {
  local jar
  ensure_jdk17_home || return 1
  jar="$(ls -t "${REPO_ROOT}"/mpc4j-psu/apps/driver/target/mpc4j-psu-driver-*-jar-with-dependencies.jar 2>/dev/null | head -1 || true)"
  if [[ "${NO_BUILD:-0}" -eq 1 && -n "${jar}" && -f "${jar}" ]]; then
    printf '%s\n' "${jar}"
    return 0
  fi
  echo "Building mpc4j-psu driver (JAVA_HOME=${JAVA_HOME}) ..." >&2
  ( cd "${REPO_ROOT}" && "${REPO_ROOT}/scripts/mvn-jdk17.sh" -f mpc4j-psu/pom.xml -q install -DskipTests )
  jar="$(ls -t "${REPO_ROOT}"/mpc4j-psu/apps/driver/target/mpc4j-psu-driver-*-jar-with-dependencies.jar 2>/dev/null | head -1 || true)"
  if [[ -z "${jar}" || ! -f "${jar}" ]]; then
    echo "PSU driver fat JAR not found after build." >&2
    return 1
  fi
  printf '%s\n' "${jar}"
}

small_ec_bench_run_dir() {
  local log="${1:-5}"
  echo "$(psu_fair_network_bench_root)/small_ec/2p${log}"
}

psu_fair_bench_dir() {
  local log="${1:-5}"
  echo "$(psu_fair_network_bench_root)/psu_fair/2p${log}"
}

# Rebuild temp/bench/psu_fair/2p<LOG>/summary_* from temp/*.output.
# --only-append fair_bench_2p<LOG> also pulls in small_ec_bench_2p<LOG>_case* rows.
resummarize_psu_fair_log() {
  local log="$1"
  local script_dir="${2:-${PSU_FAIR_SCRIPT_DIR:-}}"
  local temp_dir="${3:-$(psu_fair_output_temp_dir)}"
  local append="fair_bench_2p${log}"
  local bench_dir summary short_summary log_file

  if ! [[ "${log}" =~ ^[0-9]+$ ]]; then
    echo "resummarize_psu_fair_log: invalid log ${log}" >&2
    return 1
  fi
  if [[ -z "${script_dir}" ]]; then
    echo "resummarize_psu_fair_log: script_dir required" >&2
    return 1
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    return 0
  fi

  bench_dir="$(psu_fair_bench_dir "${log}")"
  summary="${bench_dir}/summary_2p${log}.csv"
  short_summary="${bench_dir}/summary_2p${log}_short.csv"
  log_file="${bench_dir}/logs/psu_fair_2p${log}.log"
  local log_append="${log_file}"
  mkdir -p "${bench_dir}/logs"
  if ! touch "${log_file}" 2>/dev/null; then
    log_append="${temp_dir}/summarize_refresh_2p${log}.log"
    mkdir -p "$(dirname "${log_append}")"
    touch "${log_append}" 2>/dev/null || log_append="/dev/null"
  fi

  echo "Refreshing psu_fair summary for 2^${log} (fair + SMALL_EC cases)..."
  if ! python3 "${script_dir}/summarize_psu_fair_outputs.py" \
    --temp-dir "${temp_dir}" \
    --only-append "${append}" \
    --append-glob 'PSI_*.output' \
    --out "${summary}" 2>>"${log_append}"; then
    return 1
  fi
  if [[ -f "${summary}" ]]; then
    python3 "${script_dir}/short_summarize_psu_fair_outputs.py" \
      "${summary}" "${short_summary}" 2>>"${log_append}" || return 1
    echo "  ${short_summary}"
  fi
  return 0
}

resummarize_psu_fair_logs() {
  local script_dir="${1:-${PSU_FAIR_SCRIPT_DIR:-}}"
  local temp_dir="${2:-$(psu_fair_output_temp_dir)}"
  local failures=0 log
  shift 2 2>/dev/null || true
  if [[ $# -eq 0 ]]; then
    return 0
  fi
  for log in "$@"; do
    if ! resummarize_psu_fair_log "${log}" "${script_dir}" "${temp_dir}"; then
      failures=$((failures + 1))
    fi
  done
  return "${failures}"
}

# Per-protocol wall-clock limit (default 30 minutes). Override with PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS.
psu_fair_protocol_timeout_seconds() {
  if [[ -n "${PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS:-}" ]]; then
    echo "${PSU_FAIR_PROTOCOL_TIMEOUT_SECONDS}"
  elif [[ -n "${PSU_FAIR_CLIENT_TIMEOUT_SECONDS:-}" ]]; then
    echo "${PSU_FAIR_CLIENT_TIMEOUT_SECONDS}"
  else
    echo 1800
  fi
}

# Exit status from run_java_with_timeout / run_cmd_with_timeout when the watchdog fired.
PSU_FAIR_TIMEOUT_EXIT=124

# Marker path for timeout detection (file is created only when a watchdog kills its target).
psu_fair_new_timeout_marker() {
  local marker="${TMPDIR:-/tmp}/psu_fair_timeout.$$.$RANDOM"
  rm -f "${marker}"
  echo "${marker}"
}

# Log-agnostic protocol id from a fair-bench config label, e.g. pso/01_AC:KRTW19/fair_bench_2p5.conf → pso/01_AC:KRTW19.
fair_bench_protocol_key_from_label() {
  local label="$1"
  if [[ "${label}" =~ ^(.+)/fair_bench_2p[0-9]+\.conf$ ]]; then
    echo "${BASH_REMATCH[1]}"
    return 0
  fi
  if [[ "${label}" =~ ^(.+)/fair_bench_unbalanced_.*\.conf$ ]]; then
    echo "${BASH_REMATCH[1]}"
    return 0
  fi
  echo "${label}"
}

psu_fair_protocol_timeout_registry_path() {
  echo "$(psu_fair_network_bench_root)/matrix/protocol_timeouts.tsv"
}

# Record that protocol_key timed out at 2^log; larger balanced sizes skip this protocol.
# Only call when the run genuinely timed out (no complete output for that log).
psu_fair_record_protocol_timeout() {
  local protocol_key="$1" log="$2" reg tmp existing
  [[ -n "${protocol_key}" && -n "${log}" ]] || return 0
  reg="$(psu_fair_protocol_timeout_registry_path)"
  mkdir -p "$(dirname "${reg}")"
  tmp="${reg}.$$"
  existing=""
  if [[ -f "${reg}" ]]; then
    existing="$(awk -F '\t' -v key="${protocol_key}" '$1 == key { print $2; exit }' "${reg}" 2>/dev/null || true)"
    awk -F '\t' -v key="${protocol_key}" '$1 != key { print }' "${reg}" >"${tmp}" 2>/dev/null || : >"${tmp}"
  else
    : >"${tmp}"
  fi
  if [[ -n "${existing}" && "${existing}" -lt "${log}" ]]; then
    log="${existing}"
  fi
  printf '%s\t%s\n' "${protocol_key}" "${log}" >>"${tmp}"
  mv "${tmp}" "${reg}"
}

psu_fair_clear_protocol_timeout() {
  local protocol_key="$1" reg tmp
  [[ -n "${protocol_key}" ]] || return 0
  reg="$(psu_fair_protocol_timeout_registry_path)"
  [[ -f "${reg}" ]] || return 0
  tmp="${reg}.$$"
  if awk -F '\t' -v key="${protocol_key}" '$1 != key { print }' "${reg}" >"${tmp}" && [[ -s "${tmp}" || ! -s "${reg}" ]]; then
    mv "${tmp}" "${reg}"
  else
    rm -f "${tmp}" "${reg}"
  fi
}

psu_fair_protocol_timeout_log_for_key() {
  local protocol_key="$1" reg
  [[ -n "${protocol_key}" ]] || return 1
  reg="$(psu_fair_protocol_timeout_registry_path)"
  [[ -f "${reg}" ]] || return 1
  awk -F '\t' -v key="${protocol_key}" '$1 == key { print $2; exit }' "${reg}" 2>/dev/null || true
}

# Skip when protocol timed out at a strictly smaller 2^log (matrix runs ascending sizes).
psu_fair_should_skip_protocol_after_timeout() {
  local protocol_key="$1" log="$2" timeout_log
  [[ -n "${protocol_key}" && -n "${log}" ]] || return 1
  timeout_log="$(psu_fair_protocol_timeout_log_for_key "${protocol_key}")"
  [[ -n "${timeout_log}" && "${timeout_log}" -lt "${log}" ]]
}

# Kill a benchmark Java PID and its process group (setsid session). SIGKILL is required
# because HotSpot often keeps running after SIGTERM during heavy crypto/network I/O.
psu_fair_force_kill_pid() {
  local pid="$1" pgid
  [[ -n "${pid}" && "${pid}" =~ ^[0-9]+$ ]] || return 0
  if ! kill -0 "${pid}" 2>/dev/null; then
    return 0
  fi
  pgid="$(ps -o pgid= -p "${pid}" 2>/dev/null | tr -d ' ' || true)"
  kill -TERM "${pid}" 2>/dev/null || true
  if [[ -n "${pgid}" && "${pgid}" =~ ^[0-9]+$ ]]; then
    kill -TERM "-${pgid}" 2>/dev/null || true
  fi
  sleep 1
  kill -KILL "${pid}" 2>/dev/null || true
  if [[ -n "${pgid}" && "${pgid}" =~ ^[0-9]+$ ]]; then
    kill -KILL "-${pgid}" 2>/dev/null || true
  fi
}

# Fallback: kill driver JVMs started for one staged fair-bench config.
psu_fair_kill_java_for_conf() {
  local conf="$1" resolved slug
  [[ -n "${conf}" ]] || return 0
  resolved="$(psu_fair_conf_with_save_path "${conf}")"
  slug="$(basename "${resolved}")"
  if command -v pkill >/dev/null 2>&1; then
    pkill -KILL -f "${slug}" 2>/dev/null || true
  fi
}

# Cancellable wall-clock watchdog (bash 3.2): kill target_pid after timeout unless
# cancel_timeout_watchdog is called first.
# NOTE: Do not capture the PID via $(start_timeout_watchdog ...) — command substitution
# waits for background jobs and would block for the full timeout.
# Optional 4th arg: marker file created when the watchdog kills target_pid.
LAST_TIMEOUT_WATCHDOG_PID=""

start_timeout_watchdog() {
  local timeout_seconds="$1" target_pid="$2" label="${3:-process}" marker="${4:-}"
  (
    sleep "${timeout_seconds}" &
    local sleep_pid=$!
    trap 'kill "${sleep_pid}" 2>/dev/null || true; exit 0' TERM INT
    wait "${sleep_pid}" || exit 0
    if kill -0 "${target_pid}" 2>/dev/null; then
      echo "TIMEOUT ${label} after ${timeout_seconds}s" >&2
      if [[ -n "${marker}" ]]; then
        : >"${marker}"
      fi
      psu_fair_force_kill_pid "${target_pid}"
    fi
  ) &
  LAST_TIMEOUT_WATCHDOG_PID=$!
}

cancel_timeout_watchdog() {
  local watchdog_pid="${1:-${LAST_TIMEOUT_WATCHDOG_PID:-}}"
  [[ -n "${watchdog_pid}" ]] || return 0
  if command -v pkill >/dev/null 2>&1; then
    pkill -TERM -P "${watchdog_pid}" 2>/dev/null || true
  fi
  kill -TERM "${watchdog_pid}" 2>/dev/null || true
  wait "${watchdog_pid}" 2>/dev/null || true
  if [[ "${watchdog_pid}" == "${LAST_TIMEOUT_WATCHDOG_PID:-}" ]]; then
    LAST_TIMEOUT_WATCHDOG_PID=""
  fi
}

# Run a command in the background; kill it if still running after timeout_seconds.
run_cmd_with_timeout() {
  local timeout_seconds="$1"
  shift
  local pid watchdog status marker
  marker="$(psu_fair_new_timeout_marker)"
  "$@" &
  pid=$!
  start_timeout_watchdog "${timeout_seconds}" "${pid}" "$*" "${marker}"
  watchdog="${LAST_TIMEOUT_WATCHDOG_PID}"
  wait "${pid}"
  status=$?
  cancel_timeout_watchdog "${watchdog}"
  if [[ -f "${marker}" ]]; then
    rm -f "${marker}"
    return "${PSU_FAIR_TIMEOUT_EXIT}"
  fi
  return "${status}"
}
