# Proposal — a change's own files name one tree, archived or not

## Why

**The root cause.** `loop_tree_identity` builds the digest from two listings — `git diff --name-only <base>` and
`git ls-files --others --exclude-standard` (`tools/loop/lib.sh:333-334`) — and an open change is gitignored
(`.gitignore:76-77`: `openspec/changes/*/`, with `!openspec/changes/archive/`), so a change being worked is in neither
listing: it contributes nothing to `diff=` while every path of its archived, committed self is named by the base's
diff. The archive's move therefore moves the digest of a change nobody corrected, on every item that archives — and
the run's own tail, which repairs a change directory (`design.md`, `tasks.md`) after the gate has named its tree,
moves it again. The reviewer's third item compares the gate's `diff=` with the `.identity` beside the round's diff, so
that comparison can never hold for a change whose own files were repaired between the two moments.

**Measured on the item's own probe** (`.pi/issue-loop/items/837/probe-identity.log`, 2026-11-09T23:10Z, `a`–`d` over one
throwaway tree):

```
a identity=head=c0ddb97cadaefe10cbf126ba7d19cfb41fb02b97 diff=e3b0c44298fc1c14
b identity=head=c0ddb97cadaefe10cbf126ba7d19cfb41fb02b97 diff=5439d8da57fe3501
c identity=head=c0ddb97cadaefe10cbf126ba7d19cfb41fb02b97 diff=e3b0c44298fc1c14
```

`a` is the tree as it stands with the change open, `b` one file of that change under `openspec/changes/archive/`, `c`
the same file under the unarchived name: `e3b0c44298fc1c14` is the first sixteen hex digits of sha256 over the empty
string, so the open change was in no path the derivation read, and the same content under the archive's name was in
all of them. The item's survey of the archive agrees
(`.pi/issue-loop/items/837/identity-survey.log`: `same=0 diff=3 missing=301`) — the gate's and the round's digests
differ for #721 (`gate=9553991d9dc2b555`, `review=19be6f7cdb629bba`), #820 and #833, and #721's round 2 recorded the
two commits as well: the gate measured `head=706cc2d2`, the reviewed diff was `head=333fce2f`.

**The red test.** `gate#aMovedChangeNamesTheSameTree` (`tools/loop/tests/loop-cases.sh:13980`), the smallest case that
exercises the defect, is red on the base (`4c400715`) — `evidence/red-on-HEAD-aMovedChangeNamesTheSameTree.log`,
2026-11-10T08:24:52Z, `1 of 1 cases failed`:

```
gate#aMovedChangeNamesTheSameTree|   a change being worked is in the tree the gate names, evidence or not: e3b0c44298fc1c14
FAIL gate#aMovedChangeNamesTheSameTree
```

**The owning requirement and the owning guideline section.** The requirement is
`openspec/specs/agent-issue-loop/spec.md`, "A child pays for the full gate at most the recorded number of times" —
the digest paragraph (`:3802-3808`) and the scenario "The gate and the round name one tree, over one path set"
(`:3831-3838`). The prose that states the rule is `guidelines/Backlog.recipes.md`:722, "The gate and its suite", the
paragraph that begins "The record names the tree the run measured".

**The decision this implements, and what it leaves open.** The owner recorded, 2026-11-10T05:46:01Z, option (1):
derive the digest over the change directory's own files under one name, the archive's date prefix normalised, so the
ordinary flow's archive move no longer moves it. Its two measured requirements are carried: the change family's
enumeration re-applies `LOOP_REVIEW_EXCLUDE_PATHSPEC` (the gate writes its own `evidence/` after naming its tree), and
the new case places its probe under `openspec/changes/archive/` as well as under the unarchived name. What the
decision leaves open is deliberate: a **rewrite** of a non-evidence file of the change after the gate still moves the
digest, because that is the #721 shape item 3 exists to see; this change makes the archive's *rename* invisible and
keeps the *edit* visible, and it pays no second gate.

## Blast radius

- **`tools/loop/lib.sh`** — one helper (`loop_change_path`, `:296`) and one enumeration inside
  `loop_tree_identity` (`:313-348`). `loop_change_identity` (`:356`) is unchanged, so both call sites
  (`tools/loop/gate.sh:209`, `tools/loop/run.sh:1307`) still take one derivation and neither holds a path set of its
  own.
- **`tools/loop/loop.conf`** — one key, `LOOP_CHANGE_ARCHIVE_PREFIX` (`:486`), beside `LOOP_CHANGE_SOURCE_DIR`: the
  archive's date shape is a project fact, and `tools/check-portability.sh` forbids a path of the project inside the
  engine.
- **`tools/loop/tests/loop-cases.sh`** — one case added (`gate#aMovedChangeNamesTheSameTree`) and its two registration
  sites; the case above it is unchanged.
- **`tools/loop/tests/loop-mutations.sh`** — two records (`262.5`, `262.6`), one per half of the rule.
- **`guidelines/Backlog.recipes.md`:722** — the paragraph that states the derivation. No other capability, key or
  case moves.

surface: 1 helper and 1 derivation extended in `tools/loop/lib.sh` (`:296`, `:313-348`), 2 call sites unchanged, 1
case added (0 rewritten) with its 2 registration sites, 2 mutation records added, 1 profile key added, 1 guideline
paragraph rewritten at `:722`, 1 requirement modified in `openspec/specs/agent-issue-loop/spec.md` —
`tools/look.sh 'loop_change_identity' --path tools` names 11 matches in 5 files (the definition, the two call sites, 5
in `tools/loop/tests/loop-cases.sh`, 1 mutation record), `--path guidelines` the one paragraph, and
`tools/look.sh 'loop_tree_identity' --path tools` 4 matches in 2 (`evidence/surface-counts.log`; this change's own
directory is left out of the count — a text that counted itself would not be the rule's surface, and the archived
change that introduced the derivation is neither). The digest costs what it did plus one listing of the family's
own files: measured on this working tree with its 18 open changes (the item's own among them, before this change's
own `design.md` and `tasks.md` were written), `diff=f4e46f5c65f3829a` in **290 ms** for the 83 files the evidence
exclusion leaves in (`evidence/probe-cost.log`), so a reader who re-runs it today reads a larger count and a different
digest and the same order of cost.
