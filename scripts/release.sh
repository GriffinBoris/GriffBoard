#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

read_version() {
  version=$(sed -n 's/^versionName=//p' version.properties)
  code=$(sed -n 's/^versionCode=//p' version.properties)
  [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9]+([.-][a-zA-Z0-9]+)*)?$ ]] || { echo 'Invalid versionName.' >&2; exit 1; }
  [[ "$code" =~ ^[1-9][0-9]*$ ]] && (( code <= 2100000000 )) || { echo 'Invalid Android versionCode.' >&2; exit 1; }
}

read_version
case "${1:-check}" in
  check)
    if [[ -n "${GITHUB_REF:-}" ]]; then
      [[ "$GITHUB_REF" == "refs/tags/v$version" ]] || { echo "Release ref must be refs/tags/v$version." >&2; exit 1; }
    fi
    echo "GriffBoard $version ($code)"
    ;;
  prepare)
    : "${VERSION:?Set VERSION}" "${VERSION_CODE:?Set VERSION_CODE}"
    previous_code=$code
    version=$VERSION
    code=$VERSION_CODE
    [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9]+([.-][a-zA-Z0-9]+)*)?$ ]] || { echo 'Invalid VERSION.' >&2; exit 1; }
    [[ "$code" =~ ^[1-9][0-9]*$ ]] && (( code > previous_code && code <= 2100000000 )) || { echo 'VERSION_CODE must increase and fit Android limits.' >&2; exit 1; }
    printf 'versionName=%s\nversionCode=%s\n' "$version" "$code" > version.properties
    echo "Prepared $version ($code). Run task local:check, then commit the version change."
    ;;
  tag)
    [[ -z "$(git status --porcelain)" ]] || { echo 'Commit or remove pending changes before tagging.' >&2; exit 1; }
    git tag -a "v$version" -m "GriffBoard $version"
    echo "Created local tag v$version. Run task release:publish to push it."
    ;;
  publish)
    [[ -z "$(git status --porcelain)" ]] || { echo 'Working tree must be clean.' >&2; exit 1; }
    [[ "$(git rev-parse "v$version^{commit}")" == "$(git rev-parse HEAD)" ]] || { echo 'Release tag must point to HEAD.' >&2; exit 1; }
    git push origin "refs/tags/v$version"
    ;;
  *) echo 'Usage: scripts/release.sh check|prepare|tag|publish' >&2; exit 1 ;;
esac
