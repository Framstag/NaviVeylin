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

# Run one of the core's classes. The core is Java with no Gradle source set and no build system (design D1),
# but it is **compiled once and then run from the class path**, never handed to the single-file source
# launcher: `java core/Foo.java` resolves Foo's siblings only on some JDKs — JDK 27 finds them, JDK 21 does
# not, so every script that ran fine on the author's machine failed in CI with `cannot find symbol Json`
# (measured 2026-10-17: 13 of 33 self-test cases red under JDK 21, 0 under JDK 27). `javac` is the JDK the
# caller already has, and the build is cached by the sources' own digest, so it costs one compile.
# The cache directory's name carries no project value — the engine may not name one
# (`lib#noProjectValueInTheEngine` caught the first attempt); the sources' digest is what keys it.
ISSUES_CORE_CACHE="${ISSUES_CORE_CACHE:-${TMPDIR:-/tmp}/issues-core}"

issues_core_classes() { # print the class directory for the current sources, compiling at most once
  # a caller that has already compiled the core (the self-test does) hands its directory in, so nothing is
  # compiled twice and the tooling runs exactly the classes the caller verified
  if [ -n "${ISSUES_CORE_CLASSES:-}" ]; then
    printf '%s\n' "$ISSUES_CORE_CLASSES"
    return 0
  fi
  local digest dir staging
  if command -v sha256sum >/dev/null 2>&1; then
    digest=$(cat "$ISSUES_CORE_DIR"/*.java | sha256sum | cut -c1-16)
  else
    digest=$(cat "$ISSUES_CORE_DIR"/*.java | cksum | tr -d ' ' | cut -c1-16)
  fi
  # `--release 17` makes the class files loadable by any JDK that can run the selftest, and the key carries
  # the release, so a cache built by a newer JDK can never be handed to an older one (measured 2026-10-17:
  # a JDK-27 build left class-file v71 in a shared cache and every JDK-21 run died on LinkageError)
  dir="$ISSUES_CORE_CACHE/17-$digest"
  if [ ! -d "$dir" ]; then
    staging="$ISSUES_CORE_CACHE/.staging.$$"
    mkdir -p "$staging" || return 1
    javac --release 17 -d "$staging" "$ISSUES_CORE_DIR"/*.java >&2 || { rm -rf "$staging"; return 1; }
    # a parallel invocation may have won the race: then its directory is the one to use
    mv "$staging" "$dir" 2>/dev/null || rm -rf "$staging"
  fi
  [ -d "$dir" ] || return 1
  printf '%s\n' "$dir"
}

issues_core() {
  local class="$1"; shift
  local classes
  classes=$(issues_core_classes) ||
    issues_die "issues: the Java core does not compile — JDK 17 or newer (javac) is required to run these scripts"
  java -cp "$classes" "$class" "$@"
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
