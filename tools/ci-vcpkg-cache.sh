#!/usr/bin/env bash
# ci-vcpkg-cache.sh — can this job reach the vcpkg binary cache, and what does a run do when it cannot?
#
# The cache's client is `nuget.exe` run under `mono` (`.github/workflows/build.yml`, the "Configure NuGet source for
# vcpkg binary cache" step). On `ubuntu-26.04` mono's BTLS handshake to `nuget.pkg.github.com` fails, so vcpkg —
# which treats a cache failure as non-fatal — restores nothing, compiles every port, and then pays a failing
# `nuget push` per port. Measured 2026-11-03: `Restored 0 package(s)` on 15 attempts, 73 ports built, 99 pushes
# failing from 06:10:35 to 07:12:31 — 62 minutes inside a step that costs 1–2 minutes when the transport works
# (run 41585715604 against 41509739025). Nothing in the run said so, because a non-fatal cache failure is not an
# event.
#
# This is the probe that makes it one event: it asks the feed the same question the cache asks, and its exit status
# decides whether the job relies on the cache at all (`VCPKG_BINARY_SOURCES` in the workflow). A dead transport then
# costs the source build plus one line naming the measured cost, and no package is pushed.
#
# The client and the source are seams (`--nuget`, `--source`), because a probe that needs GitHub Packages to run
# cannot be the case that proves the probe reports a dead feed.
#
# Reads text files and the network the probe is about; no build, no token printed. Exit 0 the feed answered, 1 it did
# not (or the client is not there), 2 on a usage error.
#
# Usage: ci-vcpkg-cache.sh [--probe] [--nuget <cmd>] [--source <url>] [--timeout <seconds>]
#   --probe    the only mode; accepted so a caller may read the intent
#   --nuget    the NuGet command to run, with its arguments appended (default: `mono <vcpkg fetch nuget>`)
#   --source   the NuGet source to probe (default: the project's fork-safe GitHub Packages URL below)
#   --timeout  how long the probe may take, in seconds (default 60)
set -uo pipefail

source_url="https://nuget.pkg.github.com/Framstag/index.json"
nuget_cmd=""
probe_timeout=60
while [ $# -gt 0 ]; do
  case "$1" in
    --probe) shift ;;
    --nuget) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'ci-vcpkg-cache: %s needs a value\n' "$1" >&2; exit 2; }; nuget_cmd="${2:-}"; shift 2 ;;
    --source) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'ci-vcpkg-cache: %s needs a value\n' "$1" >&2; exit 2; }; source_url="${2:-}"; shift 2 ;;
    --timeout) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'ci-vcpkg-cache: %s needs a value\n' "$1" >&2; exit 2; }; probe_timeout="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,26p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) printf 'ci-vcpkg-cache: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done
case "$probe_timeout" in ''|*[!0-9]*) printf 'ci-vcpkg-cache: --timeout takes a whole number of seconds\n' >&2; exit 2 ;; esac

# The measured cost of relying on a cache that is not there, quoted by every refusal so a reader does not have to
# find this file to know what the line is worth.
COST="a dead transport cost 62 minutes of failed pushes on ubuntu-26.04 (issue #761)"

tmp=$(mktemp -d "${TMPDIR:-/tmp}/ci-vcpkg-cache.XXXXXX") || { printf 'ci-vcpkg-cache: no scratch directory\n' >&2; exit 1; }
trap 'rm -rf "$tmp"' EXIT

# ── the client ────────────────────────────────────────────────────────────────────────────────────────────────
if [ -n "$nuget_cmd" ]; then
  read -r -a cmd <<< "$nuget_cmd"
  if [ "${#cmd[@]}" -eq 1 ] && [ ! -x "${cmd[0]}" ] && ! command -v "${cmd[0]}" >/dev/null 2>&1; then
    printf 'ci-vcpkg-cache: the NuGet client %s is not there — the cache cannot be reached at all, so the install must run without it: %s\n' "${cmd[0]}" "$COST" >&2
    exit 1
  fi
else
  vcpkg_root="${VCPKG_ROOT:-$PWD/vcpkg}"
  if [ ! -x "$vcpkg_root/vcpkg" ]; then
    printf 'ci-vcpkg-cache: no vcpkg at %s/vcpkg, so its own NuGet client cannot be asked — the install must run without the cache: %s\n' "$vcpkg_root" "$COST" >&2
    exit 1
  fi
  exe=$("$vcpkg_root/vcpkg" fetch nuget 2>/dev/null | tail -n 1) || exe=""
  if [ -z "$exe" ] || [ ! -f "$exe" ]; then
    printf 'ci-vcpkg-cache: vcpkg could not fetch its NuGet client — the install must run without the cache: %s\n' "$COST" >&2
    exit 1
  fi
  cmd=(mono "$exe")
fi

# ── the question the cache asks ──────────────────────────────────────────────────────────────────────────────
# `list` resolves the source's service index, which is the request that fails when the transport is broken; it
# needs no package name and no token beyond the source's configured credential. Its arguments are the ones the
# client has: `-Take` belongs to `search`, not to `list`, and the first version of this probe passed it and read
# the client's "Unknown option" crash as a dead feed — so the install ran with the cache switched off on a runner
# where the cache worked (issue #761's own run, measured 2026-11-03).
args=(list -Source "$source_url" -NonInteractive)
config="${HOME:-/nonexistent}/.config/NuGet/NuGet.Config"
[ -f "$config" ] && args+=(-ConfigFile "$config")

started=$(date +%s)
timeout "$probe_timeout" "${cmd[@]}" "${args[@]}" >"$tmp/out" 2>&1
rc=$?
if [ "$rc" -eq 0 ]; then
  elapsed=$(( $(date +%s) - started ))
  printf 'ci-vcpkg-cache: the feed %s answered in %ss — VCPKG_BINARY_SOURCES=clear;nuget,%s,readwrite\n' \
    "$source_url" "$elapsed" "$source_url"
  exit 0
fi

# The client's message, one line. `-Take`-style argument errors and transport failures both end in a stack frame,
# so the last line is not the diagnostic: the first line that names a cause is, and a client that rejected its
# arguments is reported as exactly that rather than as a dead feed.
diag=$(grep -viE '^[[:space:]]*$' "$tmp/out" 2>/dev/null | grep -iE 'unknown option|unrecognized|usage:|Unable to load|exception|error|could not be established' | head -n 1 | cut -c1-200)
[ -n "$diag" ] || diag=$(grep -v '^[[:space:]]*$' "$tmp/out" 2>/dev/null | head -n 1 | cut -c1-200)
[ -n "$diag" ] || diag="the client exited $rc with no message"
printf '::warning::ci-vcpkg-cache: the feed %s did not answer (%s): %s — the install runs with VCPKG_BINARY_SOURCES=clear, so every port builds from source and nothing is pushed; %s\n' \
  "$source_url" "$rc" "$diag" "$COST" >&2
exit 1
