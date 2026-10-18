# Issue #159 — closure reason and evidence

**Archived change:** `openspec/changes/archive/2026-10-18-fix-openspec-modified-delta-loss/`
(commit `fix: fix-openspec-modified-delta-loss (#159)`)

## Reason

The defect was real and is repaired. `openspec/changes/fix-navigation-engine-fault-isolation/` no longer
carries partial MODIFIED blocks: both now carry the requirement they modify whole — the description and every
scenario the shipped spec has — plus the change's own sentences and scenarios. The change validates, so
archiving it drops none of the five scenarios named on this issue.

**Root cause (one).** The two blocks were copied from the shipped requirements as those requirements stood on
2026-09-27, and the shipped specs gained the five scenarios afterwards — the two frame/tick scenarios with
`fix-car-render-loop-restart` (`car-host-fault-isolation`), and the atomic-update, injected-time-source and
native-lookup scenarios with the phone-side publication fixes (`navigation-engine`). The change's own validate
task ran at authoring time and the archive step re-validates only at archive time, so nothing re-validated the
block against the current shipped spec in between; the drift only surfaced when `openspec validate --changes`
ran over the whole set on 2026-10-17.

## Evidence

Red on HEAD (`evidence/red-on-head.log`, 2026-10-18, HEAD `b83e857`):

```
$ openspec validate fix-navigation-engine-fault-isolation --type change
✗ [ERROR] car-host-fault-isolation/spec.md: MODIFIED "No fault escapes into the host path" omits scenario(s)
  the current spec still has: "A fault in one frame keeps the frames coming", "A fault in a display tick
  keeps the follow pipeline running". The modified block has 10 scenarios; the current spec has 11.
✗ [ERROR] navigation-engine/spec.md: MODIFIED "Engine lifecycle and threading" omits scenario(s) the current
  spec still has: "A background writer cannot revert a concurrent publication", "The staleness tick reads the
  injected time source", "A native lookup result is published on the main thread".
exit=1
$ openspec validate --changes
Totals: 14 passed, 1 failed (15 items)
```

Green (`evidence/green.log`):

```
$ openspec validate fix-navigation-engine-fault-isolation --type change
Change 'fix-navigation-engine-fault-isolation' is valid                          exit=0
$ openspec validate --changes
Totals: 15 passed, 0 failed (15 items)                                           exit=0
$ openspec validate --specs
Totals: 175 passed, 0 failed (175 items)                                         exit=0
```

The repair itself: word-diff of each repaired block against its shipped requirement names **0 removed
word-groups**, and the scenario counts move 11 → 12 (`car-host-fault-isolation`) and 6 → 8
(`navigation-engine`); no shipped text is dropped. Structural checks for the `openspec/**` delta: the CI
hygiene step's replication (`hygiene fail=0`) and `bash tools/check-doc-routes.sh` (89 section references
resolve); no build, no test suite, no device — the delta compiles nothing.

## The rule, and the guard

The rule the defect broke is now stated once: `openspec/specs/documentation-ownership/spec.md` —
"A modified requirement carries the scenarios its spec still has", added by the archived change, with two
scenarios that name `openspec validate --changes` and `openspec validate <change> --type change`.

No new mechanical guard was added, deliberately (see `design.md` D3 of the archived change): no runner under
`tools/` validates change deltas (`tools/declared-cases.sh` parses a change's `tasks.md`), and the CI runner
installs no OpenSpec CLI, so `.github/workflows/build.yml`'s "Check OpenSpec artifact hygiene" step cannot run
the check. `openspec validate --changes` — the command that found this defect — stays the guard, run when a
change is validated and again when its deltas are assessed before archiving.

## What this closure does not cover

`fix-navigation-engine-fault-isolation` itself stays unarchived and is not closed here: its task 6.3 (on-device
confined-fault evidence) remains partial, which is its author's step. Its delta is now archivable.
