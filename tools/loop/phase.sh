#!/usr/bin/env bash
# phase.sh — one bounded call prints where the phase stands (issue #688).
#
# Why: a loop child is started with no project context, so its own phase prompt is its memory, and it goes back to that
# file at every phase boundary. The archive records `read:own-prompt -> read:own-prompt` 485x across 162 of 164 items
# and `read:own-prompt -> read:own-prompt -> sh:git` 146x across 116 — three calls a round (phase prompt, work package,
# `git`), re-injecting 8-17 kB at a context already at 85 k, 369 calls and 2.5 M characters over the archive
# (`python3 tools/loop-spend.py`, 2026-10-28). One call answers what those three were asked for.
#
# It reads the state the run already records — the run's state file, the item's event stream, its work package, the
# phase's own section of the worker prompt, and live git in the item's worktree — and decides nothing: no verdict, no
# next step, no selection of what counts as a criterion. The item's text is printed inside the delimiters the work
# package was rendered with, because it is data taken from a public tracker and stays data (D10).
#
# Usage: tools/loop/phase.sh <phase>
#
#   <phase>   one of `prescreen A B C D E F` — the set `tools/loop/state.sh step` records a step against. The phase's
#             checklist is its own section of the prompt of the child that performs it — `tools/loop/prompts/worker.md`
#             for A–D, `worker-gate.md` for E, `worker-tail.md` for F — printed as it stands (one home for it); a phase
#             no prompt carries a section for says so instead of inventing one.
#
#   Read live from the item's worktree: the branch, HEAD and the base the run recorded, the commits ahead of and
#   behind that base, and the paths the tree has dirty. Read from the item's event stream: the last `LOOP-GATE:` line
#   it recorded, with its case counts. Read from the work package: its fingerprint, provenance, title and the item's
#   own text. Read from the run's state: the item, the change the run asked for, and the phase it recorded.
#
#   Bound: `LOOP_TOOL_RESULT_MAX_CHARS` when the environment names it (the adapter exports the recorded value into
#   every child), else the number `tools/loop/loop.conf` records — one home for it. `0` is no bound. Output at or
#   under the bound is printed whole; above it the sections are printed in the order the bound may take them: the
#   state, the package's identity and the tree stay, then the phase's checklist, then the item's own text — and one
#   line says where the rest is. Paths under the root are printed relative to it, which that `ROOT:` line names
#   (issue #927): the absolute prefix costs the bound bytes on every line, and a deep root spent them on itself.
#
# Exit: 0 printed, 2 the invocation is unusable or the run has written no state.
set -uo pipefail

# The bound the environment names is read here and not after the configuration is sourced: the configuration records
# the project's own value under the same name, so sourcing it first would replace a caller's bound with it.
env_bound=${LOOP_TOOL_RESULT_MAX_CHARS:-}

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

phase=""
while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help) sed -n '2,31p' "$0"; exit 0 ;;
    --) shift ;;
    -*) loop_die "phase: unknown argument $1" ;;
    *) [ -z "$phase" ] || loop_die "phase: one phase, and $1 is a second"
      phase="$1"; shift ;;
  esac
done
[ -n "$phase" ] || loop_die "phase: usage: $(basename "$0") <prescreen|A|B|C|D|E|F>"
# A lower-case letter is the same phase: the letters are what a person types and what `state.sh` records.
case "$phase" in
  PRESCREEN) phase=prescreen ;;
  [a-f]) phase=$(printf '%s' "$phase" | tr '[:lower:]' '[:upper:]') ;;
esac
case "$phase" in
  prescreen|A|B|C|D|E|F) ;;
  *) loop_die "phase: '$phase' is not one of prescreen A B C D E F — the set the run records a step against" ;;
esac

loop_resolve
state_file="$LOOP_STATE_FILE"
if [ ! -f "$state_file" ]; then
  printf 'phase: the run has written no state at %s — there is nothing to print yet\n' "$state_file" >&2
  exit "$LOOP_USAGE"
fi
state_read=$(jq -r '[(.current // {} | .issue // "" | tostring),
                     (.current // {} | .phase // "" | tostring),
                     (.current // {} | .change // "" | tostring),
                     (.run // {} | .base // "" | tostring)] | @tsv' "$state_file" 2>/dev/null) \
  || { printf 'phase: %s is not a state this call can read\n' "$state_file" >&2; exit "$LOOP_USAGE"; }
IFS=$'\t' read -r issue recorded change base <<< "$state_read"

# The bound: the value the environment named, else the project's recorded one, and `0` for "no bound".
bound=$env_bound
[ -n "$bound" ] || bound=$(loop_conf_value LOOP_TOOL_RESULT_MAX_CHARS)
case "$bound" in ''|*[!0-9]*) bound=0 ;; esac

# One path as this answer prints it: relative to the run's root when it lies inside it, unchanged otherwise (issue
# #927). The `ROOT:` line the body opens with is what makes the relative form resolvable, so a path outside the root
# keeps its absolute form rather than becoming a name nobody can follow.
phase_path_from_root() { # <path> → the path relative to the root, or the path itself
  case "$1" in
    "$LOOP_ROOT"/*) printf '%s\n' "${1#"$LOOP_ROOT"/}" ;;
    *) printf '%s\n' "$1" ;;
  esac
}

temp=$(mktemp -d) || { printf 'phase: cannot create a temporary directory\n' >&2; exit "$LOOP_USAGE"; }
trap 'rm -rf "$temp"' EXIT
body="$temp/body"

prompt="$here/prompts/worker.md"
# Phase F's checklist is the tail child's own prompt, because that phase is a second child (issue #744): the run
# starts it from the gate block, so the prompt that states it is the one the tail process is handed, and a phase
# whose checklist lived in the child that has already ended would be a checklist nobody reads. Phase E is the gate
# child's for the same reason (issue #758): the run defers the gate to that child, so the section that states it is
# the one that child is started from.
case "$phase" in
  E) prompt="$here/prompts/worker-gate.md" ;;
  F) prompt="$here/prompts/worker-tail.md" ;;
esac
# The phase's own section: its heading and everything under it, up to the prompt's next second-level heading, with the
# blank lines its end carries trimmed — the checklist is read from the one file that states it, never copied here.
section=""
if [ -f "$prompt" ]; then
  section=$(awk -v p="$phase" '
    $0 ~ ("^## Phase " p "([ ]|$)") { inside = 1; lines[++n] = $0; if (NF) last = n; next }
    inside && /^## / { inside = 0 }
    inside { lines[++n] = $0; if (NF) last = n }
    END { for (i = 1; i <= last; i++) print lines[i] }
  ' "$prompt")
fi
heading=$(printf '%s\n' "$section" | sed -n '1s/^## //p')

{
  if [ -n "$heading" ]; then
    printf 'LOOP-PHASE: %s — %s\n' "$phase" "${heading#Phase "$phase" — }"
  else
    printf 'LOOP-PHASE: %s\n' "$phase"
  fi
  # The root once, and every path under it relative to this line: a deep root then costs the answer nothing, whatever
  # its length, and the bound it is cut at is spent on the state a child cannot re-derive (issue #927).
  printf 'ROOT: %s · a path under it is printed relative to it\n' "$LOOP_ROOT"
  if [ -n "$issue" ]; then
    printf 'ITEM: #%s %s · the state records phase %s · base %s\n' \
      "$issue" "${change:-<no change name yet>}" "${recorded:-none}" "${base:0:12}"
  else
    printf 'ITEM: none in progress — the run has started no item\n'
  fi

  # The item's own stream and its work package, before the two sections a cut may take (issue #927): the fingerprint,
  # the provenance and the path that names the package are what a child can never re-derive, while the checklist below
  # and the item's own text are both re-readable at the path printed here.
  item_dir=$(dirname "$state_file")
  [ -n "$issue" ] && item_dir="$item_dir/items/$issue"
  package=""
  if [ -n "$issue" ] && [ -f "$item_dir/work-package.md" ]; then
    package="$item_dir/work-package.md"
    printf 'PACKAGE: %s · fingerprint %s · provenance %s\n' "$(phase_path_from_root "$package")" \
      "$(sed -n 's/^fingerprint: //p' "$package" | head -1)" \
      "$(sed -n 's/^provenance: //p' "$package" | head -1)"
    printf 'ACCEPTANCE: %s\n' "$(sed -n 's/^title: //p' "$package" | head -1)"
  elif [ -n "$issue" ]; then
    printf 'PACKAGE: none — the run wrote no work package at %s\n' "$(phase_path_from_root "$item_dir/work-package.md")"
  fi

  # The tree the item's work lands in, and what it holds now: a phase boundary is when a child asks where it is.
  worktree="$LOOP_WORKTREE"
  if [ -d "$worktree" ] && git -C "$worktree" rev-parse --git-dir >/dev/null 2>&1; then
    branch=$(git -C "$worktree" rev-parse --abbrev-ref HEAD 2>/dev/null)
    head=$(git -C "$worktree" rev-parse HEAD 2>/dev/null)
    ahead="-"; behind="-"
    if [ -n "$base" ] && git -C "$worktree" rev-parse --verify --quiet "$base^{commit}" >/dev/null 2>&1; then
      ahead=$(git -C "$worktree" rev-list --count "$base"..HEAD 2>/dev/null)
      behind=$(git -C "$worktree" rev-list --count HEAD.."$base" 2>/dev/null)
    fi
    printf 'GIT: branch %s · HEAD %s · base %s · ahead %s · behind %s\n' \
      "${branch:-<detached>}" "${head:0:12}" "${base:0:12}" "${ahead:--}" "${behind:--}"
    dirty=$(git -C "$worktree" status --porcelain 2>/dev/null | sed 's/^...//')
    dirty_n=$(printf '%s' "$dirty" | grep -c . || true)
    if [ "$dirty_n" -eq 0 ]; then
      printf 'DIRTY: none — the tree is clean\n'
    else
      printf 'DIRTY: %s path(s): %s\n' "$dirty_n" \
        "$(printf '%s\n' "$dirty" | head -8 | paste -sd, - | sed 's/,/, /g')"
    fi
  else
    printf 'GIT: no worktree at %s\n' "$(phase_path_from_root "$worktree")"
  fi

  # The last gate verdict the item's own stream recorded. Its case counts are printed as the stream carried them; the
  # verdict is the arithmetic the gate itself applies to them, not a judgement of this call.
  events="$item_dir/events.jsonl"
  gate=""
  [ -f "$events" ] && gate=$(grep -o 'LOOP-GATE: tests=[0-9][0-9]* failures=[0-9][0-9]*\( errors=[0-9][0-9]*\)\?' \
    "$events" 2>/dev/null | tail -1)
  if [ -n "$gate" ]; then
    failures=$(printf '%s' "$gate" | sed -n 's/.*failures=\([0-9][0-9]*\).*/\1/p')
    errors=$(printf '%s' "$gate" | sed -n 's/.*errors=\([0-9][0-9]*\).*/\1/p')
    verdict=green
    [ "${failures:-0}" -eq 0 ] && [ "${errors:-0}" -eq 0 ] || verdict=red
    printf 'GATE: %s — %s — the last gate line %s records\n' "$verdict" "${gate#LOOP-GATE: }" \
      "$(phase_path_from_root "$events")"
  else
    printf 'GATE: none recorded — %s carries no gate line yet\n' "$(phase_path_from_root "$events")"
  fi

  # Which paths this phase is expected to change: the item's write surface, derived from the change the run asked for
  # and the recorded record root, never a project path written into this call.
  record_root=$(phase_path_from_root "$(loop_conf_value LOOP_UNTRACKED_RECORD_DIR)")
  change_word="${change:-<the change the run asked for>}"
  case "$phase" in
    prescreen) writes="none — the run's own pre-screen writes its queue, not the item's tree" ;;
    A|B) writes="none yet — a verdict is not a write; the red case lands in the tree the plan fixes" ;;
    C) writes="${record_root}/${change_word}/ — proposal.md, design.md, tasks.md, specs/<capability>/spec.md" ;;
    D) writes="${record_root}/${change_word}/ and the code paths its tasks name" ;;
    E) writes="none — a gate, not a write" ;;
    F) writes="${record_root}/archive/${change_word}/ and the capability spec its delta merges into" ;;
  esac
  printf 'PATHS: %s\n' "$writes"
  [ -n "$issue" ] && printf 'PATHS: the item evidence copies, always — %s\n' "$(phase_path_from_root "$item_dir")"

  # The checklist's section comes after the state, and the package's identity above it is what a cut then leaves: the
  # section is the largest block this call prints and the prompt it is read from is named on its own line (issue #927).
  printf 'CHECKLIST: %s\n' "$(phase_path_from_root "$prompt")"
  if [ -n "$section" ]; then
    printf '%s\n' "$section"
  else
    printf 'the prompt carries no section for phase %s — its checklist is the recorded one in %s\n' \
      "$phase" "$(phase_path_from_root "$prompt")"
  fi

  # The item's own text last: the one block the bound exists to take, and the one a child can re-read in full at the
  # package path printed with its identity.
  if [ -n "$package" ]; then
    sed -n '/^<<<ISSUE_TEXT$/,/^ISSUE_TEXT$/p' "$package"
  fi
} > "$body"

size=$(wc -c < "$body")
if [ "$bound" -gt 0 ] && [ "$size" -gt "$bound" ]; then
  where=${item_dir:+$(phase_path_from_root "$item_dir")}
  where=${where:-the run state directory}
  notice="[PHASE-BOUND: cut at $bound characters — the state and the package's identity are printed first, so a cut takes the checklist's tail and then the item's own text; the package is in $where]"
  # The notice's length is its bytes and not `${#notice}`, which counts characters in a UTF-8 locale: the em dash alone
  # would put the answer two bytes over the bound it claims to stay inside.
  notice_bytes=$(printf '%s' "$notice" | wc -c)
  keep=$((bound - notice_bytes - 2))
  [ "$keep" -ge 1 ] || keep=1
  head -c "$keep" "$body"
  printf '\n%s\n' "$notice"
else
  cat "$body"
fi
exit 0
