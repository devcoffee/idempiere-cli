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

If required tools are missing:

```bash
idempiere-cli doctor --fix
```

## 3. Optional: Configure AI

```bash
idempiere-cli config init
```

If you skip this, code generation still works with templates.

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
idempiere-cli build --dir ./myplugin/org.mycompany.myplugin.base
```

For standalone projects, run `validate`/`build` directly in the plugin directory.

## Done Checklist

- `doctor` shows required tools as available
- `init` generated plugin structure
- `validate` returns success
- `build` produced a JAR in `target/`

