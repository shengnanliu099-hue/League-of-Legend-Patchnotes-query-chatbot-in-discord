#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ROOT_DIR}/local.env"
JAR_FILE="${ROOT_DIR}/target/lol-version-watcher-1.0.0.jar"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}"
  echo "Create it from local.env.example first."
  exit 1
fi

if [[ ! -f "${JAR_FILE}" ]]; then
  echo "Missing ${JAR_FILE}"
  echo "Build first with: mvn -q -DskipTests package"
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

exec java -jar "${JAR_FILE}"
