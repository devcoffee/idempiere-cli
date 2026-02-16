---
name: idempiere-cli-dist
description: Create iDempiere server distribution packages (ZIP) for all platforms. Use when the user wants to build and package the iDempiere server for deployment.
---

# iDempiere CLI - Server Distribution

This skill guides you through creating iDempiere server distribution packages using `idempiere-cli dist`.

## Overview

The `dist` command builds the iDempiere source and creates distribution ZIP files for all target platforms (Linux, Windows, macOS). The output is compatible with Jenkins/SourceForge format and the install-idempiere Ansible playbook.

## Usage

```bash
# Build and package from source directory
idempiere-cli dist --source-dir=/path/to/idempiere

# Skip build, just repackage existing artifacts (fast)
idempiere-cli dist --source-dir=/path/to/idempiere --skip-build

# Full clean build with custom version label
idempiere-cli dist --source-dir=/path/to/idempiere --clean --version-label=13

# Custom output directory
idempiere-cli dist --source-dir=/path/to/idempiere --output=/tmp/releases
```

## Options

| Option             | Default      | Description                                    |
|--------------------|--------------|------------------------------------------------|
| `--source-dir`     | `.`          | Path to iDempiere source directory             |
| `--skip-build`     | false        | Skip Maven build, use existing artifacts       |
| `--clean`          | false        | Run clean before build                         |
| `--version-label`  | auto-detect  | Version label for distribution packages        |
| `--output`         | `./dist`     | Output directory for distribution packages     |

## How It Works

1. **Validate** the source directory (must contain `pom.xml` and `org.idempiere.p2/`).
2. **Build** using Maven (`mvn verify` or `mvn clean verify`) unless `--skip-build`.
3. **Discover platforms** in `org.idempiere.p2/target/products/org.adempiere.server.product/`.
4. **Create ZIP** for each platform (linux/gtk/x86_64, win32/win32/x86_64, macosx/cocoa/x86_64, etc.).
5. **Generate checksums** (`checksums.txt` with SHA-256 hashes).

## Output Format

Each platform produces a ZIP file named:

```
idempiereServer{version}.{ws}.{os}.{arch}.zip
```

Internal structure:

```
idempiere.{ws}.{os}.{arch}/
  idempiere-server/
    <all server files>
```

Example files:

```
dist/
  idempiereServer12.gtk.linux.x86_64.zip
  idempiereServer12.win32.win32.x86_64.zip
  idempiereServer12.cocoa.macosx.x86_64.zip
  checksums.txt
```

## Version Detection

When `--version-label` is not specified, the version is auto-detected from the root `pom.xml`:

- `<version>12.0.0-SNAPSHOT</version>` -> version label `12`
- Falls back to `dev` if detection fails.

## Prerequisites

- iDempiere source code (cloned from the repository).
- Java 17+ and Maven (or the project's Maven Wrapper `mvnw`).
- A successful build (`mvn verify`) if using `--skip-build`.

## CI/CD Integration

```bash
# Full build and package
idempiere-cli dist --source-dir=./idempiere --clean

# Repackage from existing build (e.g., after CI build step)
idempiere-cli dist --source-dir=./idempiere --skip-build --output=./artifacts
```

## Notes

- The command does NOT move or modify source files. It reads from the build output and creates ZIPs.
- The Maven Wrapper (`mvnw`) is used automatically when available in the source directory.
- On Windows, `mvnw.cmd` or `mvn.cmd` is used instead.
