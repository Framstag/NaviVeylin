#!/usr/bin/env bash
#
# open-changes.sh — list all open (in-progress) OpenSpec changes together with
# their open tasks, as a markdown table:
#   | Change | Relevant for | Open tasks | Test advice |
#
# Column 2 ("Relevant for") answers "which surface/module does this change
# touch?" — phone, auto (Android Auto / Android Automotive OS), core (shared
# helpers / native-JNI), or a `+` combination.
#
# Column 4 ("Test advice") is the short form of "how do I close the tasks?":
# a kind prefix (on-device / unit test / build / docs / code) plus one short
# clause taken from the task's `Verify:` instruction when it has one,
# otherwise from the task text itself.
#
# Uses ONLY openspec CLI calls + bash (jq/sed/awk/grep). No python.
#
# Env:
#   OPENSPEC_STORE        optional --store <id> appended to openspec calls
#   OPENSPEC_CHANGES_DIR  override the changes directory (default <repo>/openspec/changes)
#   TASK_NAME_MAX         max chars per task name in column 3 (default 90)
#   TASK_ADVICE_MAX       max chars per test advice in column 4 (default 70)

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
CHANGES_DIR="${OPENSPEC_CHANGES_DIR:-$ROOT/openspec/changes}"
TASK_NAME_MAX="${TASK_NAME_MAX:-90}"
TASK_ADVICE_MAX="${TASK_ADVICE_MAX:-70}"

OS="openspec"
if [ -n "${OPENSPEC_STORE:-}" ]; then OS="openspec --store $OPENSPEC_STORE"; fi

# ── platform relevance ─────────────────────────────────────────────────────
# Per-surface keyword sets. Bare "auto"/"car"/"surface"/"projection" are
# deliberately NOT used: they also match auto-zoom, card, map surface and map
# projection, which say nothing about the Android Auto surface.
AUTO_KW='android auto|aaos|android automotive|automotive|head unit|head-unit|car app|carapp|dhu|car surface|car channel|car hint|rail widget|navigation session|:auto|car screen|car display|car session|host template|surface lifecycle'
PHONE_KW='phone|foldable|tablet|mobile'
CORE_KW=':core|com\.naviveylin\.core|shared helper|\bjni\b|\bnative\b|libosmscout|cmake|repository|persistence'

# Changes routinely name the platform they explicitly do NOT touch ("Android
# Auto … out of scope", "AA behavior unchanged"): those lines must not count.
NEG_KW='out of scope|out-of-scope|not in scope|not touched|untouched|not affected|unchanged|no aa behavior|not in this change|not part of this change|excluded|separate change|not a phone|not covered|does not touch|not modified|no new native|no native|no jni|native purity'

# Number of matching lines in the change's artifacts after negation filtering.
platform_hits() { # $1 = change dir, $2 = keyword regex
  local hits
  hits="$(grep -rhiE "$2" --include='*.md' "$1" 2>/dev/null | grep -vciE "$NEG_KW" || true)"
  printf '%s' "${hits:-0}"
}

# Relevance cell for one change: highest-footprint surfaces joined with `+`.
# A surface counts when it has a real footprint (>=3 matching lines) and is not
# a marginal mention (>=25% of the strongest surface); a platform named in the
# change name gets a small boost so fresh changes are classified before their
# artifacts fill up. Falls back to the single strongest surface.
relevance_cell() { # $1 = change dir, $2 = change name
  local dir="$1" name="$2" a p c max sel=""
  a=$(platform_hits "$dir" "$AUTO_KW")
  p=$(platform_hits "$dir" "$PHONE_KW")
  c=$(platform_hits "$dir" "$CORE_KW")
  printf '%s' "$name" | grep -qiE "$AUTO_KW" && a=$((a + 3))
  printf '%s' "$name" | grep -qiE "$PHONE_KW" && p=$((p + 3))

  max=$a; [ "$p" -gt "$max" ] && max=$p; [ "$c" -gt "$max" ] && max=$c
  [ "$p" -ge 3 ] && [ $((p * 4)) -ge "$max" ] && sel="${sel}phone+"
  [ "$a" -ge 3 ] && [ $((a * 4)) -ge "$max" ] && sel="${sel}auto+"
  [ "$c" -ge 3 ] && [ $((c * 4)) -ge "$max" ] && sel="${sel}core+"

  if [ -z "$sel" ]; then
    if [ "$max" -le 0 ]; then sel="?"
    elif [ "$max" -eq "$p" ]; then sel="phone"
    elif [ "$max" -eq "$a" ]; then sel="auto"
    else sel="core"; fi
  else
    sel="${sel%+}"
  fi
  printf '%s' "$sel"
}

# ── helpers ────────────────────────────────────────────────────────────────
# Extract the task id (e.g. "6.4") from a task line; "-" when the line has none.
task_id() { printf '%s' "$1" | sed -E 's/^[*-] \[.\] *//' | awk '{ print $1 }'; }

# Task name (everything after the id), `|` escaped for the markdown table.
task_name() {
  printf '%s' "$1" | sed -E 's/^[*-] \[.\] *//' | cut -d' ' -f2- | cut -c1-"$TASK_NAME_MAX" | sed 's/|/\\|/g'
}

# Kind prefix of the test advice: what class of work closes the task.
advice_kind() { # $1 = task line + section
  local t="$1"
  if printf '%s' "$t" | grep -qEi 'on-device|emulator|head unit|head-unit|dhu|gpx|logcat|device check|manual|regression check'; then
    printf 'on-device'
  elif printf '%s' "$t" | grep -qEi 'unit test|tests?:|test |robolectric|harness|coverage'; then
    printf 'unit test'
  elif printf '%s' "$t" | grep -qEi 'openspec status|finalize|todo\.md|readme|guidelines|\bdocs\b'; then
    printf 'docs'
  elif printf '%s' "$t" | grep -qEi 'build|gradle|assemble|compile|\babi\b'; then
    printf 'build'
  else
    printf 'code'
  fi
}

# Short advice text: the `Verify:` tail when present (that is the concrete
# thing to check), else the task text. Cleaned in this order: spec citations
# and trailing "— note" suffixes dropped, leading quoted spec fragments
# dropped, leading "Label:" dropped, first clause kept, length capped.
advice_text() { # $1 = task line
  local t
  if printf '%s' "$1" | grep -qi 'Verify:'; then
    t="$(printf '%s' "$1" | sed -E 's/.*[Vv]erify: //')"
  else
    t="$(printf '%s' "$1" | sed -E 's/^[*-] \[.\] *//; s/^[0-9.]+ *//')"
  fi
  t="$(printf '%s' "$t" | sed -E 's/ — .*//; s/\(specs?:[^)]*\)//g; s/[[:space:]]*\(specs?[ :.].*$//; s/;.*//')"
  t="$(printf '%s' "$t" | sed -E 's/^"[^"]*"[,:]? *//; s/^"[^"]*"[,:]? *//; s/^[^:]{3,100}: //')"
  t="$(printf '%s' "$t" | sed -E 's/^[[:space:]"*(]+//; s/[[:space:]]+\././g; s/[[:space:]]+/ /g; s/[[:space:])]+$//')"
  printf '%s' "$t" | cut -c1-"$TASK_ADVICE_MAX" | sed 's/|/\\|/g'
}

# Bucket an open task as on-device / verification when its TEXT or its
# SECTION header matches (many on-device tasks live under a
# "## N. On-device verification" header and carry no device keyword of their
# own, e.g. "Contact with only a formatted address resolves").
is_verification() {
  printf '%s' "$1" | grep -qEi 'on-device|emulator|head unit|gpx|logcat|\(device\)| manual |verify|verification|device check|regression check|regression' && return 0 || return 1
}

# ── root check (read, never writes) ───────────────────────────────────────
LIST_JSON="$($OS list --json 2>/dev/null)" || { echo "openspec list failed — is the CLI installed?" >&2; exit 1; }
ROOT_PATH="$(printf '%s' "$LIST_JSON" | jq -r '.root.path // .root // ""' 2>/dev/null)"
if [ -z "$ROOT_PATH" ] || [ "$ROOT_PATH" = "null" ]; then
  echo "No OpenSpec root found (openspec list returned root=null)." >&2
  exit 1
fi
if [ ! -d "$CHANGES_DIR" ]; then
  echo "Changes dir not found: $CHANGES_DIR (set OPENSPEC_CHANGES_DIR to override)." >&2
  exit 1
fi

# Table header (rendered by most markdown viewers).
printf '| Change | Relevant for | Open tasks | Test advice |\n'
printf '|--------|--------------|------------|-------------|\n'

# ── collect open changes with their task counters ────────────────────────
# tab-separated: name<TAB>done<TAB>total
printf '%s' "$LIST_JSON" |
  jq -r '.changes[] | select(.status == "in-progress") | [.name, (.completedTasks // 0 | tostring), (.totalTasks // 0 | tostring)] | @tsv' |
  while IFS=$'\t' read -r name done total; do
    file="$CHANGES_DIR/$name/tasks.md"
    dir="$CHANGES_DIR/$name"
    if [ -d "$dir" ]; then relevance="$(relevance_cell "$dir" "$name")"; else relevance="?"; fi

    if [ ! -f "$file" ]; then
      printf '| **%s** _(%s/%s done)_ | %s | _(no tasks.md)_ | — |\n' "$name" "${done:-0}" "${total:-0}" "$relevance"
      continue
    fi

    # Open task lines with their section header (## N. …) as a tab-prefixed list.
    open_list="$(awk '
        /^## / { section = $0; sub(/^## */, "", section) }
        /^[*-] \[ \]/ { print section "\t" $0 }
      ' "$file")"

    if [ -z "$open_list" ]; then
      printf '| **%s** _(%s/%s done)_ | %s | _(all tasks done)_ | To close: nothing — archive it |\n' "$name" "${done:-0}" "${total:-0}" "$relevance"
      continue
    fi

    # Build cells: task rows (id + name + section) and short test advice
    # (id + kind + one clause), each joined by <br>; plus the verification
    # bucket for the summary line.
    tasks_cell=""
    advice_cell=""
    open_count=0
    verif_count=0
    while IFS=$'\t' read -r section line; do
      [ -z "$line" ] && continue
      open_count=$((open_count + 1))
      if is_verification "$line" || is_verification "$section"; then verif_count=$((verif_count + 1)); fi
      id="$(task_id "$line")"
      name_part="$(task_name "$line")"
      short_section="$(printf '%s' "$section" | sed -E 's/^[0-9]+\.? *//' | cut -c1-28 | sed 's/|/\\|/g')"
      entry="\`${id}\` ${name_part} _(${short_section})_"
      advice="\`${id}\` *$(advice_kind "$line $section")*: $(advice_text "$line")"
      if [ -n "$tasks_cell" ]; then tasks_cell="${tasks_cell}<br>${entry}"; else tasks_cell="$entry"; fi
      if [ -n "$advice_cell" ]; then advice_cell="${advice_cell}<br>${advice}"; else advice_cell="$advice"; fi
    done <<< "$open_list"

    other=$((open_count - verif_count))
    summary="${done}/${total} done · ${open_count} open"
    [ "$verif_count" -gt 0 ] && summary="${summary} (${verif_count} on-device/verification)"
    [ "$other" -gt 0 ] && summary="${summary}, ${other} other"

    printf '| **%s** _(%s)_ | %s | %s | %s |\n' "$name" "$summary" "$relevance" "$tasks_cell" "$advice_cell"
  done
