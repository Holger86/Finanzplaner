#!/usr/bin/env bash
# Resolve a GitHub Release tag or workflow_dispatch version to jpackage inputs.
# Usage: resolve-release-version.sh <release|workflow_dispatch> <raw-version>
# Prints:
#   APP_VERSION=<1 to 4 numeric components>
#   RELEASE_TAG=<tag to create or update>
set -euo pipefail

event="${1:-}"
raw="${2:-}"
raw="${raw//[[:space:]]/}"

version="${raw#v}"
version="${version#V}"
if [[ ! "$version" =~ ^([0-9]|[1-9][0-9]*)(\.([0-9]|[1-9][0-9]*)){0,3}$ ]]; then
  echo "Invalid version: '${2:-}'. Expected 1 to 4 numeric components, for example 1.2.3 or v1.2.3." >&2
  exit 1
fi

case "$raw" in
  "$version"|v"$version"|V"$version") ;;
  *)
    echo "Release tag must be ${version} or v${version}, got: ${raw}" >&2
    exit 1
    ;;
esac

if [ "$event" = "workflow_dispatch" ]; then
  tag="v${version}"
elif [ "$event" = "release" ]; then
  tag="$raw"
else
  echo "Unsupported event: ${event}" >&2
  exit 1
fi

printf 'APP_VERSION=%s\nRELEASE_TAG=%s\n' "$version" "$tag"
