# idempiere-cli

A command-line tool for iDempiere plugin development, built with [Quarkus](https://quarkus.io/) and [Picocli](https://picocli.info/).

> **This is a Proof of Concept** based on the [idempiere](https://github.com/idempiere/idempiere) and
> [idempiere-dev-setup](https://github.com/hengsin/idempiere-dev-setup) repositories.
> It is **not production-ready** and needs community contributions to be completed.
> Feedback, ideas, and pull requests are welcome!

---

## What it does

`idempiere-cli` streamlines the entire iDempiere plugin development lifecycle: environment setup, project scaffolding, component generation, dependency analysis, migration, packaging, and more.

```
doctor ──> setup-dev-env ──> init ──> add components ──> build ──> deploy
                                          │
                              deps / migrate / doctor --dir
                                          │
                                 package / diff-schema
```

---

## Quick Start

From zero to first plugin in 4 commands:

```bash
# 1. Install CLI
curl -fsSL https://raw.githubusercontent.com/devcoffee/idempiere-cli/main/install.sh | bash

# 2. Check environment & get fix suggestions
idempiere-cli doctor --fix

# 3. Setup complete dev environment (Eclipse + PostgreSQL + iDempiere source)
idempiere-cli setup-dev-env --with-docker

# 4. Create your first plugin
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
idempiere-cli doctor              # Check Java, Maven, Git, Docker, PostgreSQL
idempiere-cli doctor --fix        # Show fix suggestions with setup-dev-env commands
idempiere-cli doctor --dir ./my-plugin  # Validate plugin structure
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

Scaffold a new OSGi-ready iDempiere plugin.

```bash
# With explicit flags
idempiere-cli init org.mycompany.myplugin \
  --with-callout --with-process --with-event-handler \
  --idempiere-version 13

# Interactive mode (prompts for each option)
idempiere-cli init org.mycompany.myplugin --interactive
```

Generates: `pom.xml`, `MANIFEST.MF`, `plugin.xml`, `build.properties`, and component stubs with OSGi Declarative Services annotations.

**Platform version support:** `--idempiere-version` flag controls Java release, Tycho version, bundle version, and target branch (v12 = Java 17/release-12, v13 = Java 21/master).

### `add <component>`

Add components to an existing plugin.

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
```

The `model` subcommand connects to PostgreSQL and generates `I_`, `X_`, and `M_` classes from `AD_Column` metadata.

The `test` subcommand generates JUnit test stubs, detecting component type (process, callout, event handler) from source code.

### `info`

Display plugin metadata and detected components.

```bash
idempiere-cli info --dir=./my-plugin
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

Scans Java imports, cross-references against known iDempiere bundle-to-package mappings, and reports missing or unused entries in `Require-Bundle`.

### `package`

Package a plugin for distribution.

```bash
idempiere-cli package --dir=./my-plugin --format=zip
idempiere-cli package --dir=./my-plugin --format=p2
```

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

## Project Structure

```
src/main/java/org/idempiere/cli/
  IdempiereCli.java              # Top-level command registry
  commands/                      # Picocli command classes
    add/                         # Subcommands for "add"
  model/                         # Data models (PluginDescriptor, PlatformVersion, etc.)
  service/                       # Business logic services

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
- [x] `add` for all component types (callout, process, event-handler, zk-form, report, window-validator, rest-extension, facts-validator)
- [x] `add model` for I_/X_/M_ class generation from database
- [x] `add test` for JUnit test stub generation
- [x] `build` and `deploy` commands
- [x] `migrate` between iDempiere versions (v12 ↔ v13)
- [x] `deps` for dependency analysis (imports vs Require-Bundle)
- [x] `package --format=zip` for distribution
- [x] `diff-schema` for model vs database comparison
- [x] `generate-completion` for bash/zsh shell completion
- [x] Native image distribution (GraalVM for Linux x64/ARM64, macOS Intel/Apple Silicon, Windows x64)
- [x] Cross-platform compatibility: Windows winget, Linux apt/dnf/yum/pacman/zypper, macOS Homebrew

### Short-term
- [ ] Integration tests with real plugin fixtures (scaffold → build → validate)
- [ ] Improve `package --format=p2` (full Tycho p2 update site generation)
- [ ] Add `--config` support for persistent CLI preferences (~/.idempiere-cli.yaml)
- [ ] Expand bundle-to-package mapping in `deps` command

### Medium-term
- [ ] MCP-aware commands (e.g., `add callout` receiving AD context from MCP server)
- [ ] `publish` command (GitHub Releases, Maven deploy)
- [ ] Template customization (user-defined templates in ~/.idempiere-cli/templates/)
- [ ] IntelliJ / VS Code project file generation (beyond Eclipse PDE)

### Long-term
- [ ] Plugin marketplace integration
- [ ] CI/CD pipeline generation (GitHub Actions, Jenkins)
- [ ] Multi-plugin workspace management
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
- Built on top of [iDempiere](https://www.idempiere.org/), [idempiere-dev-setup](https://github.com/hengsin/idempiere-dev-setup), and [idempiere-examples](https://github.com/hengsin/idempiere-examples)
