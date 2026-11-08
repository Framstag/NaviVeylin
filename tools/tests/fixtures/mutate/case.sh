#!/usr/bin/env bash
# A toy gate for the mutation runner's self-test: green while the guard is present, red once a mutation
# removes it. It is run in the throwaway tree, not in the repository.
set -uo pipefail
cd "$(dirname "$0")" || exit 2
if grep -q '^GUARD=on$' engine.sh; then
  exit 0
fi
printf 'the toy guard is gone\n' >&2
exit 1
