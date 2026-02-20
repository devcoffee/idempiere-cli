#!/usr/bin/env bash
# Runs a practical pre-build smoke suite for idempiere-cli and stores all outputs.
#
# Usage:
#   scripts/run-cli-prebuild-smoke.sh [smoke_root]
#
# Optional env vars:
#   CLI_MODE     jar|binary|auto (default: jar)
#   CLI_BIN      Path or name of CLI binary when CLI_MODE=binary/auto (default: idempiere-cli)
#   JAR_PATH     Path to CLI runnable jar (default: <repo>/target/quarkus-app/quarkus-run.jar)
#   MAVEN_WRAPPER Path to maven wrapper script (default: <repo>/mvnw)
#   PLUGIN_ID    Plugin id used for scaffold smoke (default: org.smoke.demo)
#   PROJECT_NAME Project folder name (default: smoke-demo)
#   PROMPT_TEXT  Prompt used in add command (default: predefined sentence)
#   RUN_COMMAND_MATRIX 1|0 validate full command/subcommand tree with --help (default: 1)
#   RUN_SETUP_DEV_ENV_DRY_RUN 1|0 include setup-dev-env dry-run step (default: 1)
#   RUN_SETUP_DEV_ENV_FULL    1|0 include setup-dev-env full run step (default: 0)
#   SETUP_DEV_ENV_ARGS        Common args for setup-dev-env smoke (default: docker+rest profile)
#   SETUP_DB_PASS             DB user password for setup-dev-env (default: random per run)
#   SETUP_DB_ADMIN_PASS       DB admin password for setup-dev-env (default: random per run)
#   SETUP_SOURCE_DIR          Source directory used by setup-dev-env step
#   SETUP_ECLIPSE_DIR         Eclipse directory used by setup-dev-env step
#   SMOKE_MAVEN_REPO          Dedicated Maven local repository override for smoke run (default: <smoke-root>/work/.m2-repo)
#   DEPLOY_TARGET_HOME        Fake iDempiere home for deploy smoke step (default: <smoke-root>/work/idempiere-home)
#   EXPECTED_FAILURE_STEPS    Optional semicolon-separated step names treated as expected failures (XFAIL)
#   SMOKE_FAIL_ON_REGRESSION  1|0 exit non-zero when unexpected failures exist (default: 0)

set -u
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

CLI_MODE="${CLI_MODE:-jar}"
CLI_BIN="${CLI_BIN:-idempiere-cli}"
MAVEN_WRAPPER="${MAVEN_WRAPPER:-${PROJECT_ROOT}/mvnw}"
JAR_PATH="${JAR_PATH:-${PROJECT_ROOT}/target/quarkus-app/quarkus-run.jar}"
PLUGIN_ID="${PLUGIN_ID:-org.smoke.demo}"
PROJECT_NAME="${PROJECT_NAME:-smoke-demo}"
PROMPT_TEXT="${PROMPT_TEXT:-Define Description as Name + Name2 when leaving those fields.}"
RUN_COMMAND_MATRIX="${RUN_COMMAND_MATRIX:-1}"
RUN_SETUP_DEV_ENV_DRY_RUN="${RUN_SETUP_DEV_ENV_DRY_RUN:-1}"
RUN_SETUP_DEV_ENV_FULL="${RUN_SETUP_DEV_ENV_FULL:-0}"
SETUP_DEV_ENV_ARGS="${SETUP_DEV_ENV_ARGS:---with-docker --include-rest}"

SMOKE_ROOT="${1:-/tmp/idempiere-cli-smoke-$(date +%Y%m%d-%H%M%S)}"
WORK_DIR="${SMOKE_ROOT}/work"
REPORT_DIR="${SMOKE_ROOT}/reports"
PLUGIN_ROOT="${WORK_DIR}/${PROJECT_NAME}"
BASE_MODULE="${PLUGIN_ROOT}/${PLUGIN_ID}.base"
SETUP_SOURCE_DIR="${SETUP_SOURCE_DIR:-${WORK_DIR}/idempiere}"
SETUP_ECLIPSE_DIR="${SETUP_ECLIPSE_DIR:-${WORK_DIR}/eclipse}"
SMOKE_MAVEN_REPO="${SMOKE_MAVEN_REPO:-${WORK_DIR}/.m2-repo}"
DEPLOY_TARGET_HOME="${DEPLOY_TARGET_HOME:-${WORK_DIR}/idempiere-home}"
EXPECTED_FAILURE_STEPS="${EXPECTED_FAILURE_STEPS:-}"
SMOKE_FAIL_ON_REGRESSION="${SMOKE_FAIL_ON_REGRESSION:-0}"
MAVEN_REPO_ARG=""
SUMMARY_FILE="${REPORT_DIR}/summary.tsv"
INDEX_FILE="${REPORT_DIR}/index.md"
LAST_STEP_RC=0
LAST_STEP_EFFECTIVE_RC=0
LAST_STEP_OUTCOME=""
CLI_MODE_EFFECTIVE=""
SETUP_DEV_ENV_EFFECTIVE_ARGS=""
COMMAND_MATRIX_PATHS=()

generate_password() {
  LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24
}

SETUP_DB_PASS="${SETUP_DB_PASS:-$(generate_password)}"
SETUP_DB_ADMIN_PASS="${SETUP_DB_ADMIN_PASS:-$(generate_password)}"

SETUP_DEV_ENV_EFFECTIVE_ARGS="${SETUP_DEV_ENV_ARGS}"
case " ${SETUP_DEV_ENV_EFFECTIVE_ARGS} " in
  *" --db-pass="*) ;;
  *) SETUP_DEV_ENV_EFFECTIVE_ARGS="${SETUP_DEV_ENV_EFFECTIVE_ARGS} --db-pass=\"\$SETUP_DB_PASS\"" ;;
esac
case " ${SETUP_DEV_ENV_EFFECTIVE_ARGS} " in
  *" --db-admin-pass="*) ;;
  *) SETUP_DEV_ENV_EFFECTIVE_ARGS="${SETUP_DEV_ENV_EFFECTIVE_ARGS} --db-admin-pass=\"\$SETUP_DB_ADMIN_PASS\"" ;;
esac

mkdir -p "${WORK_DIR}" "${REPORT_DIR}"

if [ -n "${SMOKE_MAVEN_REPO}" ]; then
  mkdir -p "${SMOKE_MAVEN_REPO}"
  MAVEN_REPO_ARG="-Dmaven.repo.local=${SMOKE_MAVEN_REPO}"
fi

slugify() {
  printf "%s" "$1" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '_' | sed 's/^_//; s/_$//'
}

resolve_cli_mode() {
  case "${CLI_MODE}" in
    jar)
      CLI_MODE_EFFECTIVE="jar"
      ;;
    binary)
      CLI_MODE_EFFECTIVE="binary"
      ;;
    auto)
      if command -v "${CLI_BIN}" >/dev/null 2>&1; then
        CLI_MODE_EFFECTIVE="binary"
      else
        CLI_MODE_EFFECTIVE="jar"
      fi
      ;;
    *)
      echo "ERROR: Invalid CLI_MODE='${CLI_MODE}'. Use: jar, binary, auto."
      exit 1
      ;;
  esac

  if [ "${CLI_MODE_EFFECTIVE}" = "binary" ]; then
    if ! command -v "${CLI_BIN}" >/dev/null 2>&1; then
      echo "ERROR: CLI not found in PATH: ${CLI_BIN}"
      exit 1
    fi
  else
    if ! command -v java >/dev/null 2>&1; then
      echo "ERROR: java not found in PATH (required for jar mode)."
      exit 1
    fi
    if [ ! -f "${MAVEN_WRAPPER}" ]; then
      echo "ERROR: Maven wrapper not found at ${MAVEN_WRAPPER}"
      exit 1
    fi
  fi
}

build_cli_jar() {
  echo "Building CLI jar from ${PROJECT_ROOT} ..."
  (cd "${PROJECT_ROOT}" && bash "${MAVEN_WRAPPER}" -q -DskipTests package) || return $?
  if [ ! -f "${JAR_PATH}" ]; then
    echo "ERROR: Runnable jar not found at ${JAR_PATH}"
    return 1
  fi
  echo "CLI jar ready: ${JAR_PATH}"
  return 0
}

run_cli() {
  if [ "${CLI_MODE_EFFECTIVE}" = "jar" ]; then
    java -jar "${JAR_PATH}" "$@"
  else
    "${CLI_BIN}" "$@"
  fi
}

latest_session_log_markers() {
  local latest=""
  latest=$(ls -t "${HOME}"/.idempiere-cli/logs/session-*.log 2>/dev/null | head -n1 || true)
  if [ -z "${latest}" ]; then
    echo "No session logs found"
    return 0
  fi

  echo "LATEST_LOG=${latest}"
  if command -v rg >/dev/null 2>&1; then
    rg -n "Command: add|ai-prompt|ai-response|ai-response-raw|AI parse failed|Failed to parse AI response" "${latest}" || true
  else
    grep -nE "Command: add|ai-prompt|ai-response|ai-response-raw|AI parse failed|Failed to parse AI response" "${latest}" || true
  fi
}

build_multimodule_verify() {
  local -a mvn_args=("-q" "-DskipTests")
  local output=""
  local rc=0
  local attempt=1
  if [ -n "${MAVEN_REPO_ARG}" ]; then
    mvn_args+=("${MAVEN_REPO_ARG}")
  fi
  mvn_args+=("verify")

  if [ -x "./mvnw" ]; then
    MVNW_REPOURL="https://repo.maven.apache.org/maven2" ./mvnw "${mvn_args[@]}"
    local wrapper_rc=$?
    if [ "${wrapper_rc}" -eq 0 ]; then
      return 0
    fi
    echo "mvnw failed (exit=${wrapper_rc}); trying system mvn fallback..."
  fi

  if command -v mvn >/dev/null 2>&1; then
    while [ "${attempt}" -le 2 ]; do
      output="$(mvn "${mvn_args[@]}" 2>&1)"
      rc=$?
      printf "%s\n" "${output}"

      if [ "${rc}" -eq 0 ]; then
        return 0
      fi

      if printf "%s\n" "${output}" | grep -q "Could not acquire lock on file"; then
        clear_stale_tycho_lock
        echo "Transient Maven lock timeout detected (attempt ${attempt}); retrying in 5s..."
        sleep 5
        attempt=$((attempt + 1))
        continue
      fi

      return "${rc}"
    done

    return "${rc}"
  fi

  echo "mvnw failed and system mvn is not available."
  return 1
}

resolve_maven_repo_path() {
  if [ -n "${SMOKE_MAVEN_REPO}" ]; then
    printf "%s" "${SMOKE_MAVEN_REPO}"
  else
    printf "%s/.m2/repository" "${HOME}"
  fi
}

clear_stale_tycho_lock() {
  local repo_path
  local lock_file
  repo_path="$(resolve_maven_repo_path)"
  lock_file="${repo_path}/.meta/p2-artifacts.properties.tycholock"

  if [ ! -f "${lock_file}" ]; then
    return 0
  fi

  if command -v pgrep >/dev/null 2>&1; then
    if pgrep -f "org.codehaus.plexus.classworlds.launcher.Launcher" >/dev/null 2>&1; then
      echo "Maven process detected; preserving lock file: ${lock_file}"
      return 0
    fi
  fi

  rm -f "${lock_file}" 2>/dev/null || true
  echo "Removed stale Tycho lock: ${lock_file}"
}

build_base_module_with_cli() {
  local module_dir="$1"
  local rc=0
  local attempt=1
  local output=""

  while [ "${attempt}" -le 2 ]; do
    if [ -n "${MAVEN_REPO_ARG}" ]; then
      output="$(run_cli build --dir="${module_dir}" --disable-p2-mirrors --maven-args="${MAVEN_REPO_ARG}" 2>&1)"
    else
      output="$(run_cli build --dir="${module_dir}" --disable-p2-mirrors 2>&1)"
    fi
    rc=$?
    printf "%s\n" "${output}"

    if [ "${rc}" -eq 0 ]; then
      return 0
    fi

    if printf "%s\n" "${output}" | grep -q "Could not acquire lock on file"; then
      clear_stale_tycho_lock
      echo "Transient Maven lock timeout detected (attempt ${attempt}); retrying in 5s..."
      sleep 5
      attempt=$((attempt + 1))
      continue
    fi

    return "${rc}"
  done

  return "${rc}"
}

list_subcommands_for_path() {
  local help_output=""
  if ! help_output="$(run_cli "$@" --help 2>/dev/null)"; then
    return 0
  fi

  printf '%s\n' "${help_output}" | awk '
    /^Commands:/ {in_commands=1; next}
    in_commands && /^[^[:space:]]/ {in_commands=0}
    in_commands && $0 ~ /^[[:space:]][[:space:]][a-z0-9][a-z0-9-]*[[:space:]][[:space:]]/ {print $1}
  '
}

collect_command_matrix_paths() {
  local -a queue=()
  local -a parts=()
  local -a children=()
  local current=""
  local child=""

  COMMAND_MATRIX_PATHS=()
  mapfile -t queue < <(list_subcommands_for_path)

  while [ ${#queue[@]} -gt 0 ]; do
    current="${queue[0]}"
    queue=("${queue[@]:1}")
    COMMAND_MATRIX_PATHS+=("${current}")

    IFS=' ' read -r -a parts <<< "${current}"
    mapfile -t children < <(list_subcommands_for_path "${parts[@]}")
    for child in "${children[@]}"; do
      queue+=("${current} ${child}")
    done
  done
}

print_command_matrix_paths() {
  echo "Discovered ${#COMMAND_MATRIX_PATHS[@]} command/subcommand combinations:"
  if [ ${#COMMAND_MATRIX_PATHS[@]} -eq 0 ]; then
    return 1
  fi
  printf '  - %s\n' "${COMMAND_MATRIX_PATHS[@]}"
}

run_step() {
  local step="$1"
  shift
  local cmd="$*"
  local slug
  slug="$(slugify "${step}")"
  local log_file="${REPORT_DIR}/${slug}.log"

  {
    echo "[$(date '+%F %T')] STEP: ${step}"
    echo "\$ ${cmd}"
    echo
  } > "${log_file}"

  eval "${cmd}" >> "${log_file}" 2>&1
  local rc=$?
  record_step_result "${step}" "${rc}" "${rc}" "${log_file}"
}

is_expected_failure_step() {
  local step="$1"
  [ -z "${EXPECTED_FAILURE_STEPS}" ] && return 1

  local expected_step=""
  local OLD_IFS="${IFS}"
  IFS=';'
  for expected_step in ${EXPECTED_FAILURE_STEPS}; do
    expected_step="$(printf "%s" "${expected_step}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    if [ -n "${expected_step}" ] && [ "${expected_step}" = "${step}" ]; then
      IFS="${OLD_IFS}"
      return 0
    fi
  done
  IFS="${OLD_IFS}"
  return 1
}

record_step_result() {
  local step="$1"
  local raw_rc="$2"
  local effective_rc="$3"
  local log_file="$4"
  local outcome="FAIL"
  local expected="no"

  if [ "${effective_rc}" -eq 0 ]; then
    outcome="PASS"
  elif is_expected_failure_step "${step}"; then
    outcome="XFAIL"
    expected="yes"
  fi

  LAST_STEP_RC="${raw_rc}"
  LAST_STEP_EFFECTIVE_RC="${effective_rc}"
  LAST_STEP_OUTCOME="${outcome}"

  printf "%s\t%s\t%s\t%s\t%s\t%s\n" "${step}" "${raw_rc}" "${effective_rc}" "${outcome}" "${expected}" "${log_file}" >> "${SUMMARY_FILE}"

  if [ "${outcome}" = "PASS" ]; then
    echo "[PASS] ${step} (exit=${raw_rc})"
    {
      echo "- [PASS] ${step} (exit=${raw_rc})"
      echo "  - log: \`${log_file}\`"
    } >> "${INDEX_FILE}"
    return 0
  fi

  if [ "${outcome}" = "XFAIL" ]; then
    echo "[XFAIL] ${step} (exit=${raw_rc})"
    {
      echo "- [XFAIL] ${step} (exit=${raw_rc})"
      echo "  - expected failure: yes"
      echo "  - log: \`${log_file}\`"
    } >> "${INDEX_FILE}"
    return 0
  fi

  echo "[FAIL] ${step} (exit=${raw_rc})"
  {
    echo "- [FAIL] ${step} (exit=${raw_rc})"
    echo "  - log: \`${log_file}\`"
  } >> "${INDEX_FILE}"
  return 0
}

command_matrix_help_is_valid() {
  local raw_rc="$1"
  local path="$2"
  local log_file="$3"

  if [ "${raw_rc}" -eq 0 ]; then
    return 0
  fi

  # Some commands/subcommands return exit 2 for --help due parser behavior,
  # but still print a valid usage line for the resolved command path.
  if [ "${raw_rc}" -eq 2 ] && grep -Fq "Usage: idempiere-cli ${path}" "${log_file}"; then
    if grep -Eq "Unmatched argument|Unknown command" "${log_file}"; then
      return 1
    fi
    return 0
  fi

  return 1
}

run_command_matrix_step() {
  local path="$1"
  local step="Command help matrix: ${path}"
  local slug
  slug="$(slugify "${step}")"
  local log_file="${REPORT_DIR}/${slug}.log"
  local cmd="run_cli ${path} --help"
  local raw_rc=0
  local rc=0

  {
    echo "[$(date '+%F %T')] STEP: ${step}"
    echo "\$ ${cmd}"
    echo
  } > "${log_file}"

  eval "${cmd}" >> "${log_file}" 2>&1
  raw_rc=$?
  rc=$raw_rc

  if command_matrix_help_is_valid "${raw_rc}" "${path}" "${log_file}"; then
    rc=0
    if [ "${raw_rc}" -ne 0 ]; then
      echo >> "${log_file}"
      echo "[matrix] Accepted non-zero exit (${raw_rc}) because command path is valid and usage was resolved." >> "${log_file}"
    fi
  fi

  record_step_result "${step}" "${raw_rc}" "${rc}" "${log_file}"
}

resolve_cli_mode

printf "step\traw_exit_code\teffective_exit_code\toutcome\texpected_failure\tlog_file\n" > "${SUMMARY_FILE}"

{
  echo "# idempiere-cli Pre-build Smoke Report"
  echo
  echo "- UTC time: $(date -u '+%Y-%m-%d %H:%M:%S')"
  echo "- CLI mode (requested): \`${CLI_MODE}\`"
  echo "- CLI mode (effective): \`${CLI_MODE_EFFECTIVE}\`"
  if [ "${CLI_MODE_EFFECTIVE}" = "jar" ]; then
    echo "- CLI jar: \`${JAR_PATH}\`"
    echo "- Maven wrapper: \`${MAVEN_WRAPPER}\`"
  else
    echo "- CLI binary: \`${CLI_BIN}\`"
  fi
  echo "- Project root: \`${PROJECT_ROOT}\`"
  echo "- Smoke root: \`${SMOKE_ROOT}\`"
  echo "- Work dir: \`${WORK_DIR}\`"
  echo "- Maven local repo: \`${SMOKE_MAVEN_REPO}\`"
  echo "- Plugin ID: \`${PLUGIN_ID}\`"
  echo "- Project name: \`${PROJECT_NAME}\`"
  echo "- command matrix step: \`${RUN_COMMAND_MATRIX}\`"
  echo "- setup-dev-env dry-run step: \`${RUN_SETUP_DEV_ENV_DRY_RUN}\`"
  echo "- setup-dev-env full step: \`${RUN_SETUP_DEV_ENV_FULL}\`"
  if [ -n "${EXPECTED_FAILURE_STEPS}" ]; then
    echo "- expected failure steps: \`${EXPECTED_FAILURE_STEPS}\`"
  else
    echo "- expected failure steps: \`(none)\`"
  fi
  echo "- fail on regression: \`${SMOKE_FAIL_ON_REGRESSION}\`"
  echo
  echo "## Steps"
} > "${INDEX_FILE}"

if [ "${CLI_MODE_EFFECTIVE}" = "jar" ]; then
  run_step "Build CLI jar" \
    "build_cli_jar"

  if [ "${LAST_STEP_EFFECTIVE_RC}" -ne 0 ]; then
    {
      echo
      echo "## Abort"
      echo
      echo "- Build CLI jar failed. Smoke suite aborted."
    } >> "${INDEX_FILE}"

    echo
    echo "Smoke run aborted (failed to build CLI jar)."
    echo "Report folder: ${REPORT_DIR}"
    echo "Summary file:  ${SUMMARY_FILE}"
    echo "Index file:    ${INDEX_FILE}"
    exit 1
  fi
fi

run_step "CLI version" \
  "run_cli --version"

run_step "CLI help" \
  "run_cli --help"

run_step "Doctor text" \
  "run_cli doctor"

run_step "Doctor json" \
  "run_cli doctor --json"

if [ "${RUN_SETUP_DEV_ENV_DRY_RUN}" = "1" ]; then
  run_step "Setup-dev-env dry-run docker+rest profile" \
    "run_cli setup-dev-env --non-interactive --dry-run --source-dir=\"${SETUP_SOURCE_DIR}\" --eclipse-dir=\"${SETUP_ECLIPSE_DIR}\" ${SETUP_DEV_ENV_EFFECTIVE_ARGS}"
fi

if [ "${RUN_SETUP_DEV_ENV_FULL}" = "1" ]; then
  run_step "Setup-dev-env full docker+rest profile" \
    "run_cli setup-dev-env --non-interactive --source-dir=\"${SETUP_SOURCE_DIR}\" --eclipse-dir=\"${SETUP_ECLIPSE_DIR}\" ${SETUP_DEV_ENV_EFFECTIVE_ARGS}"
fi

run_step "Init non-interactive multi-module" \
  "( cd \"${WORK_DIR}\" && run_cli init \"${PLUGIN_ID}\" --name=\"${PROJECT_NAME}\" --no-interactive --with-callout --with-test --with-fragment --with-feature )"

run_step "Info base module text" \
  "run_cli info --dir=\"${BASE_MODULE}\""

run_step "Info base module json" \
  "run_cli info --dir=\"${BASE_MODULE}\" --json"

run_step "Validate base module" \
  "run_cli validate --strict \"${BASE_MODULE}\""

run_step "Deps base module text" \
  "run_cli deps --dir=\"${BASE_MODULE}\""

run_step "Deps base module json" \
  "run_cli deps --dir=\"${BASE_MODULE}\" --json"

run_step "Doctor plugin check" \
  "run_cli doctor --dir=\"${BASE_MODULE}\""

run_step "Build with plugin mvnw" \
  "( cd \"${PLUGIN_ROOT}\" && build_multimodule_verify )"

run_step "Build command at project root" \
  "build_base_module_with_cli \"${PLUGIN_ROOT}\""

run_step "Package zip" \
  "run_cli package --dir=\"${PLUGIN_ROOT}\" --format=zip --output=dist-smoke"

run_step "Package p2" \
  "run_cli package --dir=\"${PLUGIN_ROOT}\" --format=p2 --output=dist-smoke"

run_step "Deploy copy at project root" \
  "mkdir -p \"${DEPLOY_TARGET_HOME}/plugins\" && run_cli deploy --dir=\"${PLUGIN_ROOT}\" --target=\"${DEPLOY_TARGET_HOME}\""

run_step "Add callout with AI prompt" \
  "run_cli add callout --to=\"${BASE_MODULE}\" --name=SetBPDescription --prompt=\"${PROMPT_TEXT}\""

run_step "Add process with AI prompt" \
  "run_cli add process --to=\"${BASE_MODULE}\" --name=SyncPartnerName --prompt=\"${PROMPT_TEXT}\""

run_step "Latest session log markers" \
  "latest_session_log_markers"

if [ "${RUN_COMMAND_MATRIX}" = "1" ]; then
  run_step "Command matrix phase header" \
    "echo \"Running full command/subcommand help matrix after core developer flow...\""
  collect_command_matrix_paths
  run_step "Command matrix discovery" \
    "print_command_matrix_paths"
  for path in "${COMMAND_MATRIX_PATHS[@]}"; do
    run_command_matrix_step "${path}"
  done
fi

if ls "${HOME}/.idempiere-cli/logs/session-"*.log >/dev/null 2>&1; then
  cp "$(ls -t "${HOME}/.idempiere-cli/logs/session-"*.log | head -n 3)" "${REPORT_DIR}/" 2>/dev/null || true
fi

total_steps=$(( $(wc -l < "${SUMMARY_FILE}") - 1 ))
passed_steps=$(awk -F'\t' 'NR>1 && $4=="PASS" {c++} END {print c+0}' "${SUMMARY_FILE}")
xfailed_steps=$(awk -F'\t' 'NR>1 && $4=="XFAIL" {c++} END {print c+0}' "${SUMMARY_FILE}")
failed_steps=$(awk -F'\t' 'NR>1 && $4=="FAIL" {c++} END {print c+0}' "${SUMMARY_FILE}")

{
  echo
  echo "## Summary"
  echo
  echo "- Total steps: ${total_steps}"
  echo "- Passed: ${passed_steps}"
  echo "- Expected failures (XFAIL): ${xfailed_steps}"
  echo "- Regressions (FAIL): ${failed_steps}"
  echo
  echo "## Files"
  echo
  echo "- Summary TSV: \`${SUMMARY_FILE}\`"
  echo "- Full report index: \`${INDEX_FILE}\`"
} >> "${INDEX_FILE}"

REPORT_ARCHIVE="${SMOKE_ROOT}/reports.tar.gz"
tar -czf "${REPORT_ARCHIVE}" -C "${REPORT_DIR}" . >/dev/null 2>&1 || true

echo
echo "Smoke run finished."
echo "Report folder: ${REPORT_DIR}"
echo "Summary file:  ${SUMMARY_FILE}"
echo "Index file:    ${INDEX_FILE}"
echo "Archive:       ${REPORT_ARCHIVE}"

if [ "${SMOKE_FAIL_ON_REGRESSION}" = "1" ] && [ "${failed_steps}" -gt 0 ]; then
  echo "Unexpected failures detected: ${failed_steps}"
  exit 1
fi
