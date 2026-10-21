#!/usr/bin/env bash
# Runs the shipped body of the CI step "Check OpenSpec artifact hygiene" verbatim
# — the `run: |` block is extracted from .github/workflows/build.yml and
# dedented, never transcribed, so this evidence cannot drift from the step.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
while [ ! -d "$here/openspec/specs" ]; do here=$(dirname "$here"); [ "$here" = "/" ] && { echo "not inside the repository" >&2; exit 2; }; done
cd "$here"
body=$(awk '/^      - name: Check OpenSpec artifact hygiene$/{f=1;next}
            f && /^      - name:/{exit}
            f && /^        run: \|$/{r=1;next}
            r && /^          /{sub(/^          /,"");print;next}
            r{exit}' .github/workflows/build.yml)
[ -n "$body" ] || { echo "could not extract the step body" >&2; exit 2; }
printf '%s\n' "$body" | bash
