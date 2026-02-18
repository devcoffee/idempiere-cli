# idempiere-cli

A command-line tool for iDempiere plugin development, built with [Quarkus](https://quarkus.io/) and [Picocli](https://picocli.info/).

> **Early Release** - built on the [idempiere](https://github.com/idempiere/idempiere) and
> [idempiere-dev-setup](https://github.com/hengsin/idempiere-dev-setup) repositories.
> Functional and tested. Feedback, ideas, and pull requests are welcome!

---

## What it does

`idempiere-cli` streamlines the entire iDempiere plugin development lifecycle: environment setup, project scaffolding, component generation, dependency analysis, migration, packaging, and more.

```
doctor ──> config init ──> setup-dev-env ──> init ──> add components ──> build ──> deploy
                                                          │
                                              deps / migrate / doctor --dir
                                                          │
                                                 package / diff-schema
```

---

## Quick Start

From zero to first plugin in 5 commands:

```bash
# 1. Install CLI
curl -fsSL https://raw.githubusercontent.com/devcoffee/idempiere-cli/main/install.sh | bash

# 2. Check environment & get fix suggestions
idempiere-cli doctor --fix

# 3. Configure defaults and AI provider (optional)
idempiere-cli config init

# 4. Setup complete dev environment (Eclipse + PostgreSQL + iDempiere source)
idempiere-cli setup-dev-env --with-docker

# 5. Create your first plugin
idempiere-cli init org.mycompany.myplugin --with-process --with-event-handler
```

Compare this to the [traditional setup](https://wiki.idempiere.org/en/Installing_iDempiere) which requires manually configuring Eclipse, PostgreSQL, cloning repositories, and setting up target platforms.

---

## Installation

### Quick Install (Linux, macOS, Windows via Git Bash/WSL)

```bash
curl -fsSL https://raw.githubusercontent.com/devcoffee/idempiere-cli/main/install.sh | bash
```

### Install Options

```bash
# Install specific version
curl -fsSL https://raw.githubusercontent.com/devcoffee/idempiere-cli/main/install.sh | VERSION=1.0.0 bash

# Install to custom directory
curl -fsSL https://raw.githubusercontent.com/devcoffee/idempiere-cli/main/install.sh | INSTALL_DIR=~/.local/bin bash
```

### Manual Download

Download the appropriate binary from [GitHub Releases](https://github.com/devcoffee/idempiere-cli/releases):

| Platform | Architecture | File |
|----------|--------------|------|
| Linux | x64 | `idempiere-cli-linux-amd64` |
| Linux | ARM64 | `idempiere-cli-linux-arm64` |
| macOS | Intel | `idempiere-cli-darwin-amd64` |
| macOS | Apple Silicon | `idempiere-cli-darwin-arm64` |
| Windows | x64 | `idempiere-cli-windows-amd64.exe` |

### Windows Prerequisites

The Windows native binary requires the **Visual C++ Redistributable** runtime. If you see an error about `VCRUNTIME140_1.dll`, install it from:

👉 https://aka.ms/vs/17/release/vc_redist.x64.exe

### Verify Installation

```bash
idempiere-cli doctor
```

---

## Implemented Commands

### `doctor`

Check required tools and environment prerequisites.

```bash
idempiere-cli doctor                        # Check Java, Maven, Git, Docker, PostgreSQL
idempiere-cli doctor --fix                  # Auto-install missing required tools
idempiere-cli doctor --fix-optional         # Interactive selection of optional tools
idempiere-cli doctor --fix-optional=all     # Install all optional tools (Docker, Maven)
idempiere-cli doctor --fix-optional=docker  # Install specific optional tool
idempiere-cli doctor --dir ./my-plugin      # Validate plugin structure
```

**Example output:**

```
iDempiere Development Environment Check
========================================

Checking required tools:

  [✓] Java 21.0.1 (Eclipse Adoptium)
  [✓] Maven 3.9.6
  [✓] Git 2.43.0

Checking optional tools:

  [✓] Docker 24.0.7
  [✓] PostgreSQL client (psql) 16.1

All checks passed!

Configuration:
  [✓] Global config:  ~/.idempiere-cli.yaml
  [✓] AI provider:    anthropic
```

**Fix suggestions (`--fix`):**
- For critical tools (Java, Maven, Git): shows platform-specific installation commands (brew, apt, dnf, or download links)
- For optional tools (Docker, PostgreSQL): suggests using `setup-dev-env --with-docker` for containerized PostgreSQL
- Detects OS (macOS, Linux, Windows) and provides appropriate commands

Plugin validation (`--dir`) checks:
- `MANIFEST.MF` headers (Bundle-SymbolicName, Bundle-Version, etc.)
- `build.properties` entries (source, output, bin.includes)
- `pom.xml` (tycho-maven-plugin, packaging=bundle)
- `OSGI-INF/` directory
- Imports vs Require-Bundle consistency

### `setup-dev-env`

Bootstrap a complete local iDempiere development environment.

```bash
# PostgreSQL with Docker
idempiere-cli setup-dev-env --with-docker --include-rest

# Oracle with Docker
idempiere-cli setup-dev-env --db=oracle --with-docker

# Custom Oracle Docker options
idempiere-cli setup-dev-env --db=oracle --with-docker \
  --oracle-docker-container=my-oracle \
  --oracle-docker-image=gvenzl/oracle-xe:21-slim
```

Automates: git clone, database setup (PostgreSQL or Oracle, native or Docker), Eclipse workspace/target configuration, and optional REST repository.

**Database options:**
- `--db=postgresql` (default): PostgreSQL database
- `--db=oracle`: Oracle XE database
- `--with-docker`: Create database in Docker container
- `--oracle-docker-container`: Container name (default: `idempiere-oracle`)
- `--oracle-docker-image`: Docker image (default: `gvenzl/oracle-xe:21-slim`)

### `init`

Scaffold a new OSGi-ready iDempiere plugin project.

```bash
# Multi-module project (default) - recommended for production
idempiere-cli init org.mycompany.myplugin

# Multi-module with fragment and feature modules
idempiere-cli init org.mycompany.myplugin --with-fragment --with-feature

# Standalone single plugin (simpler, no p2 support)
idempiere-cli init org.mycompany.myplugin --standalone

# With component stubs
idempiere-cli init org.mycompany.myplugin \
  --with-callout --with-process --with-event-handler

# Interactive mode (prompts for each option)
idempiere-cli init org.mycompany.myplugin --interactive
```

**Multi-module structure (default):**

```
org.mycompany.myplugin/
├── org.mycompany.myplugin.parent/    (Maven parent with Tycho config)
├── org.mycompany.myplugin.base/      (Main plugin code)
├── org.mycompany.myplugin.base.test/ (JUnit tests)
├── org.mycompany.myplugin.fragment/  (optional: UI fragments)
├── org.mycompany.myplugin.feature/   (optional: Eclipse feature)
└── org.mycompany.myplugin.p2/        (P2 update site generation)
```

**Why multi-module?** This structure enables proper p2 update site generation, test isolation, and fragment support.

Generates: `pom.xml`, `MANIFEST.MF`, `plugin.xml`, `build.properties`, and component stubs with OSGi Declarative Services annotations.

**Platform version support:** `--idempiere-version` flag controls Java release, Tycho version, bundle version, and target branch (v12 = Java 17/release-12, v13 = Java 21/master).

### `add <component>`

Add components or modules to an existing plugin/project.

**Module commands (multi-module projects):**

```bash
# Add a new plugin module to existing project
idempiere-cli add plugin org.mycompany.myplugin.reports

# Add a fragment module (extends org.adempiere.ui.zk by default)
idempiere-cli add fragment
idempiere-cli add fragment --host=org.adempiere.base

# Add a feature module (groups plugins for installation)
idempiere-cli add feature
```

**Component commands (add code to plugins):**

```bash
idempiere-cli add callout --name=MyCallout --to=./my-plugin
idempiere-cli add process --name=MyProcess --to=./my-plugin
idempiere-cli add event-handler --name=MyHandler --to=./my-plugin
idempiere-cli add zk-form --name=MyForm --to=./my-plugin
idempiere-cli add report --name=MyReport --to=./my-plugin
idempiere-cli add window-validator --name=MyValidator --to=./my-plugin
idempiere-cli add rest-extension --name=MyResource --to=./my-plugin
idempiere-cli add facts-validator --name=MyFactsValidator --to=./my-plugin
idempiere-cli add model --table=C_Order --db-host=localhost --to=./my-plugin
idempiere-cli add test --dir=./my-plugin              # All components
idempiere-cli add test --for=MyProcess --dir=./my-plugin  # Specific class

# AI-powered generation with custom prompt
idempiere-cli add callout --name=OrderDateCallout --to=./my-plugin \
  --prompt="Validate that the order date is not in the future and set a warning"
```

The `--prompt` option is available on all component commands (callout, process, event-handler, zk-form, report, etc.). When an AI provider is configured, it uses the prompt to generate context-aware code. Without AI, the standard template is used.

The `model` subcommand connects to PostgreSQL and generates `I_`, `X_`, and `M_` classes from `AD_Column` metadata.

The `test` subcommand generates JUnit test stubs, detecting component type (process, callout, event handler) from source code.

### `info`

Display plugin metadata and detected components.

```bash
idempiere-cli info --dir=./my-plugin
```

**Example output:**

```
Plugin Information
==================

  Name:           org.mycompany.myplugin
  Version:        1.0.0.qualifier
  Vendor:         My Company Inc.
  Java Version:   21
  Tycho Version:  4.0.8

Components Detected:
  - Process: MyProcess (org.mycompany.myplugin.MyProcess)
  - Callout: MyCallout (org.mycompany.myplugin.MyCallout)
  - EventHandler: MyHandler (org.mycompany.myplugin.MyHandler)

Dependencies (Require-Bundle):
  - org.adempiere.base
  - org.adempiere.ui.zk
  - org.compiere.model

Build Status: Not built (run 'idempiere-cli build')
```

### `build`

Build a plugin using Maven/Tycho.

```bash
idempiere-cli build --dir=./my-plugin --clean
```

### `deploy`

Deploy a built plugin to an iDempiere instance.

```bash
idempiere-cli deploy --dir=./my-plugin --target=/opt/idempiere/plugins
```

### `migrate`

Migrate a plugin between iDempiere platform versions.

```bash
idempiere-cli migrate --from=12 --to=13 --dir=./my-plugin
```

Updates `pom.xml` (Java release, Tycho version), `MANIFEST.MF` (JavaSE version, bundle versions), and `build.properties` (javac source/target).

### `deps`

Analyze plugin dependencies.

```bash
idempiere-cli deps --dir=./my-plugin
```

**Example output:**

```
Dependency Analysis: org.mycompany.myplugin
============================================

Packages imported in source code:
  - org.compiere.model (→ org.adempiere.base)
  - org.compiere.process (→ org.adempiere.base)
  - org.adempiere.webui.component (→ org.adempiere.ui.zk)
  - org.zkoss.zul (→ org.zkoss.zul)

Current Require-Bundle:
  [✓] org.adempiere.base
  [✓] org.adempiere.ui.zk
  [!] org.zkoss.zul (missing)

Recommendations:
  Add to MANIFEST.MF Require-Bundle:
    org.zkoss.zul;bundle-version="9.6.0"
```

Scans Java imports, cross-references against known iDempiere bundle-to-package mappings, and reports missing or unused entries in `Require-Bundle`.

### `package`

Package a plugin for distribution.

```bash
# ZIP format (works for standalone and multi-module)
idempiere-cli package --format=zip

# P2 update site (requires multi-module project)
idempiere-cli package --format=p2
```

**Note:** The `--format=p2` option requires a multi-module project structure with a `.p2` module. Use `idempiere-cli init` (without `--standalone`) to create a project with p2 support.

### `diff-schema`

Compare model classes against the database schema.

```bash
idempiere-cli diff-schema --table=C_Order --dir=./my-plugin --db-host=localhost
```

Reports columns added in the database but missing from code, columns in code but removed from the database, and type mismatches.

---

## Tech Stack

| Component       | Technology           |
|-----------------|----------------------|
| Language        | Java 21              |
| CLI Framework   | Quarkus 3.17 + Picocli |
| Templates       | Qute                 |
| Build           | Maven + Tycho        |
| Database        | PostgreSQL, Oracle (JDBC) |
| AI Integration  | java.net.http + Jackson (optional, multi-provider) |
| Testing         | JUnit 5 + QuarkusMainTest |

---

## Building from Source

```bash
git clone https://github.com/devcoffee/idempiere-cli.git
cd idempiere-cli
./mvnw clean package
```

### Native Image (requires GraalVM)

```bash
./mvnw clean package -Pnative
```

This produces a standalone binary at `target/idempiere-cli-runner` (or `target/idempiere-cli-runner.exe` on Windows).

Run directly:

```bash
java -jar target/quarkus-app/quarkus-run.jar doctor
```

### Releasing

Push a tag to trigger GitHub Actions release:

```bash
git tag v1.0.0 && git push --tags
```

This builds native binaries for all platforms and creates a GitHub Release.

---

## Configuration

`idempiere-cli` supports a YAML configuration file (`.idempiere-cli.yaml`) for persistent defaults.

### Configuration File Locations

Configuration is loaded with the following precedence (highest first):

1. `--config` option: Explicit path passed to command
2. `IDEMPIERE_CLI_CONFIG` environment variable
3. Hierarchical search: `.idempiere-cli.yaml` in current directory or any parent
4. Global config: `~/.idempiere-cli.yaml`

### Example Configuration

```yaml
# ~/.idempiere-cli.yaml (global) or ./.idempiere-cli.yaml (project)
defaults:
  vendor: "My Company Inc."
  idempiereVersion: 13

templates:
  path: ~/.idempiere-cli/templates
```

### Multi-Version Workspace

The hierarchical search enables per-version configuration:

```
workspace/
├── idempiere12/
│   ├── .idempiere-cli.yaml    # idempiereVersion: 12
│   ├── plugin1/               # inherits version 12
│   └── plugin2/               # inherits version 12
└── idempiere13/
    ├── .idempiere-cli.yaml    # idempiereVersion: 13
    └── my-plugin/             # inherits version 13
```

### AI-Powered Generation (Optional)

`idempiere-cli` can use AI to generate context-aware code that adapts to your project's patterns and naming conventions. When AI is not configured, standard Qute templates are used — the same CLI commands work either way.

**Quick setup:**

```bash
# 1. Interactive configuration (recommended)
idempiere-cli config init

# 2. Use normally — AI generates code adapted to your project
idempiere-cli add callout --name=OrderTotalCalculator --to=./my-plugin

# 3. Describe what the component should do with --prompt
idempiere-cli add process --name=GenerateInvoices --to=./my-plugin \
  --prompt="Generate invoices for all confirmed orders in the current period"
```

Or configure manually:

```bash
idempiere-cli config set ai.provider anthropic
idempiere-cli config set ai.apiKey sk-ant-...
idempiere-cli config set ai.model claude-sonnet-4-20250514
```

**Supported providers:**

| Provider | Config Value | Default Model |
|----------|-------------|---------------|
| Anthropic | `anthropic` | `claude-sonnet-4-20250514` |
| Google | `google` | `gemini-2.5-flash` |
| OpenAI | `openai` | `gpt-4o` |

**API key resolution:** The CLI resolves API keys with the following precedence:
1. Environment variable (e.g., `$ANTHROPIC_API_KEY`) — ideal for CI/CD
2. Config file (`ai.apiKey` in `.idempiere-cli.yaml`) — convenient for development

**Skills management:**

```bash
idempiere-cli skills list              # List configured sources
idempiere-cli skills sync              # Clone/pull remote sources
idempiere-cli skills which callout     # Show which source provides a skill
```

**Configuration management:**

```bash
idempiere-cli config init              # Interactive configuration wizard
idempiere-cli config show              # Show current config (API keys masked)
idempiere-cli config get ai.provider   # Get a specific value
idempiere-cli config set ai.model claude-opus-4-20250514  # Set a value
```

**Full YAML example:**

```yaml
# ~/.idempiere-cli.yaml
defaults:
  vendor: "My Company Inc."
  idempiereVersion: 13

ai:
  provider: anthropic
  apiKey: sk-ant-...            # stored directly (masked in 'config show')
  apiKeyEnv: ANTHROPIC_API_KEY  # env var override (takes precedence over apiKey)
  model: claude-sonnet-4-20250514
  fallback: templates           # "templates" (default) or "error"

skills:
  sources:
    - name: official
      url: https://github.com/hengsin/idempiere-skills.git
      priority: 1
    - name: local-overrides
      path: /opt/mycompany/idempiere-skills
      priority: 0              # 0 = highest priority
  cacheDir: ~/.idempiere-cli/skills
  updateInterval: 7d
```

**Without AI:** Everything works without AI configuration. Templates provide reliable, tested output. AI adds context-awareness (adapts to your project's coding patterns, naming conventions, and existing infrastructure).

### Custom Templates

Override built-in templates by placing custom versions in your templates directory:

```
~/.idempiere-cli/templates/
├── plugin/
│   ├── pom.xml.qute           # Custom pom.xml template
│   └── MANIFEST.MF.qute
├── process/
│   └── Process.java.qute      # Custom process template
└── ...
```

Templates not found in custom path fall back to built-in templates.

---

## Project Structure

```
src/main/java/org/idempiere/cli/
  IdempiereCli.java              # Top-level command registry
  commands/                      # Picocli command classes
    add/                         # Subcommands for "add"
  model/                         # Data models (PluginDescriptor, ProjectContext, etc.)
  service/                       # Business logic services
    ai/                          # AI client abstraction (Anthropic, Google, OpenAI)

src/main/resources/templates/    # Qute templates for code generation
  plugin/                        # Base plugin files (pom.xml, MANIFEST.MF, etc.)
  callout/, process/, ...        # Component-specific templates
  test/                          # Test stub templates

src/test/java/                   # QuarkusMainTest-based integration tests
```

---

## CLI + MCP Server: The Bigger Picture

This CLI is designed to work **alongside the [iDempiere MCP Server](https://github.com/hengsin/idempiere-mcp)** — a separate project that exposes Application Dictionary knowledge to AI tools.

The responsibility split:

| Concern                        | CLI (this project)  | MCP Server          |
|--------------------------------|---------------------|---------------------|
| Scaffold plugin structure      | Yes                 |                     |
| Generate component stubs       | Yes                 |                     |
| Build / deploy / package       | Yes                 |                     |
| Migrate between versions       | Yes                 |                     |
| Analyze dependencies           | Yes                 |                     |
| AD table/column introspection  |                     | Yes                 |
| AD window/tab/field metadata   |                     | Yes                 |
| Register components in AD      |                     | Yes                 |
| Business logic context         |                     | Yes                 |
| Validation rules & callout map |                     | Yes                 |

**Example combined workflow** (AI-assisted plugin development):

```
1. MCP: "What columns does C_Order have?"        → AD introspection
2. CLI: idempiere-cli init org.acme.order-ext     → scaffold plugin
3. CLI: idempiere-cli add model --table=C_Order   → generate model classes
4. MCP: "What callouts exist for C_Order?"        → AD context
5. CLI: idempiere-cli add callout --name=...      → generate stub with context
6. CLI: idempiere-cli add test --dir=.            → generate test stubs
7. CLI: idempiere-cli build && deploy             → build and deploy
8. MCP: "Register process in AD_Process"          → AD registration
```

The MCP server gives AI agents **semantic understanding** of the iDempiere platform, while the CLI handles **filesystem operations, builds, and deployments**. Together, they enable end-to-end AI-assisted plugin development.

---

## Roadmap

### Completed
- [x] `doctor` with environment checks and `--fix` auto-installation (macOS, Linux, Windows)
- [x] `doctor --dir` for plugin structure validation
- [x] `setup-dev-env` with Docker PostgreSQL support
- [x] `setup-dev-env` with Docker Oracle XE support (`--db=oracle --with-docker`)
- [x] `init` with `--interactive` mode
- [x] `init` multi-module project structure (parent + plugin + test + p2)
- [x] `init --standalone` for single plugin projects
- [x] `init --with-fragment` and `--with-feature` options
- [x] `add` for all component types (callout, process, event-handler, zk-form, report, window-validator, rest-extension, facts-validator)
- [x] `add plugin/fragment/feature` for multi-module projects
- [x] `add model` for I_/X_/M_ class generation from database
- [x] `add test` for JUnit test stub generation
- [x] `build` and `deploy` commands
- [x] `migrate` between iDempiere versions (v12 ↔ v13)
- [x] `deps` for dependency analysis (imports vs Require-Bundle)
- [x] `package --format=zip` for distribution
- [x] `package --format=p2` for multi-module projects (Tycho p2 update site)
- [x] `diff-schema` for model vs database comparison
- [x] `generate-completion` for bash/zsh shell completion
- [x] `upgrade` command for self-updating CLI from GitHub releases
- [x] Native image distribution (GraalVM for Linux x64/ARM64, macOS Intel/Apple Silicon, Windows x64)
- [x] Cross-platform compatibility: Windows winget, Linux apt/dnf/yum/pacman/zypper, macOS Homebrew
- [x] Integration tests with real plugin fixtures (scaffold → build → validate)
- [x] Add `--config` support for persistent CLI preferences (~/.idempiere-cli.yaml)
- [x] Template customization (user-defined templates in ~/.idempiere-cli/templates/)

- [x] AI-powered scaffolding (optional, multi-provider: Anthropic, Google, OpenAI)
- [x] Skill management (`skills list/sync/which`) with multi-source priority resolution
- [x] Project context analysis for AI-aware code generation
- [x] `config` command for managing CLI settings (`config show/get/set/init`)
- [x] `config init` interactive wizard with direct API key storage

### Short-term
- [ ] Expand bundle-to-package mapping in `deps` command

### Medium-term
- [ ] MCP-aware commands (e.g., `add callout` receiving AD context from MCP server)
- [ ] `publish` command (GitHub Releases, Maven deploy)
- [ ] IntelliJ / VS Code project file generation (beyond Eclipse PDE)

### Long-term
- [ ] Plugin marketplace integration
- [ ] CI/CD pipeline generation (GitHub Actions, Jenkins)
- [ ] Migration guides with breaking change detection

---

## Contributing

This project needs help! Areas where contributions are especially welcome:

- **Testing**: Integration tests with real iDempiere plugins
- **Templates**: Improved code generation templates following iDempiere best practices
- **MCP integration**: Connecting CLI commands with the [MCP server](https://github.com/hengsin/idempiere-mcp) for AD-aware generation
- **Documentation**: Usage guides, tutorials, and examples
- **Oracle support**: Testing Oracle database workflows and seed import

---

## License

This project is licensed under the [GNU General Public License v2.0](LICENSE).

---

## Acknowledgments

- Original proposal by **dev&Co. Team**, **Saul Pina**, and **Eduardo Gil**
- Built on top of [iDempiere](https://www.idempiere.org/), [idempiere-dev-setup](https://github.com/hengsin/idempiere-dev-setup), and [idempiere-examples](https://github.com/idempiere/idempiere-examples)
