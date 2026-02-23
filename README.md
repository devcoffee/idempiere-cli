# idempiere-cli

A command-line tool for iDempiere plugin engineering, built with [Quarkus](https://quarkus.io/) and [Picocli](https://picocli.info/).

> **Status: Early release (active development).**
> Core workflows are usable and tested, but the project is still evolving.
> Expect iterative improvements and occasional breaking changes.

## Scope

`idempiere-cli` standardizes repetitive iDempiere plugin engineering tasks:
- environment checks and setup (`doctor`, `setup-dev-env`)
- scaffolding and component generation (`init`, `add`)
- quality gates (`validate`, `deps`, `diff-schema`)
- delivery (`build`, `deploy`, `package`, `migrate`)

It complements iDempiere runtime/Application Dictionary tooling; it does not replace them.

## AI Positioning

- Default/core build is deterministic and template-first.
- Embedded AI generation is experimental and only enabled in `-Pexp`.
- Recommended day-to-day flow: use external agents (Claude Code, Codex, Gemini CLI, etc.) to implement business logic on top of CLI-generated templates.
- Keep `idempiere-cli` as the engineering integrity layer: scaffold, validate, build, package, deploy.

## Install

```bash
curl -fsSL https://raw.githubusercontent.com/devcoffee/idempiere-cli/main/install.sh | bash
idempiere-cli doctor
```

Manual binaries: [GitHub Releases](https://github.com/devcoffee/idempiere-cli/releases)  
Windows prerequisite (if needed): [VC++ Redistributable](https://aka.ms/vs/17/release/vc_redist.x64.exe)

## Quick Start

```bash
# 1) Check/fix local prerequisites
idempiere-cli doctor --fix

# 2) (optional, experimental build only) AI/provider setup
idempiere-cli config init

# 3) Bootstrap local environment (Docker path)
idempiere-cli setup-dev-env --with-docker

# 4) Create and build first plugin
idempiere-cli init org.mycompany.myplugin
idempiere-cli build --dir ./myplugin/org.mycompany.myplugin.base
```

## Build Flavors

- Core (default, deterministic): `./mvnw clean package`
- Experimental (enables AI/skills generation stack): `./mvnw clean package -Pexp`

## Documentation

Start from the [Documentation Hub](docs/README.md).

Practical guides:
- [Onboarding in 15 Minutes](docs/jtbd/01-onboarding-15-min.md)
- [Daily Plugin Development Loop](docs/jtbd/02-daily-plugin-loop.md)
- [Troubleshooting Guide](docs/jtbd/03-troubleshooting.md)
- [Automation and Exit Codes](docs/jtbd/04-automation-exit-codes.md)
- [AI Generation Cycle](docs/jtbd/05-ai-generation-cycle.md)
- [Pre-Release Smoke Validation](docs/jtbd/06-pre-release-smoke.md)

Reference:
- [Command Reference](docs/reference/commands.md)
- [Configuration and AI](docs/reference/configuration.md)
- [Build and Release](docs/reference/build-release.md)
- [Release Checklist](docs/reference/release-checklist.md)
- [Known Limitations](docs/reference/known-limitations.md)
- [Architecture, Scope, and Roadmap](docs/reference/architecture-roadmap.md)

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
