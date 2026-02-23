#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MVNW="${MVNW:-$ROOT_DIR/mvnw}"
CORE_CONTRACT_GROUP="${CORE_CONTRACT_GROUP:-core-contract}"
CORE_CONTRACT_TESTS="${CORE_CONTRACT_TESTS:-}"

echo "[core-contract] Building core/default flavor..."
"$MVNW" -q -DskipTests package

echo "[core-contract] Running contract test suite..."
if [[ -n "$CORE_CONTRACT_TESTS" ]]; then
  "$MVNW" -q -Dtest="$CORE_CONTRACT_TESTS" test
else
  "$MVNW" -q -Dgroups="$CORE_CONTRACT_GROUP" test
fi

echo "[core-contract] OK"
