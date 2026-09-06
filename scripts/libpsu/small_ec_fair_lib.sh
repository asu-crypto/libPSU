# Ours mode-matrix benchmark (sourced by run_psu_fair.sh).
# Requires: REPO_ROOT, SCRIPT_DIR, psu_fair_bench_lib.sh loaded.

run_small_ec_fair_suite() {
  local LOG="$1"
  local force="${2:-0}"
  local no_build="${3:-0}"

  local N=$((1 << LOG))
  local APPEND_PREFIX="small_ec_bench_2p${LOG}"
  local BENCH_RUN_DIR
  BENCH_RUN_DIR="$(small_ec_bench_run_dir "${LOG}")"
  local CONF_DIR="${BENCH_RUN_DIR}/generated_confs"
  local TEMP_DIR
  TEMP_DIR="$(psu_fair_output_temp_dir)"
  local SAVE_PATH
  SAVE_PATH="$(psu_fair_save_path_relative)"
  local LOG_FILE="${BENCH_RUN_DIR}/logs/small_ec_bench_2p${LOG}.log"
  local SUMMARY="${BENCH_RUN_DIR}/summary_2p${LOG}.csv"
  local SHORT_SUMMARY="${BENCH_RUN_DIR}/summary_2p${LOG}_short.csv"
  local SERVER_PORT=19216
  local CLIENT_PORT=19217
  local JAVA_BIN="${JAVA_BIN:-java}"
  local JAVA_OPTS=(
    "--add-modules" "jdk.incubator.vector"
    "-Djava.library.path=${MPC4J_NATIVE_TOOL_DIR:-${REPO_ROOT}/mpc4j-native-tool/cmake-build-release}"
    "-Xms2g"
    "-Xmx8g"
  )

  local -a CASES CONF_FILES CASE_DESCS
  local DRIVER_JAR failures=0 i conf desc name

  CASES=(
    "case1_exact_no_async_no_parallel|FULL_POINT_EXACT|0|CANONICAL_POINT_PREFIX|false|false|exact, no async, no parallel"
    "case2_exact_async_no_parallel|FULL_POINT_EXACT|0|CANONICAL_POINT_PREFIX|true|false|exact, async W, no parallel"
    "case3_exact_no_async_parallel|FULL_POINT_EXACT|0|CANONICAL_POINT_PREFIX|false|true|exact, no async, parallel EC"
    "case4_exact_async_parallel|FULL_POINT_EXACT|0|CANONICAL_POINT_PREFIX|true|true|exact, async W + parallel EC"
    "case5_fp64_no_async_no_parallel|TRUNCATED_W_PROBABILISTIC|64|CANONICAL_POINT_PREFIX|false|false|fp64, no async, no parallel"
    "case6_fp64_async_no_parallel|TRUNCATED_W_PROBABILISTIC|64|CANONICAL_POINT_PREFIX|true|false|fp64, async W, no parallel"
    "case7_fp64_no_async_parallel|TRUNCATED_W_PROBABILISTIC|64|CANONICAL_POINT_PREFIX|false|true|fp64, no async, parallel EC"
    "case8_fp_auto_async_parallel|TRUNCATED_W_PROBABILISTIC|0|CANONICAL_POINT_PREFIX|true|true|fp auto λ, async + parallel"
  )

  _small_ec_write_conf() {
    local case_name="$1" wmode="$2" fpbits="$3" fpmethod="$4" asyncw="$5" parallelec="$6"
    local append="${APPEND_PREFIX}_${case_name}"
    local conf="${CONF_DIR}/${case_name}.conf"
    cat > "${conf}" <<EOF
# Ours optimization benchmark — 2^${LOG} = ${N} x ${N}
server_name = server
server_ip = 127.0.0.1
server_port = ${SERVER_PORT}

client_name = client
client_ip = 127.0.0.1
client_port = ${CLIENT_PORT}

append_string = ${append}

save_path = ${SAVE_PATH}

pto_type = PSU
element_byte_length = 16
server_log_set_size = ${LOG}
client_log_set_size = ${LOG}
parallel = true
skip_warmup = true

psu_pto_name = Ours
small_ec_item_bit_length = 128
small_ec_log_stats = true
small_ec_w_compare_mode = ${wmode}
small_ec_fingerprint_bits = ${fpbits}
small_ec_fingerprint_method = ${fpmethod}
small_ec_async_precompute_w = ${asyncw}
small_ec_async_precompute_threshold = 1024
small_ec_parallel_ec = ${parallelec}
small_ec_parallel_threshold = 1024
small_ec_statistical_security_bits = 40
EOF
    echo "${conf}"
  }

  _small_ec_wait_for_server() {
    local port="$1" log="$2" server_pid="$3" ready=0 _
    for _ in $(seq 1 180); do
      if ! kill -0 "${server_pid}" 2>/dev/null; then
        echo "Server exited during startup. Last lines of ${log}:" >&2
        tail -15 "${log}" >&2 || true
        return 1
      fi
      if grep -qE 'BindException|Address already in use' "${log}" 2>/dev/null; then
        kill "${server_pid}" 2>/dev/null || true
        return 1
      fi
      if command -v nc >/dev/null 2>&1; then
        if nc -z 127.0.0.1 "${port}" 2>/dev/null; then ready=1; break; fi
      else
        sleep 3
        ready=1
        break
      fi
      sleep 1
    done
    [[ "${ready}" -eq 1 ]]
  }

  _small_ec_run_one_case() {
    local conf="$1" desc="$2"
    local server_port client_port server_log server_pid local_failures=0
    local pto pid out

    name="$(basename "${conf}" .conf)"
    echo "======== ${APPEND_PREFIX}/${name}: ${desc} ========" | tee -a "${LOG_FILE}"

    if [[ "${force}" -eq 1 ]]; then
      remove_incomplete_fair_outputs_for_conf "${conf}" "${TEMP_DIR}" 2>/dev/null || true
      pto="$(read_fair_pto_name "${conf}")"
      for pid in 0 1; do
        out="$(fair_bench_output_path "${conf}" "${pto}" "${pid}" "${TEMP_DIR}")"
        rm -f "${out}"
      done
    elif has_complete_fair_output_for_conf "${conf}" "${TEMP_DIR}"; then
      echo "SKIP ${name} (complete output exists; use --force to re-run)" | tee -a "${LOG_FILE}"
      return 0
    fi

    server_port="$(fair_bench_read_server_port "${conf}" "${SERVER_PORT}")"
    client_port="$(fair_bench_read_client_port "${conf}" "${CLIENT_PORT}")"

    if ! free_fair_bench_ports "${server_port}" "${client_port}"; then
      echo "FAILED ${name} (ports ${server_port}/${client_port} busy after cleanup)" | tee -a "${LOG_FILE}"
      return 1
    fi

    server_log="${BENCH_RUN_DIR}/logs/${APPEND_PREFIX}_${name}_server.log"
    local resolved_conf
    resolved_conf="$(psu_fair_conf_with_save_path "${conf}")"
    "${JAVA_BIN}" "${JAVA_OPTS[@]}" -cp "${DRIVER_JAR}" \
      edu.alibaba.mpc4j.s2pc.pso.main.PsoMain "${resolved_conf}" server >"${server_log}" 2>&1 &
    server_pid=$!

    _small_ec_cleanup() {
      if kill -0 "${server_pid}" 2>/dev/null; then
        kill "${server_pid}" 2>/dev/null || true
        wait "${server_pid}" 2>/dev/null || true
      fi
      free_fair_bench_ports "${server_port}" "${client_port}" || true
    }
    trap _small_ec_cleanup EXIT INT TERM

    if ! _small_ec_wait_for_server "${server_port}" "${server_log}" "${server_pid}"; then
      echo "FAILED ${name} (server did not start)" | tee -a "${LOG_FILE}"
      _small_ec_cleanup
      trap - EXIT INT TERM
      return 1
    fi

    local protocol_timeout bench_watchdog client_status=0
    protocol_timeout="$(psu_fair_protocol_timeout_seconds)"
    start_timeout_watchdog "${protocol_timeout}" "${server_pid}" "${name}"
    bench_watchdog="${LAST_TIMEOUT_WATCHDOG_PID}"

    set +o pipefail
    run_cmd_with_timeout "${protocol_timeout}" \
        "${JAVA_BIN}" "${JAVA_OPTS[@]}" -cp "${DRIVER_JAR}" \
        edu.alibaba.mpc4j.s2pc.pso.main.PsoMain "${resolved_conf}" client 2>&1 | tee -a "${LOG_FILE}"
    client_status=${PIPESTATUS[0]}
    set -o pipefail

    cancel_timeout_watchdog "${bench_watchdog}"

    if [[ "${client_status}" -eq 0 ]] || has_complete_fair_output_for_conf "${conf}" "${TEMP_DIR}"; then
      if [[ "${client_status}" -ne 0 ]]; then
        echo "OK ${name} (complete output; client exit ${client_status})" | tee -a "${LOG_FILE}"
      else
        echo "OK ${name}" | tee -a "${LOG_FILE}"
      fi
    else
      echo "FAILED ${name} (client or timeout)" | tee -a "${LOG_FILE}"
      tail -15 "${server_log}" >&2 || true
      local_failures=1
    fi
    _small_ec_cleanup
    trap - EXIT INT TERM
    return "${local_failures}"
  }

  mkdir -p "${CONF_DIR}" "${TEMP_DIR}" "${BENCH_RUN_DIR}/logs"
  : > "${LOG_FILE}"

  echo "=== Ours mode benchmark at 2^${LOG} = ${N} x ${N} ==="
  echo "  Conf dir: ${CONF_DIR}"
  echo "  Log: ${LOG_FILE}"
  echo "  Force: ${force}"
  echo ""

  NO_BUILD="${no_build}"
  DRIVER_JAR="$(ensure_psu_driver_jar)" || return 1
  echo "Driver: ${DRIVER_JAR}" | tee -a "${LOG_FILE}"

  CONF_FILES=()
  CASE_DESCS=()
  local row wmode fpbits fpmethod asyncw parallelec
  for row in "${CASES[@]}"; do
    IFS='|' read -r name wmode fpbits fpmethod asyncw parallelec desc <<< "${row}"
    conf="$(_small_ec_write_conf "${name}" "${wmode}" "${fpbits}" "${fpmethod}" "${asyncw}" "${parallelec}")"
    CONF_FILES+=("${conf}")
    CASE_DESCS+=("${desc}")
  done
  echo "Generated ${#CONF_FILES[@]} config(s) under ${CONF_DIR}" | tee -a "${LOG_FILE}"
  echo "" | tee -a "${LOG_FILE}"

  if ! free_fair_bench_ports "${SERVER_PORT}" "${CLIENT_PORT}"; then
    echo "FAILED: SMALL_EC ports ${SERVER_PORT}/${CLIENT_PORT} busy at suite start" | tee -a "${LOG_FILE}" >&2
    return 1
  fi

  for i in "${!CONF_FILES[@]}"; do
    conf="${CONF_FILES[$i]}"
    desc="${CASE_DESCS[$i]}"
    if ! _small_ec_run_one_case "${conf}" "${desc}"; then
      failures=$((failures + 1))
    fi
    echo "" | tee -a "${LOG_FILE}"
  done

  if command -v python3 >/dev/null 2>&1; then
    echo "=== Summarizing ${APPEND_PREFIX} outputs ===" | tee -a "${LOG_FILE}"
    if ! python3 "${SCRIPT_DIR}/summarize_psu_fair_outputs.py" \
      --temp-dir "${TEMP_DIR}" \
      --only-append "${APPEND_PREFIX}" \
      --out "${SUMMARY}" 2>>"${LOG_FILE}"; then
      failures=$((failures + 1))
    else
      echo "Summary: ${SUMMARY}" | tee -a "${LOG_FILE}"
      python3 "${SCRIPT_DIR}/short_summarize_psu_fair_outputs.py" \
        "${SUMMARY}" "${SHORT_SUMMARY}" 2>>"${LOG_FILE}" || true
      echo "Short summary (ranked): ${SHORT_SUMMARY}" | tee -a "${LOG_FILE}"
    fi
  fi

  if [[ "${failures}" -gt 0 ]]; then
    echo "SMALL_EC suite completed with ${failures} failure(s)" | tee -a "${LOG_FILE}" >&2
    return 1
  fi
  echo "SMALL_EC suite OK $(date -Iseconds)" | tee -a "${LOG_FILE}"
  return 0
}
