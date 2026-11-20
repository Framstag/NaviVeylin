#!/usr/bin/env bash
# check-gate-coverage.sh — every tracked path is named by a glob of the gate's profile, or is on the allowlist with a
# reason (issue #943).
#
# Why this exists: a delta's steps are the profile's `<glob>|<command>` pairs whose glob matches a path the delta
# touched (`LOOP_GATE_SUITES`), plus the build a compiled pattern owes (`LOOP_GATE_COMPILED_PATTERNS`). A tracked path
# no glob names therefore owes nothing, and the gate prints `nothing owed` and exits 0 — a green verdict
# indistinguishable from a measured one, and a file added under such a path is added to a tree where nothing will ever
# run it. The suite map has no counterpart to the invariant `tools/check-rule-routes.sh` enforces for documentation
# ("every named rule is reached by at least one glob"); this check is that counterpart.
#
# The allowlist is the other half of the invariant, and it is not a way to silence the check: an entry names one
# pattern and the reason that path needs no step of its own, and an entry without a reason is refused. A path added
# under a directory nobody allowlisted is the finding this check exists to print, so a class entry (`vcpkg-overlays/**`)
# is written only where the whole class is deliberately ungated.
#
# Read-only: it reads the profile, the allowlist and `git ls-files`, and writes nothing.
#
# Usage: tools/check-gate-coverage.sh [--list] [--root DIR]
#   --list      print the allowlist's patterns with their reasons and exit 0
#   --root DIR  read the profile, the allowlist and `git ls-files` of DIR instead of this tree's
# Environment:
#   LOOP_CONF                       the profile to read (default <root>/tools/loop/loop.conf)#   LOOP_GATE_COVERAGE_ALLOWLIST    the allowlist to read (default <root>/tools/gate-coverage-allowlist.tsv)
# Exit: 0 every tracked path is named or allowlisted; 1 a tracked path is named by nothing and allowlisted by nothing;
#       2 the environment is unusable (no profile, no allowlist, an allowlist entry with no reason).
set -uo pipefail
set -f

root=${LOOP_GATE_COVERAGE_ROOT:-}
[ -n "$root" ] || root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null) \
  || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
profile=${LOOP_CONF:-$root/tools/loop/loop.conf}
allowlist=${LOOP_GATE_COVERAGE_ALLOWLIST:-$root/tools/gate-coverage-allowlist.tsv}

list_only=0
while [ $# -gt 0 ]; do
  case "$1" in
    --list) list_only=1; shift ;;
    --root) [ $# -ge 2 ] || { printf 'check-gate-coverage: --root needs a value\n' >&2; exit 2; }; root=$2; shift 2
            [ -n "${LOOP_CONF:-}" ] || profile=$root/tools/loop/loop.conf
            [ -n "${LOOP_GATE_COVERAGE_ALLOWLIST:-}" ] || allowlist=$root/tools/gate-coverage-allowlist.tsv ;;
    *) printf 'check-gate-coverage: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done

[ -f "$profile" ] || { printf 'check-gate-coverage: no profile at %s\n' "$profile" >&2; exit 2; }
[ -f "$allowlist" ] || { printf 'check-gate-coverage: no allowlist at %s\n' "$allowlist" >&2; exit 2; }

# One key's value, read the way the engine reads it: the assignment the profile carries, nothing else.
conf_value() { sed -n "s/^$1=\"\(.*\)\"$/\1/p" "$profile" | head -n 1; }

# The globs that name paths, one per line: every suite pair's glob, and every compiled pattern. The first-step list is
# not read, and that is deliberate: a first step is owed by a `<glob>|<command>` pair like any other, and the one entry
# this check owns names every path (`*`), so reading that list here would name the whole tree and prove nothing.
globs=$(
  {
    conf_value LOOP_GATE_SUITES | tr ';' '\n' | sed -n 's/|.*//p'
    for g in $(conf_value LOOP_GATE_COMPILED_PATTERNS); do printf '%s\n' "$g"; done
  } | sed '/^$/d'
)

# A pattern is data: `set -f` above keeps the shell from expanding a glob of the profile against the tree it happens to
# be in (the trap issue #273 measured).
named_by_glob() { # <path>
  local g
  while IFS= read -r g; do
    # shellcheck disable=SC2254 # the glob is the point: a pattern from the profile
    case "$1" in $g) return 0 ;; esac
  done <<< "$globs"
  return 1
}

# The allowlist, read once: `<pattern>\t<reason>`. A line without a tab, or with an empty reason, is refused — an
# allowlist entry that states nothing is the silence this check exists to end.
allow_patterns=()
allow_reasons=()
line_no=0
bad_reason=0
while IFS= read -r line || [ -n "$line" ]; do
  line_no=$((line_no + 1))
  case "$line" in ''|'#'*) continue ;; esac
  pattern=${line%%$'\t'*}
  reason=${line#*$'\t'}
  if [ "$reason" = "$line" ] || [ -z "${reason//[[:space:]]/}" ] || [ -z "$pattern" ]; then
    printf 'ALLOWLIST-NO-REASON: line %s carries no reason\n' "$line_no"
    bad_reason=1
    continue
  fi
  allow_patterns+=("$pattern")
  allow_reasons+=("$reason")
done < "$allowlist"

if [ "$list_only" -eq 1 ]; then
  i=0
  while [ "$i" -lt "${#allow_patterns[@]}" ]; do
    printf '%s\t%s\n' "${allow_patterns[$i]}" "${allow_reasons[$i]}"
    i=$((i + 1))
  done
  exit 0
fi

total=0
named=0
allowed=0
unnamed=0
while IFS= read -r path; do
  [ -n "$path" ] || continue
  total=$((total + 1))
  if named_by_glob "$path"; then
    named=$((named + 1))
    continue
  fi
  hit=0
  i=0
  while [ "$i" -lt "${#allow_patterns[@]}" ]; do
    # shellcheck disable=SC2254 # the same: a pattern from a data file
    case "$path" in ${allow_patterns[$i]}) hit=1; break ;; esac
    i=$((i + 1))
  done
  if [ "$hit" -eq 1 ]; then
    allowed=$((allowed + 1))
  else
    unnamed=$((unnamed + 1))
    printf 'UNNAMED: %s\n' "$path"
  fi
done < <(git -C "$root" ls-files)

printf 'LOOP-GATE-COVERAGE: tracked=%s named=%s allowlisted=%s unnamed=%s\n' "$total" "$named" "$allowed" "$unnamed"
[ "$bad_reason" -eq 0 ] || { printf 'LOOP-GATE-COVERAGE: fail — an allowlist entry carries no reason\n'; exit 2; }
if [ "$unnamed" -gt 0 ]; then
  printf 'LOOP-GATE-COVERAGE: fail — %s tracked path(s) no glob of the profile names and no allowlist entry explains\n' "$unnamed"
  exit 1
fi
printf 'LOOP-GATE-COVERAGE: pass\n'
exit 0
