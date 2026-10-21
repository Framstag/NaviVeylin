#!/usr/bin/env bash
# Every workflow pin must be a major whose committed action metadata declares `runs.using: node24`.
#
# GitHub's runner has deprecated Node 20 (`actions/*` migrations to Node 24 ran through the fall of 2025) and
# forces a Node 20 action onto Node 24 with a warning on every step it runs. The runtime is a property of the
# pinned ref, not of the runner: `.github/workflows/build.yml` pinned `actions/upload-artifact@v5`, whose
# `action.yml` at that tag declares `runs.using: node20`, and the `Build debug APK (all ABIs)` run printed the
# deprecation notice on five steps (issue #240). Bumping the pin fixes today's notice; this check is what keeps
# the next pin from bringing it back, because nothing else in the tree reads a workflow's action versions — the
# workflow parses, the run is green, and the only signal is a warning nobody gates on.
#
# What it reads: every `uses:` line under `.github/workflows/*.yml`, and a recorded floor per action — the
# FIRST major whose committed `action.yml` declares `node24`, with the URL that was read. The action one major
# below that floor is the one that declares `node20`. The floor is recorded rather than fetched because a check
# that needs the network cannot be the gate for a change (it would fail on a runner with no egress and pass on a
# workstation with it); the URLs are what a reader re-reads when an action publishes its next major.
#
# Three refs carry no Node runtime of their own and are skipped: a local action (`./path`), a container action
# (`docker://…`) and an empty value. Everything else must be a released major (`@v<number>`): a branch or a
# commit SHA cannot be resolved offline, so it is reported instead of assumed to be current.
#
# Reads text files only: no build, no device, no network. Exit 0 when every pin is at or above its action's
# Node 24 floor, 1 when a pin is below it or cannot be resolved, 2 on a usage or layout error.
#
# Usage: check-action-runtimes.sh [--root <dir>]   # default root: the repository this script lives in
set -uo pipefail

root=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,27p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "check-action-runtimes: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflows="$root/.github/workflows"
if [ ! -d "$workflows" ]; then
  echo "check-action-runtimes: no workflow directory under $workflows" >&2
  exit 2
fi

# The recorded floor table: `<action> <first node24 major> <url the declaration was read from>`.
# Read 2026-10-22 (issue #240). Each floor's own tag declares `runs.using: node24`; the major below it declares
# `node20`. A row is added when a workflow pins a new action, so "an action nobody recorded" is a defect
# rather than a silent pass.
floors='
actions/cache 5 https://raw.githubusercontent.com/actions/cache/v5/action.yml
actions/checkout 5 https://raw.githubusercontent.com/actions/checkout/v5/action.yml
actions/setup-java 5 https://raw.githubusercontent.com/actions/setup-java/v5/action.yml
actions/upload-artifact 6 https://raw.githubusercontent.com/actions/upload-artifact/v6/action.yml
'

floor_of() { # floor_of <action> -> first node24 major, empty when unrecorded
  local action major url
  while read -r action major url; do
    [ -n "${action:-}" ] || continue
    [ "$action" = "$1" ] || continue
    printf '%s\n' "$major"
    return 0
  done <<< "$floors"
  return 1
}

evidence_of() { # evidence_of <action> -> the URL its floor was read from
  local action major url
  while read -r action major url; do
    [ -n "${action:-}" ] || continue
    [ "$action" = "$1" ] || continue
    printf '%s\n' "$url"
    return 0
  done <<< "$floors"
  return 1
}

fail=0
pins=0
files=0
for file in "$workflows"/*.yml "$workflows"/*.yaml; do
  [ -e "$file" ] || continue
  files=$((files + 1))
  lineno=0
  while IFS= read -r line || [ -n "$line" ]; do
    lineno=$((lineno + 1))
    # `- uses: …` (a step) and `uses: …` (the form a nested workflow may use) are the two shapes; a comment
    # that quotes one is prose, not a pin.
    case "$line" in
      *uses:*) ;;
      *) continue ;;
    esac
    case "${line#"${line%%[![:space:]]*}"}" in
      '#'*) continue ;;
    esac
    ref=${line#*uses:}
    ref=${ref%%#*}
    ref=$(printf '%s' "$ref" | tr -d '\r' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')
    ref=${ref#\'}; ref=${ref%\'}
    ref=${ref#\"}; ref=${ref%\"}
    [ -n "$ref" ] || continue
    case "$ref" in
      ./*|docker://*) continue ;;   # a local or container action: no Node runtime of its own
    esac
    pins=$((pins + 1))
    rel=${file#"$root"/}
    case "$ref" in
      *'${{'*)
        echo "::error file=$rel,line=$lineno::'uses: $ref' is an expression — the pin cannot be resolved offline, name a released major"
        fail=1
        continue ;;
    esac
    case "$ref" in
      *@v[0-9]*) ;;
      *@*)
        echo "::error file=$rel,line=$lineno::'uses: $ref' is not a released major — a branch or SHA cannot be resolved offline, pin '@v<major>'"
        fail=1
        continue ;;
      *)
        echo "::error file=$rel,line=$lineno::'uses: $ref' carries no '@v<major>' ref"
        fail=1
        continue ;;
    esac
    action=${ref%@*}
    major=${ref##*@v}
    case "$major" in
      ''|*[!0-9]*)
        echo "::error file=$rel,line=$lineno::'uses: $ref' does not pin a numeric major ('@vN')"
        fail=1
        continue ;;
    esac
    # `actions/cache/restore@v5` is a subpath of `actions/cache`: the runtime belongs to the action.
    case "$action" in
      [[:alnum:]]*/[[:alnum:]]*/*) action=$(printf '%s' "$action" | cut -d/ -f1,2) ;;
    esac
    if ! floor=$(floor_of "$action"); then
      echo "::error file=$rel,line=$lineno::'$action' has no recorded Node runtime — add its first Node 24 major to the floor table in tools/check-action-runtimes.sh, with the action.yml URL you read it from"
      fail=1
      continue
    fi
    if [ "$major" -lt "$floor" ]; then
      echo "::error file=$rel,line=$lineno::'uses: $ref' targets Node 20 — the first Node 24 major is v$floor ($(evidence_of "$action"))"
      fail=1
    fi
  done < "$file"
done

if [ "$files" -eq 0 ]; then
  echo "check-action-runtimes: no workflow file under $workflows" >&2
  exit 2
fi
if [ "$fail" -eq 0 ]; then
  echo "action runtimes: $pins action pins across $files workflow files target Node 24."
fi
exit "$fail"
