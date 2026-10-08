---
name: openspec-open-changes
description: Lists all open (in-progress) OpenSpec changes with the surface they affect (phone / auto / core), their open tasks (ids + names) plus a per-change summary (done/total, how many open tasks are on-device verification) and a short test advice per open task. Uses only openspec CLI + bash (no python). Use when reviewing the OpenSpec backlog, planning next changes, or checking what still blocks archiving.
---

# OpenSpec Open Changes

Prints a markdown table of every open (in-progress) OpenSpec change with:

| Column | Content |
|--------|---------|
| **Change** | change name (the `openspec/changes/<name>` dir) + progress `done/total · N open (V on-device/verification, O other)` |
| **Relevant for** | which surface/module the change touches: `phone`, `auto` (Android Auto projection / Android Automotive OS), `core` (shared helpers, `:core`, native-JNI), or a `+` combination (`phone+auto`, `phone+core`, …); `?` when nothing could be derived |
| **Open tasks** | each open `- [ ]` task: `id` name _(section)_ — id/name from `tasks.md`, section from its `## N. …` header; one task per `<br>` row |
| **Test advice** | short advice on how to close each open task: `id` *kind*: one short clause — `kind` is `on-device`, `unit test`, `build`, `docs`, or `code`; the clause is the task's **`Verify:` instruction** when the task has one (the tail after the last `Verify:`), otherwise the task text. Spec citations (`(spec: …)`), trailing ` — <note>` suffixes, leading `Label:` prefixes and quoted spec fragments are stripped; one advice per `<br>` row, prefixed with the task id |

## Rules

- **Only openspec calls + bash**: `openspec list --json` provides the change inventory + task counters; `tasks.md` files are parsed with `sed`/`awk`/`grep`/`cut`. **No python** (project rule, `openspec/config.yaml`).
- Read-only: never runs `new change`, `status` writes, or any artifact edit.
- Only `status == "in-progress"` changes are listed (archived/done ones are excluded).

### Platform relevance (column 2)

Derived from the change's own artifacts (`*.md` in the change dir, plus the change name) — no hand-maintained map:

- Per-surface keyword sets: **auto** = `android auto|aaos|android automotive|automotive|head unit|car app|dhu|car surface|car channel|car hint|rail widget|navigation session|:auto|host template|surface lifecycle|…`; **phone** = `phone|foldable|tablet|mobile`; **core** = `:core|com.naviveylin.core|shared helper|jni|native|libosmscout|cmake|repository|persistence`.
- Bare `auto`, `car`, `surface`, `projection` are deliberately excluded — they also match auto-zoom, card, map surface and map projection, which say nothing about the Android Auto surface.
- Lines that name a platform as *out of scope* (`out of scope`, `not touched`, `unchanged`, `no AA behavior`, `excluded`, `no native`, …) are filtered out before counting, so a phone-only change does not become `phone+auto` just because it mentions the AA path it does not touch.
- A surface is reported when it has a real footprint (≥ 3 matching lines) and is not a marginal mention (≥ 25% of the strongest surface); a platform named in the change name gets a small boost. Falls back to the single strongest surface.

### Test advice (column 4)

- Kind priority: `on-device` (on-device/emulator/head unit/DHU/GPX/logcat/manual/regression check) → `unit test` → `docs` (openspec status, finalize, TODO.md, README, guidelines) → `build` (build/gradle/assemble/compile/ABI) → `code`.
- The clause is capped at `TASK_ADVICE_MAX` chars — it is a hint for the session, not a replacement for the task text in column 3.

## Usage

```bash
bash .pi/skills/openspec-open-changes/scripts/open-changes.sh
```

Environment overrides (all optional):

- `OPENSPEC_STORE=<id>` — append `--store <id>` to every openspec call (registered standalone store).
- `OPENSPEC_CHANGES_DIR=<dir>` — alternate changes directory (default `<repo>/openspec/changes`).
- `TASK_NAME_MAX=<n>` — truncate task names (default 90 chars).
- `TASK_ADVICE_MAX=<n>` — truncate the column-4 test advice (default 70 chars).

## Example output

```text
| Change | Relevant for | Open tasks | Test advice |
|--------|--------------|------------|-------------|
| **fix-follow-vehicle-jumps** _(19/20 done · 1 open (1 on-device/verification))_ | phone+core | `6.4` On-device GPX replay verification (phone): replay a route with constant speed, curves, and a stop; use the existing `follow` logcat diagnostics… _(Build, tests, on-device veri)_ | `6.4` *on-device*: replay a route with constant speed, curves, and a stop |
| **route-overview-fit** _(10/17 done · 7 open (2 on-device/verification), 5 other)_ | phone | `1.4` Disable follow mode on destination selection (spec R1 — "Route calculated from a favorite…"): add `followMode = false` … _(Core Implementation)_<br>`2.8` Test: fav-select flow — with follow mode on, `onFavoriteSelected` disables follow … _(Unit Tests)_<br>`3.2` On-device: calculate a 30–50 km route in the route panel — start marker, target marker … _(Build & On-Device Verification)_ | `1.4` *code*: Disable follow mode on destination selection<br>`2.8` *unit test*: fav-select flow<br>`3.2` *on-device*: calculate a 30–50 km route in the route panel |
| **auto-pan-during-navigation** _(14/16 done · 2 open (2 on-device/verification))_ | auto | `5.3` On-device check (AA emulator or head unit): free driving — PAN button visible, pan moves the map… _(Verification)_ | `5.3` *on-device*: free driving |
```

## Workflow hints

- Missing `tasks.md` → row shows `_(no tasks.md)_`; all checkboxes ticked → `_(all N tasks done)_`.
- If `openspec list` reports `root: null`, the project is not OpenSpec-initialized (or the store declaration is broken) — stop, do not create anything.
- Pass the results to `/openspec-apply-change <name>` or `/openspec-archive-change <name>` to act on a listed change.
