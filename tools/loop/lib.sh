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

# The engine's own directory. Everything the loop owns relative to itself is derived from this, never
# from the current directory, so the engine can run inside another project (D16).
LOOP_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# The issue tooling is the loop's dependency, shipped beside it. Its `gh`-facing helpers are reused
# rather than re-implemented, and the project it acts on is still resolved from the working tree.
LOOP_ISSUES_DIR="$LOOP_DIR/../issues"
# shellcheck source=tools/issues/lib.sh
source "$LOOP_ISSUES_DIR/lib.sh"

LOOP_ROOT=""
# `LOOP_STATE_FILE` and `LOOP_WORKTREE` are the self-test's configuration seam (and the only way to run
# two loops in one tree deliberately). An inherited value is preserved, never cleared: assigning an
# empty string here would drop the environment's answer.
LOOP_STATE_FILE="${LOOP_STATE_FILE:-}"
LOOP_WORKTREE="${LOOP_WORKTREE:-}"

# Read-only: the label that authorises automated work. The loop *reads* it (a candidate must carry it
# to be selected) and has no code path that writes it — the write paths of the issue tooling refuse it
# as a reserved label, and `labels#neverWritesReservedLabel` asserts this file is its only mention.
LOOP_AUTHORISATION_LABEL="agent/eligible"

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

# Resolve the project once and load the configuration. Called at the top of every script, never inside
# a command substitution: a failure inside `$( )` would exit a subshell and be swallowed.
loop_resolve() {
  LOOP_ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || {
    loop_die "not inside a git working tree — run this from the project you mean to act on"
  }
  local conf="${LOOP_CONF:-$LOOP_DIR/loop.conf}"
  [ -f "$conf" ] || loop_die "no configuration at $conf — the loop reads its defaults and its project keys from loop.conf"
  # shellcheck source=tools/loop/loop.conf
  source "$conf"
  LOOP_STATE_FILE="${LOOP_STATE_FILE:-$LOOP_ROOT/.pi/issue-loop/run.json}"
  LOOP_WORKTREE="${LOOP_WORKTREE:-$LOOP_ROOT/.pi/worktrees/loop}"
  # A relative override is relative to the project, not to whatever the caller's cwd happened to be.
  case "$LOOP_STATE_FILE" in /*) ;; *) LOOP_STATE_FILE="$LOOP_ROOT/$LOOP_STATE_FILE" ;; esac
  case "$LOOP_WORKTREE" in /*) ;; *) LOOP_WORKTREE="$LOOP_ROOT/$LOOP_WORKTREE" ;; esac
}

loop_conf_value() { # print one configuration key's value by name
  local key="$1"
  eval "printf '%s' \"\${$key:-}\""
}

# ── the issue tooling, as the loop uses it ───────────────────────────────────────────────────────

loop_profile() {
  printf '%s\n' "${ISSUES_PROFILE:-$LOOP_ROOT/tools/tooling.json}"
}

# One issue's full record, bodies included. `list.sh --json` deliberately answers without bodies (the
# cheap selection path), and the work package needs the text, so the candidate is re-read by number.
loop_issue_json() {
  gh issue view "$1" --json number,title,body,labels,author,state 2>/dev/null
}

loop_issue_tool() { # run one of the tooling's scripts
  local script="$1"; shift
  bash "$LOOP_ISSUES_DIR/$script" "$@"
}

# The authorisation label, read from the profile — the loop never spells it out.
loop_required_labels() {
  loop_compat_profile_value requiredLabels
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
