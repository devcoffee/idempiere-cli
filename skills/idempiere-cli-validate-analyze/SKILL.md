---
name: idempiere-cli-validate-analyze
description: Validate, inspect, and analyze iDempiere plugins using idempiere-cli. Use when the user wants to check plugin structure, view metadata, or analyze dependencies.
---

# iDempiere CLI - Validate and Analyze

This skill guides you through validating and analyzing iDempiere plugins using `idempiere-cli`.

## Available Commands

| Command       | Purpose                                  |
|---------------|------------------------------------------|
| `validate`    | Check plugin structure and configuration |
| `info`        | Show plugin metadata and components      |
| `deps`        | Analyze plugin dependencies              |

## Validate - Plugin Structure Check

Validates the plugin's OSGi configuration files for correctness.

```bash
# Validate current directory
idempiere-cli validate

# Validate specific plugin
idempiere-cli validate /path/to/my-plugin

# Strict mode (treat warnings as errors)
idempiere-cli validate --strict

# Quiet mode (no output on success)
idempiere-cli validate --quiet

# JSON output for CI/CD parsing
idempiere-cli validate --json
```

### What validate checks

- `META-INF/MANIFEST.MF` - OSGi bundle headers and syntax
- `build.properties` - Tycho build configuration
- `pom.xml` - Maven/Tycho configuration
- `OSGI-INF/*.xml` - Service component descriptors
- `plugin.xml` - Eclipse extension points
- Java sources - Package structure and basic checks

### Exit Codes

| Code | Meaning                              |
|------|--------------------------------------|
| 0    | Validation passed (no errors)        |
| 1    | Validation failed (errors found)     |
| 2    | Passed with warnings (with --strict) |

### CI/CD Usage

```bash
# Fail build on any error
idempiere-cli validate ./my-plugin || exit 1

# Fail build on warnings too
idempiere-cli validate --strict ./my-plugin || exit 1

# Quiet mode for scripts
idempiere-cli validate --quiet ./my-plugin && echo "OK"
```

## Info - Plugin Metadata

Displays detailed information about the plugin.

```bash
# Show plugin info
idempiere-cli info

# Specific directory
idempiere-cli info --dir=/path/to/my-plugin

# JSON output
idempiere-cli info --json
```

### What info shows

- Plugin ID (Bundle-SymbolicName)
- Version (Bundle-Version)
- Vendor (Bundle-Vendor)
- Required bundles (Require-Bundle)
- Detected components (callouts, processes, forms, etc.)

## Deps - Dependency Analysis

Analyzes plugin dependencies by scanning Java imports and comparing with MANIFEST.MF.

```bash
# Analyze dependencies
idempiere-cli deps

# Specific directory
idempiere-cli deps --dir=/path/to/my-plugin

# JSON output
idempiere-cli deps --json
```

### What deps reports

- **Required bundles**: Bundles your plugin depends on
- **Missing bundles**: Dependencies found in imports but not in MANIFEST.MF
- **Unused bundles**: Bundles in MANIFEST.MF not referenced by any import

## Complete Analysis Example

```bash
cd org.mycompany.myplugin

# 1. Check plugin structure
idempiere-cli validate

# 2. View plugin metadata
idempiere-cli info

# 3. Check for missing/unused dependencies
idempiere-cli deps
```

## Notes

- All analysis commands work on the current directory by default.
- JSON output (`--json`) is available for most commands, useful for CI/CD pipelines and scripting.
- `validate` is recommended before every `deploy` to catch configuration issues early.
