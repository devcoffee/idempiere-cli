# Pre-Release Smoke Validation

Use this before creating a release or sharing binaries.

## Goal

Run a deterministic CLI smoke flow in developer order, capture logs, and fail only on real regressions.

## 1. Run Smoke in Local Jar Mode

From repository root:

```bash
CLI_MODE=jar ./scripts/run-cli-prebuild-smoke.sh
```

This builds the current local code and executes smoke against `target/quarkus-app/quarkus-run.jar`.

## 2. Include Full Setup Flow (heavy)

If you need full environment bootstrap validation in the same run:

```bash
CLI_MODE=jar RUN_SETUP_DEV_ENV_FULL=1 ./scripts/run-cli-prebuild-smoke.sh
```

This is slower and stateful, but validates the full `setup-dev-env` path.

## 3. Keep Failures Actionable

Use expected-failure classification for known issues, then fail only on unexpected breaks:

```bash
EXPECTED_FAILURE_STEPS="Build with plugin mvnw;Build command at base module;Package zip;Package p2" \
SMOKE_FAIL_ON_REGRESSION=1 \
CLI_MODE=jar ./scripts/run-cli-prebuild-smoke.sh
```

Interpretation:
- `PASS`: command behaved as expected
- `XFAIL`: known failure, tracked but not blocking
- `FAIL`: unexpected regression (should block release)

## 4. Read Results Fast

After each run, inspect:
- `reports/index.md` (entry point)
- `reports/summary.tsv` (step + outcome)
- per-step logs in `reports/*.log`

Path is printed at the end of the run:

```text
Report folder: /tmp/idempiere-cli-smoke-<timestamp>/reports
Archive:       /tmp/idempiere-cli-smoke-<timestamp>/reports.tar.gz
```

## 5. Triage Rule

1. Fix `FAIL` first (real regressions).
2. Keep `XFAIL` list minimal and explicit.
3. Re-run until no unexpected failures remain.

## 6. Optional Command Matrix Pass

The smoke script also runs a dynamic command/subcommand `--help` matrix (safe, no side effects) after the main flow.

Keep it enabled (`RUN_COMMAND_MATRIX=1`) for release validation unless you are doing a quick local debug loop.
