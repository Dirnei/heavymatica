#!/usr/bin/env sh
# Prints the release notes for a release tag such as heavymatica-v0.3.0: the annotated tag's message, then the feat
# and fix commits since the mod's previous release tag that touch src/mod/<mod>. Prints nothing when there is nothing.
# Usage: release-notes.sh <tag>
set -eu

tag="${1:?usage: release-notes.sh <tag>}"
mod="${tag%-v[0-9]*}"
folder="src/mod/$mod"

message=""
if [ "$(git cat-file -t "refs/tags/$tag")" = "tag" ]; then
  # %(contents) of a signed tag ends with its signature, which is not part of the message.
  message=$(git for-each-ref --format='%(contents)' "refs/tags/$tag" | sed '/^-----BEGIN [A-Z ]*SIGNATURE-----$/,$d')
fi

if ! previous=$(git describe --tags --abbrev=0 --match "$mod-v[0-9]*" "$tag^" 2>/dev/null); then
  printf '%s\n' "$message" | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}'
  exit 0
fi

entries() {
  git log --no-merges --format=%s "$previous..$tag" -- "$folder" |
    sed -n -E "s/^$1(\(([^)]*)\))?!?: (.+)$/\2|\3/p" |
    while IFS='|' read -r scope subject; do
      if [ -n "$scope" ]; then
        printf -- '- **%s:** %s\n' "$scope" "$subject"
      else
        printf -- '- %s\n' "$subject"
      fi
    done
}

features=$(entries feat)
fixes=$(entries fix)

{
  [ -n "$message" ] && printf '%s\n\n' "$message"
  [ -n "$features" ] && printf '### Features\n\n%s\n\n' "$features"
  [ -n "$fixes" ] && printf '### Bug fixes\n\n%s\n\n' "$fixes"
} | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}'
