# Troubleshooting Guide

Common failures and direct fixes.

## setup-dev-env fails in headless environment

Symptom:
- `setup-dev-env requires a graphical environment (display)`

Fix:

```bash
idempiere-cli setup-dev-env --with-docker --skip-workspace --non-interactive
```

## Docker not running / permission denied

Symptom:
- `Error: Docker is not running.`
- `Error: Docker permission denied.`

Fix:

```bash
idempiere-cli doctor --fix
```

Linux permission fix (manual):

```bash
sudo usermod -aG docker $USER
newgrp docker
```

## Cannot connect to database in setup

Symptom:
- `Cannot connect to database ...`

Fix options:
- use Docker (`--with-docker`)
- skip DB temporarily (`--skip-db`)
- verify local DB credentials/port

## Not an iDempiere plugin

Symptom:
- `Not an iDempiere plugin ...`

Fix:
- run command in a module containing `META-INF/MANIFEST.MF`
- or pass module path with `--dir`

Useful check:

```bash
idempiere-cli doctor --dir /path/to/plugin
```

## No built JAR found

Symptom:
- `No built .jar found in target/`

Fix:

```bash
idempiere-cli build --dir /path/to/plugin
```

Then retry `deploy` or `package`.

## p2 packaging fails

Symptom:
- `p2 format requires a multi-module project structure`
- `No .p2 module found`
- `p2 repository not found at ...`

Fix:
- ensure project was created as multi-module (`init` without `--standalone`)
- run build before package

```bash
cd /path/to/multi-module-root
idempiere-cli build --dir ./<base-plugin-module>
idempiere-cli package --format=p2
```

## Build fails and output is truncated

Symptom:
- build command fails with summary only

Fix:
- inspect the session log path printed by setup/build flow
- rerun with Maven debug args if needed:

```bash
idempiere-cli build --dir /path/to/plugin -A="-X -e"
```
