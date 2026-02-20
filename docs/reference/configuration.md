# Configuration and AI

Configuration is loaded in this precedence order (highest first):

1. `--config` argument
2. `IDEMPIERE_CLI_CONFIG` environment variable
3. `.idempiere-cli.yaml` in current directory or parents
4. `~/.idempiere-cli.yaml`

## Minimal Config Example

```yaml
defaults:
  vendor: "My Company Inc."
  idempiereVersion: 13

ai:
  provider: anthropic
  model: claude-sonnet-4-20250514
  fallback: templates
```

## AI Provider Setup

Recommended:

```bash
idempiere-cli config init
```

Manual:

```bash
idempiere-cli config set ai.provider anthropic
idempiere-cli config set ai.model claude-sonnet-4-20250514
```

API key resolution order:
- environment variable (`ai.apiKeyEnv`)
- config file (`ai.apiKey`)

## Skills Sources

`skills.sources` is best managed directly in YAML.

Note:
- `config set` works for scalar keys (for example `ai.provider`, `defaults.vendor`)
- nested array entries like `skills.sources[0].name` should be edited in the config file

```yaml
skills:
  sources:
    - name: official
      url: https://github.com/hengsin/idempiere-skills.git
      priority: 1
    - name: local-overrides
      path: /opt/mycompany/idempiere-skills
      priority: 0
```

Supported source layouts:
- multi-skill repository (multiple directories containing `SKILL.md`)
- single-skill repository (root `SKILL.md`)

Use:

```bash
idempiere-cli skills list
idempiere-cli skills sync
```

## Multi-Workspace Pattern

Use one config per workspace root:

```text
workspace/
├── idempiere12/.idempiere-cli.yaml
└── idempiere13/.idempiere-cli.yaml
```

This keeps defaults version-specific without passing flags every time.
