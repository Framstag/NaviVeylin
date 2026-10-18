# Proposal

## Why

An item the loop will not work is excluded **silently**. Measured 2026-10-18, on this tracker:

- 72 open, non-feature items were authorised (`agent/eligible`);
- the pre-screen queued **23** and excluded **49 as `stale`** — two thirds of the authorised set;
- of those 49, **44 are false positives**: the cited name does exist, just written without its prefix
  (`SearchScreen.kt`, `MapRendering.md`), as a bare class-file name, or abbreviated
  (`app/src/test/.../XTest.kt`);
- 4 more are not staleness either — two cite **submodule** paths (`Tests/src/SearchScopeTest.cpp`), one cites
  an **SDK header** (`jni.h`, in no tree at all), one a **generated** artefact (`RELEASE-NOTES.md`, written by
  `tools/gen-feature-list.sh:838`), one is a **prose fragment** (`it.sh`);
- **1** (`#73`) is a genuine candidate.

Two defects are behind that, already filed: the extraction is wrong (**#164**) and the verdict is stored
nowhere a person can read it (**#163**). Fixing them by hand is not the answer — 165 items, and the
false-positive class recurs with every imported record, so the repair would be a standing human chore.

What is missing is the capability, not a bug fix: **a citation resolver** — one definition of what a citation
is, a mechanical ladder that repairs what can be repaired, a quarantine for what cannot, and a scheduled run
so it happens without a person. Its goal is narrow and worth stating: after it, authorising a batch puts the
batch in the queue, and the residue that needs a human decision is small, named and ranked.

## What Changes

- **One classifier, one home.** The rule "what counts as a citation" moves into a single place used by both
  the loop's pre-screen and the resolver, so the two can no longer disagree about the same body. It extracts
  only genuine path tokens (token boundaries, known source extensions, no bare identifier), and it resolves
  each one to a class: `exact`, `suffix` (one tracked path ends with it), `submodule`, `generated`,
  `not-a-path`, `absent`.
- **A mechanical repair ladder**, in order, each step a fact rather than a judgement: exact → nothing to do;
  suffix → canonicalise the citation to the full tracked path; rename → the successor from
  `git log --follow --diff-filter=DR`; symbol → the single surviving file that declares it (`git grep -l`).
  Each repair rewrites **only the citation token**, preserves the `fingerprint`/`nvid` markers verbatim, and
  posts one comment naming old → new.
- **Quarantine instead of silence.** An `absent` citation with no mechanical successor parks the item
  (`status/on-hold`) and posts one comment with the ladder's attempts, so the item leaves the eligible queue
  *visibly* and one human decision can cover many — rather than staying `agent/eligible` and being excluded on
  every run with the reason living in a machine-local file.
- **A scheduled run**, in the checks' own pipeline (issue-write, pinned, least privilege), bounded per run
  (`--max-items`), dry run by default, idempotent. A declared check row reports the **residue** — the count of
  unresolved citations — so a growing pile is a measurement rather than a surprise.
- **The pre-screen's verdict becomes true**, and it stops being the only detector: it asks the classifier, and
  its refusal is visible in the tracker.

**Explicitly not in this change**: closing an item (a `--not-planned` decision is a person's, and the reopen
path refuses to reverse it), deciding that a *claim* is obsolete, rewording prose around a citation, and any
write to `agent/*`.

## Capabilities

### New Capabilities

- `stale-citation-resolution`: one definition of a citation; a true staleness verdict; a mechanical repair
  ladder that rewrites only the citation; quarantine with the attempts recorded; a bounded, idempotent,
  scheduled run; and a write surface named in the ownership table.

### Modified Capabilities

None. The pre-screen's contract ("a cited path that no longer resolves is not worked") does not change; it
becomes true. The new check row is data the `backlog-sensors` spec already permits ("a check is a declared
row"). No requirement is removed or redefined.

## Impact

New:
- `tools/issues/resolve-stale.sh` — the resolver (ladder, quarantine, comments, bounds, dry run)
- `tools/resolve-stale-selftest.sh` + `tools/resolve-stale-cases.sh` — the gate, offline, throwaway trackers
- the classifier's home in `tools/issues/lib.sh` (one function, two callers)
- `tools/portability-…` untouched; a new mutation set `tools/resolve-stale-mutations.json` for the
  falsification group
- `.pi/skills/cleanup-todo` gains the staleness recipe (it already owns tracker hygiene)

Modified:
- `tools/loop/prescreen.sh` — asks the classifier instead of extracting tokens itself
- `tools/sensors/sensors.json` — one row (`stale-citations`) reporting the residue
- `.github/workflows/sensors.yml` — the resolver step, beside the rows
- `.github/workflows/build.yml` — one `Check stale citation resolver` step (the self-test, token-free)
- `guidelines/Backlog.md` — §5 (a new writer in the ownership table), §6/§7 (the discovery step is the
  classifier's), §9 (the residue row), §10 (unchanged)
- `AGENTS.md` — the route for "a stale citation in an item" and the change's skills
- `tools/feature-list/specs.json` — the classification line for the new capability

Not affected: no Kotlin/Java/C++ under `app` / `auto` / `core` / `osmscout-client-java` / `buildSrc`; no
manifest; no Gradle configuration; no native or JNI boundary; no release path. The tracker is written only
through the tooling's own paths (`update.sh`, `close.sh` is *not* used).

Scope: agent-harness tooling only — variant-independent. Guidelines referenced: `Build.md` §1 (skills), §2
(the iteration protocol and its scope block), §6 (test constraints). No `UI.md`, `MapRendering.md`,
`Design.md`, `Regulatory.md` or `Logging.md` section is affected.

**Additive.** **Rollback**: revert the resolver, its self-test and its CI step, the pipeline step and the
registry row; restore the pre-screen's own extraction (one function); drop the `Backlog.md` §5 row. The
tracker keeps whatever it repaired — the comments are the record, and a repair is a canonical path, not a
deletion.

**Verification.** The classifier's cases (a fragment, a submodule path, a generated artefact, a bare
basename, an ellipsis, an ambiguity); the ladder's cases (suffix, rename, symbol, no-op); the marker-preserving
and idempotence cases; the quarantine cases; the bound; the scheduled run for real against a throwaway
tracker; and **the acceptance measurement on this tracker**: the 49 recorded `stale` verdicts, re-decided by
the resolver, must come out as the 44 suffix class + 4 non-citations + **at most 1 quarantine** — the numbers
in "Why" are the change's own acceptance test.
