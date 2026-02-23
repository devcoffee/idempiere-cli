# Architecture, Scope, and Roadmap

## Scope

`idempiere-cli` is focused on plugin engineering workflows:
- setup and diagnostics
- scaffolding and incremental generation
- validation and dependency checks
- build/deploy/package/migrate automation

Out of scope:
- replacing iDempiere runtime operations
- replacing Application Dictionary workflows

Contract reference: [Core Contract (v1)](core-contract.md).

## Project Structure (high level)

```text
src/main/java/org/idempiere/cli/
  commands/
  plugin/
  plugins/experimental/
  service/
  service/skills/
  model/

src/main/resources/templates/

src/test/java/
```

## Ecosystem Positioning

- CLI handles local engineering workflows.
- Runtime operation and AD-level AI workflows are complementary concerns.
- Core CLI is deterministic by design; experimental generation features are isolated behind build flavor/profile.
- External AI agents are the primary place for iterative code authoring; CLI remains the quality/integrity layer.

## Extension Direction

The codebase now exposes internal plugin extension points for generation workflows:
- `plugin/add/AddGenerationPlugin` for optional non-template generation providers
- `service/skills/SkillsService` abstraction with core fallback implementation

This keeps core commands stable while allowing optional feature modules.

See: [Ecosystem Complementarity Analysis](../strategy/ECOSYSTEM.md)

## Roadmap (pragmatic)

Short-term:
- keep hardening setup reliability and troubleshooting
- improve bundle mapping coverage in dependency analysis

Medium-term:
- MCP-aware integrations
- publish/deploy automation improvements

Long-term:
- stronger ecosystem integrations and workflow automation
