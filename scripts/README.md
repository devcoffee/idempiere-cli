# Scripts

Utility scripts for local development and pre-release validation.

## `run-cli-prebuild-smoke.sh`

Runs a practical smoke suite for `idempiere-cli`, captures stdout/stderr for each step, and generates a report bundle.

### What it tests

- CLI startup and help
- `doctor` (text + json)
- `setup-dev-env` profile validation (`--dry-run`, docker+rest args)
- `init` (non-interactive, multi-module)
- `info`, `validate`, `deps`, plugin `doctor`
- `add` flow with AI prompt
- plugin build (`mvnw verify`)
- `build`, `package` (`zip` and `p2`) and `deploy` from multi-module project root
- session log markers (`ai-prompt`, `ai-response`, parse diagnostics)
- full command/subcommand matrix via `--help` (dynamic discovery, executed after core flow)

Build/package validation runs before AI prompt steps, so compile/package gates stay deterministic.

### Output artifacts

All artifacts are written under the smoke root (default: `/tmp/idempiere-cli-smoke-<timestamp>`):

- `reports/summary.tsv`: step, exit code, log path
- `reports/summary.tsv`: step, raw/effective exit code, outcome (`PASS`/`XFAIL`/`FAIL`), expected flag, log path
- `reports/index.md`: human-readable report index
- `reports/*.log`: one log file per step
- `reports.tar.gz`: compressed report package

### Usage

From repository root:

```bash
./scripts/run-cli-prebuild-smoke.sh
```

Custom output root:

```bash
./scripts/run-cli-prebuild-smoke.sh /tmp/my-smoke-run
```

### Execution modes

The script supports 3 CLI execution modes via `CLI_MODE`:

- `jar` (default): builds local jar and runs `java -jar`
- `binary`: runs installed CLI binary from `PATH`
- `auto`: prefers binary; falls back to jar mode

Examples:

```bash
CLI_MODE=jar ./scripts/run-cli-prebuild-smoke.sh
CLI_MODE=binary CLI_BIN=idempiere-cli ./scripts/run-cli-prebuild-smoke.sh
CLI_MODE=auto ./scripts/run-cli-prebuild-smoke.sh
```

### Optional environment variables

- `CLI_MODE` default: `jar`
- `CLI_BIN` default: `idempiere-cli`
- `JAR_PATH` default: `<repo>/target/quarkus-app/quarkus-run.jar`
- `MAVEN_WRAPPER` default: `<repo>/mvnw`
- `PLUGIN_ID` default: `org.smoke.demo`
- `PROJECT_NAME` default: `smoke-demo`
- `PROMPT_TEXT` default: predefined callout prompt
- `RUN_COMMAND_MATRIX` default: `1` (validates all discovered command/subcommand combinations with `--help`)
- `RUN_SETUP_DEV_ENV_DRY_RUN` default: `1` (adds `setup-dev-env --dry-run` smoke step)
- `RUN_SETUP_DEV_ENV_FULL` default: `0` (runs real `setup-dev-env`, heavy and stateful)
- `SETUP_DEV_ENV_ARGS` default: `--with-docker --include-rest`
- `SETUP_DB_PASS` default: random per run (24 chars, alphanumeric)
- `SETUP_DB_ADMIN_PASS` default: random per run (24 chars, alphanumeric)
- `SETUP_SOURCE_DIR` default: `<smoke-root>/work/idempiere`
- `SETUP_ECLIPSE_DIR` default: `<smoke-root>/work/eclipse`
- `SMOKE_MAVEN_REPO` default: `<smoke-root>/work/.m2-repo` (isolates Maven cache/locks per run)
- `DEPLOY_TARGET_HOME` default: `<smoke-root>/work/idempiere-home` (fake target with `plugins/` for deploy smoke)
- `EXPECTED_FAILURE_STEPS` default: empty; semicolon-separated step names treated as expected failure (`XFAIL`)
- `SMOKE_FAIL_ON_REGRESSION` default: `0`; when `1`, script exits non-zero if any unexpected failure (`FAIL`) occurs

### Setup-dev-env examples

Dry-run only (default behavior):

```bash
CLI_MODE=jar ./scripts/run-cli-prebuild-smoke.sh
```

Run full setup-dev-env step too:

```bash
CLI_MODE=jar RUN_SETUP_DEV_ENV_FULL=1 ./scripts/run-cli-prebuild-smoke.sh
```

Run a faster smoke without command matrix:

```bash
CLI_MODE=jar RUN_COMMAND_MATRIX=0 ./scripts/run-cli-prebuild-smoke.sh
```

Mark known issues as expected failures (without hiding real regressions):

```bash
EXPECTED_FAILURE_STEPS="Build with plugin mvnw;Build command at project root;Package zip;Package p2;Deploy copy at project root" \
CLI_MODE=jar ./scripts/run-cli-prebuild-smoke.sh
```

### Notes

- This script is a **developer smoke harness**. It does not replace CI.
- The script executes in a natural developer order first, then runs the full command matrix as a coverage pass.
- Core flow is designed to be deterministic; failures should indicate an actionable regression or environment issue.
- First run with isolated Maven repo may download dependencies again; expect longer runtime and required network access.
- Use `reports/index.md` + step logs for triage.
- The command matrix uses `--help` only (safe, no side effects). It does not run `doctor --fix` or `doctor --fix-optional`.
- In matrix mode, some commands may return non-zero for `--help` due parser behavior; if a valid `Usage: idempiere-cli <command...>` is resolved, the step is treated as valid.
