#!/usr/bin/env bash
# mutate.sh — the generic mutation runner for a **shell gate**: one mutation per invariant, the named case
# must go red, the file is restored, the case must go green again.
#
# `.pi/skills/revert-check` owns the rule (*one mutation per check*, a mutation that lands, a failure for the
# right reason, an exact restore, a green re-run) and `.pi/skills/mutation-runner` owns how to use this file.
# This is the mechanical form of the rule for every engine whose gate is a self-test script rather than
# Gradle — no build cache to defeat, no JUnit XML to quote — and it exists because that pass was hand-rolled
# once per change (`.pi/tmp/revert-check.sh` in `tooling-portability`, three bespoke `*-mutations.sh`
# harnesses before it), with the same two silent lies every time:
#
#   * **the mutation did not land** — the search no longer matches the line the change once wrote, so the
#     case passes and the run looks like a falsification while nothing was ever mutated;
#   * **the case did not notice** — the mutation landed and the case is green anyway, because its assertion
#     is adjacent to the invariant instead of on it.
#
# Both are reported as failures of this runner, never as a pass. The mutation's own file is backed up, the
# restore is verified byte-for-byte (`cmp`) **and** by permission bit — a mutated copy written by `awk` lands
# with the process's default mode, so a restore that dropped the executable bit would leave a tree that is not
# the tree the gate was green on — and the current file is restored even when the runner is killed (EXIT trap).
# `MUTATION-DID-NOT-APPLY`, `STAYS-GREEN`, `RESTORE-FAILED` and `RESTORE-STAYS-RED` are the four ways a run
# fails.
#
# The mutations live in a JSON file, one record per invariant, so a change's falsification is *versioned and
# reproducible* instead of living in a session's scratch directory:
#
#   {
#     "comment": "what this set proves",
#     "case-cmd": "bash tools/check-portability-selftest.sh --case {case}",
#     "mutations": [
#       { "label": "5.1", "case": "portability#valueSetIsReadNotHardCoded",
#         "file": "tools/check-portability.sh",
#         "search": "…the exact text the change wrote…", "replace": "…what removes the invariant…",
#         "why": "the value set is read from the profile, not hard-coded" }
#     ]
#   }
#
# `case-cmd` is run in the tree root with `{case}` replaced by the record's case id; it must exit 0 on a clean
# tree and non-zero with the mutation in place. `{case}` is an exact-token replacement, so a command that
# needs a shell quote writes it itself.
#
# A `search` is a **literal, single-line** string (not a regular expression, so no escaping and no globbing):
# every occurrence on the line count is replaced, and the count is printed, so a pattern that now hits two
# lines is visible rather than silent. A search that spans lines is not supported — the runner reports
# `MUTATION-DID-NOT-APPLY` rather than guessing.
#
# Exit: 0 every selected mutation went red and its restore went green; 1 a mutation did not apply, was not
#       noticed, its restore was not exact, or the case stayed red past the restore; 2 usage, a missing
#       prerequisite or a malformed mutations file.
#
# Usage: tools/mutate.sh --mutations <file.json> [--root <dir>] [--only <labels>] [--out <file>]
#                        [--timeout <seconds>] [--list]
set -uo pipefail

usage() { sed -n '2,50p' "$0"; }

mutations_file=""
root=""
only=""
out=""
list_only=0
case_timeout=600
while [ $# -gt 0 ]; do
  case "$1" in
    --mutations) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; mutations_file="${2:-}"; shift 2 ;;
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --only) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only="${2:-}"; shift 2 ;;
    --out) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; out="${2:-}"; shift 2 ;;
    --timeout) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; case_timeout="${2:-}"; shift 2 ;;
    --list) list_only=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'mutate: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done

[ -n "$mutations_file" ] || { printf 'mutate: --mutations <file.json> is required\n' >&2; exit 2; }
[ -f "$mutations_file" ] || { printf 'mutate: no mutations file at %s\n' "$mutations_file" >&2; exit 2; }
for tool in bash jq awk cmp; do
  command -v "$tool" >/dev/null 2>&1 || { printf 'mutate: %s is required\n' "$tool" >&2; exit 2; }
done
if [ -z "$root" ]; then
  root=$(git rev-parse --show-toplevel 2>/dev/null) || root=""
fi
[ -n "$root" ] || { printf 'mutate: not inside a git working tree — pass --root <dir>\n' >&2; exit 2; }
[ -d "$root" ] || { printf 'mutate: no tree at %s\n' "$root" >&2; exit 2; }

jq -e . "$mutations_file" >/dev/null 2>&1 ||
  { printf 'mutate: %s is not valid JSON\n' "$mutations_file" >&2; exit 2; }
case_cmd=$(jq -r '."case-cmd" // empty' "$mutations_file")
[ -n "$case_cmd" ] || { printf 'mutate: %s declares no "case-cmd"\n' "$mutations_file" >&2; exit 2; }
total=$(jq -r '.mutations | length' "$mutations_file" 2>/dev/null) || total=""
case "$total" in ''|*[!0-9]*) printf 'mutate: %s declares no "mutations" array\n' "$mutations_file" >&2; exit 2 ;; esac
[ "$total" -gt 0 ] || { printf 'mutate: %s declares no mutation\n' "$mutations_file" >&2; exit 2; }

selected() { # selected <label> — --only takes a space-separated label list
  [ -z "$only" ] && return 0
  case " $only " in *" $1 "*) return 0 ;; *) return 1 ;; esac
}

if [ "$list_only" -eq 1 ]; then
  printf '# %s — %s mutation(s), case command: %s\n' "$mutations_file" "$total" "$case_cmd"
  while IFS= read -r record; do
    label=$(jq -r '.label' <<< "$record"); selected "$label" || continue
    printf '%s\t%s\t%s\n' "$label" "$(jq -r '.case' <<< "$record")" "$(jq -r '.why // ""' <<< "$record")"
  done < <(jq -c '.mutations[]' "$mutations_file")
  exit 0
fi

scratch=$(mktemp -d "${TMPDIR:-/tmp}/mutate.XXXXXX") || { printf 'mutate: cannot create a scratch directory\n' >&2; exit 2; }
# The transcript goes to stdout and, with `--out`, to a file as well: `exec > >(tee …)` keeps this shell the
# one that counts (a pipeline per report would increment the counters in a subshell and lose them).
if [ -n "$out" ]; then exec > >(tee -a "$out"); fi

current_backup=""
current_file=""
cleanup() {
  # A killed runner must not leave a mutation in the tree: the case that would catch it is the one this file
  # runs, and the next suite run would fail for a reason nobody is looking for. The mode comes back with it.
  if [ -n "$current_backup" ] && [ -n "$current_file" ]; then
    cp "$current_backup" "$current_file" 2>/dev/null || true
    chmod "$(file_mode "$current_backup")" "$current_file" 2>/dev/null || true
  fi
  rm -rf "$scratch"
}
trap cleanup EXIT

# The literal replacement: awk, not a shell expansion, because `${var/…}` would treat the search as a glob
# pattern (`?`, `*`, `[` that a shell script legitimately contains) and because `-v` would eat backslashes.
# The values arrive through the environment, which does neither. Each line is processed on its own; the count
# of replaced occurrences goes to stdout, so "did it land" is decided by a number rather than by a hope.
literal_replace() { # <file> <search> <replace> <out-file> → the number of occurrences
  MUTATE_SEARCH="$2" MUTATE_REPLACE="$3" awk -v out="$4" '
    BEGIN { s = ENVIRON["MUTATE_SEARCH"]; r = ENVIRON["MUTATE_REPLACE"]; n = 0 }
    {
      line = $0; result = ""
      while ((i = index(line, s)) > 0) {
        result = result substr(line, 1, i - 1) r
        line = substr(line, i + length(s))
        n++
      }
      print result line > out
    }
    END { print n }
  ' "$1"
}

# The file's permission bits, as an octal string. A mutated copy is written by awk and lands with the
# process's default mode, so without this the runner would silently strip the executable bit — and a
# restored tree that differs by a mode is not the tree the gate was green on.
file_mode() {
  if stat -c %a "$1" >/dev/null 2>&1; then stat -c %a "$1"; else stat -f %Lp "$1" 2>/dev/null; fi
}

run_case() { # <command> → its exit status; the output is kept for the transcript
  if command -v timeout >/dev/null 2>&1; then
    ( cd "$root" && timeout "$case_timeout" bash -c "$1" ) > "$scratch/case.log" 2>&1
  else
    ( cd "$root" && bash -c "$1" ) > "$scratch/case.log" 2>&1
  fi
}

ran=0
went_red=0
failed=0

while IFS= read -r record; do
  label=$(jq -r '.label' <<< "$record")
  selected "$label" || continue
  case_name=$(jq -r '.case' <<< "$record")
  rel=$(jq -r '.file' <<< "$record")
  search=$(jq -r '.search' <<< "$record")
  replace=$(jq -r '.replace' <<< "$record")
  why=$(jq -r '.why // ""' <<< "$record")

  ran=$((ran + 1))
  printf '%s\t%s\t%s\n' "$label" "$case_name" "$why"

  file="$root/$rel"
  if [ ! -f "$file" ]; then
    printf '  MUTATION-FILE-MISSING  %s is not a file under %s\n' "$rel" "$root"
    failed=$((failed + 1)); continue
  fi
  if [ -z "$search" ]; then
    printf '  MUTATION-DID-NOT-APPLY  the record has an empty search\n'
    failed=$((failed + 1)); continue
  fi

  backup="$scratch/$label.orig"
  cp "$file" "$backup" || { printf '  MUTATION-BACKUP-FAILED  cannot back up %s\n' "$rel"; failed=$((failed + 1)); continue; }
  current_backup="$backup"; current_file="$file"
  mode=$(file_mode "$file")

  occurrences=$(literal_replace "$file" "$search" "$replace" "$scratch/$label.new")
  case "${occurrences:-}" in ''|*[!0-9]*) occurrences=0 ;; esac
  if [ "$occurrences" -eq 0 ]; then
    printf '  MUTATION-DID-NOT-APPLY  the search matches no line of %s — the pattern no longer matches the code\n' "$rel"
    failed=$((failed + 1)); current_backup=""; current_file=""; continue
  fi
  mv "$scratch/$label.new" "$file" || { printf '  MUTATION-WRITE-FAILED  cannot write %s\n' "$rel"; failed=$((failed + 1)); continue; }
  [ -z "$mode" ] || chmod "$mode" "$file" 2>/dev/null || true

  command="${case_cmd//\{case\}/$case_name}"
  run_case "$command"; rc=$?
  if [ "$rc" -eq 0 ]; then
    printf '  STAYS-GREEN  %s passed with the mutation in place (%s occurrence(s) replaced in %s) — the case does not assert the invariant\n' \
      "$case_name" "$occurrences" "$rel"
    failed=$((failed + 1))
  else
    printf '  RED(exit %s)  %s occurrence(s) replaced in %s\n' "$rc" "$occurrences" "$rel"
    went_red=$((went_red + 1))
  fi

  cp "$backup" "$file" || { printf '  RESTORE-FAILED  cannot restore %s\n' "$rel"; failed=$((failed + 1)); continue; }
  [ -z "$mode" ] || chmod "$mode" "$file" 2>/dev/null || true
  if ! cmp -s "$backup" "$file"; then
    printf '  RESTORE-FAILED  %s is not byte-identical to its backup after the restore\n' "$rel"
    failed=$((failed + 1)); continue
  fi
  if [ -n "$mode" ] && [ "$(file_mode "$file")" != "$mode" ]; then
    printf '  RESTORE-FAILED  %s came back with mode %s instead of %s\n' "$rel" "$(file_mode "$file")" "$mode"
    failed=$((failed + 1)); continue
  fi
  current_backup=""; current_file=""

  run_case "$command"; rc=$?
  if [ "$rc" -eq 0 ]; then
    printf '  GREEN(exit 0)  restored, and the case passes again\n'
  else
    printf '  RESTORE-STAYS-RED  %s exits %s on the restored tree — the tree was not in a green state before the mutation\n' \
      "$case_name" "$rc"
    failed=$((failed + 1))
  fi
done < <(jq -c '.mutations[]' "$mutations_file")

if [ "$ran" -eq 0 ]; then
  printf 'mutate: --only selected no mutation of %s\n' "$mutations_file" >&2
  exit 2
fi

printf '\nmutate: %s mutation(s) run, %s went red and restored green, %s problem(s)\n' "$ran" "$went_red" "$failed"
[ "$failed" -eq 0 ] || exit 1
printf 'mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore\n'
exit 0
