#!/usr/bin/env bash
set -euo pipefail
source_root=$(cd "$(dirname "$0")/.." && pwd)
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/scripts"
cp "$source_root/scripts/release.sh" "$fixture/scripts/"
cd "$fixture"
printf 'versionName=0.1.0\nversionCode=1\n' > version.properties
git init -q
git config user.email test@example.invalid
git config user.name 'Release test'
git add .
git commit -qm 'Test fixture'
expect_failure() {
  if "$@" > /dev/null 2>&1; then
    echo "Expected failure: $*" >&2
    exit 1
  fi
}
GITHUB_REF=refs/tags/v0.1.0 bash scripts/release.sh check
expect_failure env GITHUB_REF=refs/heads/main bash scripts/release.sh check
expect_failure env GITHUB_REF=refs/tags/v0.2.0 bash scripts/release.sh check
expect_failure env VERSION=invalid VERSION_CODE=2 bash scripts/release.sh prepare
expect_failure env VERSION=0.2.0 VERSION_CODE=1 bash scripts/release.sh prepare
VERSION=0.2.0 VERSION_CODE=2 bash scripts/release.sh prepare
expect_failure bash scripts/release.sh tag
git add version.properties
git commit -qm 'Bump version'
bash scripts/release.sh tag
[[ "$(git rev-parse 'v0.2.0^{commit}')" == "$(git rev-parse HEAD)" ]]
expect_failure bash scripts/release.sh tag
echo 'Release script checks passed.'
