# Build and Release

## Build from Source

```bash
git clone https://github.com/devcoffee/idempiere-cli.git
cd idempiere-cli
./mvnw clean package
```

Build variants:

- Core/default (deterministic toolchain): `./mvnw clean package`
- Release policy: publish core artifacts only

## Native Build (GraalVM Required)

```bash
./mvnw clean package -Pnative
```

Outputs:
- JVM runner: `target/quarkus-app/quarkus-run.jar`
- native binary: `target/idempiere-cli-runner` (or `.exe` on Windows)

## Local Smoke

```bash
java -jar target/quarkus-app/quarkus-run.jar doctor
```

For a full pre-release smoke workflow, see:
- [Pre-Release Smoke Validation](../jtbd/06-pre-release-smoke.md)
- [Release Checklist](release-checklist.md)
- [Known Limitations](known-limitations.md)

## Release Flow

```bash
git tag v1.0.0
git push --tags
```

Tag push triggers GitHub Actions release build for supported platforms.
