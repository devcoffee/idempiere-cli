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
#   RUN_SETUP_DEV_ENV_DRY_RUN 1|0 include setup-dev-env dry-run step (default: 1)
#   RUN_SETUP_DEV_ENV_FULL    1|0 include setup-dev-env full run step (default: 0)
#   SETUP_DEV_ENV_ARGS        Common args for setup-dev-env smoke (default: docker+rest profile)
#   SETUP_DB_PASS             DB user password for setup-dev-env (default: random per run)
#   SETUP_DB_ADMIN_PASS       DB admin password for setup-dev-env (default: random per run)
#   SETUP_SOURCE_DIR          Source directory used by setup-dev-env step
#   SETUP_ECLIPSE_DIR         Eclipse directory used by setup-dev-env step

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
RUN_SETUP_DEV_ENV_DRY_RUN="${RUN_SETUP_DEV_ENV_DRY_RUN:-1}"
RUN_SETUP_DEV_ENV_FULL="${RUN_SETUP_DEV_ENV_FULL:-0}"
SETUP_DEV_ENV_ARGS="${SETUP_DEV_ENV_ARGS:---with-docker --include-rest}"

SMOKE_ROOT="${1:-/tmp/idempiere-cli-smoke-$(date +%Y%m%d-%H%M%S)}"
WORK_DIR="${SMOKE_ROOT}/work"
REPORT_DIR="${SMOKE_ROOT}/reports"
PLUGIN_ROOT="${WORK_DIR}/${PROJECT_NAME}"
BASE_MODULE="${PLUGIN_ROOT}/${PLUGIN_ID}.base"
SETUP_SOURCE_DIR="${SETUP_SOURCE_DIR:-${WORK_DIR}/setup-dev-env/idempiere}"
SETUP_ECLIPSE_DIR="${SETUP_ECLIPSE_DIR:-${WORK_DIR}/setup-dev-env/eclipse}"
SUMMARY_FILE="${REPORT_DIR}/summary.tsv"
INDEX_FILE="${REPORT_DIR}/index.md"
LAST_STEP_RC=0
CLI_MODE_EFFECTIVE=""
SETUP_DEV_ENV_EFFECTIVE_ARGS=""

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
  LAST_STEP_RC=$rc

  printf "%s\t%s\t%s\n" "${step}" "${rc}" "${log_file}" >> "${SUMMARY_FILE}"

  if [ "${rc}" -eq 0 ]; then
    echo "[PASS] ${step} (exit=${rc})"
    {
      echo "- [PASS] ${step} (exit=${rc})"
      echo "  - log: \`${log_file}\`"
    } >> "${INDEX_FILE}"
  else
    echo "[FAIL] ${step} (exit=${rc})"
    {
      echo "- [FAIL] ${step} (exit=${rc})"
      echo "  - log: \`${log_file}\`"
    } >> "${INDEX_FILE}"
  fi
}

resolve_cli_mode

printf "step\texit_code\tlog_file\n" > "${SUMMARY_FILE}"

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
  echo "- Plugin ID: \`${PLUGIN_ID}\`"
  echo "- Project name: \`${PROJECT_NAME}\`"
  echo "- setup-dev-env dry-run step: \`${RUN_SETUP_DEV_ENV_DRY_RUN}\`"
  echo "- setup-dev-env full step: \`${RUN_SETUP_DEV_ENV_FULL}\`"
  echo
  echo "## Steps"
} > "${INDEX_FILE}"

if [ "${CLI_MODE_EFFECTIVE}" = "jar" ]; then
  run_step "Build CLI jar" \
    "build_cli_jar"

  if [ "${LAST_STEP_RC}" -ne 0 ]; then
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

run_step "Add callout with AI prompt" \
  "run_cli add callout --to=\"${BASE_MODULE}\" --name=SetBPDescription --prompt=\"${PROMPT_TEXT}\""

run_step "Add process with AI prompt" \
  "run_cli add process --to=\"${BASE_MODULE}\" --name=SyncPartnerName --prompt=\"${PROMPT_TEXT}\""

run_step "Build with plugin mvnw" \
  "( cd \"${PLUGIN_ROOT}\" && ./mvnw -q -DskipTests verify )"

run_step "Build command at root" \
  "run_cli build --dir=\"${PLUGIN_ROOT}\""

run_step "Package zip" \
  "run_cli package --dir=\"${PLUGIN_ROOT}\" --format=zip --output=dist-smoke"

run_step "Package p2" \
  "run_cli package --dir=\"${PLUGIN_ROOT}\" --format=p2 --output=dist-smoke"

run_step "Latest session log markers" \
  "latest_session_log_markers"

if ls "${HOME}/.idempiere-cli/logs/session-"*.log >/dev/null 2>&1; then
  cp "$(ls -t "${HOME}/.idempiere-cli/logs/session-"*.log | head -n 3)" "${REPORT_DIR}/" 2>/dev/null || true
fi

total_steps=$(( $(wc -l < "${SUMMARY_FILE}") - 1 ))
passed_steps=$(awk -F'\t' 'NR>1 && $2==0 {c++} END {print c+0}' "${SUMMARY_FILE}")
failed_steps=$(( total_steps - passed_steps ))

{
  echo
  echo "## Summary"
  echo
  echo "- Total steps: ${total_steps}"
  echo "- Passed: ${passed_steps}"
  echo "- Failed: ${failed_steps}"
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
