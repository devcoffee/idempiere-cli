# Build and Release

## Build from Source

```bash
git clone https://github.com/devcoffee/idempiere-cli.git
cd idempiere-cli
./mvnw clean package
```

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

## Release Flow

```bash
git tag v1.0.0
git push --tags
```

Tag push triggers GitHub Actions release build for supported platforms.

