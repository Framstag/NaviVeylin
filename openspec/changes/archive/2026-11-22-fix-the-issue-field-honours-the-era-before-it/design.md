# Design — the era read from the change's own file

## Root cause, restated

The fifth class of `tools/check-openspec-hygiene.sh` asks one question — "does this open change say which item it
answers?" — and reads two answers: the `x-issue:` field (`:125`) and a `fix-issue-<N>-` directory name
(`:129-130`). A directory written before the field existed and named for its claim answers neither, so `:135`
refuses it, and the trunk stays red until that directory's author writes a declaration only they can choose. Five
of this tree's open directories are in exactly that state (`created: 2026-11-14`, no field, no number in the name —
`evidence/check-before.log`), and the item's own tree carried 26.

## D1 — where the era is read from

**Chosen: the `created:` line of the change's own `.openspec.yaml`** (`:126`), compared against one constant
(`:45-48`, `2026-11-19` — the day the rule landed, `c7de199f`), and reported instead of refused when it is earlier
(`:131-133`). The report prints the date it read (`:155-157`), so the claim is checkable by the reader who meets it.

The alternatives the evidence eliminates:

- **Write `x-issue: none` into the five directories.** Impossible: the field is a declaration, not a blank — the
  shipped requirement says so (`openspec/specs/documentation-ownership/spec.md:814-815`, "`none` … is a declaration
  rather than an absence"), and issue #999 states that a blanket `none` "would be a false declaration on the changes
  that do answer an item". A false declaration buys green by making the class's own finding wrong.
- **An allowlist of the five directory names.** Impossible: every one of the five already states `created:
  2026-11-14`, so a name list is the same fact declared a second time, in a second place, needing an edit per
  pre-era directory found later — and a tree the check cannot describe without a list that drifts from the tree.
- **Derive the date from git** (the first commit that touched the directory). Impossible: the case file runs the
  checker over throwaway roots under `mktemp -d` that are not repositories at all
  (`tools/tests/check-openspec-hygiene-selftest.sh:43`, the root the case file makes, and `:127`, the one place it
  hands that root to the checker), so a check that needs git history fails in the place
  its own cases run it.
- **Report a pre-era directory only once it has been untouched for N days.** Impossible: it needs a clock the cases
  would have to fake, and the rule is about when the directory was written, not about how long it has stood.

**Risk, and why it stays.** A change written after the boundary can state a backdated `created:` and escape the
refusal. That is accepted and bounded rather than closed: the era's line prints the date it read, so the declaration
is visible to whoever reads the report, and a false `created:` is the same class of false declaration as a false
`x-issue: none` — which no check of this file can catch either. The alternative that would close it needs git, and
git is eliminated above.

## D2 — what a pre-era directory is reported as

**Chosen: one plain line, naming the directory and the date** (`:156`), beside the pass's existing verdict line. Not
silence: the summary line "Every open change names its issue: …" would then be false, and a change with no
declaration would vanish from the report its author reads. Not a `::warning` annotation: `::error` is the only
annotation this file emits, and every other pass of it reports on a plain line — the pass that finds nothing prints
its verdict, and the era is a report of five things found, so it prints beside that verdict. The line is not an
`::error`, so a consumer that greps for findings by their annotation cannot mistake a reported era for a refusal.

## D3 — where the boundary lives

**Chosen: a constant in the check** (`:48`). Not `tools/loop/loop.conf`: the check is run by CI
(`.github/workflows/build.yml:441`) and by its own case file over throwaway roots that carry no project profile, so a
value from the profile would be missing exactly where the cases run it. Not read from the archived change that
carried the rule: that is a git query, eliminated in D1.

## D4 — the two fixtures of `#aChangeNamingNoIssueIsRefused`

**Chosen: move both to `created: 2026-11-20`** (the refusal arm and the `fix-issue-<N>-` arm — `:357`, `:377`).
Leaving them at `2026-01-01` would have them pass for the era's reason rather than the field's: the refusal arm would
go green without the refusal happening, and the name arm would stop distinguishing the name from the era. The
whole-suite base reading (`evidence/red-on-base.xml`, `1 of 12 cases failed`) is what says the two dates moved
without moving that case's verdict.

## Blast radius

- `.github/workflows/build.yml:439-442` — the CI step runs the check and its case file; both exit 0 after this
  change, where the check exits 1 on a trunk whose open set predates the field.
- `tools/loop/prescreen.sh:301-302` — reads the check's finding for a collision and filters
  `^::error::… name one issue (#…)`; the era's line is not an `::error` and begins with no annotation, so it cannot
  reach that filter.
- The 19 open change directories of this worktree — verdicts unchanged except the five that gain a report line
  instead of a refusal. The one directory carrying no `.openspec.yaml` at all
  (`openspec/changes/fix-the-checkpoint-splits-the-items-chain`) is still refused: it states no `created:`, so it
  belongs to no era, which is D1's own rule and not a gap in it. It is a concurrent session's open change, ignored by
  `.gitignore:82`, and it is absent from the committed tree, so the CI reading does not see it
  (`evidence/check.log`, `evidence/check-tracked-open.log`).
- No compiled file, no module, no manifest: no build is owed (the two commands the gate plans are the check and its
  case file).

## Threading and lifecycle

Not applicable, and stated so nothing is silently skipped: the checker is one POSIX-shell pass over the tree, the
case file runs it once per case in series, and no dispatcher, lifecycle or state is involved.

## Rollback

`git revert` of the one commit restores the unconditional refusal, and the case this change adds is restored with
it: nothing outside the two files below and the capability delta was written.

## Open decisions

none
