# Onboarding in 15 Minutes

This guide gets a new machine from zero to a working iDempiere plugin loop quickly.

## Goal

At the end you should be able to:
- scaffold a plugin
- validate it
- build it

## 1. Install CLI

```bash
curl -fsSL https://raw.githubusercontent.com/devcoffee/idempiere-cli/main/install.sh | bash
```

## 2. Check Environment

```bash
idempiere-cli doctor
```

Java compatibility by iDempiere line:
- iDempiere `release-12`: Java 17
- iDempiere `master` / `release-13+`: Java 21

If required tools are missing:

```bash
idempiere-cli doctor --fix
```

## 3. Optional: Configure Defaults

```bash
idempiere-cli config init
```

If you skip this, scaffolding still works with built-in templates.

## 4. Setup Development Environment (Recommended)

Docker path (most reliable):

```bash
idempiere-cli setup-dev-env --with-docker
```

Headless/server path (skip Eclipse):

```bash
idempiere-cli setup-dev-env --with-docker --skip-workspace --non-interactive
```

## 5. Create First Plugin

```bash
idempiere-cli init org.mycompany.myplugin
```

## 6. Validate and Build

```bash
idempiere-cli validate ./myplugin/org.mycompany.myplugin.base
cd myplugin && ./mvnw verify
```

For standalone projects, run `validate` directly in the plugin directory.

## Done Checklist

- `doctor` shows required tools as available
- `init` generated plugin structure
- `validate` returns success
- `./mvnw verify` produced a JAR in `target/`
