---
name: idempiere-cli-migrate
description: Migrate iDempiere plugins between platform versions using idempiere-cli. Use when the user wants to upgrade or downgrade their plugin to match a different iDempiere version.
---

# iDempiere CLI - Migrate Plugin

This skill guides you through migrating iDempiere plugins between platform versions using `idempiere-cli migrate`.

## Workflow

1. **Identify current version**: Check which iDempiere version the plugin targets.
2. **Run migrate**: Execute `idempiere-cli migrate --from=<current> --to=<target>`.
3. **Build and test**: Rebuild and test the plugin against the new version.

## Basic Usage

```bash
# Upgrade from v12 to v13
idempiere-cli migrate --from=12 --to=13

# Downgrade from v13 to v12
idempiere-cli migrate --from=13 --to=12

# Specify plugin directory
idempiere-cli migrate --from=12 --to=13 --dir=/path/to/my-plugin
```

## Supported Versions

| Version | Java | Tycho  | Eclipse Target  |
|---------|------|--------|-----------------|
| v12     | 17   | 4.0.4  | 2023-09         |
| v13     | 21   | 4.0.8  | 2024-09         |

## What migrate updates

### pom.xml
- `maven.compiler.release` (17 ↔ 21)
- Tycho version
- Repository URLs for target platform

### META-INF/MANIFEST.MF
- `Bundle-RequiredExecutionEnvironment` (JavaSE-17 ↔ JavaSE-21)
- `Require-Bundle` version ranges (bundle-version updates)

### build.properties
- `javacTarget` and `javacSource` settings

## Options

| Option    | Required | Description                        |
|-----------|----------|------------------------------------|
| `--from`  | Yes      | Source iDempiere major version     |
| `--to`    | Yes      | Target iDempiere major version     |
| `--dir`   | No       | Plugin directory (default: `.`)    |

## Example Usage

### Upgrading a plugin to iDempiere 13

```bash
cd org.mycompany.myplugin

# Migrate configuration files
idempiere-cli migrate --from=12 --to=13

# Rebuild with new Java version
idempiere-cli build --clean

# Validate the migrated plugin
idempiere-cli validate
```

### Downgrading for backward compatibility

```bash
# Create a branch for the older version
git checkout -b release-12

# Downgrade plugin to v12
idempiere-cli migrate --from=13 --to=12

# Build and test
idempiere-cli build --clean
```

## Notes

- Always commit your code before running `migrate`, so you can revert if needed.
- After migration, rebuild the plugin and run tests to verify compatibility.
- The migrate command updates configuration files only; it does not modify Java source code. If the new iDempiere version introduces API changes, you may need to update your source code manually.
- For multi-module projects, run `migrate` from the parent directory to update all modules.
