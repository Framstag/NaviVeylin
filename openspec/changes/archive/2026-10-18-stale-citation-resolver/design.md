# Design

## Context

See `proposal.md` — Why. This document records only what shapes the resolver.

The measured shape of the problem (2026-10-18, the 72-item authorised set, the dry run's `excluded[]`):

```
44  the cited name exists — the path is written without its prefix, as a bare class-file name, or abbreviated
 3  submodule paths and an SDK header (Tests/src/SearchScopeTest.cpp, jni.h) — nothing the superproject holds
 1  a generated artefact (RELEASE-NOTES.md, written by tools/gen-feature-list.sh:838)
 1  a prose fragment (it.sh)
 1  a genuine candidate (#73: no OverrunWindow* anywhere)
49  total, of which the sensor is right about at most one
```

So the change is two things at once: make the *definition* of a citation true, and make the *repair* of one
automatic. Without the second, the first is a bug fix that leaves 165 items to a human whenever their prose
drifts.

## Goals / Non-Goals

**Goals**

- One definition of a citation, in one file, asked by both the pre-screen and the resolver.
- A repair that is a **fact**, not a judgement: the target moves, and the history or the symbol says where.
- A refusal that a person can see in the tracker, and a residue that is small, named and ranked.
- Automatic: it runs on the checks' schedule, bounded, idempotent, and safe to run twice.

**Non-Goals**

- Not "the claim is obsolete" — that is a person's decision, and the tooling's `--not-planned` closure (with
  the reopen path refusing to reverse it) is where it belongs.
- Not a prose editor: the resolver rewrites a citation token and nothing else.
- Not a new status vocabulary value; not a change to any existing requirement.
- Not a second detector: the pre-screen keeps its role and loses its own extraction code.

## Decisions

### D1 — The classifier lives in `tools/issues/lib.sh`, and both callers ask it

`tools/loop/lib.sh` already sources `tools/issues/lib.sh`, so the pre-screen and the resolver can share one
function without a new file, a new process, or a new dependency.

| alternative | why rejected |
|---|---|
| keep the extraction in `prescreen.sh` and copy it into the resolver | that is the drift that *is* this bug: two regexes, two notions of "a path", and a case set that can only see one of them |
| a Java class in `tools/issues/core` | the pre-screen is shell and would pay a JVM start per candidate for a pure string-and-git function |
| a standalone script invoked per item | a fork per item for a string function, and a second place to keep the rules |

### D2 — Extraction: a path, not a mention

A token is a citation only when all of these hold:

```
boundary     not preceded by [A-Za-z0-9_.-] and not followed by [A-Za-z0-9_]
extension    a source extension the project recognises (kt kts java cpp cc h hpp md sh xml json yaml yml
             gradle py toml properties pro)
path-shaped  a header extension (h, hpp, cc) requires a path separator in the token — a bare `jni.h`,
             `dagger.h` or the tail of `-Dorg.gradle.java.home` is not a citation
```

The third rule is the one that pays: `Dorg.gradle.java.h` (measured, #91) and `jni.h` (measured, #20) are both
header-extension tokens without a separator, and both are mentions rather than paths. A bare *source* name
(`SearchScreen.kt`) stays a citation and is resolved by D3 — it is a real file the item is talking about.

### D3 — Resolution: five classes, one of which is the verdict

```
exact      <root>/<token> exists                                    → not stale
suffix     exactly one tracked path ends with the token            → not stale (canonicalisable)
submodule  it resolves inside a submodule (i.e. under a path with a .git file at its root)
                                                                   → not stale
generated  it names an artefact the project produces, not tracks   → not stale
not-a-path the token is not a citation at all (D2)                 → no verdict
absent     no file of that name anywhere, and not generated        → stale
```

`suffix` is deliberately a *pass*: an item citing `SearchScreen.kt` is workable. Canonicalising it (rewriting
the token to the full tracked path) is a courtesy that makes the record exact and the next run cheap, and it is
the resolver's job, not the verdict's.

| alternative | why rejected |
|---|---|
| resolve a bare name by searching the **filesystem** (`find`) | it sees untracked and generated files, so the verdict would depend on the machine's build state; `git ls-files` is the record |
| require citations to be root-relative, and refuse the rest | it refuses the majority of a real backlog (44 of 49) for a writing style, which is the bug being fixed |
| treat `suffix` as stale so the item gets repaired | it parks workable items behind a repair step — the opposite of the goal |

### D4 — The repair ladder, and what "mechanical" means

```
1  exact                                  nothing to do
2  suffix, unambiguous                    rewrite the token to the full tracked path
3  absent, renamed in history             git log --follow --diff-filter=DR --name-status → the successor
4  absent, symbol survives once           git grep -l --fixed-strings <symbol> → the single declaring file
5  anything else                          park it (D5)
```

Every step is a fact the repository states. Nothing is inferred from proximity, similarity or a guess about
intent — an ambiguous match (2+ candidates) is **reported and not repaired**, because picking one would put a
wrong path into a record that is meant to be evidence.

### D5 — Unrepairable means parked, with the attempts recorded

`status/on-hold` plus one comment listing what the ladder tried, and the candidates it found.

| alternative | why rejected |
|---|---|
| leave it `agent/eligible` and let the pre-screen exclude it each run | that is today's state: the label says authorised, the queue says no, and the reason lives in a machine-local file (#163) |
| withdraw `agent/eligible` | the authorisation label is a person's, and it is the one thing an automated actor may not write (§5); withdrawing it would also hide the item without saying why |
| add `status/stale` / `status/obsolete` | `on-hold` already means "parked", `retired` already means "kept only so a citation resolves", and a third synonym is a second home for one fact — the vocabulary is closed and lives in `tools/tooling.json` |
| close it as `--not-planned` | that is a **person's** decision (and the reopen path refuses to reverse it); the resolver's job is to make that decision cheap, not to take it |

### D6 — It runs in the checks' pipeline; the residue is a row

One step in `.github/workflows/sensors.yml` (the pipeline that already holds issue-write, is pinned to main,
is guarded against overlapping runs, and sits off the change-validation path), and one **row** in
`tools/sensors/sensors.json` that reports the **count of unresolved citations** as its measurement — so the
pile is visible in the sensors' own report and a growth becomes an issue the existing machinery files.

The repair itself is not a row: a row's contract is "a condition files, comments on or closes *the issue it
owns*" (`backlog-sensors`), and a citation repair edits *other* items. Bending that contract would put "a check
may edit any item" into the spec, which is the opposite of what the ownership scope exists for.

| alternative | why rejected |
|---|---|
| a CI step in `build.yml` | that path must not write to the tracker; the separate pipeline exists exactly for the writes |
| a phase of the loop | the loop sees only authorised items, and must not spend budget repairing what it is about to work; also, a repair is useful *before* authorisation, not after |
| a skill a person runs | the request is that this happens without a person; a skill stays as the on-demand entry |

### D7 — Bounds, and why no repair marker is needed

`--dry-run` is the default, `--max-items` bounds one run (default 20, the metadata pass's number), and
idempotence needs no extra bookkeeping: **a repair is verified by re-classifying**. A canonical citation
classifies as `exact`, so the next run finds nothing to do and posts nothing. The comment is the record, and it
is posted once per repair, not once per run.

The resolver refuses a body it would push past the profile's bounds (`maxBodyBytes`, `maxLines`), and it writes
at most one comment per item per run.

### D8 — A new writer, named in the ownership table

`guidelines/Backlog.md` §5 gains the resolver: it may rewrite a citation token in a body, park an item
(`status/on-hold`) and post one comment. It may **not** write `agent/*`, `class/*`, `category/*`, `prio/*`,
close or reopen anything, or file an item of its own.

### D9 — The change's acceptance test is the measurement that opened it

The 49 recorded `stale` verdicts, re-decided by the resolver, must come out as **44 `suffix`, 4 not-a-path
(2 submodule, 1 generated, 1 fragment), ≤1 quarantine** — and after the change the same authorised set must
queue 72 minus the genuine quarantines, not 23. A resolver that "passes its cases" but leaves the queue at 23
has not done the job; the number is the test.

## Risks / Trade-offs

- **A wrong canonicalisation** (a unique suffix that is the wrong file) → a suffix is a *whole* path tail, and
  ambiguity is reported rather than repaired; the comment names old → new so a person can reverse it.
- **`git log --follow` over many items is slow** → the ladder only walks history for an `absent` citation, and
  the suffix test costs one `git ls-files` index per run, built once.
- **The resolver becomes a second home for the rule** → one function (D1), and the pre-screen's own case set
  exercises the classifier through the pre-screen's path.
- **The scheduled step silently stops working** (a pipeline change) → the residue row is the alarm: a count
  that climbs while repairs should be happening is the check's own verdict.
- **A repair hides a real staleness** → the class is decided by a fact (`exact`/`suffix`/`submodule`), and the
  `absent` class is the only one that parks; a person can always read the item's citation comments.

## Migration Plan

1. the classifier (D1, D2, D3) with its cases — this is also #164's fix;
2. the pre-screen asks it, and its refusal becomes visible (closes #163);
3. the ladder (D4), the quarantine (D5), the bounds (D7);
4. the pipeline step and the residue row (D6);
5. the ownership row, the routes, the classification line and the skill;
6. falsification (one mutation per invariant) and the acceptance measurement (D9).

Rollback: revert the resolver, its self-test, its CI step, the pipeline step and the row; restore the
pre-screen's extraction. Repairs already made stay — they are canonical paths, and the comments are the record.

## Open Questions

Deferred, none of which changes the approach:

- whether canonicalisation should stay on by default or become a flag (it is a courtesy repair; a project with
  a strong prose convention may not want its bodies edited for style);
- whether the residue row should carry a threshold and a cadence of its own, or stay a pure count (the row's
  bound is the registry's business, and a bound needs measurements first);
- whether a quarantine should also annotate the item's title, so a person scanning the list sees it without
  opening the item.
