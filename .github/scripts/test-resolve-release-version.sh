#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")" && pwd)"
resolve="$root/resolve-release-version.sh"

expect() {
  local event="$1" input="$2" version="$3" tag="$4" actual
  actual="$(bash "$resolve" "$event" "$input")"
  local expected
  expected="$(printf 'APP_VERSION=%s\nRELEASE_TAG=%s' "$version" "$tag")"
  if [ "$actual" != "$expected" ]; then
    echo "Expected for event=$event input='$input':" >&2
    printf '%s\n' "$expected" >&2
    echo "Got:" >&2
    printf '%s\n' "$actual" >&2
    exit 1
  fi
}

expect_fail() {
  if bash "$resolve" "$1" "$2" >/dev/null 2>&1; then
    echo "Expected failure for event=$1 input='$2'" >&2
    exit 1
  fi
}

expect release "1.2.3" "1.2.3" "1.2.3"
expect release "v1.2.3" "1.2.3" "v1.2.3"
expect release "V1.0.0" "1.0.0" "V1.0.0"
expect release "  v1.2  " "1.2" "v1.2"
expect release "1" "1" "1"
expect release "1.2.3.4" "1.2.3.4" "1.2.3.4"
expect workflow_dispatch "1.2.3" "1.2.3" "v1.2.3"
expect workflow_dispatch "v1.2.3" "1.2.3" "v1.2.3"
expect workflow_dispatch "  V2.0  " "2.0" "v2.0"

expect_fail release ""
expect_fail release "1.2.3-SNAPSHOT"
expect_fail release "v1.02.0"
expect_fail release "vv1.2.3"
expect_fail release "1.2.3.4.5"
expect_fail release "release-1.2.3"
expect_fail workflow_dispatch "1.2.3-beta"
expect_fail other "1.2.3"

echo "release version checks passed"
