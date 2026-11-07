# Known gaps in this change, recorded for the independent review

Written by the parent session during apply, 2026-10-31, after the change's own tasks were reported done.
Neither item fails a check; both are things the reviewer should grade rather than rediscover.

## G1 — the diet dropped two skill-path pointers, and the route check cannot see that

The check resolves a skill path that *does not exist*; it cannot report a path that was **dropped**. Measured:

```
skill paths named in AGENTS.md + guidelines/*.md, at HEAD      .pi/skills/create-issue, .pi/skills/device-check, .pi/skills/pixel-check
skill paths named after the diet                                .pi/skills/pixel-check  (and .pi/skills/feature-list later)
```

`create-issue` and `device-check` were named in the blocks this change removed (B2 the nine-skill table, B3 the
`Agent iteration loop` pointer bullets). The statements those blocks carried do live in the owning sections, and
`relocation-entry-document.txt` proves it block by block — but the *path-shaped* pointer to those skills did not
survive anywhere in `guidelines/`, so `ls .pi/skills/` is now the only way to find them from the entry document.
Grade as a NOTE unless the review holds that a route to a skill is part of the statement that was relocated; a
one-line restore in `AGENTS.md` is strictly subtractive to fix.

## G2 — block B2's evidence proves less than it claims

`relocation-entry-document.txt` justifies removing the nine-skill table by listing **all 35** `.pi/skills/*/SKILL.md`
paths as owners, while the table named **nine** skills. The claim ("each skill's job is stated in its own contract")
is true — a skill's `description` field is written for exactly that — but the evidence as recorded does not
demonstrate it for the nine, and a reader cannot tell which nine they were. Grade as a NOTE on evidence quality, and
consider requiring the next document change to list the specific owners rather than a directory listing.

## G3 — `tools/doc-routes.json` is coarse by design, and worth stating once

The map shipped with `app/src/main/java/**` as its only engine glob, plus the per-document globs the six cuts added.
So a change under `tools/**`, `guidelines/**` or `openspec/**` derived **an empty reading set until those cuts
landed** — an earlier wording of this gap said "today", and the independent review measured it false: the map now
carries **27 globs** including `tools/**`, `guidelines/**` (five entries), `openspec/**`, `AGENTS.md` and
`guidelines/UI*.md`. The disposition is unchanged — the map is coarse rather than absent — but the sentence is
corrected: a change there now derives a set, just not a fine-grained one.

## G4 — an empty rule name passes the guard, and one rule is unnamed (found 2026-10-31, after the six cuts)

```
grep -rh '\*\*Rule:' guidelines/*.md | wc -l                        575
grep -rho '\*\*Rule: [^*]*' guidelines/*.md | sort -u | wc -l        574
tools/check-rule-routes.sh   "all 574 named rule(s) are reached"     exit 0
guidelines/BuildNative.md:88  - **Rule:** the guard is the case, not a log reader — one instrumented run, no UI: …
```

Two halves, and the second is the serious one:

1. **A rule in `BuildNative.md` (line 88) has no name** — the form was written but the name left blank, so the
   statement after the colon is a rule nobody can cite. It was named during `guideline-build-layers` and no check
   saw it.
2. **The guard does not count it — it skips it silently, which is worse.** Both extractors look for the literal
   `**Rule: ` **with a space** (`tools/check-doc-routes.sh:253` and `check-rule-routes.sh`'s `extract_rules`), so
   `**Rule:**` matches neither: the empty name is not counted as a rule, it is *not seen at all*. The consequence is
   that the completeness line is false — `grep -rh '\*\*Rule:' guidelines/*.md | wc -l` is **575** while
   `check-rule-routes.sh` announces "all **574** named rule(s) are reached" — and no refusal fires. The delta's
   requirement is *"A rule SHALL be written `**Rule: <name>** <statement>.`"*, so the empty case needs both a
   refusal and a line that is true. **The independent review graded this `BLOCKING:` and corrected this item's own
   mechanism sentence, which had said the guard "counts" the blank name.**

Recommended: one follow-up change owns both one-line fixes — the name in `BuildNative.md:88`, and the refusal in the
extraction (report an empty or whitespace-only name with its file and line, plus a self-test case and a mutation
record). The parent did not fix them in place because the guard's refusal belongs to this change, whose delta would
have to state it, and the unnamed rule belongs to a document this change does not own — writing into two finished
changes from a third is the pattern this set exists to stop.

**CLOSED in the same round, 2026-10-31.** The independent review graded this item `BLOCKING:` and corrected the
mechanism sentence above, and a corrections round then landed both fixes: the rule is **named** at
`guidelines/BuildNative.md:88`; both extractors report an empty or whitespace-only name with its file and line
(`UNNAMED` in `tools/check-doc-routes.sh`, the same refusal in `check-rule-routes.sh`'s `extract_rules`), with a
self-test case (`rules#aRuleWithNoNameIsReported` — 33 cases now), a mutation record (`rule-with-no-name`, red then
green) and a scenario in the delta. The two numbers now agree: `grep -rh '\*\*Rule:' guidelines/*.md | wc -l` is
**575** and the check announces **575**. The defect is recorded here rather than deleted, because the shape it took —
a guard whose completeness line was false by one with no refusal at all — is worth the next reader's minute.
