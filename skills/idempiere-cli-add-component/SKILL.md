---
name: idempiere-cli-add-component
description: Add new components or modules to an existing iDempiere plugin using idempiere-cli. Use when the user wants to add a callout, process, event handler, form, or other component to their plugin.
---

# iDempiere CLI - Add Component

This skill guides you through adding new components and modules to an existing iDempiere plugin using `idempiere-cli add`.

## Workflow

1. **Navigate to plugin directory**: `cd` into the plugin root (where `META-INF/MANIFEST.MF` exists).
2. **Choose component type**: Select from 16+ available component types.
3. **Run add command**: Execute `idempiere-cli add <type> <ClassName>`.

## Component Commands

### Business Logic

```bash
# Callout - Column-level business logic with @Callout annotation
idempiere-cli add callout MyCallout

# Process - Server-side batch process with own AnnotationBasedProcessFactory
idempiere-cli add process MyProcess

# Process (mapped) - Process using global MappedProcessFactory (2Pack compatible)
idempiere-cli add process-mapped MyMappedProcess

# Event handler - Model lifecycle hooks (BeforeNew, AfterChange, etc.)
idempiere-cli add event-handler MyEventHandler
```

### User Interface

```bash
# ZK form - Programmatic ZK form extending ADForm
idempiere-cli add zk-form MyForm

# ZK form (ZUL) - Declarative ZUL-based form with controller
idempiere-cli add zk-form-zul MyZulForm

# Listbox group - Form with grouped/collapsible Listbox
idempiere-cli add listbox-group MyGroupForm

# WListbox editor - Form with custom WListbox column editors
idempiere-cli add wlistbox-editor MyEditorForm
```

### Reports

```bash
# Report - Basic report process
idempiere-cli add report MyReport

# Jasper report - Jasper report with Activator and .jrxml template
idempiere-cli add jasper-report MyJasperReport
```

### Validators and Extensions

```bash
# Window validator - Window-level event validation
idempiere-cli add window-validator MyWindowValidator

# REST extension - REST API endpoint using JAX-RS
idempiere-cli add rest-extension MyResource

# Facts validator - Accounting facts validation
idempiere-cli add facts-validator MyFactsValidator
```

### Model and Testing

```bash
# Model - Generate I_/X_/M_ model classes from database
idempiere-cli add model --table=MY_Table

# Test - JUnit 5 test for a specific component
idempiere-cli add test MyCalloutTest

# Base test - Base test class using AbstractTestCase
idempiere-cli add base-test
```

### Module Commands (Multi-module projects)

```bash
# Add a new plugin module to multi-module project
idempiere-cli add plugin org.mycompany.project.reports

# Add a fragment module (extends another bundle)
idempiere-cli add fragment --host=org.adempiere.ui.zk

# Add a feature module (groups plugins for p2 installation)
idempiere-cli add feature

# Add Maven wrapper scripts
idempiere-cli add maven-wrapper
```

## Common Options

| Option           | Description                                  |
|------------------|----------------------------------------------|
| `--to=<path>`    | Target plugin directory (if not current dir)  |
| `--dir=<path>`   | Project directory (for module commands)       |

## How It Works

When you run `idempiere-cli add <type> <ClassName>`:

1. **Detects plugin structure**: Reads `META-INF/MANIFEST.MF` to find plugin ID and package.
2. **Generates Java source**: Creates the component class from built-in templates.
3. **Registers OSGi service**: Adds `OSGI-INF/<component>.xml` descriptor if needed.
4. **Updates MANIFEST.MF**: Adds `Service-Component` header entries.
5. **Reuses shared infrastructure**: Detects existing factories/activators and reuses them.

## AI-Powered Generation

For richer code generation, `idempiere-cli add` supports AI-powered scaffolding when an AI provider is configured:

```bash
# Configure AI provider
idempiere-cli config set ai.provider anthropic
idempiere-cli config set ai.api-key sk-ant-...

# AI generates contextual code based on skill files
idempiere-cli add callout OrderDateCallout
```

AI scaffolding uses SKILL.md files from configured skill sources to generate more complete, context-aware code.

## Example Usage

### Adding a callout to validate order dates

```bash
cd org.mycompany.sales
idempiere-cli add callout OrderDateCallout
```

This creates:
- `src/org/mycompany/sales/callout/OrderDateCallout.java`
- `OSGI-INF/org.mycompany.sales.callout.OrderDateCallout.xml`
- Updates `META-INF/MANIFEST.MF` with Service-Component entry

### Adding a process to an existing plugin

```bash
idempiere-cli add process GenerateInvoices --to=/path/to/org.mycompany.sales
```

### Adding model classes from database

```bash
idempiere-cli add model --table=MY_Custom_Table \
  --db-host=localhost --db-port=5432 \
  --db-name=idempiere --db-user=adempiere --db-pass=adempiere
```

## Notes

- Run `add` commands from within the plugin directory (where `META-INF/MANIFEST.MF` exists).
- The CLI auto-detects the plugin's base package from the plugin ID.
- Shared components (factories, activators) are reused across multiple `add` calls.
- Component types `process` and `process-mapped` differ in registration: `process` creates its own factory, `process-mapped` uses the global MappedProcessFactory (compatible with 2Pack import/export).
