#!/usr/bin/env bash
# guard.sh — the runtime guards, run by the pipeline *and* by the local path, before any sensor.
#
# A YAML trigger does not pin the ref: `workflow_dispatch` can be dispatched against a branch, so a run
# against a branch's code could hold the write token. Each guard is therefore a check with its own
# message and its own exit code rather than a line of YAML to be trusted (design D3):
#
#   4  on the project's main branch?          (locally the current branch, in CI `SENSORS_REF`)
#   5  HEAD == the remote main branch's commit?   (and the message names both commits)
#   6  a clean working tree?                  (a run must not write beside uncommitted work)
#   7  an ambient identity on the allowlist?  (asked of the issue tooling, never re-read here)
#   9  a registry whose every row is complete and whose command exists
#
# Usage: guard.sh [--ref <ref>] [--remote <name>] [--profile <file>] [--registry <file>] [--json]
# Exit:  0 every guard passed; otherwise the code of the guard that refused.
#
# The remote it compares against is a *checkout* fact, not a project value: `origin` is what a clone and a CI
# checkout both produce, and a checkout that names its remote differently — or a local run whose only remote
# is somebody's fork — overrides it with `--remote <name>` or `SENSORS_REMOTE`. The refusal names the remotes
# that do have a main ref, so the value to pass is in the message rather than in a doc.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/lib.sh"

profile_arg=""
registry_arg=""
ref=""
remote=""
json=0
while [ $# -gt 0 ]; do
  case "$1" in
    --ref) ref="${2:-}"; shift 2 ;;
    --remote) remote="${2:-}"; shift 2 ;;
    --profile) profile_arg="${2:-}"; shift 2 ;;
    --registry) registry_arg="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,23p' "$0"; exit 0 ;;
    *) sensors_die "guard: unknown argument $1" ;;
  esac
done
ref="${ref:-${SENSORS_REF:-}}"
remote="${remote:-${SENSORS_REMOTE:-origin}}"

sensors_resolve --profile "$profile_arg" --registry "$registry_arg"

passed=""
line() { # <guard> <detail>
  passed="$passed $1"
  if [ "$json" -eq 0 ]; then
    printf 'guard: %s %s\n' "$1" "$2"
  fi
}

report() { # <guard> <exit> <reason> <detail>
  if [ "$json" -eq 1 ]; then
    jq -nc --arg guard "$1" --arg reason "$3" --arg detail "$4" --argjson code "$2" \
      '{guard: $guard, decision: "refuse", reason: $reason, detail: $detail, code: $code}'
  fi
  sensors_refuse "$2" "$3" "$4"
}

# The project's main branch: the branch the remote's HEAD points at, else the remote's own conventional
# name, else `main`. It is read from the remote's refs, never from the branch that happens to be checked out.
main_branch() { # <remote>
  local remote="$1" name=""
  name=$(git symbolic-ref --short -q "refs/remotes/$remote/HEAD" 2>/dev/null) || name=""
  case "$name" in "$remote"/*) name="${name#"$remote"/}" ;; esac
  if [ -z "$name" ]; then
    if git rev-parse --verify --quiet "refs/remotes/$remote/main" >/dev/null 2>&1; then
      name="main"
    elif git rev-parse --verify --quiet "refs/remotes/$remote/master" >/dev/null 2>&1; then
      name="master"
    else
      name="main"
    fi
  fi
  printf '%s\n' "$name"
}

main=$(main_branch "$remote")

# 1. on the main branch — locally the checked-out one, in CI the ref the dispatch selected.
branch=""
if [ -n "$ref" ]; then
  branch="${ref#refs/heads/}"
  if [ "$ref" != "refs/heads/$main" ]; then
    report on-main "$SENSORS_OFF_MAIN" "off-main" \
      "the run was dispatched against $ref, not refs/heads/$main"
  fi
else
  branch=$(git symbolic-ref --short -q HEAD 2>/dev/null) || branch=""
  if [ "$branch" != "$main" ]; then
    report on-main "$SENSORS_OFF_MAIN" "off-main" \
      "the run is on '${branch:-a detached HEAD}' but the project's main branch is '$main'"
  fi
fi
line on-main "($branch)"

# 2. HEAD is the remote main branch's commit — a run whose tree is not what is published would file
#    against a commit nobody else has.
head_sha=$(git rev-parse HEAD 2>/dev/null) || head_sha=""
remote_sha=$(git rev-parse --verify --quiet "refs/remotes/$remote/$main" 2>/dev/null) || remote_sha=""
if [ -z "$remote_sha" ]; then
  have_remote_main=$(git for-each-ref --format='%(refname)' "refs/remotes/*/$main" 2>/dev/null |
    sed -e 's|^refs/remotes/||' -e "s|/$main$||" | sort -u | tr '\n' ' ')
  report head-is-remote-main "$SENSORS_HEAD_NOT_REMOTE" "no-remote-main" \
    "there is no refs/remotes/$remote/$main to compare with HEAD $head_sha; refs exist for: ${have_remote_main:-none} — pass --remote <name> or set SENSORS_REMOTE"
fi
if [ "$head_sha" != "$remote_sha" ]; then
  report head-is-remote-main "$SENSORS_HEAD_NOT_REMOTE" "head-is-not-origin" \
    "HEAD is $head_sha but $remote/$main is $remote_sha"
fi
line head-is-remote-main "$remote/$main ${head_sha:0:12}"

# 3. a clean working tree — a run that scribbles beside uncommitted work would be indistinguishable from
#    the work itself, and the sensors are not a second writer.
dirty=$(git status --porcelain 2>/dev/null) || dirty=""
if [ -n "$dirty" ]; then
  report clean-tree "$SENSORS_DIRTY" "dirty-tree" \
    "$(printf '%s' "$dirty" | tr '\n' ';')"
fi
line clean-tree ""

# 4. the identity — asked of the issue tooling's own gate, so the allowlist has one reader.
if ! out=$(bash "$SENSORS_ISSUES_DIR/policy.sh" preflight --profile "$SENSORS_PROFILE" 2>&1); then
  report identity "$SENSORS_IDENTITY" "author-not-allowlisted" "$out"
fi
login=$(issues_login)
line identity "${login:-(unknown login)}"

# 5. the registry — every row complete, every command present, every label inside the declared taxonomy.
registry_problems=$(sensors_registry_problems)
if [ -n "$registry_problems" ]; then
  report registry "$SENSORS_REGISTRY_BAD" "registry-incomplete" \
    "$(printf '%s' "$registry_problems" | tr '\n' ';')"
fi
line registry "$(sensors_row_ids | wc -l) rows"

if [ "$json" -eq 1 ]; then
  jq -nc --arg guards "${passed# }" --argjson rows "$(sensors_row_ids | wc -l)" \
    '{decision: "allow", guards: ($guards | split(" ")), rows: $rows}'
fi
exit "$SENSORS_OK"
