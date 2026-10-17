#!/usr/bin/env bash
# pr-conflicts.sh — the condition "a change of the owner's own is in conflict with its base".
#
# `gh pr list` is read twice: `mergeable` is computed lazily and is `UNKNOWN` on the first read after a
# push, so one read would report a conflict that a second read would deny — and the second read is the
# one that decides (design D10). A conflict in somebody else's pull request is *reported* and not filed:
# the tracker is this project's, and a contributor's conflict is not a backlog item (design D4).
#
# Inputs (set by `run.sh`): SENSORS_PROFILE (the allowlist), SENSORS_CONDITIONS.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

if ! prs=$(gh pr list --state open --limit 100 --json number,mergeable,author 2>/dev/null); then
  sensors_emit_error '{"pr":"unknown"}' "no-pull-request-list"
  exit 0
fi
# The re-read: the field is computed by GitHub on the first request and answered on the next one.
prs=$(gh pr list --state open --limit 100 --json number,mergeable,author 2>/dev/null) || prs="$prs"
if ! jq -e . >/dev/null 2>&1 <<< "$prs"; then
  sensors_emit_error '{"pr":"unknown"}' "unreadable-pull-request-list"
  exit 0
fi

allowed=$(jq -r '.allowedAuthors[]? // empty' "$SENSORS_PROFILE" 2>/dev/null || true)

conflicting=""
while IFS=$'\t' read -r number mergeable author; do
  [ -n "$number" ] || continue
  [ "$mergeable" = "CONFLICTING" ] || continue
  if [ -n "$allowed" ] && printf '%s\n' "$allowed" | grep -qxF -- "$author"; then
    conflicting="$conflicting $number"
    fields=$(jq -nc --arg pr "$number" '{pr: $pr}')
    fingerprint=$(sensors_fingerprint "$fields" "pr")
    sensors_emit "$fingerprint" "conflicting" "violation" "$fields" "" \
      "a change of the owner's is in conflict" \
      "pull request #${number} ($author) is CONFLICTING with its base"
  else
    # Reported, never filed: an outside contributor's conflict is not this project's backlog item.
    printf 'pr-conflicts: reported, not filed — pull request #%s by %s is CONFLICTING\n' "$number" "${author:-unknown}" >&2
  fi
done <<< "$(jq -r '.[]? | [.number, (.mergeable // "UNKNOWN"), (.author.login // "")] | @tsv' <<< "$prs")"

while IFS= read -r number; do
  [ -n "$number" ] || continue
  case " $conflicting " in *" $number "*) continue ;; esac
  fields=$(jq -nc --arg pr "$number" '{pr: $pr}')
  fingerprint=$(sensors_fingerprint "$fields" "pr")
  sensors_emit "$fingerprint" "mergeable" clear "$fields" "" \
    "the change is no longer in conflict" "pull request #${number} no longer reports a conflict"
done < <(jq -r '.[]? | select(.state == "OPEN") | .condition.pr // empty' <<< "${SENSORS_CONDITIONS:-[]}")
