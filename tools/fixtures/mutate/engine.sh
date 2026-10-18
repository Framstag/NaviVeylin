#!/usr/bin/env bash
# A toy engine for the mutation runner's self-test: one invariant on one line.
set -uo pipefail
GUARD=on
printf 'engine: %s\n' "$GUARD"
