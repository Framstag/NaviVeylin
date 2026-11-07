# Notes — guideline-backlog-cut

## 1. Who implemented this, and why not a subagent

Parent-side, on the same grounds as `guideline-ui-cut`: the change directory carried nothing but
`.openspec.yaml`, the two child routes are blocked (one 18-minute stall that produced nothing, one provider
`402` credit refusal before its first tool call), and the artifacts were measured in the parent before the
brief. The deviation from "one subagent per change" is recorded here rather than hidden.

This cut was also the first whose artifacts were authored by the parent — `proposal.md`, `design.md` and
`tasks.md` were written in the same pass that executed them. `openspec validate --strict` was run before the
first edit, so the change's shape was checked while it was still a claim.

## 2. A claim in this change's own design was wrong, and the check caught it

`design.md` D3 first said "`guidelines/Backlog.why.md` carries no §9/§10/§11 anchor, so it does not split".
That was read off a grep that had covered only `Backlog.recipes.md`. `Backlog.why.md` does carry
`## §9 Checks — why` at line 87, and `check-doc-routes.sh` reported:

    ::error file=guidelines/Backlog.why.md::the anchor §9 names no section of its base document guidelines/Backlog.md

The companion split was extended to it: that block became `guidelines/BacklogTooling.why.md` (1 962 B), while
`## §5`, `## §6`, `## §12` and `## §13` stayed. The design now records the correction instead of the claim,
and the "checks and are not risks" list gained the third companion.

The lesson is the one the mechanism exists for: **an anchor is reported, never silently orphaned** — the
companion error is what turned a wrong assumption into one more move instead of a dangling document.

## 3. The companion preambles needed a third variant

`Backlog.recipes.md`'s preamble introduces itself as "the procedure layer of `guidelines/Backlog.md`" and
listed "§9 for the rules about checks". With §9 gone it pointed at nothing, so its preamble now names
`BacklogTooling.recipes.md` for that procedure. `BacklogTooling.recipes.md` carries the counterpart preamble.
The same shape was applied to `Backlog.why.md` (unchanged preamble, its §9 block gone) and
`BacklogTooling.why.md` (new preamble).

## 4. What was retargeted

Twelve `tools/doc-routes.json` entries, two `AGENTS.md` rows (one split into two, one citation inside a row
that stays), eight prose references in `tools/**/*.sh` and `.pi/skills/{loop-spend,portability-check}`,
and five internal references inside `Backlog.md`. Every one is name-only: no statement, sentence, example or
heading text changed except the companion preambles described above.

## 5. Left alone, deliberately

- **`.pi/issue-loop/items/**`** — the loop's own run records. A report that cited `Backlog.md` §9 on the day
  it read the file is a historical statement; rewriting it would falsify a record.
- **`.pi/worktrees/**`** — other working trees, not this one. `grep` from the root walks into them; the
  referring set excludes them explicitly.
- **The two `"UI.md §7"` entries in `tools/doc-routes.json`** — `guideline-uiphone-cut`'s drift, recorded here
  and in that cut's notes. The review made it blocking; both entries are now deleted, and the deletion exposed
  one genuinely unreached rule in `DesignNative.md` §5b, which is repaired and named in the map (see
  `rules.txt` and `guideline-ui-cut`'s notes §7).

## 7. The count moved to 576 during the pass

The check reports **576** named rules at the end of this round, 575 when this cut's own verification ran. The
extra rule is `guidelines/DesignNative.md` §5b "the map scan publishes only what this build can read"; it
arrived from a write to `DesignNative.md` at 19:26:58 that this session did not make, and it was unreached by
any glob, which is what turned `check-rule-routes.sh` red mid-round. The write is identified: change
`fix-basemap-scan-format` (#716, committed `fd719a8d`), a second workstream landing in this working tree while
the set ran — the set's own rule is one builder per working tree. The guard the user asked for then ran:
checks green immediately before the archive and green after it.

## 6. What the four cuts together did

`UIPhone.md` 47 926 → 19 395 (+ `UIPhoneMap.md` 29 340), `MapRendering.md` 46 751 → 30 344 (+ `MapFrames.md`,
`MapRoute.md`), `UI.md` 45 327 → 24 881 (+ `UICar.md` 21 562), `Backlog.md` 35 887 → 26 032
(+ `BacklogTooling.md` 10 829). The `document-size` row went from **4 conditions to 0**. The named-rule count
was 575 before and after all four; the corpus carries **576** once `fix-basemap-scan-format` (#716) landed its
rule into `DesignNative.md` §5b during the pass, which §7 records.
