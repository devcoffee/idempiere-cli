# idempiere-cli

A pragmatic CLI for iDempiere plugin engineering, built with [Quarkus](https://quarkus.io/) and [Picocli](https://picocli.info/).

> **Status: Early release (active development).**
> Core workflows are usable and covered by targeted tests, but the project is still evolving.
> Expect iterative improvements and occasional breaking changes.

## What it does

`idempiere-cli` turns recurring iDempiere plugin tasks into repeatable commands:
- environment checks and setup (`doctor`, `setup-dev-env`)
- scaffolding and code generation (`init`, `add`)
- quality gates (`validate`, `deps`, `diff-schema`)
- delivery (`build`, `deploy`, `package`, `migrate`)

Its role is to standardize iDempiere plugin engineering workflows across teams of any size, with repeatable scaffolding, validation, and delivery commands.
It does not replace iDempiere runtime tooling or Application Dictionary operations.

## Quick Start

```bash
# 1) Install
curl -fsSL https://raw.githubusercontent.com/devcoffee/idempiere-cli/main/install.sh | bash

# 2) Check environment
idempiere-cli doctor --fix

# 3) Optional AI/provider setup
idempiere-cli config init

# 4) Bootstrap local environment (Docker path)
idempiere-cli setup-dev-env --with-docker

# 5) Create and build first plugin
idempiere-cli init org.mycompany.myplugin
idempiere-cli build --dir ./myplugin/org.mycompany.myplugin.base
```

## Documentation

- [Documentation Hub](docs/README.md)
- [Onboarding in 15 Minutes](docs/jtbd/01-onboarding-15-min.md)
- [Daily Plugin Development Loop](docs/jtbd/02-daily-plugin-loop.md)
- [Troubleshooting Guide](docs/jtbd/03-troubleshooting.md)
- [Automation and Exit Codes](docs/jtbd/04-automation-exit-codes.md)
- [AI Generation Cycle](docs/jtbd/05-ai-generation-cycle.md)

Reference docs:
- [Command Reference](docs/reference/commands.md)
- [Configuration and AI](docs/reference/configuration.md)
- [Build and Release](docs/reference/build-release.md)
- [Architecture, Scope, and Roadmap](docs/reference/architecture-roadmap.md)

## Installation

Quick install (Linux/macOS/Windows via Git Bash or WSL):

```bash
curl -fsSL https://raw.githubusercontent.com/devcoffee/idempiere-cli/main/install.sh | bash
```

Manual binaries: [GitHub Releases](https://github.com/devcoffee/idempiere-cli/releases)

Windows prerequisite (if needed): [VC++ Redistributable](https://aka.ms/vs/17/release/vc_redist.x64.exe)

Verify:

```bash
idempiere-cli doctor
```

## Core Commands

| Area | Commands |
|------|----------|
| Environment | `doctor`, `setup-dev-env` |
| Scaffolding | `init`, `add` |
| Quality | `validate`, `deps`, `diff-schema`, `info` |
| Delivery | `build`, `deploy`, `package`, `migrate`, `dist` |
| Tooling | `config`, `skills`, `upgrade`, `generate-completion` |

For full options and examples, see [Command Reference](docs/reference/commands.md).

## Contributing

Contributions are welcome, especially in:
- real-world integration tests
- template quality and maintainability
- documentation and troubleshooting playbooks
- Oracle and multi-platform setup validation

## License

Licensed under [GNU GPL v2.0](LICENSE).

## Acknowledgments

Built on top of:
- [idempiere](https://github.com/idempiere/idempiere)
- [idempiere-dev-setup](https://github.com/hengsin/idempiere-dev-setup)
- [idempiere-examples](https://github.com/idempiere/idempiere-examples)
