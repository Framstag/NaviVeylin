#!/usr/bin/env bash
# check-change-readiness.sh — is this change ready to be worked by an unattended process?
#
# A worker that may not ask a person can only be handed a change that leaves it nothing to decide. This checker
# answers that question from three artifacts of the change and from nothing else — `design.md`'s
# `## Open decisions` declaration, the `**Case**:` line of every `#### Scenario:` in the change's
# `specs/**/spec.md`, and the `surface:` line of `proposal.md` — and it never reads a section for its meaning: a
# body-reading rule the project trusted once was wrong about 48 of 49 verdicts and excluded two thirds of an
# authorised set over prose (`guidelines/Backlog.md` §6). The capability is `openspec/specs/change-readiness/`
# `spec.md`; the design that fixed these choices is the change `change-readiness-gate`.
#
# The refusal vocabulary is fixed here, because the writing path that will call this checker
# (`finding-shape-at-filing`) quotes it and must not invent a second spelling:
#
#   declaration-missing       design.md carries no `## Open decisions`, or carries it and names nothing
#   open-decision             the declaration's first non-empty line is not `none`
#   scenario-without-case     a `#### Scenario:` carries no `**Case**:` line, or names `none` with no reason
#   surface-missing           proposal.md carries no line beginning `surface:`
#   surface-command-missing   that line quotes no command, so its count cannot be re-taken
#   change-unreadable         the argument names no change directory, or the directory carries no design.md
#
# The verdict is exactly one line, so a caller can quote it into a comment, a work package or a label without
# parsing prose, and every refusal is named on it with the artifact and the line it came from:
#
#   LOOP-CHANGE-READY: ok
#   LOOP-CHANGE-READY: refused declaration-missing=design.md open-decision=design.md:41
#
# With `--all` the checker prints one verdict line per change, each carrying that change's refusals with the
# change's own directory name in front of the path (`open-decision=some-change/design.md:41`), so a run over the
# whole tree is readable and each line is still one line. A single change directory is always exactly one line.
#
# Reads text files only: no tracker, no git, no network, no build, and nothing is written. Exit 0 when the
# verdict is `ok`, 1 when a refusal was printed, 2 on a usage error.
#
# Usage: check-change-readiness.sh <change-dir>...
#        check-change-readiness.sh --all [--changes-dir <dir>]
#        --json    print one object `{verdict, refusals:[{name,path,line,detail}], checked:[paths]}` instead
#        --help    this text
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project_root=$(cd "$script_dir/.." && pwd)

VERDICT_PREFIX="LOOP-CHANGE-READY:"

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; }
fail_usage() { printf 'check-change-readiness: %s\n' "$*" >&2; exit 2; }

json=0
all=0
changes_dir=""
targets=()
while [ $# -gt 0 ]; do
  case "$1" in
    --all) all=1 ;;
    --json) json=1 ;;
    --changes-dir) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || fail_usage "--changes-dir needs a value"; changes_dir="$2"; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) fail_usage "unknown option '$1'" ;;
    *) [ "$all" -eq 0 ] || fail_usage "a change directory and --all are two forms; give one of them"; targets+=("$1") ;;
  esac
  shift
done

if [ "$all" -eq 1 ]; then
  [ "${#targets[@]}" -eq 0 ] || fail_usage "a change directory and --all are two forms; give one of them"
  changes_root="${changes_dir:-${CHANGE_READINESS_CHANGES_DIR:-$project_root/openspec/changes}}"
  [ -d "$changes_root" ] || fail_usage "no changes directory at $changes_root"
  while IFS= read -r d; do
    [ -n "$d" ] || continue
    [ "$(basename "$d")" = "archive" ] && continue
    targets+=("$d")
  done < <(find "$changes_root" -mindepth 1 -maxdepth 1 -type d | sort)
  [ "${#targets[@]}" -gt 0 ] || fail_usage "no change directory under $changes_root"
elif [ "${#targets[@]}" -eq 0 ]; then
  usage >&2
  exit 2
fi

scratch=$(mktemp -d "${TMPDIR:-/tmp}/check-change-readiness.XXXXXX") || fail_usage "cannot create a scratch directory"
FINDINGS="$scratch/findings"       # every refusal of the whole invocation, for `--json`
ONE="$scratch/one"                 # the findings of the change being rendered
CHECKED="$scratch/checked"
: > "$FINDINGS"
: > "$CHECKED"
trap 'rm -rf "$scratch"' EXIT

record() { # <name> <path> <line-or-empty> <detail>; `-` is the line of a refusal that has none
  printf '%s\t%s\t%s\t%s\n' "$1" "$2" "${3:--}" "$4" >> "$ONE"
}

# One JSON string, without a dependency on jq: a field here is one line of a text file, and the tab is the
# separator this script writes with, so only the quote and the backslash need escaping.
json_escape() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e "s/$(printf '\t')/ /g"; }

# ── one change ───────────────────────────────────────────────────────────────────────────────────────────
check_change() { # <change dir> <prefix for the detail paths>
  local dir="$1" prefix="$2"
  local design="$dir/design.md" proposal="$dir/proposal.md"

  if [ ! -d "$dir" ]; then
    record change-unreadable "$dir" "" "the path names no change directory"
    return
  fi
  if [ ! -f "$design" ]; then
    record change-unreadable "${prefix}design.md" "" "the change directory carries no design.md"
    return
  fi
  printf '%s\n' "${prefix}design.md" >> "$CHECKED"

  # ── the declaration (`change-readiness`, "A change declares the decisions it leaves open") ──────────────
  # The heading is sought and the section's first non-empty line is read; the section's prose is never
  # interpreted, so a `## Open Questions` entry that defers is invisible here by construction.
  local decl_heading="" decl_line="" decl_text=""
  local decl
  decl=$(awk '
    BEGIN { ins = 0; found = 0 }
    /^## / {
      if (ins) exit
      if (tolower($0) ~ /^##[ \t]+open decisions[ \t]*$/) { ins = 1; heading = NR; next }
      next
    }
    ins && $0 ~ /[^ \t]/ { print heading "\t" NR "\t" $0; found = 1; exit }
    END { if (ins && !found) print heading "\t" 0 "\t" }
  ' "$design")
  if [ -n "$decl" ]; then
    IFS=$'\t' read -r decl_heading decl_line decl_text <<< "$decl"
  fi

  # Each refusal is decided by its own flag and emitted by its own one-line call, so a mutation record can name
  # the single line that stops it firing (`tools/tests/check-change-readiness-mutations.json`).
  local decl_missing=0 decl_decision=0 decl_norm=""
  if [ -z "$decl_heading" ] || [ "${decl_line:-0}" = "0" ]; then decl_missing=1; fi
  if [ "$decl_missing" = 0 ]; then
    decl_norm=$(printf '%s' "$decl_text" | tr -d ' \t' | tr '[:upper:]' '[:lower:]')
    if [ "$decl_norm" != "none" ]; then decl_decision=1; fi
  fi
  if [ "$decl_missing" = 1 ]; then record declaration-missing "${prefix}design.md" "$decl_heading" "no ## Open decisions section, or one that names nothing: a declaration of none is a claim, an absent section is not"; fi
  if [ "$decl_decision" = 1 ]; then record open-decision "${prefix}design.md" "$decl_line" "the declaration names a decision, so an unattended worker would have to make it: $decl_text"; fi

  # ── the case each scenario owes (`change-readiness`, "Every scenario names the case that exercises it") ─
  local spec
  while IFS= read -r spec; do
    [ -n "$spec" ] || continue
    printf '%s\n' "${prefix}${spec#"$dir"/}" >> "$CHECKED"
    local s_line s_name s_why
    while IFS=$'\t' read -r s_line s_name s_why; do
      [ -n "$s_line" ] || continue
      record scenario-without-case "${prefix}${spec#"$dir"/}" "$s_line" "$s_name — $s_why"
    done < <(awk '
      function flush() {
        if (pending && !seen) print line "\t" name "\t" (why == "" ? "carries no **Case**: line" : why)
        pending = 0
      }
      /^####[ \t]+Scenario:/ {
        flush()
        pending = 1; seen = 0; why = ""; line = NR; name = $0
        sub(/^####[ \t]+Scenario:[ \t]*/, "", name); sub(/[ \t\r]+$/, "", name)
        next
      }
      /^#{1,4}[ \t]/ { flush(); next }
      pending && /^[ \t]*-?[ \t]*\*\*Case\*\*:/ {
        v = $0
        sub(/^[ \t]*-?[ \t]*\*\*Case\*\*:[ \t]*/, "", v)
        sub(/[ \t\r]+$/, "", v)
        if (v == "") { why = "the **Case**: line names no case"; next }
        nv = tolower(v)
        if (nv ~ /^none[ \t]*[.,;:]?$/) { why = "the **Case**: line names none and gives no reason"; next }
        seen = 1
        next
      }
      END { flush() }
    ' "$spec")
  done < <( [ -d "$dir/specs" ] && find "$dir/specs" -type f -name 'spec.md' | sort )

  # ── the surface the proposal records (`change-readiness`, "The proposal records the surface it will touch") ─
  if [ ! -f "$proposal" ]; then
    record surface-missing "${prefix}proposal.md" "" "the change carries no proposal.md, so no surface is recorded"
    return
  fi
  printf '%s\n' "${prefix}proposal.md" >> "$CHECKED"
  local surface_line surface_text
  surface_line=$(grep -n '^surface:' "$proposal" | head -1 | cut -d: -f1) || surface_line=""
  if [ -z "${surface_line:-}" ]; then record surface-missing "${prefix}proposal.md" "" "no line beginning surface:, so the change states no surface it will touch"; return; fi
  # The statement is the `surface:` line and its continuation lines, so a surface line that wraps before the
  # command — as this change's own proposal does — is still one statement.
  surface_text=$(awk -v start="$surface_line" 'NR >= start { if ($0 ~ /^[ \t]*$/) exit; print }' "$proposal")
  if ! printf '%s\n' "$surface_text" | grep -q '`[^`]\+`'; then record surface-command-missing "${prefix}proposal.md" "$surface_line" "the surface line quotes no command, so a reader cannot re-take its count: $surface_text"; fi
}

render_line() { # the verdict line for the change whose findings are in $ONE, its name as `$1` or ""
  local suffix=""
  if [ "$1" != "" ]; then suffix=" ${1}"; fi
  if [ ! -s "$ONE" ]; then
    printf '%s ok%s\n' "$VERDICT_PREFIX" "$suffix"
    return
  fi
  local out="$VERDICT_PREFIX refused"
  local name path n detail
  while IFS=$'\t' read -r name path n detail; do
    [ -n "$name" ] || continue
    if [ "$n" != "-" ]; then out="$out $name=$path:$n"; else out="$out $name=$path"; fi
  done < "$ONE"
  printf '%s\n' "$out"
}

refused_changes=0
for dir in ${targets[@]+"${targets[@]}"}; do
  : > "$ONE"
  if [ "$all" -eq 1 ]; then
    check_change "$dir" "$(basename "$dir")/"
    [ -s "$ONE" ] && refused_changes=$((refused_changes + 1))
    [ "$json" -eq 1 ] || render_line "$(basename "$dir")"
  else
    check_change "$dir" ""
    [ -s "$ONE" ] && refused_changes=$((refused_changes + 1))
    [ "$json" -eq 1 ] || render_line ""
  fi
  cat "$ONE" >> "$FINDINGS"
done

if [ "$refused_changes" -eq 0 ]; then verdict="ok"; else verdict="refused"; fi

if [ "$json" -eq 1 ]; then
  out='{"verdict":"'"$verdict"'"'
  out+=',"refusals":['
  first=1
  while IFS=$'\t' read -r name path line detail; do
    [ -n "$name" ] || continue
    [ "$first" -eq 1 ] || out+=','
    first=0
    if [ "$line" != "-" ]; then line_json="$line"; else line_json="null"; fi
    out+='{"name":"'"$(json_escape "$name")"'","path":"'"$(json_escape "$path")"'","line":'"$line_json"
    out+=',"detail":"'"$(json_escape "$detail")"'"}'
  done < "$FINDINGS"
  out+='],"checked":['
  first=1
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    [ "$first" -eq 1 ] || out+=','
    first=0
    out+='"'"$(json_escape "$p")"'"'
  done < "$CHECKED"
  out+=']}'
  printf '%s\n' "$out"
fi

[ "$verdict" = "ok" ] || exit 1
exit 0
