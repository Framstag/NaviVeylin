# Proposal — fix-openspec-modified-delta-loss

## Why

The unarchived change `openspec/changes/fix-navigation-engine-fault-isolation/` carries two **MODIFIED**
requirement blocks that were written as *partial* copies of the requirements they modify. A MODIFIED block
replaces the whole requirement when the change archives, so those two blocks would drop five scenarios the
shipped specs still have — and the CLI refuses the change today, which is why it cannot be archived at all.

**Evidence (repro command, 2026-10-18 on HEAD `b83e857`, saved in `evidence/red-on-head.log`):**

```
$ openspec validate fix-navigation-engine-fault-isolation --type change
✗ [ERROR] car-host-fault-isolation/spec.md: MODIFIED "No fault escapes into the host path" omits scenario(s)
  the current spec still has: "A fault in one frame keeps the frames coming", "A fault in a display tick
  keeps the follow pipeline running". The modified block has 10 scenarios; the current spec has 11.
✗ [ERROR] navigation-engine/spec.md: MODIFIED "Engine lifecycle and threading" omits scenario(s) the current
  spec still has: "A background writer cannot revert a concurrent publication", "The staleness tick reads
  the injected time source", "A native lookup result is published on the main thread". The modified block
  has 5 scenarios; the current spec has 6.
exit=1
$ openspec validate --changes
Totals: 14 passed, 1 failed (15 items)
```

**Root cause (one).** The blocks were copied from the shipped requirements as those requirements stood when
the change was written (2026-09-27), and the shipped specs gained those five scenarios *afterwards*: the two
frame/tick scenarios with `fix-car-render-loop-restart` (`car-host-fault-isolation`), and the atomic-update,
injected-time-source and native-lookup scenarios with the phone-side publication fixes (`navigation-engine`).
The change's own validate task (its 6.4) ran once at authoring time and its archive step re-validates only at
archive time; nothing re-validated the block against the *current* shipped spec in between. A partial block
therefore stayed invisible until `openspec validate --changes` ran over the whole set on 2026-10-17 — the
change's own delta validation cannot see drift that arrives from a sibling change landing later.

**The single resulting fix.** Complete both MODIFIED blocks so each carries the shipped requirement whole —
its description and every scenario — plus the sentences and scenarios the change itself adds. This is a pure
superset: no shipped text is removed, the delta's own new content is untouched.

**The requirement the defect contradicts.** No shipped requirement states the rule that was broken, which is
why the omission was invisible until the CLI caught it — the MODIFIED-block rule lives only in the CLI's own
error text. This change adds it to `documentation-ownership` ("A modified requirement carries the scenarios
the shipped spec still has"), next to that capability's existing change-artifact requirement.

**Fix the case, not only the document.** No new mechanical guard is added: no runner under `tools/` validates
change deltas today (`tools/declared-cases.sh` parses a change's `tasks.md` only), and the CI runner installs
no OpenSpec CLI, so `openspec validate --changes` — the command that found this — stays the guard, run by the
change's own validate task and by the archive step. `design.md` D3 records why, with the evidence.

## What Changes

- **`car-host-fault-isolation` MODIFIED block completed.** It gets back the shipped requirement's per-loop-
  iteration sentence and the two scenarios that go with it (`A fault in one frame keeps the frames coming`,
  `A fault in a display tick keeps the follow pipeline running`), keeping the change's own engine sentence and
  its `Engine fault while a car session is live` scenario.
- **`navigation-engine` MODIFIED block completed.** It gets back the shipped requirement's atomic-update and
  injected-time-source sentences and the three scenarios that go with them (`A background writer cannot revert
  a concurrent publication`, `The staleness tick reads the injected time source`, `A native lookup result is
  published on the main thread`), keeping the change's own fault-handler, native-boundary and stays-usable
  sentences and its two new scenarios.
- **One requirement added to `documentation-ownership`**: a MODIFIED block carries the shipped requirement
  whole, and a block that omits a shipped scenario is refused, with the two validate commands named.
- **The two shipped specs are the source, not the target**: `openspec/specs/car-host-fault-isolation/spec.md`
  and `openspec/specs/navigation-engine/spec.md` are unchanged by this change (verified: the delta is now a
  superset, `diff` names only additions), and `guidelines/Design.md` §8/§4 already state the engine fault rule
  the delta carries (checked — no edit needed).
- **Additive, no breaking change.** No production code, no API, no dependency, no user-visible behaviour; the
  change set becomes archivable. Rollback path: revert this change's commit — the two blocks return to the
  partial state that `openspec validate --changes` refuses.
- **Not a native/JNI change**: no submodule patch, no `:osmscout-client-java` override. No compiled file at
  all, so no build and no test suite is owed (`guidelines/Build.md` §2, scope block).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `documentation-ownership`: one requirement **added** — "A modified requirement carries the scenarios the
  shipped spec still has". It belongs to the capability that owns the project's change-artifact rules: its
  existing requirement "The change-artifact guidance reads by section" is the only requirement in the shipped
  set that governs a change artifact, and the capability owns which document owns which rule
  (`A rule has one normative home`). No shipped requirement is modified.

**Sequencing note (decided, recorded).** `fix-navigation-engine-fault-isolation` stays unarchived and outside
this change's scope: this change repairs its delta and stops there. Its own archive (task 6.3 remains partial)
is its author's step, and its delta is now archivable.

## Impact

Changes (the case this change fixes)
- `openspec/changes/fix-navigation-engine-fault-isolation/specs/car-host-fault-isolation/spec.md` — MODIFIED
  block completed (description + two scenarios restored)
- `openspec/changes/fix-navigation-engine-fault-isolation/specs/navigation-engine/spec.md` — MODIFIED block
  completed (description + three scenarios restored)

Docs and specs
- `openspec/specs/documentation-ownership/spec.md` — gains the added requirement when this change archives
  (the delta lives at `openspec/changes/fix-openspec-modified-delta-loss/specs/documentation-ownership/spec.md`)
- `guidelines/Design.md` — read, not edited: §4 and §8 already state the engine fault-handler rule and the
  per-loop-iteration exception the repaired blocks carry
- Read, unchanged: `openspec/specs/car-host-fault-isolation/spec.md`, `openspec/specs/navigation-engine/spec.md`
  (the source of the five scenarios), `openspec/specs/spec-feature-index/spec.md` (its index is keyed per shipped
  spec id, so an added requirement needs no classification edit — `tools/feature-list/specs.json` already names
  `documentation-ownership`)

Structural checks (no compiled file, no Gradle)
- `openspec validate fix-navigation-engine-fault-isolation --type change` → exit 0 (was exit 1)
- `openspec validate --changes` → the open set clean (was 14 passed / 1 failed of 15, now 14 passed / 0 failed
  of the 14 pre-existing items plus this one)
- `.github/workflows/build.yml` "Check OpenSpec artifact hygiene" (task markers, config quoting) — the step CI
  runs for `openspec/**`
