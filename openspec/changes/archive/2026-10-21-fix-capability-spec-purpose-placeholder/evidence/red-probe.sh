#!/usr/bin/env bash
# Probe for issue #11 (§171): a capability spec's `## Purpose` still carries the
# sentence `openspec archive` writes for a capability it creates. This is the
# body the CI step "Check OpenSpec artifact hygiene" gains in this change.
# Only the lines between a `## Purpose` heading and the next `## ` heading are
# read, so requirement prose may quote the sentence freely.
# The pass is a `find` over openspec/specs at any depth: 7 of the 176 capability
# specs sit under a group directory (auto/*, share/*), which a flat glob misses.
# Exit 0 = no spec carries it; exit 1 = at least one does, and it is named.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
while [ ! -d "$here/openspec/specs" ]; do here=$(dirname "$here"); [ "$here" = "/" ] && { echo "not inside the repository" >&2; exit 2; }; done
cd "$here"
bad=$(find openspec/specs -name spec.md -exec awk '/^## Purpose/{inp=1;next} inp&&/^## /{inp=0} inp&&/TBD - created by archiving/{printf "%s:%d\n", FILENAME, FNR}' {} +)
if [ -n "$bad" ]; then
  echo "::error::a capability spec's '## Purpose' is still the archive placeholder — write what the capability is for (a delta's Purpose is read only when the capability is created)"
  printf '%s\n' "$bad"
  exit 1
fi
echo "Every capability spec states its Purpose."
