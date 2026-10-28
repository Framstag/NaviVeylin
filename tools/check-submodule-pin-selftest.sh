#!/usr/bin/env bash
# Self-test for check-submodule-pin.sh: a pin the module's remotes carry passes, a pin only this clone holds is
# refused with the push that publishes it, and a question that cannot be asked is exit 2 rather than a green
# answer. Runs against a throwaway tree (a bare origin, a module clone and a parent), so it needs no network, no
# build and no fixture in the repository.
#
# Usage: check-submodule-pin-selftest.sh
set -uo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
checker="$repo/tools/check-submodule-pin.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

fail() { printf 'check-submodule-pin-selftest: %s\n' "$1" >&2; exit 1; }
case_name=""
run_case() { case_name="$1"; printf '  %s\n' "$1"; }

origin="$tmp/origin"; work="$tmp/work"; parent="$tmp/parent"
git init -q --bare -b main "$origin" || fail "could not create the bare origin"
git init -q -b main "$work" || fail "could not create the module's source repository"
git -C "$work" config user.email selftest@example.invalid
git -C "$work" config user.name selftest
printf 'the module\n' > "$work/CMakeLists.txt"
git -C "$work" add -A && git -C "$work" commit -qm one || fail "could not commit in the module's source"
git -C "$work" remote add origin "$origin"
git -C "$work" push -q origin main || fail "could not publish the module's first commit"

mkdir -p "$parent"
git -C "$parent" init -q -b main || fail "could not create the parent repository"
git -C "$parent" config user.email selftest@example.invalid
git -C "$parent" config user.name selftest
printf 'the parent\n' > "$parent/README.md"
git -C "$parent" add -A && git -C "$parent" commit -qm init || fail "could not commit in the parent"
git -C "$parent" -c protocol.file.allow=always submodule add -q "$origin" app/src/main/cpp/libosmscout >/dev/null 2>&1 ||
  fail "could not add the module to the parent"
git -C "$parent" commit -qm 'the module' || fail "could not commit the parent's pin"
module="$parent/app/src/main/cpp/libosmscout"
git -C "$module" config user.email selftest@example.invalid
git -C "$module" config user.name selftest

status=0; out=""
check() { out=$(bash "$checker" --root "$parent" 2>&1); status=$?; }

run_case "a pin its remote carries passes"
check
[ "$status" -eq 0 ] || fail "a published pin was refused: $out"
case "$out" in *"is on refs/remotes/"*) ;; *) fail "the pass does not name the ref it read: $out" ;; esac

run_case "a pin only this clone holds is refused with the push"
printf 'only this clone\n' >> "$module/CMakeLists.txt"
git -C "$module" commit -qam two || fail "could not commit in the module clone"
git -C "$parent" commit -qam 'the unpublished pin' || fail "could not bump the parent"
check
[ "$status" -eq 1 ] || fail "an unpublished pin was not refused (exit $status): $out"
case "$out" in *"is on no remote-tracking ref"*) ;; *) fail "the refusal does not say what is wrong: $out" ;; esac
case "$out" in *"push origin"*) ;; *) fail "the refusal names no repair: $out" ;; esac
case "$out" in *"fetch origin"*) ;; *) fail "the refusal does not name the stale-clone answer: $out" ;; esac

run_case "--quiet says nothing on a pass"
git -C "$module" push -q origin HEAD:main || fail "could not publish the module's second commit"
status=0; out=$(bash "$checker" --root "$parent" --quiet 2>&1); status=$?
[ "$status" -eq 0 ] || fail "the published pin was refused: $out"
[ -z "$out" ] || fail "--quiet still printed: $out"

run_case "a staged bump is read from the index with --index"
printf 'staged only\n' >> "$module/CMakeLists.txt"
git -C "$module" commit -qam three || fail "could not commit the staged pin in the module"
git -C "$parent" add app/src/main/cpp/libosmscout || fail "could not stage the bump"
status=0; out=$(bash "$checker" --root "$parent" --index 2>&1); status=$?
[ "$status" -eq 1 ] || fail "a staged unpublished pin was not refused (exit $status): $out"
git -C "$module" push -q origin HEAD:main || fail "could not publish the staged pin"
status=0; out=$(bash "$checker" --root "$parent" --index --quiet 2>&1); status=$?
[ "$status" -eq 0 ] || fail "the published staged pin was refused: $out"

run_case "a module that is not checked out is exit 2"
mv "$module" "$tmp/module-aside"
check
[ "$status" -eq 2 ] || fail "an absent module answered $status instead of 2: $out"
case "$out" in *"not checked out"*) ;; *) fail "the refusal does not name the missing checkout: $out" ;; esac
mv "$tmp/module-aside" "$module"

run_case "a path that is no gitlink is exit 2"
git -C "$parent" rm -q --cached app/src/main/cpp/libosmscout
git -C "$parent" update-index --add --cacheinfo 100644,$(git -C "$parent" rev-parse "HEAD:README.md"),app/src/main/cpp/libosmscout
git -C "$parent" commit -qm 'a plain file where the module was' || fail "could not commit the plain file"
check
[ "$status" -eq 2 ] || fail "a plain file at the path answered $status instead of 2: $out"
case "$out" in *"no gitlink in HEAD"*) ;; *) fail "the refusal does not say the path is no gitlink: $out" ;; esac

run_case "a tree that declares no submodule is exit 2"
status=0; out=$(bash "$checker" --root "$tmp" 2>&1); status=$?
[ "$status" -eq 2 ] || fail "a tree with no .gitmodules answered $status instead of 2: $out"

run_case "an unknown argument is exit 2"
status=0; out=$(bash "$checker" --root "$parent" --nonsense 2>&1); status=$?
[ "$status" -eq 2 ] || fail "an unknown argument answered $status instead of 2: $out"

printf 'check-submodule-pin-selftest: all cases passed\n'
