#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MVNW="${MVNW:-$ROOT_DIR/mvnw}"

CORE_CONTRACT_TESTS="${CORE_CONTRACT_TESTS:-CoreContractCommandSurfaceTest,CommandExitCodeContractTest,DoctorCommandTest,BuildCommandTest,DeployCommandTest,PackageCommandTest,MigrateCommandTest,DiffSchemaCommandTest}"

echo "[core-contract] Building core/default flavor..."
"$MVNW" -q -DskipTests package

echo "[core-contract] Running contract test suite..."
"$MVNW" -q -Dtest="$CORE_CONTRACT_TESTS" test

echo "[core-contract] OK"
