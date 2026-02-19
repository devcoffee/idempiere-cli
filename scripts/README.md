# Scripts

Utility scripts for local development and pre-release validation.

## `run-cli-prebuild-smoke.sh`

Runs a practical smoke suite for `idempiere-cli`, captures stdout/stderr for each step, and generates a report bundle.

### What it tests

- CLI startup and help
- `doctor` (text + json)
- `init` (non-interactive, multi-module)
- `info`, `validate`, `deps`, plugin `doctor`
- `add` flow with AI prompt
- plugin build (`mvnw verify`)
- `build`, `package` (`zip` and `p2`)
- session log markers (`ai-prompt`, `ai-response`, parse diagnostics)

### Output artifacts

All artifacts are written under the smoke root (default: `/tmp/idempiere-cli-smoke-<timestamp>`):

- `reports/summary.tsv`: step, exit code, log path
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

### Notes

- This script is a **developer smoke harness**. It does not replace CI.
- Some steps can fail depending on machine state (tooling, network, AI config, local env).
- Use `reports/index.md` + step logs for triage.
