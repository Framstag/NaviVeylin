#!/usr/bin/env bash
# Revert-check of the artifact-hygiene Purpose invariant (issue #11, §171).
# Puts the archive's placeholder under one spec's `## Purpose`, runs the flat
# glob a top-level pass would use, then the shipped step body, then restores the
# file and runs the step body again. Both halves are in the transcript, and the
# restore is verified with `git diff` of the mutated file.
#
# Usage: bash revert-check.sh <spec-path-relative-to-repo-root>
set -uo pipefail
scriptdir=$(cd "$(dirname "$0")" && pwd)
here=$scriptdir
while [ ! -d "$here/openspec/specs" ]; do here=$(dirname "$here"); [ "$here" = "/" ] && { echo "not inside the repository" >&2; exit 2; }; done
cd "$here"
spec="$1"
[ -f "$spec" ] || { echo "no such spec: $spec" >&2; exit 2; }

placeholder='TBD - created by archiving change revert-check. Update Purpose after archive.'
orig=$(mktemp)
cp "$spec" "$orig"
trap 'cp "$orig" "$spec"; rm -f "$orig"' EXIT

show_purpose() { sed -n '/^## Purpose/,+2p' "$spec"; }

echo "# revert-check for $spec"
echo "# $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo
echo "\$ sed -n '/^## Purpose/,+2p' $spec   # before"
show_purpose
echo
awk -v p="$placeholder" '/^## Purpose$/ {print; insec=1; next}
  insec && !done {print p; done=1; insec=0; next}
  {print}' "$spec" > "$spec.tmp" \
  && mv "$spec.tmp" "$spec"
echo "\$ sed -n '/^## Purpose/,+2p' $spec   # mutated"
show_purpose
echo
echo '$ old flat-glob pass: awk ... openspec/specs/*/spec.md'
old=$(awk '/^## Purpose/{inp=1;next} inp&&/^## /{inp=0} inp&&/TBD - created by archiving/{printf "%s:%d\n", FILENAME, FNR}' openspec/specs/*/spec.md)
if [ -n "$old" ]; then
  printf '%s\n' "$old"
  echo "RED=yes"
else
  echo "(no output — a nested spec is invisible to the flat glob)"
  echo "RED=no"
fi
echo
echo '$ bash hygiene-step.sh   # shipped step body, mutated tree'
bash "$scriptdir/hygiene-step.sh"
echo "EXIT=$? (expect 1)"
echo
cp "$orig" "$spec"
echo "\$ sed -n '/^## Purpose/,+2p' $spec   # restored"
show_purpose
echo
echo '$ bash hygiene-step.sh   # shipped step body, restored tree'
bash "$scriptdir/hygiene-step.sh"
echo "EXIT=$? (expect 0)"
echo
echo "\$ git diff --stat -- $spec"
git diff --stat -- "$spec"
echo "(empty = the mutation is gone; other paths carry this item's fix)"
