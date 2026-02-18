# iDempiere CLI - Project Summary

## Overview

**idempiere-cli** is a developer CLI tool for [iDempiere](https://www.idempiere.org/) plugin development. It streamlines the entire plugin development lifecycle: environment setup, project scaffolding, component generation, dependency analysis, migration, packaging, and deployment.

**Status**: Early Release - functional and tested, contributions welcome to expand features.

---

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 17+ (targets 21) |
| Framework | Quarkus | 3.17.5 |
| CLI Framework | Picocli | (via quarkus-picocli) |
| Template Engine | Qute | (via quarkus-qute) |
| DI Container | ArC (CDI) | (via quarkus-arc) |
| Database Driver | PostgreSQL JDBC | 42.7.7 |
| Config Parsing | SnakeYAML | 2.3 |
| Build System | Maven + Tycho | 3.9.x / 4.0.8 |
| Testing | JUnit 5 + QuarkusMainTest | |
| Native Image | GraalVM | 21+ |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         IdempiereCli.java                        │
│                    (Picocli Top-Level Command)                   │
└─────────────────────────────────────────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    ▼                         ▼
        ┌───────────────────┐     ┌───────────────────────┐
        │     Commands      │     │       Services        │
        │  (Picocli @Command)│     │  (@ApplicationScoped) │
        └───────────────────┘     └───────────────────────┘
                │                           │
                │  SetupDevEnvCommand       │  SetupDevEnvService
                │  DoctorCommand            │  DoctorService
                │  InitCommand              │  ScaffoldService
                │  AddCommand (+ subcommands)│  DatabaseManager
                │  BuildCommand             │  ProcessRunner
                │  DeployCommand            │  SessionLogger
                │  MigrateCommand           │  MigrateService
                │  DepsCommand              │  ValidateService
                │  PackageCommand           │  CliConfigService
                │  DiffSchemaCommand        │  InteractivePromptService
                │  InfoCommand              │
                │  ValidateCommand          │
                │  UpgradeCommand           │
                │  GenerateCompletionCommand│
                └───────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    ▼                         ▼
        ┌───────────────────┐     ┌───────────────────────┐
        │      Models       │     │      Templates        │
        │   (Plain POJOs)   │     │   (Qute .qute files)  │
        └───────────────────┘     └───────────────────────┘
                │                           │
                │  SetupConfig              │  plugin/pom.xml.qute
                │  CliConfig                │  plugin/MANIFEST.MF.qute
                │  DbConfig                 │  process/Process.java.qute
                │  PluginDescriptor         │  callout/Callout.java.qute
                │  PlatformVersion          │  event-handler/...
                └───────────────────────────┘  zk-form/...
                                               test/...
                                               (40+ templates)
```

### Key Design Decisions

1. **Quarkus + Picocli**: Fast startup, native image support, CDI for services
2. **Qute Templates**: Type-safe templating for code generation
3. **Service Layer**: Business logic separated from CLI parsing
4. **ProcessRunner**: Unified wrapper for external command execution (git, mvn, docker, etc.)
5. **SessionLogger**: Audit trail for troubleshooting long-running commands
6. **CliDefaults**: Centralized default values for all configurable options

---

## Project Structure

```
src/main/java/org/idempiere/cli/
├── IdempiereCli.java              # Entry point, registers all commands
├── commands/
│   ├── SetupDevEnvCommand.java    # Bootstrap dev environment
│   ├── DoctorCommand.java         # Environment health check
│   ├── InitCommand.java           # Scaffold new plugin
│   ├── AddCommand.java            # Add components (parent command)
│   ├── add/
│   │   ├── AddCalloutCommand.java
│   │   ├── AddProcessCommand.java
│   │   ├── AddEventHandlerCommand.java
│   │   ├── AddModelCommand.java   # Generates I_/X_/M_ from DB
│   │   ├── AddTestCommand.java
│   │   ├── AddPluginModuleCommand.java
│   │   ├── AddFragmentModuleCommand.java
│   │   ├── AddFeatureModuleCommand.java
│   │   └── ... (15+ subcommands)
│   ├── BuildCommand.java
│   ├── DeployCommand.java
│   ├── MigrateCommand.java
│   ├── DepsCommand.java
│   ├── PackageCommand.java
│   ├── DiffSchemaCommand.java
│   ├── InfoCommand.java
│   ├── ValidateCommand.java
│   ├── UpgradeCommand.java
│   └── GenerateCompletionCommand.java
├── service/
│   ├── SetupDevEnvService.java    # Clone, build, configure Eclipse
│   ├── DatabaseManager.java       # Docker PostgreSQL/Oracle, schema import
│   ├── DoctorService.java         # Environment checks + fix suggestions
│   ├── ScaffoldService.java       # Template-based code generation
│   ├── MigrateService.java        # Version migration (v12↔v13)
│   ├── ValidateService.java       # Plugin structure validation
│   ├── ProcessRunner.java         # External command execution
│   ├── SessionLogger.java         # Audit logging
│   ├── CliConfigService.java      # YAML config loading
│   └── InteractivePromptService.java
├── model/
│   ├── SetupConfig.java           # setup-dev-env configuration
│   ├── CliConfig.java             # YAML config model
│   ├── DbConfig.java              # Database connection config
│   ├── PluginDescriptor.java      # Plugin metadata
│   └── PlatformVersion.java       # iDempiere version constants
└── util/
    ├── CliDefaults.java           # Centralized default values
    └── PluginUtils.java           # Plugin detection utilities

src/main/resources/
├── templates/                     # Qute templates (40+ files)
│   ├── plugin/                    # pom.xml, MANIFEST.MF, plugin.xml
│   ├── callout/                   # Callout.java, CalloutFactory.java
│   ├── process/                   # Process.java
│   ├── event-handler/             # EventHandler.java
│   ├── zk-form/                   # ZkForm.java, FormFactory.java
│   ├── test/                      # JUnit test templates
│   ├── fragment/                  # OSGi fragment templates
│   ├── feature/                   # Eclipse feature templates
│   └── ...
├── eclipse/                       # Eclipse workspace templates
└── application.properties         # Quarkus configuration

src/test/java/                     # 31 test files
```

---

## Implemented Features

### Commands (100% implemented)

| Command | Description | Status |
|---------|-------------|--------|
| `doctor` | Environment check + fix suggestions | ✅ Complete |
| `doctor --dir` | Plugin structure validation | ✅ Complete |
| `setup-dev-env` | Bootstrap dev environment | ✅ Complete |
| `setup-dev-env --with-docker` | PostgreSQL/Oracle in Docker | ✅ Complete |
| `init` | Scaffold new plugin project | ✅ Complete |
| `init --standalone` | Single plugin (no p2) | ✅ Complete |
| `init --with-fragment/feature` | Multi-module options | ✅ Complete |
| `add callout/process/...` | Add components to plugin | ✅ Complete |
| `add model --table=X` | Generate I_/X_/M_ from DB | ✅ Complete |
| `add test` | Generate JUnit test stubs | ✅ Complete |
| `add plugin/fragment/feature` | Add modules to project | ✅ Complete |
| `build` | Maven/Tycho build | ✅ Complete |
| `deploy` | Deploy to iDempiere | ✅ Complete |
| `migrate` | Version migration (v12↔v13) | ✅ Complete |
| `deps` | Dependency analysis | ✅ Complete |
| `package --format=zip/p2` | Package for distribution | ✅ Complete |
| `diff-schema` | Model vs DB comparison | ✅ Complete |
| `info` | Display plugin metadata | ✅ Complete |
| `validate` | Validate plugin structure | ✅ Complete |
| `upgrade` | Self-update from GitHub | ✅ Complete |
| `config show/get/set` | Configuration management | ✅ Complete |
| `config init` | Interactive configuration wizard | ✅ Complete |
| `generate-completion` | Shell completion scripts | ✅ Complete |

### Infrastructure

| Feature | Status |
|---------|--------|
| Native image (GraalVM) | ✅ Linux x64/ARM64, macOS Intel/Apple Silicon, Windows x64 |
| Cross-platform install script | ✅ curl \| bash |
| GitHub Actions CI/CD | ✅ Build + Release on tag |
| YAML configuration | ✅ ~/.idempiere-cli.yaml (with `config init` wizard) |
| AI integration | ✅ Anthropic, Google, OpenAI (direct API key + env var) |
| Custom templates | ✅ ~/.idempiere-cli/templates/ |
| Session logging | ✅ ~/.idempiere-cli/logs/ |
| PostgreSQL 16 default | ✅ Docker + client |
| Oracle XE support | ✅ Docker only |

---

## Not Implemented / Roadmap

### Short-term
- [ ] Expand bundle-to-package mapping in `deps` command (currently limited)

### Medium-term
- [ ] MCP-aware commands (receive AD context from [iDempiere MCP Server](https://github.com/hengsin/idempiere-mcp))
- [ ] `publish` command (GitHub Releases, Maven deploy)
- [ ] IntelliJ / VS Code project file generation

### Long-term
- [ ] Plugin marketplace integration
- [ ] CI/CD pipeline generation (GitHub Actions, Jenkins)
- [ ] Migration guides with breaking change detection

---

## Key Files for Understanding the Code

| File | Purpose |
|------|---------|
| `IdempiereCli.java` | Entry point, command registration |
| `CliDefaults.java` | All default values (DB, Docker, ports, etc.) |
| `SetupDevEnvService.java` | Main setup logic (clone, build, Eclipse) |
| `DatabaseManager.java` | PostgreSQL/Oracle Docker + schema import |
| `DoctorService.java` | Environment checks + auto-fix |
| `ScaffoldService.java` | Template-based code generation |
| `ProcessRunner.java` | External command execution wrapper |

---

## Configuration

### Default Values (CliDefaults.java)

```java
// Git
GIT_BRANCH = "master"
IDEMPIERE_REPO_URL = "https://github.com/idempiere/idempiere.git"

// Database
DB_TYPE = "postgresql"
DB_HOST = "localhost"
DB_PORT = 5432
DB_NAME = "idempiere"
DB_USER = "adempiere"
DB_PASSWORD = "adempiere"

// Docker
DOCKER_POSTGRES_VERSION = "16"
DOCKER_ORACLE_IMAGE = "gvenzl/oracle-xe:21-slim"

// Eclipse
ECLIPSE_VERSION = "2025-09"
```

### YAML Config Example

```yaml
# ~/.idempiere-cli.yaml
defaults:
  vendor: "My Company"
  idempiereVersion: 13

templates:
  path: ~/.idempiere-cli/templates

ai:
  provider: anthropic
  apiKey: sk-ant-...              # direct storage (masked in 'config show')
  apiKeyEnv: ANTHROPIC_API_KEY    # env var override (takes precedence)
  model: claude-sonnet-4-20250514
  fallback: templates
```

Use `idempiere-cli config init` for interactive setup.

---

## Build & Run

```bash
# Development (JVM mode)
./mvnw quarkus:dev

# Package JAR
./mvnw clean package
java -jar target/quarkus-app/quarkus-run.jar doctor

# Native image (requires GraalVM)
./mvnw clean package -Pnative
./target/idempiere-cli-runner doctor

# Run tests
./mvnw test
```

---

## Integration with MCP Server

The CLI is designed to work with [iDempiere MCP Server](https://github.com/hengsin/idempiere-mcp):

| Concern | CLI | MCP Server |
|---------|-----|------------|
| Scaffold plugin | ✅ | |
| Generate code | ✅ | |
| Build/deploy | ✅ | |
| AD introspection | | ✅ |
| AD registration | | ✅ |
| Business context | | ✅ |

Future: CLI commands receive AD context from MCP for smarter code generation.

---

## Known Issues / Technical Debt

1. **deps command**: Bundle-to-package mapping is incomplete (needs expansion)
2. **Oracle support**: Limited testing, seed import not fully automated
3. **Windows**: Some path handling edge cases with backslashes
4. **Tests**: 31 test files, but coverage could be improved

---

## Discussion Points for Claude.ai

1. **MCP Integration**: Best approach to integrate with the MCP server? Should commands query MCP for AD context before generating code?

2. **Template System**: Current Qute templates are embedded in JAR. Should we support remote template repositories?

3. **Plugin Marketplace**: What would a plugin marketplace for iDempiere look like? Integration with existing p2 infrastructure?

4. **AI-Assisted Development**: Beyond code generation, what other AI-assisted features would benefit iDempiere developers?

5. **Oracle Support**: Currently Docker-only. Worth investing in native Oracle support?

6. **IDE Support**: Currently Eclipse-only. Priority for IntelliJ/VS Code support?
