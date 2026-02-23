# Build and Release

## Build from Source

```bash
git clone https://github.com/devcoffee/idempiere-cli.git
cd idempiere-cli
./mvnw clean package
```

Build variants:

- Core/default (deterministic toolchain only): `./mvnw clean package`
- Experimental (enables AI generation stack): `./mvnw clean package -Pexp`

## Native Build (GraalVM Required)

```bash
./mvnw clean package -Pnative
```

Native + experimental:

```bash
./mvnw clean package -Pnative,exp
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
