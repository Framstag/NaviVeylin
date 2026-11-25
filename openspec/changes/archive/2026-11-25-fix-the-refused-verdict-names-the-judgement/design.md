# Design — the refused-class verdict names the judgement

## Root cause

`tools/loop/prescreen.sh:408` writes `verdict="class-feature"` in the branch that refuses an item whose class the
profile does not work (`is_refused_class`, `:407`). The class is a *value* read from the profile's class namespace;
the verdict is the *judgement* the pre-screen made. The two were conflated, so a project that refuses
`class/improvement` logs a refused feature, and the engine carries a name the portability rule forbids it
(`guidelines/BacklogTooling.md` §10).

The two fixtures that exercise the branch could not see it: both set `refusedClasses = ["feature"]`
(`tools/loop/tests/loop-cases.sh:3045`, `:3086` on the base commit), the single value for which the literal and a
judgement word coincide.

## The single fix

The verdict becomes `class-refused` — the judgement, with no class in it — and the two readers move with it:

- the reason keeps naming the class (`prescreen.sh:409`), which is the record a person reads;
- the refusal-label mapping keys on the new word (`prescreen.sh:700`), and the label it writes is unchanged, because
  the profile's `excludedFeatureAgent` role is the profile's own declaration and not the engine's word.

Nothing else moves: the refusal's behaviour, the label written, the queue's shape and the run's report are
unchanged, so no fixture that does not spell the verdict is touched.

## Blast radius

- **Engine:** `tools/loop/prescreen.sh`, two lines (the literal and the `case` pattern) plus the comment above the
  branch. It is the only engine that writes or reads the word: `git grep -n 'class-feature' ae2373cb -- tools/`
  names these two lines in it, the two fixture assertions in `tools/loop/tests/loop-cases.sh`, and no other engine
  file.
- **Cases:** `tools/loop/tests/loop-cases.sh` — one case added with its two registry rows (the selection set at
  `tools/loop/tests/loop-cases.sh:68` and the dispatch at `:909`; a body with no row in both is the
  `--list`-and-selection disagreement issue #929 refuses), two existing assertions re-pointed. No fixture file
  changes.
- **Spec:** three shipped lines in `agent-issue-loop` (`openspec/specs/agent-issue-loop/spec.md:247`,
  `openspec/specs/agent-issue-loop/spec.md:1851`, `openspec/specs/agent-issue-loop/spec.md:1864` on the base
  commit), which is why this lands as a change and not as a one-line fix.
- **Not touched:** archived changes that quote the old word (8 matches in 5 files of 3 archived changes). An
  archive is a record of what was true when it ran; rewriting it would make the record lie.

## Rollback

Revert the diff. The renamed word is internal to two lines of one script and one `case` arm, so nothing outside the
tree remembers it: the tracker carries labels (`agent/excluded-feature`), never verdicts, and no state file holds
one. A run started on a reverted tree logs the old word again from its first item.

## Alternatives the evidence eliminated

- **The verdict is the class** (`class/improvement`): unbounded vocabulary, and every reader would need to accept a
  namespace where it expects a judgement. The reason already carries the class once.
- **Tighten `name.awk`'s path-ish exemption in `tools/check-portability.sh`:** the exemption is what keeps every
  legitimate hyphenated engine identifier out of the scan, so tightening it is a scan change with its own fixtures —
  and it repairs the *detector*, not the verdict a person reads.
- **Refuse nothing and let the queue work every class:** a different decision, already made the other way by the
  profile's `refusedClasses` and by change `idea-shape`'s D5, and not this item's.

## Proof shapes

- The verdict word: `evidence/mutation-red.log` — the branch set back to `class-feature` with everything else in
  place, `prescreen#theRefusedVerdictNamesNoClassValue` red for exactly the clause
  (`expected 'class-refused', got 'class-feature'`), `evidence/mutation-restored.log` green again after the file was
  restored byte for byte (`evidence/mutation-before.sha256`).
- The reason clause: `evidence/mutation-red-reason.log` — the reason's `${class}` removed while the verdict stays
  `class-refused`, so the AND clause fails on its own assertion rather than behind the verdict's.
