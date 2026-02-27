---
name: idempiere-cli-build-deploy
description: Build, deploy, and distribute iDempiere plugins using Maven and idempiere-cli. Use when the user wants to compile, deploy, or distribute their iDempiere plugin.
---

# iDempiere CLI - Build, Deploy and Distribute

This skill guides you through building, deploying, and distributing iDempiere plugins.

## Workflow

1. **Build**: Compile the plugin using Maven/Tycho (`./mvnw verify`).
2. **Deploy**: Install the built plugin to an iDempiere instance (`idempiere-cli deploy`).
3. **Distribute**: Create distributable archives (`idempiere-cli dist`).

## Build

Compiles the plugin into an OSGi bundle (JAR) placed in `target/`. Use Maven directly:

```bash
# Basic build (from plugin or multi-module root directory)
./mvnw verify

# Clean build with tests skipped
./mvnw clean verify -DskipTests

# On Windows
mvnw.cmd verify
```

### Dependency Resolution

- **With `-Didempiere.core.repository.url`**: Uses a local p2 repository. Faster, works offline.
  ```bash
  ./mvnw verify -Didempiere.core.repository.url=file:///opt/idempiere/org.idempiere.p2/target/repository
  ```
- **Without**: Downloads dependencies from remote p2 repositories. Slower, requires network.

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

## Distribute

Creates distributable packages. Auto-detects project type (core or plugin).

```bash
# Plugin: build and create distribution packages
idempiere-cli dist --dir=./my-plugin

# Plugin: skip build, package existing artifacts
idempiere-cli dist --dir=./my-plugin --skip-build

# Core: build and create server distribution ZIPs
idempiere-cli dist --source-dir=./idempiere

# Core: skip build, repackage existing artifacts
idempiere-cli dist --source-dir=./idempiere --skip-build
```

### What dist produces

| Project Type | Artifacts |
|-------------|-----------|
| Plugin (standalone) | `pluginId-version.zip` (JAR + metadata) + checksums |
| Plugin (multi-module with p2) | `pluginId-version.zip` + `pluginId-version-p2.zip` (p2 repository) + checksums |
| Core | Per-platform server ZIPs (Linux/Windows/macOS) + checksums |

### Dist Options

| Option            | Default | Description                               |
|-------------------|---------|-------------------------------------------|
| `--dir`           | -       | Plugin project directory                  |
| `--source-dir`    | `.`     | iDempiere core source directory           |
| `--skip-build`    | false   | Skip Maven build, use existing artifacts  |
| `--clean`         | false   | Run clean before build                    |
| `--version-label` | -       | Version label (default: auto-detect)      |
| `--output`        | `dist`  | Output directory                          |

## Complete Workflow Example

```bash
# Navigate to plugin
cd org.mycompany.myplugin

# Build
./mvnw clean verify

# Validate before deploying
idempiere-cli validate

# Deploy to local instance
idempiere-cli deploy --target=/opt/idempiere --hot

# Package for distribution
idempiere-cli dist --dir=. --skip-build
```

## CI/CD Integration

```bash
# Typical CI pipeline
idempiere-cli validate --strict --quiet
idempiere-cli dist --dir=.
```

## Notes

- Always build (`./mvnw verify`) before `deploy`. The `dist` command builds automatically unless `--skip-build` is used.
- For multi-module projects, run from the project root.
- Hot deploy is the fastest development cycle but requires OSGi console access.
