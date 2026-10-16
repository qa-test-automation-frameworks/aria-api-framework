#!/usr/bin/env bash
set -euo pipefail

java_output=$(java -version 2>&1)
java_version=$(printf '%s\n' "$java_output" | sed -n '1s/.*version "\([^"]*\)".*/\1/p')
gradle_output=$(./gradlew --version)
gradle_version=$(printf '%s\n' "$gradle_output" | awk '$1 == "Gradle" { print $2; exit }')

if [[ -z "$java_version" || -z "$gradle_version" ]]; then
  echo "Could not determine actual Java and Gradle versions" >&2
  exit 1
fi

printf 'JAVA_VERSION=%s\nGRADLE_VERSION=%s\n' "$java_version" "$gradle_version" >> "$GITHUB_ENV"
printf 'Captured Java %s and Gradle %s for evidence records.\n' "$java_version" "$gradle_version"
