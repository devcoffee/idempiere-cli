---
name: idempiere-cli-build-deploy
description: Build, deploy, and package iDempiere plugins using idempiere-cli. Use when the user wants to compile, deploy, or distribute their iDempiere plugin.
---

# iDempiere CLI - Build, Deploy and Package

This skill guides you through building, deploying, and packaging iDempiere plugins using `idempiere-cli`.

## Workflow

1. **Build**: Compile the plugin using Maven/Tycho.
2. **Deploy**: Install the built plugin to an iDempiere instance.
3. **Package**: Create a distributable archive (zip or p2 update site).

## Build

Compiles the plugin into an OSGi bundle (JAR) placed in `target/`.

```bash
# Basic build (from plugin directory)
idempiere-cli build

# Clean build with tests skipped
idempiere-cli build --clean --skip-tests

# Build with local iDempiere for dependency resolution (faster)
idempiere-cli build --idempiere-home=/opt/idempiere

# Using IDEMPIERE_HOME environment variable
export IDEMPIERE_HOME=/opt/idempiere
idempiere-cli build

# Force update of snapshot dependencies
idempiere-cli build --update

# Specify plugin directory
idempiere-cli build --dir=/path/to/my-plugin

# Pass additional Maven arguments
idempiere-cli build -A="-X -e"

# CI/CD build (disable p2 mirrors for reliability)
idempiere-cli build --clean --skip-tests --disable-p2-mirrors
```

### Build Options

| Option                | Default | Description                                      |
|-----------------------|---------|--------------------------------------------------|
| `--dir`               | `.`     | Plugin directory                                 |
| `--idempiere-home`    | -       | Path to iDempiere installation (for dependencies)|
| `--clean`             | false   | Run clean before build                           |
| `--skip-tests`        | false   | Skip tests during build                          |
| `--update` / `-U`     | false   | Force update of snapshots                        |
| `--disable-p2-mirrors`| true    | Disable p2 mirrors (recommended for CI)          |
| `--maven-args` / `-A` | -       | Additional Maven arguments                       |

### Dependency Resolution

- **With `--idempiere-home`**: Uses the local p2 repository from the iDempiere installation. Faster, works offline.
- **Without `--idempiere-home`**: Downloads dependencies from remote p2 repositories. Slower, requires network.

## Deploy

Installs the built JAR to an iDempiere instance.

```bash
# Copy deploy (requires restart)
idempiere-cli deploy --target=/opt/idempiere

# Hot deploy via OSGi console (no restart needed)
idempiere-cli deploy --target=/opt/idempiere --hot

# Hot deploy to remote instance
idempiere-cli deploy --target=/opt/idempiere --hot \
  --osgi-host=192.168.1.100 --osgi-port=12612

# Specify plugin directory
idempiere-cli deploy --dir=/path/to/my-plugin --target=/opt/idempiere
```

### Deploy Modes

| Mode        | Flag   | Restart Required | How It Works                           |
|-------------|--------|------------------|----------------------------------------|
| Copy deploy | -      | Yes              | Copies JAR to `plugins/` directory     |
| Hot deploy  | `--hot`| No               | Installs via OSGi console (port 12612) |

### Deploy Options

| Option         | Default    | Description                     |
|----------------|------------|---------------------------------|
| `--dir`        | `.`        | Plugin directory                |
| `--target`     | (required) | Path to iDempiere installation  |
| `--hot`        | false      | Hot deploy via OSGi console     |
| `--osgi-host`  | localhost  | OSGi console host               |
| `--osgi-port`  | 12612      | OSGi console port               |

### Hot Deploy Requirements

Hot deploy requires the OSGi console to be enabled in iDempiere. The console listens on port 12612 by default.

## Package

Creates a distributable package for the plugin.

```bash
# Create zip archive
idempiere-cli package --format=zip

# Create p2 update site (requires multi-module project with .p2 module)
idempiere-cli package --format=p2

# Custom output directory
idempiere-cli package --format=zip --output=/tmp/releases

# Specify plugin directory
idempiere-cli package --dir=/path/to/my-plugin --format=zip
```

### Package Formats

| Format | Description                              | Requirements           |
|--------|------------------------------------------|------------------------|
| `zip`  | Simple archive of the built JAR          | Built plugin           |
| `p2`   | Eclipse update site for managed installs | Multi-module with .p2  |

### Package Options

| Option     | Default | Description               |
|------------|---------|---------------------------|
| `--dir`    | `.`     | Plugin directory           |
| `--format` | `zip`   | Package format: zip or p2  |
| `--output` | -       | Output directory           |

## Complete Workflow Example

```bash
# Navigate to plugin
cd org.mycompany.myplugin

# Build
idempiere-cli build --idempiere-home=/opt/idempiere --clean

# Validate before deploying
idempiere-cli validate

# Deploy to local instance
idempiere-cli deploy --target=/opt/idempiere --hot

# Package for distribution
idempiere-cli package --format=zip --output=./dist
```

## CI/CD Integration

```bash
# Typical CI pipeline
idempiere-cli build --clean --skip-tests --disable-p2-mirrors
idempiere-cli validate --strict --quiet
idempiere-cli package --format=zip --output=./artifacts
```

## Notes

- Always run `idempiere-cli build` before `deploy` or `package`.
- The `build` command must be run from within a valid iDempiere plugin directory (with `META-INF/MANIFEST.MF`).
- For multi-module projects, run `build` from the parent directory.
- Hot deploy is the fastest development cycle but requires OSGi console access.
