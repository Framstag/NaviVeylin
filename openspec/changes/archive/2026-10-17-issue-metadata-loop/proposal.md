# Proposal

## Why

`triage-todo` re-reads the whole backlog and re-scores every entry on every run. That is affordable
for prose — it is not affordable once the backlog is the tracker, because "the whole backlog" becomes
136 issue bodies handed to a model on every triage, and the score it produces is thrown away each time.
The migration's rewrite of the skill removes the *file* read; it does not remove the *recompute*. This
change does.

Two consequences of leaving it undone, both measured in this repository:

- **Token cost scales with the backlog, not with the change.** The score of an issue whose text, labels,
  cited code and recurrence count are unchanged cannot differ from yesterday's — yet today it is
  recomputed. With a persisted band, that work disappears from every run.
- **The loop's own start is expensive for no reason.** `agent-issue-loop`'s prescreen is deliberately
  mechanical (class, status, author, a path-exists sensor) and its design says the *score* stays a
  judgement. Without a persisted score, that judgement has to be taken for the whole queue at the start
  of every run, which is the cost the prescreen exists to remove.

The other half is classification. Imported issues arrive classified, and a sensor's registry row
declares its `class` and `category` — but a human-filed or third-party-filed issue has neither, and an
issue without a class cannot be ranked, filtered or gated. One pass over the unclassified set closes
that, which is what makes "file first, classify later" a workable workflow and an issue template
optional.

## What Changes

- **Priority becomes persisted state in a fixed range.** A `prio/p0`–`prio/p3` label carries the band;
  a body marker carries the score's components and the basis they were computed from. The existing
  scoring model survives intact — `severity(1..5) × reach(1..3) + confidence(1..3)`, `3..18`, banded —
  so no resolution is lost to the four-value range.
- **A recorded basis decides whether to recompute.** The marker stores
  `band, score, sev, reach, conf, basis, at, occ`; `basis` is a hash over exactly what the score
  depended on — the `class/*`, `category/*` and `status/*` label set, the body's hash, whether the cited
  path or symbol still exists, the sensor `occurrence` count, and a long TTL. Unchanged basis ⇒ the band
  is reused and **the model is never called for that issue**.
- **Only the changed subset is scored.** The script reads titles, labels and bodies for the whole
  backlog in one call — an HTTP call, not a model call — recomputes each basis, and hands only the
  mismatches to the model, bounded by `--max-rescore` so a vocabulary-wide edit cannot trigger a sweep
  of the whole tracker.
- **Classification joins the same pass.** An issue with no `class/*` label (the definition agreed for
  "unbearbeitet") is classified in the same run: the model proposes `class` and `category`, the script
  writes them. One queue, one write path, one audit line.
- **The judgement/mechanical split is explicit.** The model produces severity, reach and confidence.
  Band order, bugs-before-improvements, the deadline override and the gate lists (`needs-device`,
  `needs-decision`, `in-flight`, `unverifiable`) stay derived from labels in the script, so the ranked
  list costs nothing and no gate has to be persisted.
- **Priority is not authorisation.** `prio/p0` does not make an issue loop-eligible; `agent/eligible`
  remains the only opt-in, still reserved and unwritable by this pass. Stated in the design and enforced
  by the same reserved-label refusal every write path already goes through.
- **A band change is explained.** A comment is posted when the band *transitions*, not on every
  rescore, so the timeline answers "why did this become P0" without becoming a log of arithmetic.
- **Metrics for the change's own justification**: issues scored, issues skipped by an unchanged basis,
  issues classified, and the wall time of the pass. That is the number that shows whether the scheme
  works.
- **Explicitly not in this change**: the sensors that produce issues (`sensor-registry-and-metric-issues`
  — this pass classifies what they file only when a row did not declare a class), the human decision lane
  (`decision-queue`), the loop's own ranking implementation (`agent-issue-loop` consumes the bands), and
  any agent sandbox.

## Capabilities

### New Capabilities

- `issue-priority`: an item's priority is stored on the item in a fixed band range, with the components it was derived from and the basis they were computed over; the stored value is reused while that basis is unchanged, so ordering a backlog costs a query and a repeated run interprets nothing; only the changed subset is interpreted and a run is bounded; unclassified items are grouped in the same pass and written by the tooling; priority is never authorisation; and ordering uses the stored band while the gating conditions stay derived from labels.

### Modified Capabilities

None.

## Impact

New:
- `tools/issues/prioritise.sh` — the selector, the basis computation, and the writer of the band label
  and the body marker
- `tools/issues/classify.sh` — the writer of `class/*` and `category/*` for the unclassified set
- `tools/tooling.json`'s `bands` section — the band edges and the scoring table, so the mapping is committed and
  testable
- `tools/issues/check-metadata.sh` + `tools/issues/check-metadata-selftest.sh` — the marker/label
  agreement and range check
- `.pi/skills/issue-metadata/SKILL.md` — the judgement half: what the model is asked for, and what it
  must never write

Modified:
- `guidelines/Backlog.md` — the sections this change makes stale: its own row in §1, and the §12 rows for the decisions it lands
- `.pi/skills/triage-todo/SKILL.md` — ranks by the persisted band, loads a body only for a candidate
  whose basis no longer matches, and reports a band-less issue as *unranked*
- `tools/issues/list.sh` — a `--prio` selector and an `--unranked` selector
- `.github/workflows/build.yml` — one token-free step running `check-metadata.sh`
- `openspec/config.yaml` — nothing, deliberately: this change adds no rule, it implements an existing one

Not affected: no Kotlin/Java/C++ under `app` / `auto` / `core` / `osmscout-client-java` / `buildSrc`; no
manifest; no Gradle configuration; no native or JNI boundary; no release path; the sensor pipeline is
untouched. No priority is set by the migration's import — this pass is what fills the bands in, and
until it runs `triage-todo` reports those issues as unranked.

Scope: agent-harness and process tooling only — variant-independent; neither phone-only nor car-only
work. Guidelines referenced: `guidelines/Build.md` §1, §2 (the iteration protocol and its scope block),
§3. No `UI.md`, `MapRendering.md`, `Design.md`, `Regulatory.md` or `Logging.md` section is affected.

**Additive.** Every existing label and field keeps its meaning; a band is added, and a score that was
previously recomputed is now reused. **Rollback**: revert `tools/issues/prioritise.sh`,
`classify.sh`, the profile's `bands` section, the two check scripts, the skill, and the `list.sh` selectors. The
`prio/*` labels and the body markers may be left in place — they are inert once nothing reads them —
or removed by `cleanup-todo`'s hygiene pass. Nothing in the app is involved.

**Verification.** Offline cases over a fixture set with a stub `gh`: the basis is stable across a
whitespace-only body edit and across an unchanged label set; a changed `class/*` invalidates it; the
band edges map the full `3..18` range; the marker and the label are written together and a check
detects a hand-edited mismatch; an unclassified issue is classified but never given `agent/eligible`;
and the pass skips everything when nothing changed. Then one real run over the live tracker, recorded
with its scored/skipped counts and its wall time. No device or pixel measurement applies.
