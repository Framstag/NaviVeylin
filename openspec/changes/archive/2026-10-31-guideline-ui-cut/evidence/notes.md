# Notes — guideline-ui-cut

## 1. Who implemented this, and why not a subagent

The change was implemented **parent-side**, which is a deviation from the set's "one subagent per
change". Two child attempts failed on infrastructure, not on the task:

- run `20d13650` (2026-10-31T15:30:51Z–15:49:43Z, 18m52s, exit 0, `state: complete`) produced
  **nothing**: `evidence/` empty, `UI.md` byte-identical, no `UICar.md`. Its whole clock went into
  surveying section boundaries — the survey tasks.md asked for, which the parent then did once.
- run `6af1e147` died on a provider credit wall (`402 … you requested up to 926321 tokens, but can
  only afford 95294`) before its first tool call. Tree untouched.

With the artifacts already carrying every measurement the child needed, and the tree free of other
writers, the parent did the work. The lesson this records: **a cut task's brief must carry the measured
section table**, because a worker that has to measure the boundaries itself may spend its entire budget
there.

## 2. `§8d` was missing from the design's clusters — the amendment

`design.md`'s D1 named a car cluster and a shared cluster; **§8d (5 539 B, nine rules, seven dual-surface)
belonged to neither**, and it is the section the first child stalled on, because task 2.3 requires the
heading union to close. Two facts were behind it: the section is dual-surface by content, and **no row of
AGENTS.md's Documentation Map named it** — `--sizes` marked it `not-routed`, so a design that read the
table's clusters could not have seen what the table does not route.

`§8d` stays in `UI.md`, and the parity row of the Documentation Map now names it (5.5 kB, measured
2026-10-31), which is the repair of the routing drift that hid the section in the first place. See
`design.md` → "Amendment during apply — §8d was missing" and `evidence/pre-cut.md`.

## 3. The three edits inside moved text, and the one inside a kept section

Everything that moved is byte-identical except three references whose document name follows the text:

| where | before | after | why |
|---|---|---|---|
| `UICar.md` §3a | `UI.why.md` §3a | `UICar.why.md` §3a | its rationale moved with the section |
| `UICar.md` §3c | `UI.why.md` §3c | `UICar.why.md` §3c | its rationale moved with the section |
| `UICar.md` §6b | bare `(§1)` | `(`UI.md` §1)` | §1 stays in `UI.md`, so the bare number would dangle |

`UI.md` §20 is the one kept section with an edit: its first bullet now says the restatement rule holds
"in this document and in `UICar.md`", and its glyph-table reference names `UICar.md` §2. §20 is the
maintenance rule for both documents, which is why `UICar.md` carries no §20 of its own — it points at
`UI.md` §20 in its preamble.

## 4. Companions

- `UI.why.md` 15 551 → 14 936 B: `## §3a` and `## §3c` left for `UICar.why.md` (859 B, created — it holds
  the material, so it is not an empty companion).
- `UI.recipes.md` 1 032 B had **one** anchor, `## §3 Reading the car-list capacity` — a car section. The
  whole companion therefore moved and is now `UICar.recipes.md` (1 035 B); `UI.md` has no procedure layer,
  which is correct: a procedure layer whose only anchor moved is not this document's layer.
- `## §8d The car search distance reference` stays in `UI.why.md` even though its title reads
  car-flavoured, because its section stays in `UI.md`.

## 5. The stale `tools/doc-routes.json` section entries — found, reported, then repaired

`tools/doc-routes.json` carried two `"UI.md §7"` *section* entries (lines 14 and 373), and `UI.md` has
carried no §7 since `guideline-uiphone-cut` moved it to `UIPhoneMap.md` §7. Nothing checks section names in
this map — only glob existence and rule reach — so the drift was invisible to CI.

It was first recorded here and handed to `guideline-uiphone-cut`. **The independent review made it blocking**,
and the owning change is in this set and still open, so this round removed both entries (the
`UIPhoneMap.md §7` entry in the same glob already covers what they meant).

Removing them exposed a second thing the same map was hiding: the two junk lines had made
`check-rule-routes.sh`'s reached-set contain a line that was no rule at all. With them gone the check reported
one genuinely unreached rule — `guidelines/DesignNative.md` §5b "the map scan publishes only what this build
can read" — and that rule's own line was malformed (`**Rule: X** **X.**`, the name repeated as a bold
statement). Both are repaired: the line now reads `**Rule: X** The map scan …`, and the map names it under
`DesignNative.md §5b`. See `evidence/notes.md` §7 and the correction note in `guideline-backlog-cut`.

## 6. Correction round — the independent review's three blocking findings

The review of the four cuts returned **FAIL, 3 blocking**. All three were real and all three were this set's:

| finding | what was wrong | fix |
|---|---|---|
| 1 — the referring set was measured too narrowly | `pre-cut-citations.txt` records `grep -rln 'UI\.md.*§' guidelines/ AGENTS.md .pi/skills/`. Code comments and a spec also cite the document, so five live references still named `UI.md` §3/§3a | four name-only retargets: `auto/…/AboutScreen.kt:17`, `auto/…/DiagnosticsScreen.kt:36`, `auto/…/PoiResultsScreen.kt:65`, `app/…/NavigationNotificationService.kt:317`. The fifth, `openspec/specs/about-dialog/spec.md:156`, is inside a requirement body — a spec edit during apply is the user's standing prohibition, so it is **asked, not edited** (see §8) |
| 2 — two bare cross-section refs dangled across the `guideline-uiphone-cut` split | `UIPhone.md`: "the on-screen stop button, §7b" (§7b is in `UIPhoneMap.md`); `UIPhoneMap.md`: "the driving modes only (§17)" (§17 is in `UIPhone.md`) | both qualified, `UIPhoneMap.md` §7b and `UIPhone.md` §17 — the same repair this cut made for a bare `(§1)` in `UICar.md` §6b |
| 3 — the map kept two unresolvable entries | the `"UI.md §7"` pair above | both entries deleted; `check-rule-routes.sh` then exposed the unreached `DesignNative.md` §5b rule, which is repaired and now named |

After the round: `check-doc-routes.sh` → 327 references resolve, exit 0; `check-rule-routes.sh` → every glob
matches the tree and all **576** named rules are reached, exit 0.

## 7. The rule count is 576, not 575 — and a concurrent writer caused it

Every figure in this change's evidence that says **575** was correct when it was measured. The corpus now
carries **576** named rules. The 576th is `guidelines/DesignNative.md` §5b "the map scan publishes only what
this build can read", and it arrived **while this pass was running**: `DesignNative.md`'s mtime moved to
19:26:58 without any write from this session, `Build.md` and `UI.md` moved with it at 19:26:38, and the rule was
unreached by any glob — which is why `check-rule-routes.sh` went from green to `1 rule(s) are reached by no
glob` in the middle of the correction round.

This session did not make that edit. **A second workstream was landing in this working tree** — the set's own
rule is one builder per working tree — and it is now identified: change `fix-basemap-scan-format` (#716),
committed as `fd719a8d` at 16:31 and applied into the tree while this pass ran. Its subject is exactly the rule
that appeared: the map scan skipping a database whose data format this build does not read. Recorded rather
than smoothed over — a rule count that changes under a pass is a fact about the tree, not a rounding
difference, and the identification is the point of recording it.

## 8. Open: the spec citation

`openspec/specs/about-dialog/spec.md:156` cites `guidelines/UI.md` §3 inside the requirement "License list is
not surfaced in the car app". §3 now lives in `UICar.md`. The repair is one word on one line, but it edits a
**shipped spec during apply**, which the standing constraint forbids ("the delta in the change dir is what
archives"). This change declares `skip_specs: true` and carries no delta in which the repair could travel.
Put to the user rather than decided here.

## 5a. One pre-existing drift found and NOT fixed here

`tools/doc-routes.json` carries two `"UI.md §7"` section entries (lines 14 and 373 after this edit) and
`UI.md` has carried no §7 since `guideline-uiphone-cut` moved it to `UIPhoneMap.md` §7. Nothing checks
`doc-routes.json`'s *section* names — only that each glob matches a tracked path and that every named
rule is reached — so the drift is invisible to `check-rule-routes.sh`.

It is recorded here rather than repaired, because it belongs to the change that caused it
(`guideline-uiphone-cut`), and this change's rule is that a correction is made in its owning change.
Whoever archives the cuts should correct both entries to `UIPhoneMap.md §7`.
