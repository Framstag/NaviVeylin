# Proposal — the section that owns the item context bound records the lever that was refused

## Why

**Root cause.** `guidelines/Backlog.recipes.md:254`, section 7b, states the stop's value and the measurement that set it
(`:459`–`:460`: `the stop is 200 000, above the p95, where a turn costs 1.86× the archive's median largest context`),
and on the base commit no place in this tree recorded that raising it was measured and refused — `tools/look.sh
'refused lever|raise the stop' --path guidelines` prints `0 matches in 0 files`, before this delta and after it, and
`tools/look.sh '#833'` printed `0 matches in 0 files` over the whole tree. So the first lever a reader reaches at the
bound was one the tree could not refuse, and every pass that reached it re-derived the same measurement from the run
records.

**Evidence, verified against the records (issue #833).** The run `2026-11-07T19:53:44Z` ended three of its items at
the bound, and each had already written its red-on-HEAD case and its change before it was cut:

```
$ jq -r '.. | objects | select(.issue? and (.issue==719 or .issue==753 or .issue==819))
         | select(.metrics.context) | "\(.issue) context=\(.metrics.context) turns=\(.metrics.turns)"' \
       .pi/issue-loop/runs/2026-11-07T19:53:44Z.json
753 context=200427 turns=69
819 context=200733 turns=80
719 context=201981 turns=145

$ ls -l .pi/issue-loop/items/{719,753,819}/leftover-from-killed-run.patch
-rw-r--r-- 1 tim tim 512102  8. Nov 03:27 …/items/719/leftover-from-killed-run.patch
-rw-r--r-- 1 tim tim 553973  7. Nov 22:52 …/items/753/leftover-from-killed-run.patch
-rw-r--r-- 1 tim tim 509913  8. Nov 02:07 …/items/819/leftover-from-killed-run.patch
```

All three tokens sit within a thousand of the recorded 200 000, and
`.pi/issue-loop/items/{719,753,819}/` each carries the red-on-HEAD XML and the change diff its worker had written
before the run ended it (`719`: `TEST-NavigationEngineStepIndexTest-red-on-HEAD.xml`; `753`: `red-on-HEAD.xml`;
`819`: four `red-on-HEAD-*.log`), so a wider bound buys more of the tail a cut is made of rather than the item the
cut interrupted. `719`'s record names `bound` as its mechanism; `753` and `819` carry the context reading and the
kept patch and no stop reason, which is what their records were written with. What the bound owed instead is #754's
reading of a kept patch and #809's rate line, and both have landed
(`openspec/changes/archive/2026-11-09-fix-a-kept-patch-resumes-the-item`,
`…/2026-11-09-fix-a-bound-hit-rate-is-named`); neither moves the value.

**The red test.** `evidence/refusal-record-probe.sh`, a text probe that reads the body of section 7b
(`guidelines/Backlog.recipes.md:254`) — from its heading to
the next `## ` heading — and requires the refusal, its issue and the three recorded contexts in it. Run against the
section as the base commit carries it (`git show 8a2f1adb:guidelines/Backlog.recipes.md` into a scratch root, which
is how a red read is taken without reverting the tree) it reports `tests=1 failures=1` — `missing '#833' '200 427'
'200 733' '201 981'` (`evidence/red-on-HEAD.xml`, `evidence/red-on-HEAD.log`, 2026-11-09T21:46:54Z). It is
text evidence, not a declared case — the section it reads is the one this change writes, so a permanent case over it
would be the refusal wearing a case's name, the reading `fix-reversal-rules-name-their-record` recorded for its own
probe.

**The requirement the item contradicts.** None is added: `openspec/specs/agent-issue-loop/spec.md` "The bound on a
child is priced in what it spends, not in how many turns it takes" (`:3510`) states that the bound is read and
enforced and records no value, and the value is the profile's (`tools/loop/loop.conf:212`). This change moves no
behaviour, so it carries no `specs/` delta and changes no requirement.

**The guideline that owns the rule.** `guidelines/Backlog.recipes.md:254` "What an item costs, and the bounds on it"
(section 7b) owns the context bound, its p75/p95 measurement and its value; section 7b gains the paragraph. `guidelines/BacklogTooling.md`
`:148` names the key and its value in the profile's key table and gains nothing: it is a key inventory, and a second
statement of the measurement there would be the rule stated twice.

## What Changes

- `guidelines/Backlog.recipes.md:254` (section 7b) — one paragraph, after the paragraph that measures the bound and before the
  paragraph that prints its hit rate: raising the stop above 200 000 is refused, with the three items the run of
  2026-11-07T19:53Z ended at it, the work each already carried, the size each kept, and the value unchanged.
- `openspec/changes/fix-a-refused-lever-is-recorded/` — the probe, its red and green tallies, and the structural
  checks.

Nothing else: no case, no fixture, no profile key and no existing shell file changes. Additive prose; the only
rollback is reverting the paragraph, and the probe then reports `tests=1 failures=1` again.

surface: 0 cases added, 0 rewritten, 0 requirements added, 1 paragraph in 1 guideline section —
`tools/look.sh 'refused lever|raise the stop|a wider bound|raising the bound' --path guidelines` → 0 matches before
the delta; `tools/look.sh 'LOOP_ITEM_CONTEXT_MAX' --path guidelines` → 4 matches in 2 files, of which only section 7b gains
text.

## Impact

Additive, prose only: no compiled file, no shell file the gate runs, so no build and no Gradle task is owed.

- `guidelines/Backlog.recipes.md:254` — one paragraph in section 7b.
- `openspec/changes/fix-a-refused-lever-is-recorded/evidence/` — the probe and its tallies.

**Rollback.** Reverting the paragraph restores the section byte for byte; `evidence/refusal-record-probe.sh` then
reports `tests=1 failures=1`, which is the signal the record is gone.

## Open decisions

none — the item's `ITEM_DECISION` block records `none`, and the item's own direction is the one this change takes:
record the refusal, do not move the bound.
