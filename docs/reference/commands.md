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

### `diff-schema`
Compare model classes against database metadata.

```bash
idempiere-cli diff-schema --table=C_Order --dir=./plugin
```

### `info`
Show plugin metadata and component overview.

```bash
idempiere-cli info --dir=./plugin
idempiere-cli info --json --dir=./plugin
```

## Build, Deploy, Package

### `build`
Compile plugin module with Maven/Tycho.

If `--dir` points to a multi-module root, CLI resolves the primary `.base` module automatically.

```bash
idempiere-cli build --dir=./plugin --clean
idempiere-cli build --dir=./plugin --skip-tests
idempiere-cli build --dir=./plugin --idempiere-home=/opt/idempiere
idempiere-cli build --dir=./multi-module-root
```

### `deploy`
Deploy built JAR to iDempiere instance.

If `--dir` points to a multi-module root, CLI resolves the primary `.base` module automatically.

```bash
idempiere-cli deploy --dir=./plugin --target=/opt/idempiere
idempiere-cli deploy --dir=./plugin --target=/opt/idempiere --hot
idempiere-cli deploy --dir=./multi-module-root --target=/opt/idempiere
```

### `package`
Create distribution artifacts.

```bash
idempiere-cli package --dir=./plugin --format=zip

cd ./multi-module-root
idempiere-cli package --format=p2
```

### `migrate`
Migrate plugin between iDempiere platform versions.

```bash
idempiere-cli migrate --from=12 --to=13 --dir=./plugin
```

### `dist`
Create iDempiere server distributions.

```bash
idempiere-cli dist --source-dir=./idempiere --output=./dist
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
