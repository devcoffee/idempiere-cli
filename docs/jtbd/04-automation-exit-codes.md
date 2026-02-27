# Automation and Exit Codes

Use this contract in local scripts and CI jobs.

## Exit Code Contract

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Validation/user input error |
| `2` | I/O or command execution error |
| `3` | State error (invalid project/context) |

Notes:
- `validate --strict` can return `2` when warnings are treated as failures (command-specific strict-warning failure; not I/O).
- Prefer `!= 0` checks unless your script needs fine-grained behavior.

## Bash Pattern (recommended)

```bash
set -euo pipefail

idempiere-cli validate --strict ./plugin
idempiere-cli dist --dir ./plugin
```

## Branching by Exit Code

```bash
idempiere-cli dist --dir ./plugin
rc=$?

if [ "$rc" -eq 3 ]; then
  echo "Project state invalid (likely wrong directory or not a plugin)"
elif [ "$rc" -ne 0 ]; then
  echo "Command failed with code $rc"
fi
```

## JSON-Friendly Commands

For machine parsing:

```bash
idempiere-cli validate --json ./plugin
idempiere-cli doctor --json
idempiere-cli info --json --dir ./plugin
idempiere-cli deps --json --dir ./plugin
```
