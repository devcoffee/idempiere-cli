# idempiere-cli Skills

AI-consumable skills that describe how to use `idempiere-cli` for iDempiere plugin development.

These skills follow the [SKILL.md format](https://github.com/hengsin/idempiere-skills) and can be used by AI agents (Claude Code, Gemini CLI, Cursor, etc.) to understand and operate `idempiere-cli` commands.

## Available Skills

| Skill | Description |
|-------|-------------|
| [idempiere-cli-setup-environment](idempiere-cli-setup-environment/SKILL.md) | Bootstrap and validate an iDempiere development environment |
| [idempiere-cli-create-plugin](idempiere-cli-create-plugin/SKILL.md) | Scaffold a new iDempiere plugin project |
| [idempiere-cli-add-component](idempiere-cli-add-component/SKILL.md) | Add components or modules to an existing plugin |
| [idempiere-cli-build-deploy](idempiere-cli-build-deploy/SKILL.md) | Build, deploy, and package plugins |
| [idempiere-cli-validate-analyze](idempiere-cli-validate-analyze/SKILL.md) | Validate structure, inspect metadata, analyze dependencies |
| [idempiere-cli-migrate](idempiere-cli-migrate/SKILL.md) | Migrate plugins between iDempiere versions |
| [idempiere-cli-dist](idempiere-cli-dist/SKILL.md) | Create iDempiere server distribution packages for all platforms |

## Usage with idempiere-cli

These skills can be registered as a local skill source in `idempiere-cli`:

```bash
# Add as local skill source
idempiere-cli config set skills.sources[0].name cli-skills
idempiere-cli config set skills.sources[0].path /path/to/idempiere-cli/skills

# List available skills
idempiere-cli skills list

# Check which source provides a skill
idempiere-cli skills which idempiere-cli-setup-environment
```

## Usage with AI Agents

Point your AI agent to this directory or the individual SKILL.md files. Each file is self-contained with complete command documentation, options, and examples.

### Example: Claude Code

```
Add this to your CLAUDE.md or agent configuration:
Skills source: https://github.com/devcoffee/idempiere-cli/tree/main/skills
```

### Example: Gemini CLI

```bash
# Install individual skills
gemini skills install https://github.com/devcoffee/idempiere-cli/tree/main/skills/idempiere-cli-setup-environment
```

## Skill Format

Each skill directory contains a `SKILL.md` file with:

```yaml
---
name: skill-name
description: Short description of what the skill does and when to use it.
---
```

Followed by markdown documentation with workflow, commands, options, and examples.
