# Backlog — the rationale

This is the rationale layer of `guidelines/Backlog.md`: the history, the derivation and the alternatives behind
the rules the base document states, read against the numbered section the base document carries. An anchor here
names a section number the base document carries; where a reader holds a number, the anchor carries the same one.

## §12 The decisions, and why


Every decision below eliminated at least one alternative. The full reasoning, with the alternatives named,
is in the change's `design.md`; this is the map, so a reader can find it and so a reversal is visible.

| decision | reason | change |
|---|---|---|
| the tracker, not a prose file | a prose backlog is read whole to answer any question, and its index drifted; labels make selection a query | 1, 2 |
| shell wrappers over a small policy core in single-file Java | a gate must be testable offline and must not pay a build system to run | 1 |
| one project profile, discovery instead of arguments | "adapt one file" and "always the current project" are the adoption cost | 1, 6 |
| the gate is an allowlist **and** a reserved authorisation label | the two terms share an actor, so the label alone would be self-served | 1 |
| closure only; no deletion anywhere | an item is the only record of a finding, and closure is reversible | 1 |
| fingerprint dedup checked live, never a committed ledger | a second source of truth would disagree with the tracker | 1 |
| the record format behind a source adapter | the format is one project's; the reconciler is everyone's — the reconciler and the adapter both landed in change 1, the live run in change 2 | 1, 2 |
| the removal of the prose file happens **last** | between import and rewiring, twelve skills would point at a deleted file | 2 |
| citations preserved, instructions rewired | 300+ citations are history; the rewiring is 107 lines in 23 files, and no compiled file's *behaviour* changes | 2 |
| a citation of an already-removed entry gets a tombstone rather than a rewrite | 87 citations name 23 ids whose section an earlier cleanup had removed; rewriting them would touch 64 sites inside compiled sources and lose which entry proved the rule, so each id keeps its citations and gains one `status/retired` issue carrying its text from git | 2 |
| the reference check is one script, and its exclusions are printed | a proof that nothing points at a deleted file must be mechanical and must not hide an exclusion: a citation that resolves to nothing fails it, and every allowlisted path is named with its reason on every run | 2 |
| a budget as arguments, not a dialogue | a script cannot ask, and an unrecorded budget cannot be audited | 3 |
| the loop writes only through the tooling's own write paths, so the two it was missing were added there | taking a label off and commenting without closing are authorisation decisions, and a raw tracker write inside the loop would have skipped the reserved-label refusal and the evidence-before-closure rule | 3 |
| one fresh process per item, and a separate reviewer | isolation becomes structural instead of a discipline | 3 |
| git owned by the script | the landing step is this repository's most fragile, and it is mechanical | 3 |
| the run verifies rather than trusts | a claim of success is not evidence | 3 |
| escalation instead of abandonment | a mid-item decision must not discard the work that found it | 3 |
| a check is a row; its pipeline is separate and pinned | a check must not be able to block a change, and a pipeline credential must not sit near PR content | 4 |
| three outcomes, and only "cleared" closes | a broken check reading as health is the classic monitoring failure | 4 |
| reopen rather than re-file; hysteresis; escalation | the record of a fix that did not hold is the valuable part | 4 |
| ownership scope on a check's reach | a subverted pipeline must still not touch what it does not own | 4 |
| priority persisted with a recorded basis | recomputation proportional to the backlog is the cost being removed | 5 |
| a band label **and** a body marker, written together by one script | the label is the cheap selector and the marker is the reason: a label alone cannot decide whether to recompute, and a marker alone makes every selection read a body — so the marker is a line of its own at column 0, closing at its end and outside a fenced span, and a body that quotes the grammar is prose rather than a record (issue #293) | 5 |
| classification rides the same pass | two queues, two write paths and two CI steps for one selection mechanic; and keeping it here is what lets classification write a kind and an area and nothing else | 5 |
| only the changed subset interpreted, bounded per run | a vocabulary change must not become a sweep | 5 |
| priority is not authorisation | else a threshold or a check could promote work into the loop | 5 |
| a correctness check over a value set read from the profile | a hand-written ban list fails open — change 1 landed the check over the tooling's own vocabulary, change 6 adds the synthetic project that proves it in a project that differs | 6 |
| a leak is a **named** value, not a word that appears | a category can be an ordinary word (`core`), so the check matches only values that are quoted, labelled or a flag's argument — a rule that fires on identifiers and prose is a rule that gets switched off (measured: a plain word-boundary scan reported 3 false positives on the foreign fixture, 14 over this tree) | 6 |
| a synthetic project as the proof | only a project that differs tests the profile | 6 |
| a recipe whose commands the self-test executes | a recipe that is never run drifts away from the tooling it describes; the copy itself stays prose because it runs in the source project | 6 |
| one indirection for the agent command | running agents elsewhere must be configuration | 3 |
| a scheduled metadata pass, deterministic rather than model-reading | the stored facts go stale when only an agent runs the pass; the heuristic stand-in needs no provider key, so a schedule can keep them fresh without a credential in a pipeline | 5 |


## §13 Deferred, and why


Named so a reader can tell a decision from an omission:

| deferred | why it is deferred |
|---|---|
| the human lane's queue and answer path (change 7) | **landed 2026-10-20** — see the change table above; what remains deferred is a *prepared-options* format richer than the escalation comment, which needs more real escalations than the twelve the drain produced |
| a shared home for the engines, or a published tool | premature with one consumer; the profile boundary keeps it cheap, and the copy's own cost is stated in `PORTING.md` |
| scheduled **model-reading** unattended runs, budgets read from a file | it would put a model provider's key in a pipeline, which is the one place a credential is hardest to bound. The deterministic metadata pass is scheduled (`.github/workflows/metadata.yml`); a run whose judgement needs a model stays an agent-session job |
| parallel runs and a landing queue | serialising runs is the one-writer rule at run scale; parallelism needs a merge queue |
| the size/frequency loop and a ratified threshold in CI | the measurement and its row have landed (`sensor-registry-and-metric-issues`); what is still deferred is *ratifying* the bound from recorded baselines, which needs the check to have run for a while first |
| periodic spec-drift auditing | it needs a history of checked pairs; the cheap drift forms are worth doing first |
| running the sensors inside the loop's environment | a scheduled producer and a batch consumer should not be coupled |



## §5 Who may write what — why

The rules of §5 were each a decision taken against a concrete failure:

- An answer reaches the next worker's package because a bare answer bound to the verdict word before the
  `**The decision:**` line was fixed (issue #243), and because a recorded `Decision: 2` silently meant a different
  option once the package no longer carried the question it answered (issue #196: recorded 04:52, re-asked 08:48).
- A block asks its own question because a reason is not something a person can answer (issue #225).
- A stale skip asks its own question because a bare hold is reported by the pre-screen as held — neither work nor a
  decision — and leaves the item open for good (issue #682, measured on #147).
- The authorisation label is reserved because the two terms of the gate (an allowlist and the label) share an actor,
  so the label alone would be self-served; the reservation is the recorded decision (§12, change 1).

## §6 How an item becomes work — why

The intake path files a kind and an area because an item filed from a finding whose kind lived only in the finding's
fields used to arrive with no label at all — present in the tracker and in no queue (#199, #200, issue #201). The
citation definition is shared so detection and repair cannot disagree about a body; before the shared rule the
extraction was wrong about 48 of 49 verdicts and two thirds of an authorised set was excluded over prose (measured
2026-10-18).

