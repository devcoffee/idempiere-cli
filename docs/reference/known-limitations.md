# Known Limitations

Current limitations to keep in mind.

## AI Generation

- AI output can still produce code that does not compile against your exact target platform.
- Guardrails reduce common errors, but they do not guarantee build success for every prompt.
- Prefer prompt constraints and immediate `validate --strict` + `deps` after generation.

## setup-dev-env Runtime

- `Loading target platform` can take a long time on first run (network and cache dependent).
- Full setup is stateful and environment-sensitive (Docker permissions, display availability, local toolchain).

## Skills Source Configuration

- `config set` handles scalar keys only; skills source arrays should be managed via:
  - `skills source add`
  - `skills source remove`
  - `skills source list`
  - or direct YAML editing

## Command Matrix

- Some command paths may return exit code `2` for `--help` due parser behavior.
- Smoke matrix accepts exit `2` only for an explicit allowlist and only when usage output is valid.
