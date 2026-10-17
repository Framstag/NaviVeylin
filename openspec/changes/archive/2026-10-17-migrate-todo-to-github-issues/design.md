# Design

## Context

See `proposal.md` — Why. This document records only what shapes the flip.

The tooling this change consumes, all fixed by `github-issue-tooling`:

```
tools/issues/import.sh        --dry-run default, --apply to create; idempotent via <!-- nvid: N -->
tools/issues/import/todo-md.sh  the SOURCE ADAPTER: this project's grammar + the two renames
tools/tooling.json            the profile: value sets, bounds, bands; the renames are the adapter's
tools/issues/update.sh        label/status writes, refuses reserved labels
tools/issues/create.sh        files a new finding, fingerprint-deduped
```

The reference census that decides the work, measured 2026-10-16 by reading every occurrence rather
than counting them:

| kind | what it looks like | where | count | this change |
|---|---|---|---|---|
| **citation** | `TODO.md §113`, `tracked in TODO.md §153`, `closed §121`, `§40.46` | 292 archived artifacts, 4 live specs, 7 guidelines, `app/build.gradle.kts` (3), `tools/*.sh\|py` (4), `build.yml` (2) | 300+ | **untouched** — history and provenance |
| **instruction** | `documented in a TODO.md`, `file the residual in \`TODO.md\``, `TODO.md bookkeeping`, `the TODO.md entry moved to fixed-by`, "the live backlog is…" | `openspec/config.yaml:21,92,129` · `AGENTS.md:43,156` · `guidelines/Build.md:107,417,880` · `guidelines/UI.md:1003` · the 12 skills | ~20 sites | **rewired** |

That distinction is the change's central decision (D2): it is why the diff is small, why the archive
stays honest, and why **no compiled file is touched at all** — all three `app/build.gradle.kts`
occurrences are citations, so no Gradle build or suite is required (`guidelines/Build.md` §2, scope
block).

One more measured fact, **corrected in the run (2026-10-17)**: `TODO.md` has **137** sections and **137**
metadata lines — this document said 136, which was measured before one more section was filed — and its
`status` histogram is `open 75 · open-device 21 · fixed-by 17 · on-hold 14 · in-flight 8 · retired 1 ·
unverified 1` (`import-evidence.md` §1 records the command and the diff). `import.sh`'s recorded dry run
previews 137 issues, and the live run's counts are checked against those numbers, not against each other.

## Goals / Non-Goals

**Goals**

- The tracker becomes the only live backlog, and `§N` keeps resolving after the file is gone.
- The deletion happens last, so no change of this series ever leaves a skill reading a deleted file.
- The proof that nothing live still points at the file is a **mechanical check**, not a review.

**Non-Goals**

- Not rewriting history: the 292 archived artifacts and the 4 provenance specs keep their text.
- Not the loop's mechanism (`agent-issue-loop`), not the decision queue, not scheduled runs.
- Not a two-way sync: the import is a one-way copy. Editing a body in GitHub does not edit anything
  local, and `TODO.md` is never regenerated.
- Not a `TODO.md` stub or shim. The owner's decision is deletion, and a stub that says "moved" is a
  file a reader must still open to learn nothing (D4).
- Not sandbox-aware. The import, `list.sh --id` and the reference check are host commands run by the
authenticated human, and they must keep working with no agent sandbox present — a future sandboxed
child never holds the tracker credential (`agent-issue-loop` D7, D15).

## Decisions

### D1 — The flip order, with the deletion last

```
1  import.sh --apply           137 issues; counts checked against the dry run and TODO.md
2  rewire the ~20 instruction sites  config.yaml, AGENTS.md, 2 guideline lines, 12 skills
3  resolve every §N                  list.sh --id N over the nvid marker
4  verify                            the reference check (D11) green in its pre-deletion form
5  delete TODO.md                    last action of the change
6  verify again                      the same check, now with the file absent
```

| alternative | why rejected |
|---|---|
| delete `TODO.md` at import time (steps 1 and 5 together) | between the import and step 2, twelve skills and three `config.yaml` rules instruct a reader to edit a deleted file; the window is short but the repository would be knowingly broken, which is the property the whole series avoids. |
| keep `TODO.md` frozen as the end state | the owner's decision; and a frozen copy still costs a reader's context to discover it is frozen, which is the cost this migration removes. |
| never delete, keep both as sources of truth | two backlogs, no rule about which wins, and the drift between them becomes the next `cleanup-todo` problem. |

### D2 — Rewire instructions, preserve citations

The rule: a line is an **instruction** if it tells a reader or a process to *use* `TODO.md` as a
workflow step; it is a **citation** if it names a `§N` as provenance for a claim. Instructions are
rewired to the tracker's vocabulary; citations stay byte-identical.

| alternative | why rejected |
|---|---|
| rewrite every occurrence, including the archive | 292 archived artifacts are history: a change's artifacts record what was known when it landed, and editing them to point at a tracker that did not exist then makes the record false. It also puts a ~300-site diff behind a routing decision. |
| rewrite only the guidelines, leave `config.yaml` and `AGENTS.md` | they are the two places a session is actually routed from, so leaving them stale leaves the highest-traffic instructions pointing at a deleted file. |
| rewrite nothing but delete the file anyway | every instruction becomes a dead end, and the reader's recourse is a `git log` for a file that no longer exists. |

### D3 — `§N` resolves through the issue body marker

`list.sh --id N` matches the `<!-- nvid: N -->` marker and prints the issue, so
`TODO.md §129` and `§40.46` still find their entry. The marker is the only source of truth; a
short-lived `.pi/` cache is advisory.

| alternative | why rejected |
|---|---|
| a committed `§N → issue number` map file | a second source of truth, offline by construction, and wrong the moment an issue is filed by hand; `github-issue-tooling` D4 rejected the same shape for fingerprints. |
| a label per id (`id/129`) | 137 labels in the namespace, and the marker already carries the identity; `github-issue-tooling` D4. |
| GitHub text search for `§129` | a server-side ranking decides the match; the exact rule belongs in a tested script. |
| accept that citations dangle | 300+ of them; the migration's own justification is that `§N` keeps resolving. |

### D4 — The pointer lives where a reader looks, not in a stub

The replacement for "what is open, and what to do next" is the `AGENTS.md` Documentation Map row,
which names the tracker and the two queries; no `TODO.md` file remains.

| alternative | why rejected |
|---|---|
| leave a one-line `TODO.md` pointing at the tracker | it survives as a file that every `grep TODO.md` and every stale habit finds, and it defeats the deletion it claims to announce. |
| `docs/BACKLOG.md` | a third place to look, with no rule about which one is current. |
| put it in `README.md` | the README is for the app's users; the backlog is an agent-and-owner concern, and `AGENTS.md` is where the routing table already lives. |

### D5 — The cluster index becomes saved searches, not a file

`TODO.md`'s `**Clusters**` block (category → `§ids`) is replaced by `list.sh` filters and two saved
searches (open bugs; open improvements). Nothing regenerates an index file.

| alternative | why rejected |
|---|---|
| generate an index file, committed and regenerated | that is the same maintained list with a script in front of it, and the measured problem is that it drifted (`§190`/`§202` sat outside the vocabulary while the index still named them). |
| GitHub Projects views | its own API surface, its own drift, and concept doc §4 step 4 is where a decision-oriented view belongs. |
| nothing | the triage need is real; `triage-todo` ranks the same set, so the selectors must exist even if no file does. |

### D6 — The import is one pass, and its idempotence is the checkpoint

`import.sh --apply` creates with the throttling it already has. Because a re-run skips an existing
`nvid`, the script is its own resume: a failure halfway is continued by re-running, not by a checkpoint
file, and after `TODO.md` is deleted the script has nothing to read.

| alternative | why rejected |
|---|---|
| dry-run only | the change's purpose is the move; `github-issue-tooling` already recorded the dry run. |
| import into a scratch repository first | the dry run plus the marker's idempotence already prove the mapping; the remaining risk (a malformed body, a rate limit) is not a tracker-identity risk, and a scratch tracker would not reproduce label permissions anyway. |
| a checkpoint file recording the last imported `§N` | a third state to keep consistent with the marker, which already says it. |

Issue numbers are recorded in `import-evidence.md` (`§N → #number`), so a `§N` can be resolved from a
document even without the API.

### D7 — `cleanup-todo` becomes tracker hygiene

Its subject changes from a file's structure to the tracker's integrity: label/status repair against the
closed vocabularies, duplicate detection (two issues whose fingerprints match), `nvid` collisions, and
entries whose premise the tree has moved past. Its evidence rules and its "never delete without
evidence" stance carry over unchanged.

| alternative | why rejected |
|---|---|
| retire the skill | the drift it fights is not a file-format property: labels can be wrong, duplicates can exist, and its verification discipline is exactly what the tracker needs. |
| fold it into `triage-todo` | triage is read-only by contract, and that separation is what makes its ranking trustworthy. |
| keep it file-based | the file is deleted. |

### D8 — `process-failure-log` files its guardrails as issues

The log's extracted actions now become issues through `create.sh` (fingerprint-deduped, `class` and
`category` from the same mapping the import uses) instead of a new `TODO.md` section.

| alternative | why rejected |
|---|---|
| keep appending a `§40`-style section to a local file | that is a second backlog, which is the thing this migration removes; it would also immediately be unread by every rewired skill. |
| write the actions straight into `guidelines/*` and skip the tracker | the actions often need verification or a decision, which is what an issue carries; the guideline edit happens when the action lands. |
| drop the action items and keep only the log | loses the actionable half of the pass, which is its point. |

### D9 — `config.yaml` names the tracker, keeping the rules enforceable

Three sites change: the `context:` sentence about the loop's bookkeeping, the `apply` guidance about
unrelated problems, and the `archive` guidance about the entry's status. Each keeps its obligation and
swaps the mechanism (`the entry moved to fixed-by` → the issue's `status/fixed-by` label and closure).

| alternative | why rejected |
|---|---|
| delete the rules | the obligations are the sample of what the repository's gates enforce; removing them weakens the harness for a routing change. |
| generalise to "the backlog" without naming the tracker | an unenforceable instruction: a reader cannot act on "the backlog", and the CI hygiene check's own vocabulary would drift. |
| leave the three sites stale | they are the highest-traffic instructions in the project — `apply` and `archive` guidance is loaded into every session that writes. |

### D10 — Issues filed after the migration carry no `§N`

`§N` identifies an **imported** entry. A new finding gets an issue number, its fingerprint, and
`source/agent` or `source/human`.

| alternative | why rejected |
|---|---|
| continue the `§`-numbering for new issues | measured in `.pi/skills/fix-loop/REFERENCE.md`: two parallel writers allocated `§155–§164` and `§154–§158` and `cleanup-todo` had to renumber five. The tracker's own number has no such collision, and the tracker is now the only writer. |
| require every issue to carry a `§N` | the import is one-way and finite; a `§`-formatted id would have to be minted by hand from the start, which is the coordination this migration ends. |

### D11 — The proof is a reference check, and it distinguishes citations from instructions

`tools/check-todo-references.sh`:

```
FAIL if any live file (outside the allowlist) has a `TODO.md` line that carries no `§`
     -> i.e. an instruction survived the rewiring
FAIL if any cited `§N` does not resolve through `list.sh --id N`
PASS, listing the allowlist it excluded: openspec/changes/archive/**, the 4 provenance specs
```

The allowlist is explicit and printed, so an exclusion is visible rather than silent. The check runs in
the self-test and as one CI step, and it stays after the deletion — the second half (every `§N`
resolves) is the standing guarantee that a citation written next month still finds its entry.

| alternative | why rejected |
|---|---|
| assert the token `TODO.md` appears nowhere in the tree | false by construction: the archive and the specs are allowed to keep it, and asserting it globally would have to be weakened the first time someone cites history again. |
| assert no live file mentions it, allowlisting the archive and specs | the same check, but it flags 300+ legitimate citations and trains a reader to ignore it. |
| rely on review | the whole point is that this class of dangling reference is mechanical; the migration's own justification is `§N` resolution. |

### D12 — `app/build.gradle.kts`, the tools and the CI workflow are not touched

Verified by reading all three build-file occurrences (`:291`, `:401`, `:531`) and the tool and workflow
ones: every one is a `§N` citation. No compiled file, no build script and no workflow is edited, so the
change runs no Gradle build and no suite — its gate is the reference check plus the structural checks.

| alternative | why rejected |
|---|---|
| rewrite the citations for consistency | needless diff in a compiled-file-adjacent file, and it would force a Gradle run for a documentation edit. |
| touch them to "clean up" | a comment that names a `§N` keeps its meaning; the number resolves. |

### D14 — This is the one project-specific act of the series

Everything else in the five changes is a portable engine; this change is what a project does **once**,
and it must not push this project's facts into the engines:

```
project-specific, stays here          this project's TODO.md grammar   -> tools/issues/import/todo-md.sh
                                      the two category renames        -> the adapter, not the profile's logic
                                      the 12 skills it rewires        -> this project's skill set
                                      the 292 archived artifacts      -> untouched history
portable, must stay engine-side       the reconciler, the gate, the taxonomy reader, the checks
```

The renames are a fact about `TODO.md`, so they belong to the adapter; the *value sets* they map into
belong to `tools/tooling.json`. A change that put either into an engine would make the second project's
import a code edit instead of a profile edit.

| alternative | why rejected |
|---|---|
| put the renames in the profile as generic `renames` | a second project has no `renames`; the mapping is this backlog's shape, and the profile's job is the vocabulary, not one format's quirks. |
| give the migration its own parser and leave the reconciler for later | then the portable half never gets extracted, and the second project copies a project-specific importer — the leak D11 exists to prevent. |

### D13 — Priority is persisted state, and this rewrite must expect it

`issue-metadata-loop` owns the computation, but the **shape** is fixed here because D7's `triage-todo`
rewrite must not assume the old "read everything, score everything" model. A `prio/p0`–`prio/p3` label
carries the band; a body marker carries the score's components and the basis they were computed from;
and the model is invoked only for the issues whose basis changed. D7's rewrite therefore ranks by the
persisted band and loads a body only for a candidate whose recorded basis no longer matches — it does
not re-score the backlog. **The import sets no priority**: the imported items arrive without a band, and
`triage-todo` reports them as *unranked* (it is read-only by contract and cannot write one) until
`issue-metadata-loop` has run once.

| alternative | why rejected |
|---|---|
| rewrite `triage-todo` for the old model and let `issue-metadata-loop` edit it again | two edits to one skill, and the intermediate state would tell every reader to recompute the whole backlog on each triage — the token cost this series exists to remove. |
| leave the priority model unstated here | then D7's rewrite has no expectation to write against, and whichever of the two changes lands second silently contradicts the first. |

## Risks / Trade-offs

- **The deletion is the irreversible half** → `TODO.md` is deleted last, after the rewiring and after
  the reference check is green in its pre-deletion form; the file is recoverable from git
  (`git show <sha>:TODO.md`) and the proposal states that as the rollback path.
- **Issue creation is irreversible** (no delete path, `github-issue-tooling` D6) → the live run is
  checked against the dry run's recorded previews before the file is deleted, `nvid` makes a re-run
  a no-op rather than a duplicate, and a mismatch stops the change with the file still in place.
- **An instruction site is missed and leaves a dead end** → the check fails on any live `TODO.md` line
  carrying no `§`; missed sites are found by the gate rather than by a reader.
- **A citation's `§N` resolves to the wrong issue** → the `--id` match is on the exact marker
  `<!-- nvid: N -->`, and the import's `§N → #number` table in `import-evidence.md` is a second,
  independent record that the check cross-reads.
- **`AGENTS.md` is edited by two changes of this series** → `agent-issue-loop` also rewrites the
  `fix-loop` row; the two must not run in parallel (one writer per tree), and `agent-issue-loop` states
  that this change applies first.
- **The rewiring is a large prose diff with no test** → the check is the test for the reference half;
  the skill edits are verified by the same check plus one structural assertion per skill (no
  `TODO.md`-as-instruction line left), not by review.

## Migration Plan

1. `tools/check-todo-references.sh` + its self-test, and one CI step;
2. `list.sh --id N` and its cases;
3. the live import, with `import-evidence.md` written from `import.sh --json`;
4. rewire the ~20 instruction sites (`config.yaml`, `AGENTS.md`, `guidelines/Build.md:107,417,880`,
   `guidelines/UI.md:1003`) and the 12 skills;
5. resolve every cited `§N`;
6. check green, then delete `TODO.md`;
7. check green again, now with the file absent.

Rollback: `git checkout <sha> -- TODO.md` restores the backlog, and the reference and skill edits are
reverted with it. The imported issues stay; a wrong import is corrected by closing the wrong issues with
a comment naming the correction, not by deleting them.

## Open Questions

Genuinely deferrable; none changes the approach or the task breakdown:

- Whether `tools/check-todo-references.sh` should also enforce that every issue carries exactly one
  `class/*` and one `status/*` label, or whether that belongs to `cleanup-todo`'s hygiene pass.
- The saved searches' names (they are a GitHub-side setting, not a repository artifact).
- Whether a later change adds `.github/ISSUE_TEMPLATE/` so a human-filed finding arrives with the
  Observed / Why / Impact / Fix-candidate skeleton the import produces.
