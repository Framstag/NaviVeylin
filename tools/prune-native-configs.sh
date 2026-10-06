#!/usr/bin/env bash
# Prune superseded native build configurations — change `speed-up-build-test-gate`, spec
# `build-test-gate` ("Superseded native configurations are pruned").
#
# AGP keeps one native configuration tree per configuration hash (`app/.cxx/<buildType>/<hash>/`,
# a full CMake/ninja build each). A CMake-args or NDK change adds a hash and leaves the previous
# tree behind: measured 2026-10-04, five trees totalling 7.6 GB. This keeps the newest N per build
# type and deletes the rest.
#
# Usage:
#   bash tools/prune-native-configs.sh [--dry-run] [--keep N] [--skip NAME] [root]
# Defaults: keep 2 (the current configuration and its predecessor), root `app/.cxx`. `--skip` keeps a
# whole build type — pass `--skip tools` for the small host-tool configurations, whose "current" entry
# is ambiguous and whose cost of deletion (a re-configure) exceeds what they free.
#
# Refuses to run while a Gradle build is active, because the tree of a configuration AGP still
# considers current must not disappear under a running build; `--force` skips that check (only for a
# tree that is not AGP state — a fixture in the self-test, or an operator who is certain). Self-test
# (device-free, fixture tree):
#   bash tools/prune-native-configs-selftest.sh
#
# `NAVIVEYLIN_PRUNE_ASSUME_BUSY=1` forces the busy branch — it exists so the refusal path can be
# tested without a running build, and is honoured only for that.
set -uo pipefail

dry_run=0
keep=2
root="app/.cxx"
skip=""
force=0
while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) dry_run=1 ;;
        --force) force=1 ;;
        --keep) shift; keep="${1:-2}" ;;
        --skip) shift; skip="$skip ${1:-}" ;;
        -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
        *) root="$1" ;;
    esac
    shift
done

[ -d "$root" ] || { echo "prune-native-configs: no native configuration root at '$root'"; exit 1; }

# Guard against a mis-scoped root: this script removes whole configuration-hash trees under
# <root>/<buildType>/<hash>/. Handed a build-type directory instead of the .cxx root, the layout
# shifts by one level and the script would delete *ABI* directories inside hash trees — measured
# 2026-10-04 on app/.cxx/Debug: 5 ABI dirs removed, 1.5 GB, inside superseded trees (recoverable,
# but wrong). Refuse unless a grandchild looks like a configuration hash rather than an ABI.
# Guard against a mis-scoped root: this script removes whole configuration-hash trees under
# <root>/<buildType>/<hash>/. Handed a build-type directory instead of the .cxx root, the layout
# shifts by one level and the script would delete *ABI* directories inside hash trees — measured
# 2026-10-04 on app/.cxx/Debug: 5 ABI dirs removed, 1.5 GB, inside superseded trees (recoverable, but
# wrong). A grandchild named like an ABI is that mistake's signature.
for abi in arm64-v8a armeabi-v7a x86_64; do
    if [ -n "$(find "$root" -mindepth 2 -maxdepth 2 -type d -name "$abi" -print -quit 2>/dev/null)" ]; then
        echo "prune-native-configs: '$root' looks like a build-type directory, not the native configuration"
        echo "  root: its grandchildren are ABI directories. Pass the root that holds the build types"
        echo "  (default: app/.cxx). This script removes whole <buildType>/<hash> trees, never directories"
        echo "  inside one."
        exit 1
    fi
done
if [ -z "$(find "$root" -mindepth 2 -maxdepth 2 -type d -print -quit 2>/dev/null)" ]; then
    echo "prune-native-configs: '$root' holds no <buildType>/<hash> configuration tree to prune"
    exit 1
fi

busy() {
    [ "${NAVIVEYLIN_PRUNE_ASSUME_BUSY:-0}" = "1" ] && return 0
    [ "$force" -eq 1 ] && return 1
    # The documented check in guidelines/Build.md §2 (`pgrep -f 'GradleWrapper[M]ain'`) cannot match the
    # wrapper — the main class lives inside the jar (TODO.md §125) — so match the jar on the command line.
    pgrep -f 'gradle-wrapper\.ja[r]' >/dev/null && return 0
    return 1
}

if busy; then
    echo "prune-native-configs: a Gradle build is active — refusing to touch '$root'."
    echo "  Re-run when the build has finished (the current configuration tree is AGP state)."
    exit 2
fi

kept=0
deleted=0
freed_kb=0
while IFS= read -r build_type_dir; do
    [ -n "$build_type_dir" ] || continue
    build_type="$(basename "$build_type_dir")"
    skipped=0
    for s in $skip; do
        [ "$s" = "$build_type" ] && skipped=1
    done
    if [ "$skipped" -eq 1 ]; then
        echo "  skip    $build_type_dir (--skip)"
        continue
    fi
    # Newest first: keep the head, delete the tail. `find -printf %T@` sorts by mtime.
    mapfile -t configs < <(find "$build_type_dir" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' \
        | sort -rn | awk '{ $1=""; sub(/^ /, ""); print }')
    index=0
    for cfg in "${configs[@]}"; do
        if [ "$index" -lt "$keep" ]; then
            echo "  keep    $cfg"
            kept=$((kept + 1))
        else
            size_kb=$(du -sk "$cfg" 2>/dev/null | awk '{print $1}')
            if [ "$dry_run" -eq 1 ]; then
                echo "  would delete $cfg (${size_kb} kB)"
            else
                rm -rf "$cfg"
                echo "  deleted $cfg (${size_kb} kB)"
            fi
            deleted=$((deleted + 1))
            freed_kb=$((freed_kb + size_kb))
        fi
        index=$((index + 1))
    done
done < <(find "$root" -mindepth 1 -maxdepth 1 -type d | sort)

echo "prune-native-configs: kept $kept, $([ "$dry_run" -eq 1 ] && echo "would delete" || echo "deleted") $deleted, $([ "$dry_run" -eq 1 ] && echo "would free" || echo "freed") $((freed_kb / 1024)) MB"
