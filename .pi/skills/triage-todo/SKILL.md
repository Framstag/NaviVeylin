---
name: triage-todo
description: Ranks the tracker's open backlog from its persisted priority band (`prio/p0`–`prio/p3`), bugs first, and proposes the next change. Reads labels first and a body only for a candidate whose recorded basis no longer matches, verifies every claim against the repo, and flags items an in-flight change, an archived change or a merged PR already covers. Use when asked "what should we work on next", "triage the backlog", "propose the next change", "which bug is most important", or before starting a new OpenSpec change after the backlog grew.
---

# Triage the backlog

Turns the open backlog into a ranked, evidence-checked short list and names the next change to start.
The tracker holds *what is wrong or missing*; this skill decides *what to do about it first* — and it
answers from the labels, without reading the items themselves:

| what the selection needs | where it comes from | cost |
|---|---|---|
| which items are open, and of which kind | the `status/*` and `class/*` labels | one paged list call |
| which open item outranks which | the `prio/*` band label | the same call |
| what the item says | its body | **only** for a candidate the band and the basis do not settle |

Output order is fixed: **bugs → improvements → new features**, each segment in band order. A high-band
improvement never outranks a low-band bug; within a segment, the band decides.

## When to use

- User asks what to do next, which bug matters most, or to pick from the backlog
- A session start after several changes landed and the backlog grew
- Before `/openspec-propose`, to choose the scope rather than invent one

## When NOT to use

- The user already named a change → go straight to `/openspec-propose` or `/openspec-apply-change`
- To *repair* the tracker — labels that are wrong, duplicates, `nvid` collisions → the
  **`cleanup-todo` skill**; this skill only reads
- To recompute the bands or the scores → `issue-metadata-loop`; this skill reads what that pass recorded
- To process `ki_processing_failures.log` → `process-failure-log`
- To mine the openspec changes for unproven requirements (why an area was fixed twice) →
  `openspec-proof-gap-audit`

## Inputs / outputs

| Input | Role |
|---|---|
| the tracker (`tools/issues/list.sh`) | input — the labels are read-only to this skill |
| an item's body | input — read only for a candidate whose recorded basis no longer matches, or whose band is missing |
| `openspec/specs/**`, `openspec/changes/**` | input — which of the triaged items an in-flight change already covers |
| `guidelines/*.md`, `AGENTS.md` | input — the contract an item deviates from (cite the section, not the prose) |
| the repo tree (`file:line`) | input — every proposed item must be verified against it |
| the answer | output — the ranked triage plus the recommended next change; nothing is written unless the user asks |

This skill proposes. It does not implement, it does not create the change, and **it never writes a label**
— a band is the metadata pass's to compute, and this skill is read-only by contract, which is what makes
its ranking trustworthy. It runs no build and no tests: nothing it does compiles.

## Procedure

### 1. Read the labels, in one call

```bash
tools/issues/list.sh --status open --json            # every open item: number, title, labels, author
tools/issues/list.sh --status unverified --json      # the ones nobody has confirmed anywhere
tools/issues/list.sh --status open-device --json     # the device-gated ones
```

Build the inventory from the labels — no body is read yet:

| # | class | category | status | band | title |

Facts the vocabulary fixes, and that decide the rest of the run:

- **the band** is `prio/p0` … `prio/p3`. It is the primary key of the ranking, and it is *persisted*: it
  was computed by `issue-metadata-loop` from the item's severity, reach, confidence and occurrence, and
  the same pass is the only thing that recomputes it.
- **an item with no `prio/*` band is _unranked_** — the import set none, and this skill cannot. List it in
  its own section, with the reason (`the band pass has not scored it yet`), and never invent a score for
  it. A first triage over an unranked backlog therefore reports what the metadata pass owes, rather than
  ranking noise.
- **`agent/eligible` is authorisation, not priority.** It is present only when a human opted the item into
  the automated loop; it never reorders the segments.
- **`status/fixed-by` is not work.** With the change archived it is a closure candidate; without the
  archive it is *already covered*.
- **the body marker** `<!-- prio: band=… score=… sev=… reach=… conf=… occ=… basis=… at=… -->` carries how
  the band was computed and what it was computed from. It is the reason a body is read at all (step 3).

### 2. Cross-check what is already in flight

```bash
openspec list --json                                    # active changes + task progress
openspec list --specs --json                            # existing capabilities (what already exists)
ls -d openspec/changes/archive/*<change>*                # is that item's change released yet?
git log --oneline -30                                   # what just landed
ls .pi/skills/                                          # skills that already wrap a recurring task
```

Then build the two sets **mechanically from the labels** instead of hunting prose:

- **already covered** — every item with `status/in-flight`; quote that change and its task progress. Never
  propose a change that duplicates an in-flight one.
- **closure candidates** — every item with `status/fixed-by` **whose change has a directory under
  `openspec/changes/archive/`**. An item that says work remains is *covered*, not closed: it stays out of
  the ranked segments and out of the closure candidates.

### 3. Load a body only where the basis says so

For each candidate in a ranked segment — never for the whole backlog — read its body **once** and use it
for three things: the claim, the evidence pointers (`file:line`, a device run, a test name), and the
`prio` marker.

```bash
tools/issues/list.sh --id <N> --json | jq -r '.[0].body'      # one item
```

An item is *due for rescoring* when the basis the marker recorded no longer matches the labels it
depends on (the marker's `basis=` is a digest of the item's `class/*`, `category/*` and `status/*`
labels). Report that per item as `<!-- prio: basis mismatch -->`, and **do not rescore it here** — a
recomputation proportional to the backlog is exactly the cost this pipeline exists to remove, and the
pass that owns it is `issue-metadata-loop`.

The bands are only as good as the labels they were computed from, so verify the **item** too:

- open the named file and confirm the condition still holds (`file:line`) — items go stale when a later
  change touches the same code
- if the item names a spec, read that spec's requirement and check the deviation is real
- if the item claims on-device evidence, treat it as confirmed; if its status is `open-device` or
  `unverified`, cap the confidence and say so in the row
- verify the **labels** as well, not just the claim: a `class/improvement` item that describes a crash, a
  `status/open` item whose named code no longer exists, an `in-flight` item whose change is gone — each is
  a reclassification or a stale item to *report*, never to re-derive silently (repairing it is
  `cleanup-todo`'s job, and a band change is the metadata pass's)

Anything that cannot be verified from the tree is reported, never silently dropped.

### 4. Classify each open item

One class per item, and it is the recorded `class/*` label — a cleanup pass assigned it against these same
tests, so re-deriving it invents churn. Classify from scratch only for an item with no `class/*` label,
and when the evidence contradicts the recorded class, say so as a **reclassification** ("§129 is recorded
`improvement`, but the crash it describes makes it a `bug`") instead of quietly overriding it. When in
doubt the earlier class wins (a crash is a bug, not a missing feature).

| Class | Test | Typical shapes |
|---|---|---|
| **Bug** | current behaviour contradicts a spec, crashes, loses data, or silently fails a user action | "Spec deviation", "Defect", native crash / ANR / process death, wrong search results, leak, "the Save button is inert", flaky gate that masks real failures, compliance deadline risk |
| **Improvement** | behaviour is correct but a quality attribute is poor: performance, memory, latency, noise, robustness, testability, harness/tooling, debt, docs that mislead | "Debt", "hygiene", "not caught by any test", "tooling gap", deprecation sweeps, refactor candidates, stale verification recipes, guardrail items (`§40`) that belong in `guidelines/*` |
| **Feature** (`class/feature`) | capability absent entirely; nothing is broken because nothing exists | the `✗` feature tables (no UI yet, callback exists in JNI, unwired platform support), new platform support, `PositionSimulator`-style design notes |

Special shapes that are **not** change candidates on their own — each is readable from the labels:

- `category/specs-and-process` guardrail items (the imported `§40` block) → home is `guidelines/*`,
  `AGENTS.md`, CI or a skill; route them through `process-failure-log` or a docs change, and say so
  instead of ranking them
- `status/open-device` → the device list (step 6), and for a table of pending device checks (`§10`-style)
  each row is a verification step of the change that owns it
- `status/on-hold` → the blocked list (step 6), with the decision named
- `status/fixed-by …` → not a work item: a closure candidate (archived) or already covered (not yet)
- `status/retired` → a tombstone of an item that was removed from the backlog before the migration; it
  exists so that a `§N` citation still resolves. Never rank one, and never close one.

### 5. Read the band, fall back to the score

The band is the ranking key, and the score behind it is defined here so a reader can audit a band without
recomputing it:

```
score = severity x reach + confidence          (severity 1-5, reach 1-3, confidence 1-3)
```

| severity | 5 | 4 | 3 | 2 | 1 |
|---|---|---|---|---|---|
| | crash, process death, host kill, data loss/corruption | silent failure of a user action, wrong result, spec deviation with user impact | spec deviation without user impact, latent defect, external compliance deadline risk | degraded quality: perf, memory, latency, noise, flaky gate | hygiene, docs, tooling only |

| reach | 3 | 2 | 1 |
|---|---|---|---|
| | all users / every startup / both surfaces | one surface (phone or car) or one locale/device class | developers only (harness, CI, tooling) |

| confidence | 3 | 2 | 1 |
|---|---|---|---|
| | reproduced with a citation (device, CI, test name) | read from code with `file:line` | inference, no direct evidence yet |

Bands, as the profile currently cuts them (`tools/tooling.json` `.bands.edges` — the pass reads this file,
this table is the reading aid): `prio/p0` 15-18, `prio/p1` 11-14, `prio/p2` 7-10, `prio/p3` 2-6. The
edges are the metadata pass's to fix, and the profile wins when the two disagree; what this skill relies on
is the **order** the bands give.

The status caps confidence and the band inherits it: `unverified` is confidence 1, `open-device` cannot be
raised above 2 locally unless the item quotes a device run (and it always lands in the device list), and
`fixed-by` means the fix landed — what is left to rank is the **residue** the item still describes, a
verification or a measurement, not the original defect.

Tie-breakers, in order: smallest effort first; a deadline-forced item before the rest of its segment; an
item that unblocks several others (e.g. a diagnosis that gates device verification) before its siblings.

Deadline override: an item with a dated external deadline (a store policy, a dependency removal) is placed
at the top of its segment even in a lower band, and the date is quoted.

### 6. Sort, gate, and present

Sort each segment by band first (`p0` → `p3`), then by the recorded `score=` inside a band. Then move
items out of the ranked lists into these gate lists — derived from the labels plus the repo state:

- **Unranked** — no `prio/*` band: the metadata pass has not scored it. Name it, do not rank it
- **Blocked / needs a decision** — `status/on-hold`; name the decision and the consequence of each option
- **Needs a device** — `status/open-device`; cannot be validated locally right now (`adb devices` empty,
  car AVD unusable, a head unit required). Recommend one only if the user can provide the device,
  otherwise they are verification steps for a later pass
- **Already covered** — `status/in-flight`: an active change will close it; quote the change name and its
  task progress
- **Unverifiable** — `status/unverified`: report the item and what could not be checked; never rank it as
  if it had evidence
- **Retired** — `status/retired`: not work (a tombstone the migration filed); listed only when a citation
  brought it up

Cap each ranked segment at 5 rows and state the totals ("3 of 17 bug items shown"). Keep the report short
enough that the recommendation is unmistakable.

### 7. Report, then hand off

Use this shape:

```markdown
## Backlog triage — <date> (N open items, M ranked, U unranked)

### 1. Bugs (band first, then score)
| band | # | category | Title | Evidence | Next action |
|---|---|---|---|---|---|
| p0 | 63 | car | car path never configures the tile data cache | OSMScoutClient.cpp:1530, AutoServiceModule.kt:107 | configure at the shared open seam; spec delta |

Name the recorded `class/*` in the row whenever it disagrees with the segment (a reclassification from
step 4), and name the `category` so a reader can narrow the next query with
`tools/issues/list.sh --category <value>`.

**Recommended for this segment:** #N — why now (one sentence), scope sketch, verification path
(unit test / on-device recipe), suggested change name `fix-<slug>`.

### 2. Improvements (band first, then score)
...
### 3. New features (band first, then score)
...

### Unranked (no band yet)
### Blocked / needs a decision
### Needs a device
### Already covered by an in-flight change
### Unverifiable
### Closure candidates (status/fixed-by, change already archived)
### Retired (tombstones, not work)

### Recommended next change
<one item> — segment, band, why it beats the runner-up, and the handoff:
run `/openspec-propose` with this scope: "<one-sentence scope>"; suggested change name `fix-<slug>`.
```

Rules for the recommendation:

- **Exactly one** recommended next change, plus one named runner-up in the same segment.
- Every claim carries a citation: the issue number (`#N`, or `§N` when the item predates the migration)
  **and** at least one `file:line` or command output.
- Name the verification path — a change whose result cannot be verified (no test seam, no device) is
  recommended with that limitation stated, not hidden.
- If the top item is a diagnosis rather than a fix, say so and say what it unblocks.
- Do not restate the whole item; the item is the long form, the report is the decision.

## Pitfalls

1. **Bodies are the expensive read.** A triage that loads every body is the cost this pipeline removes;
   select from the labels, and read a body only for a candidate whose basis changed or whose band is
   missing.
2. **An unranked item is not a low-priority one.** No band means the metadata pass has not run; ranking it
   by your own estimate duplicates that pass and hides the fact that it is owed.
3. **Never write a band.** This skill is read-only; a band written here would be unverifiable and would
   invalidate the basis the metadata pass recorded.
4. **Stale items look urgent.** An item found two changes ago may already be fixed or moved; always
   re-check the tree in step 3. Conversely, `status/fixed-by` is neither work nor yet a closure: with the
   change archived it is a closure candidate, with the archive missing it is *already covered*.
5. **`✗` is not a bug.** In the feature tables `✗` means *missing capability* — the `class/feature` label
   is the same rule in machine-readable form. Ranking it as a defect inflates the bug segment and buries
   real deviations.
6. **Do not double-count.** One root cause can produce several items (memory growth, host kill, transient
   buffers); rank the cluster by its highest-severity member and note the siblings, instead of presenting
   the same work three times.
7. **Guardrails are not bugs.** `category/specs-and-process` items and `ki_processing_failures.log`-derived
   rules belong in `guidelines/*`/CI/skills; report them as a docs change, not as ranked defects.
8. **Device-gated items are not "next".** Without a device, recommending them stalls the session; put them
   in the device list unless the user offers hardware.
9. **Never write anything, never implement.** Repairing a label or closing an item is `cleanup-todo`'s job
   and needs approval; computing a band is `issue-metadata-loop`'s; creating the change is
   `/openspec-propose`. This skill only reads and ranks.
10. **Shell tools, not a blocked allowlist.** Use `jq`, `grep`, `sed`, `awk`. `python3`/`perl` are present
    but `openspec/config.yaml` forbids `python3` for OpenSpec interaction; `pgrep -f gradlew` matches its
    own command line, so use `pgrep -af 'gradle-wrapper\.ja[r]'`; git/gh output may be German, and that is
    normal.

## Notes

- The three-segment order is the default contract of this skill; if the user asks for a different order or
  a single flat list, follow the user and say which order was used.
- The bands are a starting point, not a conclusion: verifying the item (step 3) is what makes the ranking
  trustworthy, and reporting a wrong label is how the next hygiene pass learns about it.
- The result is deliberately a *proposal*: the ranking is an input to the user's decision, and the next
  artefact is an OpenSpec change, not a code edit.
