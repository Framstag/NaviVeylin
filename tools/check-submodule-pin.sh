#!/usr/bin/env bash
# check-submodule-pin.sh — the gitlink the parent records must be a commit the submodule's remotes carry.
#
# A pin only one machine's clone holds is a tree nobody else can check out: CI clones the module from its URL and
# a loop worktree clones it too, and both end in `upload-pack: not our ref`. The worktree then reports the module
# as dirt under no item, and a run stops before it works anything (measured 2026-10-28: a pin two unpublished
# commits ahead of `origin/naviveylin-local` cost a run with 600 minutes of budget its whole life at 69 seconds).
# The rule the bump skill states — push the branch *before* the parent bump — is checked here rather than
# remembered, offline: the answer is read from the clone's own remote-tracking refs, so a stale clone is a false
# alarm the third line of the refusal names, and nothing waits on a network.
#
# Usage: check-submodule-pin.sh [--root <dir>] [--path <submodule path>] [--index] [--quiet]
#        --root defaults to the current directory, --path to the first submodule `.gitmodules` declares.
#        --index reads the gitlink the index holds instead of the one HEAD records, which is what a bump script
#        needs between `git add` and `git commit`.
# Exit:  0 the recorded pin is contained in a remote-tracking ref; 1 it is on none (the refusal names the pin and
#        the push that publishes it); 2 the question cannot be asked — no `.gitmodules`, no gitlink at that path,
#        the module not checked out, or a usage error.
set -uo pipefail

usage() { sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

root="."; path=""; quiet=0; index=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] || { printf 'check-submodule-pin: --root needs a value\n' >&2; exit 2; }; root="$2"; shift 2 ;;
    --path) [ $# -ge 2 ] || { printf 'check-submodule-pin: --path needs a value\n' >&2; exit 2; }; path="$2"; shift 2 ;;
    --index) index=1; shift ;;
    --quiet) quiet=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'check-submodule-pin: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done

[ -d "$root" ] || { printf 'check-submodule-pin: %s is not a directory\n' "$root" >&2; exit 2; }
root=$(cd "$root" && pwd) || exit 2
[ -f "$root/.gitmodules" ] || { printf 'check-submodule-pin: %s declares no submodule (.gitmodules is absent)\n' "$root" >&2; exit 2; }
if [ -z "$path" ]; then
  path=$(git -C "$root" config -f .gitmodules --get-regexp '\.path$' 2>/dev/null | head -1 | cut -d' ' -f2-)
fi
[ -n "$path" ] || { printf 'check-submodule-pin: %s/.gitmodules names no submodule path\n' "$root" >&2; exit 2; }

pin=""
if [ "$index" -eq 1 ]; then
  gitlink=$(git -C "$root" ls-files -s -- "$path" 2>/dev/null | head -1)
  case "$gitlink" in 160000\ *) pin=$(printf '%s\n' "$gitlink" | awk '{print $2}') ;; esac
else
  gitlink=$(git -C "$root" ls-tree HEAD -- "$path" 2>/dev/null | head -1)
  case "$gitlink" in 160000\ commit\ *) pin=$(printf '%s\n' "$gitlink" | awk '{print $3}') ;; esac
fi
[ -n "$pin" ] || { printf 'check-submodule-pin: %s is no gitlink in %s of %s\n' "$path" \
  "$([ "$index" -eq 1 ] && printf 'the index' || printf 'HEAD')" "$root" >&2; exit 2; }
[ -e "$root/$path/.git" ] ||
  { printf 'check-submodule-pin: the module at %s is not checked out — run: git submodule update --init %s\n' "$path" "$path" >&2; exit 2; }

on_remote=$(git -C "$root/$path" for-each-ref --contains "$pin" --format='%(refname)' refs/remotes/ 2>/dev/null | head -3)
if [ -n "$on_remote" ]; then
  [ "$quiet" -eq 1 ] || printf 'check-submodule-pin: %s is on %s\n' "${pin:0:12}" "$(printf '%s' "$on_remote" | head -1)"
  exit 0
fi

branch=$(git -C "$root/$path" rev-parse --abbrev-ref HEAD 2>/dev/null); branch=${branch:-HEAD}
printf 'check-submodule-pin: %s records %s at %s, which is on no remote-tracking ref — every clone that is not this one fails to check it out\n' \
  "$root" "$path" "${pin:0:12}" >&2
printf '  git -C %s push origin %s\n' "$path" "$branch" >&2
printf '  (a stale clone answers the same: git -C %s fetch origin, then run this check again)\n' "$path" >&2
exit 1
