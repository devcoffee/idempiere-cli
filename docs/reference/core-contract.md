# Core Contract (v1)

This document defines the stable contract of `idempiere-cli` core behavior.

It is the reference for:
- local automation scripts
- AI agents orchestrating the CLI
- release compatibility checks

## Contract Scope

Included in contract:
- default/core build (`./mvnw clean package`)
- deterministic template-first workflows
- command behavior, exit-code semantics, and JSON output shape for core commands

Explicitly out of core contract:
- embedded AI generation stack
- `skills` source resolution behavior tied to experimental providers
- any internal-only experimental feature

Release targeting:
- official releases publish core artifacts only

## Stability Promise

For stable releases:
- core command names and primary flags should remain backward compatible
- documented core exit-code semantics should remain stable
- backward-incompatible core changes require explicit release-note callout

Experimental behavior can evolve independently, but must not break core flows.

## Core Commands Covered

Environment and setup:
- `doctor`
- `setup-dev-env`

Scaffolding:
- `init`
- `add` (template/deterministic path)

Quality and analysis:
- `validate`
- `deps`
- `diff-schema`
- `info`

Delivery:
- `build`
- `deploy`
- `package`
- `migrate`
- `dist`

Utilities:
- `config` (core keys/flows)
- `import-workspace`
- `generate-completion`
- `upgrade`

## Exit Code Contract (Core)

Core commands use this semantic convention:
- `0`: success
- `1`: validation/usage error
- `2`: I/O or command execution error
- `3`: project/state error (for example: not in plugin directory, missing expected structure)

Notes:
- Exact command-level semantics are validated by tests and command docs.
- `validate --strict` is a documented command-specific exception: it returns `2` when warnings are treated as failures (strict-warning failure), not an I/O failure.
- Scripts should prefer `!= 0` for generic failure handling unless command-specific branching is required.

## JSON Contract (Core)

When `--json` is supported:
- JSON must be emitted on `stdout`
- failures still return non-zero exit codes according to command semantics
- text diagnostics and troubleshooting stay in non-JSON modes

## Deterministic Generation Contract

Core/default build guarantees:
- `init` and `add` work without AI providers
- templates are sufficient to scaffold compilable project structure
- failures in experimental AI paths must not block deterministic template generation

## Release Gate

A release is core-compatible only if:
1. core smoke passes using only released core paths
2. core exit-code tests pass
3. command docs match implemented core behavior
4. this contract remains valid or is versioned with explicit change notes

Recommended command:

```bash
./scripts/run-core-contract-check.sh
```

Implementation note:
- contract tests are marked with JUnit tag `core-contract`
- the script runs this tag set by default
- this tag set includes command surface/exit-code checks plus deterministic scaffolding checks (`init`, template-based `add`) and `validate --json` behavior
