#!/usr/bin/env bash
# Loads .env (if present) and runs the app. Usage: ./run.sh
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
else
  echo "No .env found — copy .env.example to .env and fill in OPENAI_API_KEY." >&2
  exit 1
fi

if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home)"
fi
export JAVA_HOME

exec ./mvnw spring-boot:run
