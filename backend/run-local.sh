#!/usr/bin/env bash
# Runs the backend with local secrets from .env.local (git-ignored) and the Java 21 that this project needs.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME_21:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
if [ -f .env.local ]; then
  set -a; . ./.env.local; set +a
else
  echo "No .env.local found - copy .env.example to .env.local and fill it in." >&2
fi
# ./run-local.sh dev  -> also serves ../frontend live from disk (profile "dev")
if [ "${1:-}" = "dev" ]; then
  shift
  set -- "-Dspring-boot.run.profiles=dev" "$@"
fi
exec mvn spring-boot:run "$@"
