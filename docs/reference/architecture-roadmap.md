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

## Project Structure (high level)

```text
src/main/java/org/idempiere/cli/
  commands/
  service/
  service/ai/
  model/

src/main/resources/templates/

src/test/java/
```

## Ecosystem Positioning

- CLI handles local engineering workflows.
- Runtime operation and AD-level AI workflows are complementary concerns.

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

