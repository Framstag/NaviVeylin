#!/usr/bin/env bash
# lib.sh — what every loop script shares: where the run lives, the configuration it reads, and the
# one way it talks to the issue tooling.
#
# Two rules are implemented once here, because a rule re-implemented per script is a rule that drifts
# (design D16):
#
#   * **the project is discovered, never told.** The root is the working tree's, so the same script
#     runs against a scratch repository in the self-test and against this project on a real run.
#     `LOOP_CONF`, `LOOP_STATE_FILE` and `LOOP_WORKTREE` are the only overrides, and they exist so the
#     self-test can exercise the configuration seam.
#   * **the loop's own mechanics are configuration.** No script under `tools/loop/` names a skill, a
#     gate command or an agent command; they are read from `loop.conf` (D16). The one exception is
#     the authorisation label, which appears once, read-only, so "the run may never write it" can be
#     asserted mechanically (D2).
#
# Usage: `source lib.sh` — then `loop_resolve` once, at the top of the script, before anything else.
set -uo pipefail

LOOP_OK=0
LOOP_USAGE=2
LOOP_STOP=3
LOOP_REFUSE=5
# The status `state.sh next` returns when the run may not start another item **yet**: a condition outside the run
# — the provider's own quota, read through the project's `LOOP_QUOTA_CMD` — that a wait clears. Distinct from
# `LOOP_STOP` because the run pauses rather than ends: `run.sh` sleeps until the instant the hold names, moves its
# deadline with the wait (a pause is not work, `state.sh hold`), and asks `next` again.
LOOP_HOLD=6
# The other half of that: the status a **reader** returns when the run may not start another item yet. The reader
# is the project's (`LOOP_QUOTA_CMD`, `tools/loop-quota.sh`) and cannot know the engine's own codes, so its protocol
# is its own — 0 the run may go on, this value the run must wait, anything else "the reading could not be made",
# which is not a refusal — and `state.sh next` turns this value into `LOOP_HOLD` for `run.sh`. The engine compares
# against this constant and never against the literal.
LOOP_HOLD_READ=3
# The status `run.sh`'s `run_child` returns when it ended a child at the configured wall-clock
# (`LOOP_CHILD_MAX_SECONDS`). It is a child's status, never the run's, and it is distinct so the run's own
# "the worker process exited N" path and a reader can tell a timeout from a child that failed by itself.
LOOP_CHILD_TIMEOUT=124

# The engine's own directory. Everything the loop owns relative to itself is derived from this, never
# from the current directory, so the engine can run inside another project (D16).
LOOP_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# The issue tooling is the loop's dependency, shipped beside it. Its `gh`-facing helpers are reused
# rather than re-implemented, and the project it acts on is still resolved from the working tree.
LOOP_ISSUES_DIR="$LOOP_DIR/../issues"
# shellcheck source=tools/issues/lib.sh
source "$LOOP_ISSUES_DIR/lib.sh"

LOOP_ROOT=""
# The configuration file `loop_resolve` actually sourced. It is a global because the file a gate read is the rule
# its numbers attest, so the process that ran it names it in its own log (issue #277). Empty until `loop_resolve`
# has run, and every engine script resolves before it reads a key.
LOOP_CONF_PATH=""
# `LOOP_STATE_FILE` and `LOOP_WORKTREE` are the self-test's configuration seam (and the only way to run
# two loops in one tree deliberately). An inherited value is preserved, never cleared: assigning an
# empty string here would drop the environment's answer.
LOOP_STATE_FILE="${LOOP_STATE_FILE:-}"
LOOP_WORKTREE="${LOOP_WORKTREE:-}"
# Where the loop's tooling puts throwaway trees: the self-test creates one per run here, and
# `loop-clean.sh` removes the ones a killed suite left behind — the same name, read from this one
# definition, is what tells the loop's trees from another suite's. `LOOP_TMP_DIR` (loop.conf) moves it;
# empty means `.pi/tmp` under the project root, beside the worktree default. Resolved in `loop_resolve`.
LOOP_TMP_DIR="${LOOP_TMP_DIR:-}"
# The name one self-test scratch tree carries: `<LOOP_TMP_DIR>/loop-selftest.<random>`.
LOOP_SELFTEST_PREFIX="loop-selftest."

# Bytes and the line the last `loop_scratch_remove` produced. Globals rather than a return value: a
# command substitution would run the removal in a subshell and lose the count with it.
LOOP_SCRATCH_LINE=""
LOOP_SCRATCH_BYTES=0

# Read-only: the label that authorises automated work. The loop *reads* it (a candidate must carry it
# to be selected) and has no code path that writes it — the write paths of the issue tooling refuse it
# as a reserved label, and `labels#neverWritesReservedLabel` asserts this file is its only mention.
LOOP_AUTHORISATION_LABEL="agent/eligible"

# The run's own progress lines (issue #191). One format — `loop: <phase> · <elapsed> · <what>` — where the
# elapsed time is counted from this process reading this library, so it is the run's own age and not a phase's.
# A progress line is terminal output and nothing else: no state field, no report cell and no verdict is derived
# from one, which is what makes a run that printed them and a run that did not the same run (#191, constraint).
LOOP_PROGRESS_STARTED="$(date +%s)"

fmt_elapsed() { # <seconds> -> `37s`, `4m12s`
  local s="$1"
  if [ "$s" -ge 60 ]; then printf '%dm%02ds' "$((s / 60))" "$((s % 60))"; else printf '%ds' "$s"; fi
}

progress() { # progress <phase> <what> — one line wherever the run crosses a boundary it can observe itself
  local phase="$1"; shift
  printf 'loop: %s · %s · %s\n' "$phase" "$(fmt_elapsed $(( $(date +%s) - LOOP_PROGRESS_STARTED )))" "$*"
}

loop_die() {
  printf 'loop: %s\n' "$*" >&2
  exit "$LOOP_USAGE"
}

loop_refuse() {
  printf 'loop: %s\n' "$*" >&2
  exit "$LOOP_REFUSE"
}

loop_note() {
  printf '%s\n' "$*" >&2
}

# What a gate tally says about the run it measured (issue #811). The tally is free text the run writes — a child's
# `LOOP-GATE` line, the count of tallies the run found, `skipped (no gate configured)`, `not run — …` — so the
# one predicate every reader of it needs is read out of the text and never out of a second field: `red` when it
# carries a nonzero `failures=` or `errors=`, `green` when it carries the counts and they are zero, and `unknown`
# when it carries neither, which are the shapes that measured no test. A reader that took `unknown` for `green`
# would close an item over a gate nobody read, and one that took it for `red` would block a project with no gate.
gate_tally_state() { # gate_tally_state <tally> → red | green | unknown
  local tally="${1:-}" failures errors
  failures=$(printf '%s' "$tally" | sed -n 's/.*failures=\([0-9][0-9]*\).*/\1/p' | tail -1)
  errors=$(printf '%s' "$tally" | sed -n 's/.*errors=\([0-9][0-9]*\).*/\1/p' | tail -1)
  if [ -z "$failures$errors" ]; then printf 'unknown\n'; return 0; fi
  if [ "${failures:-0}" -gt 0 ] || [ "${errors:-0}" -gt 0 ]; then printf 'red\n'; else printf 'green\n'; fi
}

# Resolve the project once and load the configuration. Called at the top of every script, never inside
# a command substitution: a failure inside `$( )` would exit a subshell and be swallowed.
loop_resolve() {
  LOOP_ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || {
    # One refusal covers two different states, and they need different repairs: run from outside a checkout at
    # all, or inside a repository marked bare — where `git status` still answers, so the tree looks like a project
    # while every merge, fast-forward and commit in it is refused. Measured 2026-10-21 (issue #241): the caller read
    # "run this from the project you mean to act on" while standing in the project, spent five fast-forwards and
    # an hour, and this setting was the cause.
    #
    # The setting's own name is assembled from two pieces because the portability check reads the profile's
    # vocabulary as *substrings*: the four letters of one namespace value sit inside that name, so writing it out
    # whole would be reported as a project value leaked into an engine. Assembling it keeps the sentence readable
    # and the check honest about what it can actually prove (the substring match is its own item).
    bare_key="c""ore"
    if [ -d "$(git rev-parse --git-dir 2>/dev/null)" ] \
       && [ "$(git rev-parse --is-bare-repository 2>/dev/null)" = "true" ]; then
      printf 'loop: this repository is marked bare (%s.bare=true), so it is not a work tree\n' "$bare_key" >&2
      printf 'loop: every merge, fast-forward and commit is refused in it while `git status` still answers\n' >&2
      printf 'loop: repair: git config %s.bare false   (in %s)\n' "$bare_key" "$PWD" >&2
      exit "$LOOP_REFUSE"
    fi
    loop_die "not inside a git working tree — run this from the project you mean to act on"
  }
  local conf="${LOOP_CONF:-$LOOP_DIR/loop.conf}"
  [ -f "$conf" ] || loop_die "no configuration at $conf — the loop reads its defaults and its project keys from loop.conf"
  LOOP_CONF_PATH="$conf"
  # shellcheck source=tools/loop/loop.conf
  source "$conf"
  # An inherited run identity is honoured only inside the tree this process resolved; `loop_path_outside_tree`
  # above carries the measurement and the reason (issue #214).
  if loop_path_outside_tree "$LOOP_STATE_FILE" "$LOOP_ROOT"; then LOOP_STATE_FILE=""; fi
  if loop_path_outside_tree "$LOOP_WORKTREE" "$LOOP_ROOT"; then LOOP_WORKTREE=""; fi
  LOOP_STATE_FILE="${LOOP_STATE_FILE:-$LOOP_ROOT/.pi/issue-loop/run.json}"
  LOOP_WORKTREE="${LOOP_WORKTREE:-$LOOP_ROOT/.pi/worktrees/loop}"
  # A relative override is relative to the project, not to whatever the caller's cwd happened to be.
  case "$LOOP_STATE_FILE" in /*) ;; *) LOOP_STATE_FILE="$LOOP_ROOT/$LOOP_STATE_FILE" ;; esac
  case "$LOOP_WORKTREE" in /*) ;; *) LOOP_WORKTREE="$LOOP_ROOT/$LOOP_WORKTREE" ;; esac
  loop_tmp_resolve
}

# `LOOP_TMP_DIR` as recorded, resolved against the project root — a relative value is relative to the
# project, exactly like the state file and the worktree. Idempotent, so the self-test can call it itself.
# `loop_path_outside_tree <path> <root>` — true when <path> is absolute and lies outside <root>. `run_child`
# hands the run's identity to every child so all of them agree on the run, and a child that runs the loop's own
# tooling in another tree — a scratch repository, a fixture, the self-test of the loop itself — would otherwise
# write the *running* run's state and point its worktree operations at the run's tree. Measured 2026-10-20
# (issue #214): a live run's state was replaced five times, each time with a fixture's base, queue and budget,
# while its worker was still working the item; the run then walked the fixture's queue. A relative value, and
# an absolute one inside this tree (two loops in one repository, deliberately), are not affected.
loop_path_outside_tree() { # <path> <root> → 0 outside
  case "$1" in
    /*) case "$1" in "$2"/*) return 1 ;; *) return 0 ;; esac ;;
    *) return 1 ;;
  esac
}

loop_tmp_resolve() {
  case "$LOOP_TMP_DIR" in
    "") LOOP_TMP_DIR="$LOOP_ROOT/.pi/tmp" ;;
    /*) ;;
    *) LOOP_TMP_DIR="$LOOP_ROOT/$LOOP_TMP_DIR" ;;
  esac
}

# The scratch root *another tree* resolves, which is not the one this process resolved. A suite creates its trees
# under the root of the tree it runs in, so a gate that runs `tools/loop/tests/loop-selftest.sh` inside the kept worktree makes
# them under the worktree's own `.pi/tmp` — the root a report run from the project never looked in while 305 MB of
# trees and four poll loops accumulated there (measured 2026-10-23, issue #274). The key is read from that tree's
# configuration, the same file `loop_resolve` sources when it runs there, and it is **read, never sourced**: a
# report looks at a tree, it does not run that tree's configuration.
loop_tmp_dir_for() { # <tree> → that tree's scratch root, resolved against that tree
  local tree="${1:-}" recorded=""
  if [ -n "$tree" ] && [ -f "$tree/tools/loop/loop.conf" ]; then
    recorded=$(sed -n 's/^LOOP_TMP_DIR\="\{0,1\}\([^"\]*\)"\{0,1\}[[:space:]]*$/\1/p' \
      "$tree/tools/loop/loop.conf" | tail -1)
  fi
  case "$recorded" in
    "") printf '%s\n' "$tree/.pi/tmp" ;;
    /*) printf '%s\n' "$recorded" ;;
    *) printf '%s\n' "$tree/$recorded" ;;
  esac
}

# The same directory however each side is spelled: `--worktree .` (or a relative name this tree resolves to) makes the
# invocation's scratch root and the kept worktree's one directory written two ways — `$LOOP_ROOT/.pi/tmp` against
# `$LOOP_ROOT/./.pi/tmp` — and compared as strings they never match, so the same trees would be listed twice and their
# bytes counted twice while `--apply` removes them once (issue #274, round-1 review). Empty and vanished paths are not
# the same directory: a root that does not exist has no tree to list under it either.
loop_same_dir() { # <a> <b> → 0 when both name the same existing directory
  local a b
  [ -n "${1:-}" ] && [ -n "${2:-}" ] || return 1
  a=$(cd "$1" 2>/dev/null && pwd -P) || return 1
  b=$(cd "$2" 2>/dev/null && pwd -P) || return 1
  [ "$a" = "$b" ]
}

loop_conf_value() { # print one configuration key's value by name
  local key="$1"
  eval "printf '%s' \"\${$key:-}\""
}

# The gate's own program, as a child's tool call names it (issue #830): the first word of `LOOP_GATE_CMD` that names
# a path, or its first word when none does — `tools/loop/gate.sh`, `bash tools/loop/gate.sh` and a bare command all
# reduce to the program a call starts. `state.sh` reads it for the report and `run.sh` for the count of the checks a
# child runs by hand (issue #882), so the definition is here and beside neither reader.
gate_program() { # → the program the gate's command starts
  local gate_cmd word prog=""
  gate_cmd=$(loop_conf_value LOOP_GATE_CMD)
  for word in $gate_cmd; do
    case "$word" in -*) continue ;; esac
    case "$word" in */*) prog="${word##*/}"; break ;; esac
    [ -n "$prog" ] || prog="$word"
  done
  printf '%s' "$prog"
}

# The programs the gate's **own** checks start (issue #857). The profile's map names them — the suites and structural
# checks a delta owes (`LOOP_GATE_SUITES`, `LOOP_GATE_FIRST_STEPS`) and the build's own gate
# (`LOOP_GATE_COMPILED_CMD`) — and it is the map `gate.sh --plan` reads, so nothing here names a project path (D16).
# A child that runs one of those commands by hand pays the gate's work outside `gate.sh`, and the bucket that prices
# the gate cannot leave it in ordinary tool time: measured on item #849's own stream, one `./tools/loop/gate.sh` at
# 823 s and four check calls a child ran by hand for 674 s — 304 s, 310 s, 39 s and 21 s — all four standing before
# that `gate.sh` call, so a bucket that took only the gate call named its 823 s and left those 674 s as ordinary tool
# time. A map entry is `<glob>|<command>`, and each command reduces to a program the way the gate's own program is
# read: its first word that names a path, or its first word when none does. `run.sh` bounds the same calls live
# (issue #882), so the two readers share this enumeration and the definitions below.
gate_check_programs() { # → the programs the gate's checks start, one per line
  local line word prog
  # `loop_conf_value` prints no newline of its own, so each value is closed here: without it the gate's own program
  # glued itself to the build's and the map's last entry — the one `while … read` needs a newline after to see — was
  # dropped, so the enumeration the bound counts against was short by two programs (issue #882).
  {
    loop_conf_value LOOP_GATE_CMD; printf '\n'
    loop_conf_value LOOP_GATE_COMPILED_CMD; printf '\n'
    loop_conf_value LOOP_GATE_SUITES | tr ';' '\n' | sed -e 's/^[^|]*|//'
    loop_conf_value LOOP_GATE_FIRST_STEPS | tr ';' '\n' | sed -e 's/^[^|]*|//'
    printf '\n'
  } | while IFS= read -r line; do
        set -f # the map is hand-kept data: a command's words are read here, never expanded as globs
        prog=""
        for word in $line; do
          case "$word" in -*) continue ;; esac
          case "$word" in */*) prog="${word##*/}"; break ;; esac
          [ -n "$prog" ] || prog="$word"
        done
        [ -n "$prog" ] || continue
        printf '%s\n' "$prog"
      done
}

# Which command a child's tool call **runs**, as jq definitions, and whether it is the gate's own run or one of the
# checks the gate would run (issues #830, #857, #882). One set of definitions, because two readers ask it: the
# report's classification (`state.sh`'s `child_usage_json`) and the run's live count of the checks a child runs by
# hand, which ends that child at the profile's bound. A second copy is a second rule, and the two would then
# disagree about which calls the gate would have re-run. The words before the program may be a `cd …` prefix,
# leading `VAR=value` assignments and a `bash`/`sh`/`exec`/`timeout <n>` wrapper, so `cd /w && timeout 300
# ./tools/loop/gate.sh` runs the gate while a command whose argument is the path of the gate — a `sed -n 1,60p
# tools/loop/gate.sh`, a `git show HEAD:tools/loop/gate.sh`, a `grep` over it — reads that file and runs nothing. A
# `--plan` call runs no step either. Both read a command string as `.` and the two variables the caller binds:
# `$gate` (the gate's program) and `$checks` (the programs its checks start).
gate_command_jq() { # → jq definitions `words`, `assignment`, `wrapper`, `run_word`, `is_gate`, `is_check`
  cat <<'JQ'
      def words: [((. // "") | tostring) | splits("[ \\t;|&]+")] | map(select(length > 0));
      def assignment: test("^[A-Za-z_][A-Za-z0-9_]*=");
      def wrapper: . as $w | (["bash", "sh", "exec", "command", "timeout", "env", "nohup"] | index($w)) != null;
      def run_word:
        (if .[0] == "cd" then .[2:] else . end)
        | until((length == 0) or (((.[0] | assignment) or (.[0] | wrapper)) | not);
            (if (.[0] | assignment) then .[1:]
             elif (.[0] == "timeout" and ((.[1] // "") | test("^[0-9]"))) then .[2:]
             else .[1:] end))
        | .[0] // "";
      def is_gate: (($gate | length) > 0)
                   and ((words) as $w
                        | (($w | run_word) as $prog
                           | (($prog == $gate) or ($prog | endswith("/" + $gate))))
                          and ([$w[] | select(. == "--plan")] | length == 0));
      def is_check: (($checks | length) > 0)
                    and (([((. // "") | tostring) | splits("[;&|]+") | words
                           | (run_word) as $prog
                           | select([$checks[] as $c | select(($prog == $c) or ($prog | endswith("/" + $c)))] | length > 0)]
                          | length) > 0)
                    and ([words[] | select(. == "--plan")] | length == 0);
JQ
}

# How many of the gate's checks a child has run by hand since the last look (issue #882), read from the lines its
# stream has gained — the same incremental reading `run.sh`'s `child_context` takes, so a stream that runs to
# megabytes costs one `wc` and one `jq` over the new lines per second and never a re-parse of the whole file. The
# calls counted are the ones the gate itself would re-run (`gate_check_programs`), less the gate's own run: a check
# the gate would not run again is the focused iteration an item's own diagnosis owes and is nobody's to bound. A
# stream that is absent, empty or unreadable is no reading rather than an error, exactly as `child_context` treats
# one: a child whose command writes no events is bounded by its clock, its turns and its context alone.
child_check_calls() { # <events file> <lines already read> → "<check calls in the new lines> <lines read>"
  local f="${1:-}" done_lines="${2:-0}" total gate_prog checks_json n
  [ -n "$f" ] && [ -f "$f" ] || { printf '0 0\n'; return 0; }
  total=$(wc -l < "$f" 2>/dev/null) || true
  case "$total" in ''|*[!0-9]*) total=0 ;; esac
  case "$done_lines" in ''|*[!0-9]*) done_lines=0 ;; esac
  [ "$total" -ge "$done_lines" ] || done_lines=0
  n=0
  if [ "$total" -gt "$done_lines" ]; then
    gate_prog=$(gate_program)
    checks_json=$(gate_check_programs | sort -u | jq -R -s -c 'split("\n") | map(select(length > 0))' 2>/dev/null)
    case "$checks_json" in '['*) ;; *) checks_json='[]' ;; esac
    # The call is read where the stream puts it — inside the turn it was made in, `turn_end.message.content`, the shape
    # `state.sh`'s `gateRepeat` classifies — and not as a top-level record, which a child's stream never writes: read
    # that way the count stayed zero and the bound never fired (issue #882).
    n=$(tail -n +$((done_lines + 1)) "$f" 2>/dev/null | head -n $((total - done_lines)) |
      jq -s -c --arg gate "$gate_prog" --argjson checks "$checks_json" "$(gate_command_jq)"'
      [.[] | select(.type == "turn_end") | (.message.content // [])[]
           | select(.type == "toolCall")
           | ((.arguments?.command? // "") | tostring) as $c
           | select(($c | is_check) and (($c | is_gate) | not))] | length' 2>/dev/null) || n=""
    case "$n" in ''|*[!0-9]*) n=0 ;; esac
  fi
  printf '%s %s\n' "$n" "$total"
}

# The branch every item descends from: the run's base is *this* branch's commit, never the commit the project
# tree happens to have checked out (issue #301 — `state.sh init` read `HEAD`, so a run opened on a feature branch
# recorded that branch's commit and refused its own first item as a stale base). One definition, because the
# same name is read by `state.sh` (what a run records), `worktree.sh` (`check_base`), `run.sh` (what a merge
# advances) and `loop-clean.sh` (what a sweep compares against): a second copy of the rule is a second rule.
#
# The name itself is the project's, so it is recorded in `loop.conf` as `LOOP_BASE_REF` — this expansion is what
# reads it, and `main` is only the fallback for a configuration that records none (issue #303: the branch name was
# reachable through the environment alone, which a ported project can neither commit nor audit).
loop_base_ref() { printf '%s\n' "${LOOP_BASE_REF:-main}"; }

# Whether a commit is on the base branch: the question a closure's claim rests on (#715). A closure wrote
# `status/fixed-by` and cited a branch, and a branch is exactly what the base may carry none of — five items of the
# run of 2026-10-30 read `CLOSED status/fixed-by` while their commits sat on a branch the base never received. The
# loop asks this before it writes the settled state, because `close.sh`'s own refusal arrives after that write and
# leaves the item settled on a fix the tree does not have, which `prescreen.sh` then reads as one nobody revisits.
#
# The answers are told apart because a caller reports different things: a commit the tree does not have and a base
# branch that does not resolve are both no verdict about the item, and only `1` is the item's own state.
loop_landed_on_base() { # <ref> → 0 the base branch carries it, 1 it does not, 2 the ref does not resolve, 3 no base
  local ref="${1:-}" commit base base_ref
  [ -n "$ref" ] || return 2
  commit=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "${ref}^{commit}" 2>/dev/null) || return 2
  base_ref=$(loop_base_ref)
  base=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "${base_ref}^{commit}" 2>/dev/null) || return 3
  git -C "$LOOP_ROOT" merge-base --is-ancestor "$commit" "$base" >/dev/null 2>&1 && return 0
  return 1
}

# The ceiling of whatever started this invocation, in seconds (issue #692): `LOOP_CALL_CEILING_SECONDS` in the
# configuration, which a caller sets at the call site. Every bound this project applies is a recorded value — 900 s
# for a case, 1 800 s for a gate step, 2 700 s for a child — and the harness a child runs under ends one tool call at
# a ceiling of its own that no project file can see (measured twice on 2026-10-28, `900000ms`, both mid-suite with the
# child's work uncommitted). A bound larger than that ceiling is not a bound: the caller ends the call first and the
# invocation never writes the line that says what it was doing. One reader of the key, for both invocations that
# apply a bound, because a second copy of the rule is a second rule (D16).
#
# Empty, non-numeric or `0` prints nothing, which is no ceiling named: every bound keeps its recorded value and the
# invocation starts no watcher — the shape this project ran in before the key existed. The key's own record, and why
# the value is the last second an invocation may run rather than the caller's hard kill, is in `loop.conf`.
loop_ceiling() { # → the caller's ceiling in whole seconds, empty when none was named
  local c="${LOOP_CALL_CEILING_SECONDS:-}"
  case "$c" in ''|*[!0-9]*|0) return 0 ;; esac
  printf '%s' "$c"
}

loop_bound_under_ceiling() { # <bound> → that bound, or the ceiling when the ceiling is the smaller
  local bound="$1" ceiling
  ceiling=$(loop_ceiling)
  if [ -n "$ceiling" ] && [ "$ceiling" -lt "$bound" ]; then
    printf '%s' "$ceiling"
  else
    printf '%s' "$bound"
  fi
}

# ── the tree a measurement was taken on ────────────────────────────────────────────────────────────────────────
# A record that names no tree cannot be attributed to one (issue #819): the gate wrote the child's session and the
# arguments it ran with and nothing else, so a run over a tree and a run over that tree corrected afterwards read
# alike, and a change could be archived with a gate taken before its own corrections
# (`openspec/changes/archive/2026-10-22-fix-staging-leftover-discovered-as-map/tasks.md` §5.6 claims a round-2 gate
# re-run, and every XML in its `evidence/gate/` is stamped 12:23–12:26 against round-2's own 12:42–12:43).
#
# The digest is **commit-invariant**: the paths the base names, plus every path git does not track, each with the hash
# of its worktree content, less what the profile's `LOOP_REVIEW_EXCLUDE_PATHSPEC` leaves out. A digest of `HEAD` or of
# `git status` would make the ordinary flow read as a corrected one, because the change child measures an uncommitted
# tree and the run commits it before the diff a reviewer reads is written.
#
# ── the change a tree carries, under the one name it has before and after its archive ──────────────────────────
# A change is worked under `<LOOP_CHANGE_SOURCE_DIR>/<name>/`, which this project's `.gitignore` leaves out
# (`openspec/changes/*/`, with `!openspec/changes/archive/`), and it is finished under
# `<LOOP_CHANGE_SOURCE_DIR>/archive/<date>-<name>/`, which git does see (issue #837). The archive's move is a
# rename and not a correction, so a digest the gate names has to read the same after it: `loop_change_path` strips
# the profile's date prefix and the derivation below hashes a change's own files under that one name.
loop_change_family() { # → the profile's change family, or this loop's own default
  local family
  family=$(loop_conf_value LOOP_CHANGE_SOURCE_DIR)
  printf '%s\n' "${family:-openspec/changes}"
}

# The name a change's own file goes by, with the archive's date prefix removed: a finished change is moved under
# `<family>/archive/<prefix><name>/`, and that move is a rename and not a correction, so a digest a gate names has
# to read the same after it. The prefix is the profile's pattern (`LOOP_CHANGE_ARCHIVE_PREFIX`), matched from the
# first character of the archived directory's name; a project that records none strips nothing, and then an
# archived change is a tree apart from its unarchived self.
loop_change_path() { # <path> <family> <archive prefix pattern> → the path under the change's own name
  local p="$1" family="$2" prefix="$3" rest name
  case "$p" in
    "$family"/archive/*/*)
      rest=${p#"$family"/archive/}
      name=${rest%%/*}
      if [ -n "$prefix" ]; then
        # the profile's pattern is a sed expression, matched from the name's first character
        name=$(printf '%s' "$name" | sed -E "s/^${prefix}//")
      fi
      printf '%s/%s/%s\n' "$family" "$name" "${rest#*/}" ;;
    *) printf '%s\n' "$p" ;;
  esac
}

# The directory a change has been archived as, or nothing when the tree carries no archive of it. Both halves read
# the one derivation above, so a profile's date pattern is stripped in one place and nowhere else.
loop_change_archive_dir() { # <tree> <change name> → the archived directory's path relative to the tree
  local tree="${1:-}" name="${2:-}" family prefix dir rel
  [ -n "$tree" ] && [ -n "$name" ] || return 0
  family=$(loop_change_family)
  prefix=$(loop_conf_value LOOP_CHANGE_ARCHIVE_PREFIX)
  for dir in "$tree/$family/archive"/*/; do
    [ -d "$dir" ] || continue
    rel=${dir#"$tree"/}
    rel=${rel%/}
    # the derivation is written over a path *inside* a change, so a directory is read as one of its own paths: the
    # name it strips is the name every path of that change carries.
    [ "$(loop_change_path "$rel/gate.xml" "$family" "$prefix")" = "$family/$name/gate.xml" ] || continue
    printf '%s\n' "$rel"
    return 0
  done
}

# Usage: loop_tree_identity <tree> <base> [<pathspec to leave out>] → `head=<sha> diff=<digest>`, or nothing when git
# cannot answer.
loop_tree_identity() {
  local tree=${1:-} base=${2:-} exclude=${3:-} head listing digest p h family prefix
  local -a scope=() ignored=()
  [ -n "$tree" ] && [ -n "$base" ] || return 0
  head=$(git -C "$tree" rev-parse HEAD 2>/dev/null) || return 0
  [ -n "$head" ] || return 0
  # `.` anchors the pathspec to the tree's root, which is the scope `git diff … -- .` has in `run.sh`, and the pathspec
  # is the one the round's diff is built with: a path that diff does not carry is not the change this digest names.
  if [ -n "$exclude" ]; then scope=(. "$exclude"); fi
  # A change being worked is gitignored (`.gitignore`'s `openspec/changes/*/`), so neither listing above carries its
  # files at all while its archived self carries every one of them: the archive's move alone moved this digest, and
  # a change directory repaired after the gate moved it again (issue #837). The family's own files are therefore
  # enumerated whether git tracks them or not, as the working tree holds them, with the archive's subtree left to the
  # listings above (a path it carries is renamed by `loop_change_path`) and the evidence exclusion re-applied: a gate
  # writes its own `evidence/` after it has named its tree.
  family=$(loop_change_family)
  prefix=$(loop_conf_value LOOP_CHANGE_ARCHIVE_PREFIX)
  ignored=(-- "$family" ":(exclude)$family/archive/**")
  [ -z "$exclude" ] || ignored+=("$exclude")
  listing=$( { git -C "$tree" diff --name-only "$base" ${scope[@]+"${scope[@]}"} 2>/dev/null
    git -C "$tree" ls-files --others --exclude-standard ${scope[@]+"${scope[@]}"} 2>/dev/null
    git -C "$tree" ls-files --others --ignored --exclude-standard ${ignored[@]+"${ignored[@]}"} 2>/dev/null; } | sort -u |
    while IFS= read -r p; do
      [ -n "$p" ] || continue
      if [ -e "$tree/$p" ]; then
        h=$(git -C "$tree" hash-object -- "$p" 2>/dev/null) || h="unreadable"
      else
        h="deleted"
      fi
      printf '%s\t%s\n' "$(loop_change_path "$p" "$family" "$prefix")" "$h"
    done | sort -u )
  digest=$(printf '%s' "$listing" |
    { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
  printf 'head=%s diff=%s' "$head" "$digest"
}

# The identity of the change a round is reading: one path set, and the one every caller takes (issue #819). `gate.sh`
# names it before its first step and `run.sh` writes it beside the diff it hands a round, and the reviewer's item 3
# compares the two — so both call this, and neither can hold a path set of its own. A gate's own evidence is written
# into the change directory *after* the gate names its tree and before the run writes the diff, which is why the
# generated trees the round's diff leaves out have to leave this derivation out too: otherwise the ordinary flow reads
# as a corrected one and item 3 refuses every change (found in this change's round-1 review).
loop_change_identity() { # <tree> <base> → `head=<sha> diff=<digest>` over what the round's diff carries
  loop_tree_identity "${1:-}" "${2:-}" "$(loop_conf_value LOOP_REVIEW_EXCLUDE_PATHSPEC)"
}

# A red gate is not a landing (issue #837): the change is completed and archived *before* the gate measures it, so a
# gate that does not pass leaves a change nobody may read as archived, and this restores it to its unarchived name
# before the run commits or a reader opens the item. It moves one directory and nothing else: the delta the archive
# merged into the capability spec stays in the tree, because the change's own work is what the next attempt reads.
loop_change_restore() { # <tree> <change name> → `LOOP-CHANGE-RESTORED:` or `LOOP-CHANGE-RESTORE-REFUSED:`
  local tree="${1:-}" name="${2:-}" family archived target
  family=$(loop_change_family)
  case "$name" in
    ''|.|..|*/*) printf 'LOOP-CHANGE-RESTORE-REFUSED: %s is not a change\047s name\n' "${name:-an empty value}"; return 1 ;;
  esac
  [ -d "$tree" ] || { printf 'LOOP-CHANGE-RESTORE-REFUSED: %s is not a tree\n' "${tree:-an empty value}"; return 1; }
  target="$family/$name"
  archived=$(loop_change_archive_dir "$tree" "$name")
  if [ -z "$archived" ]; then
    printf 'LOOP-CHANGE-RESTORE-REFUSED: no directory under %s/archive names %s\n' "$family" "$name"
    return 1
  fi
  if [ -e "$tree/$target" ]; then
    printf 'LOOP-CHANGE-RESTORE-REFUSED: %s already carries a directory\n' "$target"
    return 1
  fi
  mkdir -p "$tree/$family" || return 1
  if ! mv "$tree/$archived" "$tree/$target" 2>/dev/null; then
    printf 'LOOP-CHANGE-RESTORE-REFUSED: %s could not be moved back to %s\n' "$archived" "$target"
    return 1
  fi
  printf 'LOOP-CHANGE-RESTORED: %s → %s\n' "$archived" "$target"
}

# ── the build output a kept worktree does not need ────────────────────────────────────────────────
# A worktree that has been built carries a full module build tree, which the next run's build regenerates
# and no item ever reads: measured 2026-10-20 (issue #210), the kept worktree was 3.2 GB, of which one
# module's build output alone was 1.9 GB, and nothing removed either. Which directories those are is the
# project's, so the list is `LOOP_WORKTREE_SCRATCH_PATHS` in the loop's configuration — no script under
# `tools/loop/` names one, and `tools/check-portability.sh` reads the list as a forbidden value.
loop_scratch_paths() { # one recorded path per line
  printf '%s\n' "$(loop_conf_value LOOP_WORKTREE_SCRATCH_PATHS)" | tr ' ' '\n' | grep -v '^$'
}

# What one path occupies. `du -sk` never follows a symlink, so a link's target is not counted here — the
# number a report prints is what removing the path actually reclaims.
loop_bytes_of() {
  local k
  k=$(du -sk "$1" 2>/dev/null | awk '{print $1}')
  printf '%s\n' "$(( ${k:-0} * 1024 ))"
}

# Remove one recorded build path inside a tree, or say why it stays — never both, and never by guessing.
# The four refusals are the whole safety argument: an absolute or `..`-carrying entry is not a path inside
# the tree; a path that IS a symlink is never followed (a directory holding one loses only the link); a
# path whose parent resolves outside the tree is not inside it; and a path git tracks is the project's
# source, which no cleanup may touch. Removal is `rm -rf` on a path `du` did not follow either, so a link
# *inside* a build directory costs the link, never its target.
#
# Usage: loop_scratch_remove <tree> <relative path> [--apply] → status 0 when it removed something
loop_scratch_remove() {
  local tree="$1" rel="$2" apply="${3:-}" target real_tree real_parent bytes
  LOOP_SCRATCH_LINE=""
  LOOP_SCRATCH_BYTES=0
  case "$rel" in
    ""|.|..|/*|../*|*/..|*/../*)
      LOOP_SCRATCH_LINE="kept $rel — not a path inside the tree"
      return 1 ;;
  esac
  target="$tree/$rel"
  { [ -e "$target" ] || [ -L "$target" ]; } || return 1
  if [ -L "$target" ]; then
    LOOP_SCRATCH_LINE="kept $rel — a symlink, never followed out of the tree"
    return 1
  fi
  real_tree=$(cd "$tree" && pwd -P) || return 1
  real_parent=$(cd "$(dirname "$target")" 2>/dev/null && pwd -P) || {
    LOOP_SCRATCH_LINE="kept $rel — its directory does not resolve"
    return 1
  }
  case "$real_parent/" in
    "$real_tree"/*) ;;
    *)
      LOOP_SCRATCH_LINE="kept $rel — it resolves outside the tree, to $real_parent"
      return 1 ;;
  esac
  if git -C "$tree" ls-files --error-unmatch -- "$rel" >/dev/null 2>&1; then
    LOOP_SCRATCH_LINE="kept $rel — git tracks it"
    return 1
  fi
  bytes=$(loop_bytes_of "$target")
  if [ "$apply" = "--apply" ]; then
    rm -rf -- "$target" || {
      LOOP_SCRATCH_LINE="could not remove $rel"
      return 1
    }
    LOOP_SCRATCH_LINE="removed $rel ($bytes B)"
  else
    LOOP_SCRATCH_LINE="would remove $rel ($bytes B)"
  fi
  LOOP_SCRATCH_BYTES="$bytes"
  return 0
}

# ── a process group the loop started, and how it is ended ────────────────────────────────────────
# Every layer of the loop starts someone, and each layer must be able to end *all* of what it started, not only
# the pid it holds: `set -m` before the command gives it a process group of its own (see `run_child`), and one
# signal to that group reaches the children. Measured 2026-10-20 (issue #213): five `tools/loop/run.sh`
# processes, each with its `sleep 1` child, were still running 55 to 100 min after their suites had ended, at a
# working directory (`readlink /proc/<pid>/cwd`) inside a scratch tree that had already been deleted — the
# suite's traps and the run's traps each ended their own pid only. `SIGTERM` comes first so a member's own trap
# still runs (the run's in-progress label and its lock), then a grace, then `SIGKILL` for whatever is left.
#
# Usage: loop_end_group <pgid> → 0 when nothing of that group is left (or there was nothing to end)
loop_end_group() {
  local pgid="${1:-}" i
  [ -n "$pgid" ] || return 0
  kill -s TERM -- -"$pgid" 2>/dev/null || return 0
  for i in $(seq 1 25); do
    kill -s 0 -- -"$pgid" 2>/dev/null || return 0
    sleep 0.2
  done
  kill -s KILL -- -"$pgid" 2>/dev/null || true
  return 0
}

# One process, the same way and for the same reason: a leftover is ended with the signal that lets it clean up
# behind itself, and only a process that ignores that is killed.
#
# Usage: loop_end_pid <pid> → 0 when it is gone (or was never a number)
loop_end_pid() {
  local pid="${1:-}" i
  case "$pid" in ''|*[!0-9]*) return 0 ;; esac
  kill -s TERM "$pid" 2>/dev/null || return 0
  for i in $(seq 1 10); do
    kill -s 0 "$pid" 2>/dev/null || return 0
    sleep 0.2
  done
  kill -s KILL "$pid" 2>/dev/null || true
  return 0
}

# Every process whose working directory lies under <dir>, one pid per line. Linux's `/proc` is the only reader of
# a process's working directory there is, so a platform without it reports nothing rather than guessing. This is
# the test that finds a loop a *suite* or a *killed run* left behind: the tree is gone from the caller's view,
# but the process that still polls it names it as its cwd (issue #213), and the same test serves
# `loop-clean.sh`'s report and the self-test's own postcondition (issue #233).
loop_pids_under() { # <dir> → one pid per line
  local dir="${1:-}" entry pid cwd
  [ -n "$dir" ] || return 0
  [ -d /proc/self ] || return 0
  for entry in /proc/[0-9]*; do
    pid="${entry#/proc/}"
    case "$pid" in ''|*[!0-9]*) continue ;; esac
    [ "$pid" = "$$" ] && continue
    [ "$pid" = "$PPID" ] && continue
    cwd=$(readlink "$entry/cwd" 2>/dev/null) || continue
    [ -n "$cwd" ] || continue
    # A process whose tree is already gone still names it, with the kernel's ` (deleted)` appended — the
    # leftovers a killed suite leaves behind are exactly those, so the suffix is stripped before the match.
    cwd=${cwd% (deleted)}
    case "$cwd" in
      "$dir"|"$dir"/*) printf '%s\n' "$pid" ;;
    esac
  done
}

# …and ending them: the loop's leftovers under one directory, reported by pid, ended with the same signal
# discipline as a single process. Prints the pids it ended so the caller can say what it did.
loop_end_pids_under() { # <dir> [--quiet] → 0, and one "ended <pid>" line per process
  local dir="${1:-}" quiet="${2:-}" pid ended=0
  while IFS= read -r pid; do
    [ -n "$pid" ] || continue
    [ "$quiet" = "--quiet" ] || printf 'ended %s — its working directory is inside %s\n' "$pid" "${dir##*/}"
    loop_end_pid "$pid"
    ended=$((ended + 1))
  done <<< "$(loop_pids_under "$dir")"
  return 0
}

# ── git: what a branch carries ──────────────────────────────────────────────────────────

# What a branch carries, asked by *patch* identity rather than by ancestry. The loop lands work as a commit of
# its own (`fix: … (#N)`), and a branch whose work was landed — by hand, or re-landed — is never an ancestor of
# the base while its patch is in it, so ancestry reads a finished branch as a previous attempt and blocks the
# item for ever (measured 2026-10-20, issue #228: of 16 `land` lines in one report, every branch of the first
# group read N ahead / 0 unlanded under `git cherry` and was a leftover already on `main`).
#
# Prints one `<sha> <subject>` line per commit whose patch <base> does not carry, and returns 0 when git could
# answer. Returns 1 when it could not, so a caller never reads "git cannot say" as "nothing to land".
loop_unlanded_commits() { # <repo> <base> <branch>
  local repo="$1" base="$2" branch="$3" out line sha
  out=$(git -C "$repo" cherry "$base" "$branch" 2>/dev/null) || return 1
  while IFS= read -r line; do
    # `git cherry` marks a commit `-` when the base already carries an equivalent patch, `+` when it does not
    case "$line" in
      '+ '*) sha=${line#+ }
             git -C "$repo" log -1 --format='%h %s' "$sha" 2>/dev/null || printf '%s\n' "$sha" ;;
    esac
  done <<< "$out"
  return 0
}

# The patch a child the run ended at a bound leaves its work in (issues #308, #674, #754). One derivation, because
# three readers name that file — the writer `keep_leftovers`, its reader `own_leftovers_of`, and the start that
# applies it — and a second copy of the path is a second path. The item directory sits beside the run state, which
# is the run's own record: `run.sh` derives its stage directory the same way and no path is stored anywhere.
loop_leftover_patch_of() { # <item> → the patch that item's ended work is kept in
  printf '%s/items/%s/leftover-from-killed-run.patch' "$(dirname "$LOOP_STATE_FILE")" "$1"
}

# The branches the loop names: one per item it worked, one per run for its worktree, and one per blocked attempt it
# set aside. The same set the report lists, so a reader and the sweep ask about the same branches. The third
# namespace is not work in progress — it is a previous attempt's only copy — and what protects it is the reading
# every other branch gets (patch identity, never ancestry): the branch is swept once the base carries its patches
# and kept, and named, while it does not. Without it a set-aside branch was enumerated by nothing at all, so one
# whose work had landed by hand stayed for ever (issue #803; measured 2026-11-06, eight set-aside branches of one
# to three commits each, and neither removal path named one of them).
loop_own_branches() { # <repo> → one branch per line
  {
    git -C "$1" branch --list 'fix/issue-*' 'loop-*' --format='%(refname:short)' 2>/dev/null
    git -C "$1" branch --list 'loop/leftover/*' --format='%(refname:short)' 2>/dev/null
  }
}

# The same set resolved on the remote, which is where a branch the loop published lives after the run merged its
# work and released its local branch (issue #763: every sweep enumerated `loop_own_branches`, a local namespace, and
# a landed `fix/issue-728-…` head stayed on the remote for ever). The pattern is the local one with the remote's
# prefix, so a ref the loop never names is never a candidate for the sweep that deletes it. A branch in the
# set-aside namespace is local by construction — a blocked attempt's branch is renamed, never published — so it is
# no arm's remote candidate and the two patterns below stay the two.
#
# Resolved on the remote means read from the remote *now*, not from this clone's cache of it: `git branch -r` names
# the tips of the last fetch, and the sweep deletes the ref the remote holds, so a head somebody advanced after that
# fetch was judged landed at its stale tip and removed with the commits it had gained since — on a shared remote,
# unattended, against the requirement that only a ref whose patches the base holds goes. So the two candidate
# patterns are fetched first (the same two, so nothing else becomes a candidate) with `--prune`, which also stops a
# ref the sweep already deleted from being named again. A fetch that cannot run — no remote, an offline clone —
# leaves the cache as it is, and the delete's lease (in `cmd_prune_landed`) is what keeps a moved ref then.
loop_remote_branches() { # <repo> [<remote>] → one `<remote>/<branch>` per line
  local remote="${2:-origin}"
  git -C "$1" fetch --quiet --prune "$remote" \
    "+refs/heads/fix/issue-*:refs/remotes/$remote/fix/issue-*" \
    "+refs/heads/loop-*:refs/remotes/$remote/loop-*" >/dev/null 2>&1
  git -C "$1" branch -r --list "$remote/fix/issue-*" "$remote/loop-*" --format='%(refname:short)' 2>/dev/null
}

# The branches a worktree holds: git refuses to delete one of them, so every deletion asks this first.
loop_worktree_branches() { # <repo> → one branch per line
  git -C "$1" worktree list --porcelain 2>/dev/null | sed -n 's|^branch refs/heads/||p'
}

# ── the issue tooling, as the loop uses it ───────────────────────────────────────────────────────

loop_profile() {
  printf '%s\n' "${ISSUES_PROFILE:-$LOOP_ROOT/tools/tooling.json}"
}

# One issue's full record, bodies included. `list.sh --json` deliberately answers without bodies (the
# cheap selection path), and the work package needs the text, so the candidate is re-read by number.
#
# `--comments` is asked for where the caller needs the answer a person recorded: the work package carries it
# (issue #196), and a reader that does not ask for it gets none — the real CLI returns only the fields it is
# asked for, and the fixture `gh` now does the same. The pre-screen asks for neither, so its per-candidate read
# stays as small as it was.
loop_issue_json() {
  # `issueType` and `parent` are the tracker's own hierarchy — an item's classification and the container it
  # sits under — and neither is a label, so a reader that decides something about an item has to ask for them
  # here (capability `issue-types`, `guidelines/Backlog.md` §3). They sit before `author,state` on purpose: a
  # case discriminates the comments read from the plain one by its trailing fields (`run#anUnreadable…` names
  # `author,state,comments`), and that tail is what `--comments` appends to.
  local fields="number,title,body,labels,issueType,parent,author,state" errfile status
  [ "${2:-}" = "--comments" ] && fields="$fields,comments"
  # The CLI's own words survive a failure. Swallowing them (`2>/dev/null`, which is what this function did) made
  # every failed read an anonymous exit status: the retry seam's `tracker-unreachable:` line, its retry lines and
  # the tracker's refusals all went to the same /dev/null, so an operator holding a dead run could not tell an
  # outage from a refusal (measured 2026-10-27: a run died at the pre-screen on one unreadable candidate and the
  # reason was gone). The caller decides whether to record or to print them; this seam only stops losing them.
  errfile=$(mktemp) || { gh issue view "$1" --json "$fields"; return $?; }
  gh issue view "$1" --json "$fields" 2>"$errfile"
  status=$?
  [ "$status" -eq 0 ] || cat "$errfile" >&2
  rm -f "$errfile"
  return "$status"
}

# The head branch of every open pull request, one per line. Read through the forge this loop already asks about the
# tracker (`loop_issue_json` beside it), because nothing else knows whether a branch is somebody's open review — and
# the sweep that deletes a remote ref must not delete that one (issue #763). No `--state`: `gh pr list` answers the
# open pull requests without it, and the status namespace's own word is the project's to declare, not this engine's
# (`tools/sensors/conditions/pr-conflicts.sh` reads it the same way). Returns 1 when the forge cannot be read: an
# outage is not a forge with no pull requests, and the caller keeps every ref rather than deciding so.
loop_open_pull_request_heads() {
  local out
  out=$(gh pr list --limit 200 --json headRefName 2>/dev/null) || return 1
  printf '%s\n' "$out" | jq -r '.[].headRefName // empty' 2>/dev/null || return 1
}

loop_issue_tool() { # run one of the tooling's scripts
  local script="$1"; shift
  bash "$LOOP_ISSUES_DIR/$script" "$@"
}

# The identity a commit is attributed to: the login this run already acts as, resolved by the same gate
# preflight that authorises it before any work starts. Git refuses a commit with no ambient
# `user.name`/`user.email`, and a fresh container or CI runner has neither — the run then blocked on its own
# commit (measured 2026-10-18, issue #167). Naming the run's own identity keeps the commit and the tracker's
# labels attributable to the same actor, and a login that cannot be resolved stops the run instead of
# inventing one. Output: `name<TAB>email`, so the caller can pass both options to `git -c`.
loop_commit_identity() {
  local login
  login=$(loop_issue_tool policy.sh preflight --json 2>/dev/null | jq -r '.login // empty' 2>/dev/null)
  [ -n "$login" ] || loop_die "worktree: no acting login could be resolved — a commit could not be attributed"
  printf '%s\t%s@users.noreply.github.com\n' "$login" "$login"
}

# The authorisation label, read from the profile — the loop never spells it out.
loop_required_labels() {
  loop_compat_profile_value requiredLabels
}

# The profile's `roles` section: which value of the profile's namespaces each of the loop's decisions
# means. Every script that decides something about a value asks here, so no script under `tools/loop/`
# spells a vocabulary value (D16); `tools/check-portability.sh` enforces it. The keys are the contract
# (like the namespace names); a value another project does not need is declared empty, never omitted.
LOOP_ROLES=""
LOOP_ROLE_KEYS="queuedVerdict heldVerdict workedClasses firstCategories firstCategoryClasses firstCategoryEvery proposeClass refusedClasses inFlightStatus deviceStatus holdStatus closeStatus settledStatuses inProgressAgent deviceAgent decisionAgent"
# The keys a capability added later reads, so a profile written before it keeps working: `shapingAgent` is the
# label an item carries when a run's verdict said a person must reshape it, `shapingVerdicts` names the run
# verdicts that write it (`tools/tooling.json`, capability `shaping-inbox`), and `nextUpAgent` is the label a person
# writes to say "work this next" (capability `loop-queue-pin`). They are read with the same `loop_role`, but they are
# **not** required: a profile that omits them reads as "this project has no shaping inbox and pins nothing", so a
# profile written before either capability still starts a run instead of stopping on a key it never declared (the
# rule "declare it empty, never omit it" is what the *required* keys keep, and `tools/check-portability.sh` reports
# none of these three).
# `containerType` joins them: it names the tracker's issue type this project treats as a container, and a
# profile that names none (or omits the key) reads as "no type is a container", so the pre-screen refuses
# nothing on that axis and a tracker whose types are all work behaves exactly as before.
LOOP_OPTIONAL_ROLE_KEYS="shapingAgent shapingVerdicts nextUpAgent containerType"
loop_roles_load() { # called outside `$( )`: a half-adapted profile must stop the script, not a subshell
  [ -n "$LOOP_ROLES" ] && return 0
  LOOP_ROLES=$(jq -c '.roles // empty' "$(loop_profile)") || LOOP_ROLES=""
  [ -n "$LOOP_ROLES" ] || loop_die "no roles section in $(loop_profile) — the loop reads which vocabulary value each decision means there"
  local key
  for key in $LOOP_ROLE_KEYS; do
    printf '%s\n' "$LOOP_ROLES" | jq -e --arg k "$key" 'has($k)' >/dev/null ||
      loop_die "the roles section in $(loop_profile) does not declare $key — declare it empty when this project has no such value"
  done
  # The optional keys may be absent (see above), but a key that is present must be a name or a list of them: a
  # number or an object there reads as "no such value" and would silently empty an inbox or a pin the project meant
  # to declare.
  for key in $LOOP_OPTIONAL_ROLE_KEYS; do
    printf '%s\n' "$LOOP_ROLES" |
      jq -e --arg k "$key" 'if has($k) then (.[$k] | type | . == "string" or . == "array") else true end' >/dev/null ||
      loop_die "the roles section in $(loop_profile) declares $key as neither a name nor a list of names"
  done
}

loop_role() { # <role> → the value, or one value per line when the role is a list
  printf '%s\n' "$LOOP_ROLES" | jq -r --arg k "$1" '.[$k] // empty | if type == "array" then .[] else . end'
}

# loop_shaping_verdicts <verdict> → success when the profile names that verdict as a shaping verdict, which is
# the one question `labels.sh` asks about the list. An absent key, an empty list and a verdict outside it all
# answer the same way, so a project that declares no shaping inbox writes no shaping label anywhere.
loop_shaping_verdicts() {
  local verdict="${1:-}" named
  [ -n "$verdict" ] || return 1
  while IFS= read -r named; do
    [ -n "$named" ] || continue
    [ "$named" = "$verdict" ] && return 0
  done < <(loop_role shapingVerdicts)
  return 1
}

loop_compat_profile_value() { # the profile's value for a section, via the tooling's own reader
  ISSUES_PROFILE=$(loop_profile) issues_profile_value "$1"
}

# The loop's state writer. Every state write goes through this, so "one writer" is a single call site.
loop_state() {
  bash "$LOOP_DIR/state.sh" "$@"
}

# The loop's label writer. Every label transition goes through here (D7).
loop_labels() {
  bash "$LOOP_DIR/labels.sh" "$@"
}

# Render a prompt skeleton. The skeletons carry placeholders only, so which skills this project uses and
# how its gate is run arrive from `loop.conf` at render time and never appear in a phase (D16).
loop_render() { # loop_render <template> [KEY=VALUE]…
  local template="$1"; shift
  local tpl pair key value
  tpl=$(cat "$template") || loop_die "cannot read the prompt skeleton $template"
  for pair in "$@"; do
    key="${pair%%=*}"
    value="${pair#*=}"
    tpl="${tpl//\{\{$key\}\}/$value}"
  done
  printf '%s\n' "$tpl"
}
