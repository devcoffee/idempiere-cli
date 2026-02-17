---
name: idempiere-cli-setup-environment
description: Bootstrap and validate an iDempiere development environment using idempiere-cli. Use when the user wants to set up a new iDempiere development machine or check if their environment is ready.
---

# iDempiere CLI - Setup Development Environment

This skill guides you through setting up a complete iDempiere development environment using `idempiere-cli`.

## Prerequisites

The user must have `idempiere-cli` installed. If not, install it with:

```bash
curl -fsSL https://raw.githubusercontent.com/devcoffee/idempiere-cli/main/install.sh | bash
```

## Workflow

1. **Check environment**: Run `idempiere-cli doctor` to validate that all required tools are installed.
2. **Fix missing tools**: If tools are missing, run `idempiere-cli doctor --fix` to auto-install them.
3. **Bootstrap environment**: Run `idempiere-cli setup-dev-env` to clone source, create database, and configure Eclipse.

## Step 1: Doctor - Environment Validation

```bash
# Check all required tools
idempiere-cli doctor

# Auto-fix missing required tools (Java, Maven, Git, PostgreSQL)
idempiere-cli doctor --fix

# Also install optional tools (interactive selection)
idempiere-cli doctor --fix-optional

# Install all optional tools
idempiere-cli doctor --fix-optional=all

# Install specific optional tools
idempiere-cli doctor --fix-optional=docker
idempiere-cli doctor --fix-optional=docker,maven

# Validate a plugin directory too
idempiere-cli doctor --dir /path/to/my-plugin

# JSON output for CI/CD
idempiere-cli doctor --json
```

### What doctor checks

| Tool       | Required | Purpose                        |
|------------|----------|--------------------------------|
| Java JDK   | Yes      | Compilation and runtime        |
| Maven      | Yes      | Build system                   |
| Git        | Yes      | Source control                  |
| PostgreSQL | Yes      | Database (psql client)         |
| Docker     | No       | Containerized database         |

### Auto-fix sources by platform

| Platform | Package Manager |
|----------|-----------------|
| macOS    | Homebrew        |
| Linux    | apt / SDKMAN    |
| Windows  | winget          |

## Step 2: Setup Dev Env - Full Bootstrap

```bash
# Full setup with Docker database (recommended)
idempiere-cli setup-dev-env --with-docker

# Use existing native PostgreSQL
idempiere-cli setup-dev-env

# Oracle database with Docker
idempiere-cli setup-dev-env --db=oracle --with-docker

# Custom configuration
idempiere-cli setup-dev-env \
  --branch=release-12 \
  --db-host=localhost \
  --db-port=5432 \
  --db-name=idempiere \
  --db-user=adempiere \
  --db-pass=adempiere \
  --db-admin-pass=postgres

# Skip steps
idempiere-cli setup-dev-env --skip-db          # Database already configured
idempiere-cli setup-dev-env --skip-build       # Source already built
idempiere-cli setup-dev-env --skip-workspace   # Headless server (no Eclipse)

# Non-interactive for CI/CD
idempiere-cli setup-dev-env --with-docker --non-interactive

# Dry run (validate parameters only)
idempiere-cli setup-dev-env --with-docker --dry-run
```

### What setup-dev-env does

1. Clones iDempiere source from GitHub
2. Creates and configures database (PostgreSQL or Oracle, native or Docker)
3. Imports seed data and applies migrations
4. Downloads and configures Eclipse IDE
5. Imports projects into Eclipse workspace
6. Configures target platform and preferences

### Important options

| Option                     | Default                | Description                               |
|----------------------------|------------------------|-------------------------------------------|
| `--db`                     | postgresql             | Database type: `postgresql` or `oracle`   |
| `--with-docker`            | false                  | Create database using Docker              |
| `--branch`                 | master                 | iDempiere branch to checkout              |
| `--db-host`                | localhost              | Database host                             |
| `--db-port`                | 5432                   | Database port                             |
| `--db-name`                | idempiere              | Database name                             |
| `--db-user`                | adempiere              | Database user                             |
| `--db-pass`                | adempiere              | Database password                         |
| `--db-admin-pass`          | postgres               | Database admin password                   |
| `--skip-db`                | false                  | Skip database setup                       |
| `--skip-build`             | false                  | Skip Maven build                          |
| `--skip-workspace`         | false                  | Skip Eclipse workspace setup              |
| `--include-rest`           | false                  | Also clone idempiere-rest repository      |
| `--install-copilot`        | false                  | Install GitHub Copilot in Eclipse         |
| `--docker-postgres-name`   | idempiere-postgres     | Docker container name for PostgreSQL      |
| `--docker-postgres-version`| 16                     | PostgreSQL Docker version                 |
| `--non-interactive`        | false                  | Run without prompting for confirmation    |
| `--continue-on-error`      | false                  | Continue setup even if a step fails       |
| `--dry-run`                | false                  | Validate parameters only                  |

## Example Usage

If the user asks "I want to start developing iDempiere plugins", guide them through:

```bash
# 1. Check environment
idempiere-cli doctor

# 2. Fix any missing tools
idempiere-cli doctor --fix

# 3. Bootstrap everything with Docker (easiest path)
idempiere-cli setup-dev-env --with-docker
```

If the user has an existing PostgreSQL installation:

```bash
idempiere-cli setup-dev-env --db-admin-pass=mypostgrespass
```

## Notes

- `setup-dev-env` requires a graphical environment (display) for Eclipse workspace setup. Use `--skip-workspace` on headless servers.
- Docker is the recommended approach for database setup as it requires no manual PostgreSQL/Oracle configuration.
- The command is idempotent: it can detect existing source directories and Docker containers.
