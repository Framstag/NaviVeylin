#!/usr/bin/env bash
# Probe (read-only): what the report's ancestry count says vs what the patches say, on the leftover branches
# of this repository. Prints one line per branch: <ahead-by-object> <unlanded-by-patch> <branch>.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 2
base=HEAD
total=0
zero=0
for branch in $(git for-each-ref --format='%(refname:short)' 'refs/heads/fix/issue-*'); do
  ahead=$(git rev-list --count "$base..$branch" 2>/dev/null || printf 1)
  [ "$ahead" -gt 0 ] || continue
  unlanded=$(git cherry "$base" "$branch" 2>/dev/null | grep -c '^+ ' || true)
  total=$((total + 1))
  [ "$unlanded" -eq 0 ] && zero=$((zero + 1))
  printf '%s\t%s\t%s\n' "$ahead" "$unlanded" "$branch"
done
printf '\nbranches the report names: %s · of them carrying no unlanded patch: %s\n' "$total" "$zero"
