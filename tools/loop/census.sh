#!/usr/bin/env bash
# census.sh — count the engine's guards whose patron is gone, whose refusal no case carries, and whose reason
# nobody wrote down.
#
# This is a reader, not a run phase: it writes no state, changes no label and is consulted by no run (design D1,
# D3, D5). A person runs it before a cleanup; the number it prints is the change's own threshold, never a
# profile value.
#
# A guard is a code line that calls one of the engine's refusal mechanisms (`loop_die`, `stop_run`, `block`,
# `refuse`, `need_value`, `issues_die`) or returns/exits non-zero. Its patron is the issue number its own comment
# cites in the engine's form — `issue #123`, parenthesised or not. A bare `#123` in prose is deliberately not a
# patron: the engine's citations carry the word `issue`.
#
# Usage: census.sh [--json] [--engine <file>] [--cases <file>]
# Exit: 0 the census was printed (including a tracker it could not read); 2 usage.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../.." && pwd)
engine="$root/tools/loop/run.sh"
cases="$root/tools/loop/tests/loop-cases.sh"
json=0

while [ $# -gt 0 ]; do
  case "$1" in
    --json) json=1; shift ;;
    --engine) engine="${2:-}"; shift 2 ;;
    --cases) cases="${2:-}"; shift 2 ;;
    -h|--help)
      sed -n '2,15p' "$0"
      exit 0
      ;;
    *) printf 'census: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done

[ -f "$engine" ] || { printf 'census: no engine at %s\n' "$engine" >&2; exit 2; }
[ -f "$cases" ] || { printf 'census: no cases at %s\n' "$cases" >&2; exit 2; }

# The refusal mechanisms a guard is made of. Word boundaries keep `blocked` out; a full-line comment is not a
# site (the comments are where the patron is written, not where the guard stands).
guard_re='(^|[^[:alnum:]_])(loop_die|stop_run|block|refuse|need_value|issues_die)([^[:alnum:]_]|$)|(^|[^[:alnum:]_])(return|exit)[[:space:]]+[1-9][0-9]*([^0-9]|$)'
patron_re='issue[[:space:]]*#[0-9]+'

# The phrase is the first double-quoted argument of the call. Variable expansions are dropped because a case
# carries the static text, not the run's values; a call whose message is all expansion falls back to its own line.
phrase_of() { # phrase_of <code line>
  local line="$1" phrase=""
  if [[ $line =~ \"([^\"]*)\" ]]; then
    phrase="${BASH_REMATCH[1]}"
    phrase=$(printf '%s' "$phrase" | sed -E 's/\$\{[^}]*\}//g; s/\$\([^)]*\)//g; s/\$[A-Za-z_][A-Za-z0-9_]*//g')
    phrase=$(printf '%s' "$phrase" | tr '\t' ' ' | tr -s ' ' | sed -E 's/^ +//; s/ +$//; s/^[—:;,. -]+//; s/[—:;,. -]+$//')
  fi
  [ -n "$phrase" ] || phrase=$(printf '%s' "$line" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')
  printf '%s' "$phrase"
}

declare -a g_line=() g_phrase=() g_patrons=()
mapfile -t lines < "$engine"
i=0
while [ "$i" -lt "${#lines[@]}" ]; do
  line="${lines[$i]}"
  i=$((i + 1))
  if [[ $line =~ ^[[:space:]]*# ]]; then continue; fi
  [ -n "$line" ] || continue
  [[ $line =~ $guard_re ]] || continue

  # The comment block directly above the guard, plus a trailing comment on its own line. That is where the engine
  # writes the issue that put the rule there.
  comment=""
  j=$((i - 2))
  while [ "$j" -ge 0 ]; do
    prev="${lines[$j]}"
    if [[ $prev =~ ^[[:space:]]*# ]]; then
      comment="$prev"$'\n'"$comment"
    else
      break
    fi
    j=$((j - 1))
  done
  if [[ $line == *'#'* ]]; then comment="$comment"$'\n'"${line#*#}"; fi

  patrons=""
  if [ -n "$comment" ]; then
    patrons=$(printf '%s\n' "$comment" | grep -oE "$patron_re" | grep -oE '[0-9]+' | awk '!seen[$0]++' | paste -sd, -)
  fi

  g_line[${#g_line[@]}]="$i"
  g_phrase[${#g_phrase[@]}]="$(phrase_of "$line")"
  g_patrons[${#g_patrons[@]}]="$patrons"
done

# Ask the tracker once for every cited item's state. A failure is an unread count, never zero.
declare -A state_of=()
patron_state="read"
patron_reason=""
cited=""
for p in ${g_patrons[@]+"${g_patrons[@]}"}; do
  [ -n "$p" ] || continue
  cited="$cited,$p"
done
cited=$(printf '%s' "${cited#,}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)
if [ -n "$cited" ]; then
  out=$(gh issue list --state all --limit 1000 --json number,state 2>&1)
  status=$?
  if [ "$status" -ne 0 ]; then
    patron_state="unread"
    patron_reason=$(printf '%s\n' "$out" | sed -n '1p' | sed -E 's/[[:space:]]+$//')
    [ -n "$patron_reason" ] || patron_reason="the tracker query exited $status"
  elif ! printf '%s' "$out" | jq -e 'type == "array"' >/dev/null 2>&1; then
    patron_state="unread"
    patron_reason="the tracker answered no issue list"
  else
    while IFS=$'\t' read -r n s; do
      [ -n "$n" ] && state_of["$n"]="$s"
    done < <(printf '%s' "$out" | jq -r '.[] | select(.number != null) | "\(.number)\t\(.state // "")"' 2>/dev/null)
  fi
fi

case_text=$(cat "$cases")

entry_json() { # entry_json <line> <phrase> <comma-separated patrons>
  local issues_json
  issues_json=$(printf '%s' "$3" | awk -F, 'NF { printf "["; for (i=1;i<=NF;i++) printf "%s%s", (i>1?",":""), $i; printf "]" }')
  [ -n "$issues_json" ] || issues_json='[]'
  jq -cn --argjson line "$1" --arg phrase "$2" --argjson issues "$issues_json" \
    '{line: $line, phrase: $phrase, issues: $issues}'
}

closed_entries=(); nocase_entries=(); nopatron_entries=(); unheld_entries=()
for idx in "${!g_line[@]}"; do
  ln="${g_line[$idx]}"
  phrase="${g_phrase[$idx]}"
  patrons="${g_patrons[$idx]}"
  entry=$(entry_json "$ln" "$phrase" "$patrons")

  closed=0
  if [ -n "$patrons" ]; then
    if [ "$patron_state" = read ]; then
      all_closed=1
      IFS=',' read -r -a pnums <<< "$patrons"
      for n in "${pnums[@]}"; do
        [ "${state_of[$n]:-}" = "CLOSED" ] || all_closed=0
      done
      [ "$all_closed" -eq 1 ] && closed=1
    fi
  fi

  nocase=0
  if [ -n "$phrase" ] && ! grep -Fq -- "$phrase" "$cases"; then
    nocase=1
  fi
  nopatron=0
  [ -z "$patrons" ] && nopatron=1

  [ "$closed" -eq 1 ] && closed_entries[${#closed_entries[@]}]="$entry"
  [ "$nocase" -eq 1 ] && nocase_entries[${#nocase_entries[@]}]="$entry"
  [ "$nopatron" -eq 1 ] && nopatron_entries[${#nopatron_entries[@]}]="$entry"
  # The total is the retirement's own pair: a closed patron *and* no case carrying its refusal. The no-patron
  # bucket is a finding of its own, not a retirement candidate.
  if [ "$closed" -eq 1 ] && [ "$nocase" -eq 1 ]; then
    unheld_entries[${#unheld_entries[@]}]="$entry"
  fi
done

json_array() { printf '%s\n' "$@" | jq -s '.'; }
closed_json=$(json_array ${closed_entries[@]+"${closed_entries[@]}"})
nocase_json=$(json_array ${nocase_entries[@]+"${nocase_entries[@]}"})
nopatron_json=$(json_array ${nopatron_entries[@]+"${nopatron_entries[@]}"})
unheld_json=$(json_array ${unheld_entries[@]+"${unheld_entries[@]}"})

if [ "$json" -eq 1 ]; then
  jq -n \
    --argjson guards "${#g_line[@]}" \
    --argjson closed "$closed_json" \
    --argjson nocase "$nocase_json" \
    --argjson nopatron "$nopatron_json" \
    --argjson unheld "$unheld_json" \
    --arg patron_state "$patron_state" \
    --arg patron_reason "$patron_reason" \
    '{guards: $guards,
      closed_patron: $closed,
      no_case: $nocase,
      no_patron: $nopatron,
      unheld: $unheld,
      patron: ({state: $patron_state} + (if $patron_reason == "" then {} else {reason: $patron_reason} end))}'
else
  printf 'guards: %s\n' "${#g_line[@]}"
  if [ "$patron_state" = read ]; then
    printf 'closed-patron: %s\n' "${#closed_entries[@]}"
  else
    printf 'closed-patron: unread — %s\n' "$patron_reason"
  fi
  printf 'no-case: %s\n' "${#nocase_entries[@]}"
  printf 'no-patron: %s\n' "${#nopatron_entries[@]}"
  if [ "$patron_state" = read ]; then
    printf 'unheld: %s\n' "${#unheld_entries[@]}"
  else
    printf 'unheld: unread — %s\n' "$patron_reason"
  fi
fi
