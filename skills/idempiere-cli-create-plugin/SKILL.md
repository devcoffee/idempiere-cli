---
name: idempiere-cli-create-plugin
description: Scaffold a new iDempiere plugin project using idempiere-cli. Use when the user wants to create a new plugin, extension, or customization for iDempiere.
---

# iDempiere CLI - Create Plugin

This skill guides you through creating a new iDempiere plugin project using `idempiere-cli init`.

## Workflow

1. **Choose project structure**: Multi-module (default, recommended) or standalone.
2. **Select components**: Choose which component stubs to include (callout, process, form, etc.).
3. **Run init command**: Execute `idempiere-cli init` with the desired options.

## Basic Usage

```bash
# Interactive mode (prompts for component selection)
idempiere-cli init org.mycompany.myplugin

# Non-interactive with specific components
idempiere-cli init org.mycompany.myplugin --with-callout --with-process

# Standalone plugin (single module, no parent POM)
idempiere-cli init org.mycompany.myplugin --standalone
```

## Project Structure Options

### Multi-module (default)

Creates a parent project with sub-modules:

```
org.mycompany.myplugin/
  pom.xml                            (parent POM)
  org.mycompany.myplugin/            (main plugin)
    META-INF/MANIFEST.MF
    pom.xml
    src/
  org.mycompany.myplugin.test/       (test module)
    pom.xml
    src/
```

### Standalone

Creates a single plugin directory:

```
org.mycompany.myplugin/
  META-INF/MANIFEST.MF
  build.properties
  pom.xml
  src/
```

## Component Options

| Flag                    | Component Type                          |
|-------------------------|-----------------------------------------|
| `--with-callout`        | Column-level business logic             |
| `--with-process`        | Server-side batch process               |
| `--with-event-handler`  | Model lifecycle hooks                   |
| `--with-zk-form`        | Programmatic ZK form                    |
| `--with-report`         | Basic report process                    |
| `--with-jasper-report`  | Jasper report with .jrxml template      |
| `--with-rest-extension` | REST API endpoint                       |

## Additional Options

| Flag                     | Description                                    |
|--------------------------|------------------------------------------------|
| `--name=<name>`          | Project directory name (default: last segment of plugin ID) |
| `--with-fragment`        | Include a fragment module (multi-module only)   |
| `--with-feature`         | Include a feature module for p2 distribution    |
| `--no-test`              | Skip test module creation                       |
| `--fragment-host`        | Fragment host bundle (default: org.adempiere.ui.zk) |
| `--eclipse-project`      | Generate Eclipse `.project` files (default: true) |
| `--no-eclipse-project`   | Skip Eclipse `.project` file generation         |
| `--no-interactive`       | Disable interactive prompts                     |

## Example Usage

### Simple plugin with callout and process

```bash
idempiere-cli init org.mycompany.sales --with-callout --with-process
```

### Full-featured plugin with all common components

```bash
idempiere-cli init org.mycompany.customization \
  --with-callout \
  --with-process \
  --with-event-handler \
  --with-zk-form
```

### Plugin with REST API extension

```bash
idempiere-cli init org.mycompany.api --with-rest-extension
```

### Standalone plugin (for simpler use cases)

```bash
idempiere-cli init org.mycompany.simple --standalone --with-process
```

### Plugin with fragment module (extends ZK UI)

```bash
idempiere-cli init org.mycompany.uicustom --with-fragment
```

## Generated File Structure

After `init`, the plugin includes:

- `META-INF/MANIFEST.MF` - OSGi bundle metadata with proper Require-Bundle dependencies
- `build.properties` - Tycho/PDE build configuration
- `pom.xml` - Maven/Tycho POM with iDempiere p2 repositories
- `OSGI-INF/*.xml` - Service component descriptors (for registered components)
- `.project` - Eclipse project file (unless `--no-eclipse-project`)
- `.gitignore` - Ignores build output, IDE files, and OS files
- `src/` - Java source with generated component stubs
- `mvnw`, `mvnw.cmd` - Maven Wrapper scripts

## What Happens Next

After creating the plugin, the typical workflow is:

```bash
cd myplugin

# Add more components later (use --prompt for AI-powered generation)
idempiere-cli add callout MyNewCallout --prompt="Validate order total against credit limit"
idempiere-cli add process MyBatchProcess

# Build the plugin
idempiere-cli build --idempiere-home=/path/to/idempiere

# Validate before deployment
idempiere-cli validate

# Deploy to iDempiere
idempiere-cli deploy --target=/path/to/idempiere
```

## Notes

- The plugin ID should follow Java package naming conventions (e.g., `org.mycompany.myplugin`).
- The project directory uses the last segment of the plugin ID by default (e.g., `myplugin/`). Use `--name` to override.
- Multi-module is recommended for plugins that will have tests and p2 packaging.
- Use standalone for simple, single-purpose plugins.
- Interactive mode auto-detects if running in a terminal and prompts accordingly.
- Components are generated using built-in templates. For AI-powered generation with context-aware code, use `add <component> --prompt="..."` after creating the project.
