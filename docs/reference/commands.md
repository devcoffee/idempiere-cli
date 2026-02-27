# Command Reference

This page is the practical command index. For complete flags, use `--help` per command.
For stability guarantees and compatibility scope, see [Core Contract (v1)](core-contract.md).

## Environment

### `doctor`
Check required tools and optional prerequisites.

```bash
idempiere-cli doctor
idempiere-cli doctor --fix
idempiere-cli doctor --dir ./my-plugin
idempiere-cli doctor --json
```

### `setup-dev-env`
Bootstrap source, DB, and (optionally) Eclipse workspace.

Java preflight is branch-aware:
- `--branch=release-12` requires Java 17
- `--branch=master` and `release-13+` require Java 21

```bash
idempiere-cli setup-dev-env --with-docker
idempiere-cli setup-dev-env --db=oracle --with-docker
idempiere-cli setup-dev-env --with-docker --skip-workspace --non-interactive
```

## Scaffolding

### `init`
Create a new plugin project.

```bash
idempiere-cli init org.mycompany.myplugin
idempiere-cli init org.mycompany.myplugin --standalone
idempiere-cli init org.mycompany.myplugin --with-fragment --with-feature
idempiere-cli init org.mycompany.myplugin --interactive
```

### `add`
Add modules or components to existing projects.

```bash
idempiere-cli add plugin org.mycompany.myplugin.extra
idempiere-cli add fragment
idempiere-cli add feature

idempiere-cli add callout --name=MyCallout --to=./plugin
idempiere-cli add process --name=MyProcess --to=./plugin
idempiere-cli add jasper-report --name=SalesSummary --to=./plugin
idempiere-cli add test --dir=./plugin
```

`add jasper-report` scaffolds:
- `reports/<Name>.jrxml`
- `src/<package>/fontfamily.xml`
- `src/<package>/jasperreports_extension.properties`

Internal AI flags (`--prompt`, `--show-ai-prompt`, `--save-ai-debug`) are hidden from public help.
Use them only for internal/dev diagnostics; they are not part of the released core contract.

## Quality and Analysis

### `validate`
Validate plugin structure and conventions.

```bash
idempiere-cli validate ./plugin
idempiere-cli validate --strict ./plugin
idempiere-cli validate --json ./plugin
```

Exit codes:
- `0`: valid (or only warnings in non-strict mode)
- `1`: validation errors
- `2`: strict mode warning failure (`--strict` with warnings)

### `deps`
Analyze imports vs `Require-Bundle`.

```bash
idempiere-cli deps --dir=./plugin
idempiere-cli deps --json --dir=./plugin
```

### `info`
Show plugin metadata and component overview.

```bash
idempiere-cli info --dir=./plugin
idempiere-cli info --json --dir=./plugin
idempiere-cli info --verbose --dir=./plugin
idempiere-cli info --dir=./my-multi-module-root
```

Includes:
- OSGi surface summary (`Require-Bundle`, `Import-Package`, `Export-Package`)
- registered `plugin.xml` extensions (when present)
- DS component declarations (`Service-Component` / `OSGI-INF`)
- build artifact status from `target/*.jar`
- multi-module overview when run at project root

## Deploy & Distribution

### `deploy`
Deploy built JAR to iDempiere instance.

If `--dir` points to a multi-module root, CLI resolves the primary `.base` module automatically.

```bash
idempiere-cli deploy --dir=./plugin --target=/opt/idempiere
idempiere-cli deploy --dir=./plugin --target=/opt/idempiere --hot
idempiere-cli deploy --dir=./multi-module-root --target=/opt/idempiere
```

### `migrate`
Migrate plugin between iDempiere platform versions.

```bash
idempiere-cli migrate --from=12 --to=13 --dir=./plugin
```

### `dist`
Create distribution packages for iDempiere core or plugins.

Auto-detects project type (core source vs plugin) and produces appropriate artifacts:
- **Core**: per-platform server ZIPs + checksums (Jenkins/SourceForge format)
- **Plugin (multi-module)**: plugin JAR ZIP + p2 repository ZIP + checksums
- **Plugin (standalone)**: plugin JAR ZIP + checksums

```bash
# Core: build and create server distribution ZIPs
idempiere-cli dist --source-dir=./idempiere --output=./dist

# Core: skip build, just repackage existing artifacts
idempiere-cli dist --source-dir=./idempiere --skip-build

# Core: full clean build with custom version label
idempiere-cli dist --source-dir=./idempiere --clean --version-label=13

# Plugin: build and package (auto-detects multi-module or standalone)
idempiere-cli dist --dir=./my-plugin

# Plugin: skip build, just package existing artifacts
idempiere-cli dist --dir=./my-plugin --skip-build
```

## Utilities

### `config`
Manage CLI config.

```bash
idempiere-cli config init
idempiere-cli config show
idempiere-cli config get defaults.vendor
idempiere-cli config set defaults.vendor MyCompany
```

### `skills`
Internal/experimental command (hidden from default help).
Manage skill sources when explicitly working on experimental AI path.

```bash
idempiere-cli skills list
idempiere-cli skills sync
idempiere-cli skills which idempiere-cli-build-deploy
idempiere-cli skills source list
idempiere-cli skills source add --name=official --url=https://github.com/hengsin/idempiere-skills.git --priority=1
idempiere-cli skills source remove --name=official
```

### `upgrade`
Self-update from GitHub releases.

```bash
idempiere-cli upgrade
```

### `generate-completion`
Generate shell completion script.

```bash
idempiere-cli generate-completion > ~/.idempiere-cli-completion.bash
```
