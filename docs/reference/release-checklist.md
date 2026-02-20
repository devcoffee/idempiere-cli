# Release Checklist

Use this before tagging a new version.

## 1. Build and Unit Test

```bash
./mvnw clean test
./mvnw -DskipTests package
```

## 2. Run Pre-Release Smoke

Deterministic core + AI phase:

```bash
CLI_MODE=jar SMOKE_FAIL_ON_REGRESSION=1 ./scripts/run-cli-prebuild-smoke.sh
```

Full environment path (heavy):

```bash
CLI_MODE=jar RUN_SETUP_DEV_ENV_FULL=1 SMOKE_FAIL_ON_REGRESSION=1 ./scripts/run-cli-prebuild-smoke.sh
```

## 3. Review Smoke Artifacts

Check:
- `reports/index.md`
- `reports/summary.tsv`
- unexpected `FAIL` steps (must be zero)

`XFAIL` can be acceptable only when explicitly expected.

## 4. Validate Docs and Command Surface

Confirm:
- `README.md` quick-start commands still work
- `docs/reference/commands.md` matches current CLI
- `docs/reference/configuration.md` matches config behavior

## 5. Tag and Publish

```bash
git tag vX.Y.Z
git push --tags
```

Tag push triggers release automation.
