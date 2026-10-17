#!/usr/bin/env bash
# Shared helpers for the issue tooling.
#
# Two rules are implemented here for every script, because a rule re-implemented per script is a rule that
# drifts (design D8, D11):
#
#   * the exit codes are the contract — 0 acted or a deliberate no-op, 2 usage/malformed input,
#     3 policy refusal, 4 a duplicate or an already-present marker (reported, not an error);
#   * the project is discovered, never told: the root from the working tree, the repository from the
#     authenticated `gh` (with a git fallback), the profile from `<root>/tools/tooling.json`. No script
#     takes a project name, a repository or a path as a required argument, and `--profile <path>` is the
#     only override. The environment never decides the project.
#
# `issues_resolve` is called once, at the top of a script, and *not* inside a command substitution: a
# failure inside `$( )` would exit a subshell and be swallowed, leaving an empty root that silently
# resolves to `/`. Discovery therefore writes globals and dies in the caller's own shell.
#
# Usage: `source lib.sh`, or run `lib.sh discover` to see what the discovery rule resolves.
set -uo pipefail

ISSUES_EXIT_OK=0
ISSUES_EXIT_USAGE=2
ISSUES_EXIT_POLICY=3
ISSUES_EXIT_DUPLICATE=4

# The engine's own directory — where the Java core lives. The project facts are resolved from the working
# tree instead, so the engine can run inside somebody else's project.
ISSUES_LIB_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ISSUES_CORE_DIR="$ISSUES_LIB_DIR/core"

ISSUES_ROOT=""
ISSUES_REPO=""
ISSUES_PROFILE=""

issues_die() {
  printf '%s\n' "$*" >&2
  exit "$ISSUES_EXIT_USAGE"
}

issues_note() {
  printf '%s\n' "$*" >&2
}

# The repository: asked of `gh`, with `git remote get-url origin` as the fallback, so the tooling works
# with no network and no token for everything that does not need GitHub.
issues_repo_of() {
  local repo url
  repo=$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null) || repo=""
  if [ -z "$repo" ]; then
    url=$(git remote get-url origin 2>/dev/null) || url=""
    case "$url" in
      git@*:*) repo=${url#*:} ;;
      *://*/*) repo=${url#*://*/} ;;
      *) repo="" ;;
    esac
    repo=${repo%.git}
  fi
  printf '%s\n' "$repo"
}

# Resolve the project once: root from the working tree, repo from `gh`, profile from the root.
issues_resolve() {
  local override="${1:-}"
  local root
  root=$(git rev-parse --show-toplevel 2>/dev/null) || {
    printf 'issues: not inside a git working tree — run this from the project you mean to act on\n' >&2
    exit "$ISSUES_EXIT_USAGE"
  }
  ISSUES_ROOT="$root"
  ISSUES_REPO=$(issues_repo_of)
  if [ -n "$override" ]; then
    [ -f "$override" ] || issues_die "issues: --profile $override does not exist"
    ISSUES_PROFILE="$override"
  else
    ISSUES_PROFILE="$ISSUES_ROOT/tools/tooling.json"
  fi
}

# The identity the tooling runs as — the attributable term of the gate.
issues_login() {
  gh api user --jq .login 2>/dev/null || true
}

# Run one of the core's single-file Java classes. The core is Java run as source: no Gradle source set, no
# build system, no dependency (design D1).
issues_core() {
  local class="$1"; shift
  java "$ISSUES_CORE_DIR/$class.java" "$@"
}

# Read one section of the resolved profile.
issues_profile_value() {
  local section="$1"
  issues_core Profile "$ISSUES_PROFILE" "$section"
}

# Print a refusal with its reason code and stop with the policy exit code.
issues_refuse() {
  local reason="$1" detail="${2:-}"
  if [ -n "$detail" ]; then
    printf '%s: %s\n' "$reason" "$detail"
  else
    printf '%s\n' "$reason"
  fi
  exit "$ISSUES_EXIT_POLICY"
}

# Check a label set against the profile: a reserved label, then the closed vocabulary. Callers pass the
# labels a write is about to set, whatever flag or field produced them.
issues_check_labels() {
  local profile="$1"; shift
  [ "$#" -gt 0 ] || return 0
  local out status
  out=$(issues_core Policy labels --profile "$profile" "$@" 2>&1)
  status=$?
  if [ "$status" -eq 3 ]; then
    printf '%s: %s\n' "${out%%:*}" "${out#*: }"
    exit "$ISSUES_EXIT_POLICY"
  fi
  [ "$status" -eq 0 ] || issues_die "issues: policy check failed: $out"
}

# The gate's preflight: refuse to run at all when the identity is not on the allowlist.
issues_preflight() {
  local profile="$1" login="$2"
  local out status
  out=$(issues_core Policy preflight --profile "$profile" --login "$login" 2>&1)
  status=$?
  if [ "$status" -eq 3 ]; then
    printf '%s: %s\n' "${out%%:*}" "${out#*: }"
    exit "$ISSUES_EXIT_POLICY"
  fi
  [ "$status" -eq 0 ] || issues_die "issues: preflight failed: $out"
}

# The live issue list, as the dedup and selection queries read it.
issues_issue_list() {
  gh issue list --state all --limit 400 --json number,title,labels,body,author
}

# --------------------------------------------------------------------------- discovery, for the self-test

issues_discover() {
  issues_resolve "${1:-}"
  printf 'root=%s\n' "$ISSUES_ROOT"
  printf 'repo=%s\n' "$ISSUES_REPO"
  printf 'profile=%s\n' "$ISSUES_PROFILE"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  case "${1:-discover}" in
    discover) shift || true; issues_discover "${1:-}" ;;
    *) issues_die "usage: lib.sh [discover] [--profile <path>]" ;;
  esac
fi
