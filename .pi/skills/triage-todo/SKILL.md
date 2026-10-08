---
name: triage-todo
description: Scans NaviVeylin's `TODO.md`, ranks every open entry (bugs first, then improvements, then features, each by impact), and proposes the next change. Reads the entry metadata the file now carries (`id · category · class (bug|improvement|feature) · status`), verifies it against the repo, and flags entries that an in-flight change, an archived change or a merged PR already covers. Use when asked "what should we work on next", "triage TODO.md", "propose the next change", "which bug is most important", or before starting a new OpenSpec change after a backlog grew.
---

# Triage TODO.md

Turns the open backlog into a ranked, evidence-checked short list and names the next change to start.
`TODO.md` holds *what is wrong or missing*; this skill decides *what to do about it first*.

Output order is fixed: **bugs → improvements → new features**, each segment sorted by impact/criticality.
A high-impact improvement never outranks a low-impact bug; within a segment, impact decides.

## When to use

- User asks what to do next, which bug matters most, or to pick from `TODO.md`
- A session start after several changes landed and the backlog grew
- Before `/openspec-propose`, to choose the scope rather than invent one

## When NOT to use

- The user already named a change → go straight to `/openspec-propose` or `/openspec-apply-change`
- To *clean* `TODO.md` — remove entries that are already implemented, repair ids/categories/classes, or
  cluster the backlog → the **`cleanup-todo` skill**; this skill only reads it
- To process `ki_processing_failures.log` → `process-failure-log`
- To mine the openspec changes for unproven requirements (why an area was fixed twice) →
  `openspec-proof-gap-audit`

## Inputs / outputs

| Path | Role |
|---|---|
| `TODO.md` | input — read-only, never edited by this skill |
| `openspec/specs/**`, `openspec/changes/**` | input — which of the triaged items an in-flight change already covers |
| `guidelines/*.md`, `AGENTS.md` | input — the contract an entry deviates from (cite the section, not the prose) |
| the repo tree (`file:line`) | input — every proposed item must be verified against it |
| the answer | output — the ranked triage plus the recommended next change; nothing is written unless the user asks |

This skill proposes. It does not implement, and it does not create the change; it ends by naming the
scope and handing off to `/openspec-propose` with a suggested change name.

## Procedure

### 1. Read the whole of `TODO.md`

`ctx_read` compression drops lines of long files, so read raw ranges and do not triage from a
compressed excerpt. Derive the windows from the real length (1301 lines as of 2026-10-03) instead of
trusting fixed offsets from an older session:

```bash
wc -l TODO.md                    # then read in <=300-line raw windows until EOF
sed -n '1,300p' TODO.md          # 301-600, 601-900, 901-1200, 1201-end
grep -n '^## ' TODO.md           # inventory: ids + titles (76 sections as of 2026-10-03)
grep -n '^\*\*id:\*\*' TODO.md    # the metadata line of every entry: id · category · class · status
grep -c '^## ' TODO.md; grep -c '^\*\*id:\*\*' TODO.md   # equal counts — every section carries metadata (76/76)
```

Every open section must be seen at least once. Build the inventory table from the metadata line:
`§id | category | class | status | title | one-line claim`. The `**Clusters**` block in the header is a
generated index (category, then class → `§ids`); use it to navigate, never as a substitute for reading
the entries.

The file's own legend (`✗` missing / `⏳` in progress or blocked / `✅` done) is **prose emphasis inside a
body**. The machine-readable state of an entry is its `status:` field — `open` · `open (device)` ·
`in-flight <change>` · `fixed-by <change|commit>` · `on-hold <decision>` · `unverified` — which is what
the gate lists in step 6 are derived from. Treat a `✗`/`⏳`/`✅` in a title or bullet as a hint you still
verify.

Sections are numbered and referenced from change artifacts and sessions — a number you renumber silently
breaks those references, so never renumber here (the `cleanup-todo` skill owns ids; adding or repairing
metadata is its job too). Report a collision instead of fixing it: the known `§79` duplicate was resolved
to `§119` on 2026-10-03, so a new one is news.

### 2. Cross-check what is already in flight

```bash
openspec list --json                                    # active changes + task progress
openspec list --specs --json                            # existing capabilities (what already exists)
ls -d openspec/changes/archive/*<change>*                # is that entry's change released yet?
git log --oneline -30                                   # what just landed (and "(TODO n)" in the subjects)
ls .pi/skills/                                          # skills that already wrap a recurring task
```

Then build the two sets **mechanically from the metadata** instead of hunting prose:

- **already covered** — every entry with `status: in-flight <change>`; quote that change and its task
  progress. Never propose a change that duplicates an in-flight one.
- **archive candidates** — every entry with `status: fixed-by <change>` **whose change has a directory
  under `openspec/changes/archive/`**. An entry whose text says "removed when the change is archived" is
  a *covered* item while that archive is missing: do not triage it away, and do not call it an archive
  candidate yet.

### 3. Verify before you rank

An entry is a memory, not a fact. Before an item enters a segment:

- open the named file and confirm the condition still holds (`file:line`) — entries go stale when a
  later change touches the same code
- confirm the entry is not already `✅`/`FIXED` with evidence; those become **archive candidates**, not
  work items
- if the entry names a spec, read that spec's requirement and check the deviation is real
- if the entry claims on-device evidence, treat it as confirmed; if its `status:` is `open (device)` or
  `unverified`, or its text says "not verified / unconfirmed / suspected", cap the confidence score and
  say so in the row
- verify the **metadata** too, not just the claim: a `class: improvement` entry that describes a crash,
  an `open` entry whose named code no longer exists, or an `in-flight` entry whose change is gone is a
  reclassification or a stale entry to *report* — never silently re-derived (rewriting it is
  `cleanup-todo`'s job)

Anything that cannot be verified from the tree is reported, never silently dropped.

### 4. Classify each open entry

One class per entry. **Read the entry's recorded `class:` first** — a cleanup pass assigned it against
these same tests, so re-deriving it invents churn. Classify from scratch only for an entry with no
metadata, and when the evidence contradicts the recorded class, say so as a **reclassification**
("§n is recorded `improvement`, but the crash it describes makes it a `bug`") instead of quietly
overriding it. When in doubt the earlier class wins (a crash is a bug, not a missing feature).

| Class | Test | Typical `TODO.md` shapes |
|---|---|---|
| **Bug** | current behaviour contradicts a spec, crashes, loses data, or silently fails a user action | "Spec deviation", "Defect", native crash / ANR / process death, wrong search results, leak, "the Save button is inert", flaky gate that masks real failures, compliance deadline risk |
| **Improvement** | behaviour is correct but a quality attribute is poor: performance, memory, latency, noise, robustness, testability, harness/tooling, debt, docs that mislead | "Debt", "hygiene", "not caught by any test", "tooling gap", deprecation sweeps, refactor candidates, stale verification recipes, guardrail items (§40) that belong in `guidelines/*` |
| **Feature** (`class: feature`) | capability absent entirely; nothing is broken because nothing exists | the `✗` feature tables (no UI yet, callback exists in JNI, unwired platform support), new platform support, `PositionSimulator`-style design notes |

Special shapes that are **not** change candidates on their own — each is now readable from the status
and the category rather than from prose:

- `category: specs-and-process` guardrail items (the `§40` block) → home is `guidelines/*`, `AGENTS.md`,
  CI or a skill; route them through `process-failure-log` or a docs change, and say so instead of ranking
  them
- `status: open (device)` → the device list (step 6), and for a table of pending device checks
  (`§10`-style) each row is a verification step of the change that owns it
- `status: on-hold <decision>` → the blocked list (step 6), with the decision named
- `status: fixed-by …` → not a work item: an archive candidate (released) or already covered (not yet)

### 5. Score impact / criticality

Per entry, with citations as the justification:

```
score = severity x reach + confidence
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

The `status:` field caps confidence. `unverified` (never confirmed anywhere) is 1. `open (device)` cannot
be raised above 2 locally unless the entry quotes a device run, and it always lands in the device list.
`fixed-by` means the fix landed, so what you score is the **residue the entry still describes** — a
verification, a measurement, or a defect the fix left behind — not the original defect.

Tie-breakers, in order: smallest effort first; a deadline-forced item before the rest of its segment;
an item that unblocks several other entries (e.g. a diagnosis that gates device verification) before
sibling items of equal score.

Deadline override: an entry with a dated external deadline (a store policy, a dependency removal) is
placed at the top of its segment even at a lower raw score, and the date is quoted.

### 6. Sort, gate, and present

Sort each segment by score descending. Then move items out of the ranked lists into these gate lists —
derived from `status:` plus the repo state, not from prose:

- **Blocked / needs a decision** — `status: on-hold <decision>`; name the decision and the consequence of
  each option
- **Needs a device** — `status: open (device)`; cannot be validated locally right now (`adb devices`
  empty, car AVD unusable, a head unit required). Recommend one only if the user can provide the device,
  otherwise they are verification steps for a later pass
- **Already covered** — `status: in-flight <change>`: an active change will close it; quote the change
  name and its task progress
- **Unverifiable** — `status: unverified`: report the entry and what could not be checked; never rank it
  as if it had evidence

Cap each ranked segment at 5 rows and state the totals ("3 of 17 bug entries shown"). Keep the report
short enough that the recommendation is unmistakable.

### 7. Report, then hand off

Use this shape:

```markdown
## TODO.md triage — <date> (N sections scanned, M open)

### 1. Bugs (highest impact first)
| score | § | category | Title | Evidence | Next action |
|---|---|---|---|---|---|
| 18 | 63 | car | car path never configures the tile data cache | OSMScoutClient.cpp:1530, AutoServiceModule.kt:107 | configure at the shared open seam; spec delta |

Quote the entry's recorded `class:` in the row whenever it disagrees with the segment (a reclassification
from step 4), and name the `category` so the reader can jump via the header's cluster index.

**Recommended for this segment:** <§n> — why now (one sentence), scope sketch, verification path
(unit test / on-device recipe), suggested change name `fix-<slug>`.

### 2. Improvements (highest impact first)
...
### 3. New features (highest impact first)
...

### Blocked / needs a decision
### Needs a device
### Already covered by an in-flight change
### Unverifiable
### Archive candidates (status fixed-by, change already archived)

### Recommended next change
<one item> — segment, score, why it beats the runner-up, and the handoff:
run `/openspec-propose` with this scope: "<one-sentence scope>"; suggested change name `fix-<slug>`.
```

Rules for the recommendation:

- **Exactly one** recommended next change, plus one named runner-up in the same segment.
- Every claim carries a citation: `TODO.md` §number **and** at least one `file:line` or command output.
- Name the verification path — a change whose result cannot be verified (no test seam, no device) is
  recommended with that limitation stated, not hidden.
- If the top item is a diagnosis rather than a fix, say so and say what it unblocks.
- Do not restate the whole entry; the entry is the long form, the report is the decision.

## Pitfalls

1. **Compressed reads lose entries.** `ctx_read` windows and truncated `grep` output silently drop lines
   of `TODO.md`; a dropped entry is a wrong triage. Read raw ranges, and verify the count
   (`grep -c '^## ' TODO.md`) against the inventory.
2. **Stale entries look urgent.** An entry found two changes ago may already be fixed or moved; always
   re-check the tree in step 3. Conversely, `status: fixed-by <change>` is neither work nor yet a removal:
   with the change archived it is an archive candidate, with the archive missing it is *already covered*.
3. **`✗` is not a bug.** In the feature tables `✗` means *missing capability* — the metadata's
   `class: feature` is the same rule in machine-readable form. Ranking it as a defect inflates the bug
   segment and buries real deviations.
4. **Do not double-count.** One root cause can produce several entries (memory growth, host kill,
   transient buffers); rank the cluster by its highest-severity member and note the siblings, instead
   of presenting the same work three times.
5. **Guardrails are not bugs.** `category: specs-and-process` items (the `§40` block) and
   `ki_processing_failures.log`-derived rules belong in `guidelines/*`/CI/skills; report them as a docs
   change, not as ranked defects.
6. **Device-gated items are not "next".** Without a device, recommending them stalls the session;
   put them in the device list unless the user offers hardware.
7. **Never edit `TODO.md`, never implement.** Removing or adding entries and repairing
   id/category/class/status is the `cleanup-todo` skill's job, and it needs explicit approval; creating
   the change is `/openspec-propose`. This skill only reads and ranks.
8. **Shell tools, not a blocked allowlist.** Use `jq`, `grep`, `sed`, `awk` (and the metadata lines, not
   the prose, for the mechanical sets). `python3`/`perl`/`tesseract` are **not** blocked — verified
   2026-10-03 (3.14.7 / present / 5.5.3); `openspec/config.yaml` still forbids `python3` for OpenSpec
   interaction. `pgrep -f gradlew` matches its own command line: use `pgrep -af 'gradle-wrapper\.ja[r]'`
   (the `GradleWrapper[M]ain` pattern can never match — that class lives inside the jar); git/gh output may
   be German, and that is normal.

## Notes

- The three-segment order is the default contract of this skill; if the user asks for a different order
  or a single flat list, follow the user and say which order was used.
- `TODO.md` is a working file: entries carried "until the change is archived", stale metadata, an
  `unverified` entry and a category outside the vocabulary all occur. Report those anomalies; do not fix
  them here (that is `cleanup-todo`).
- The recorded `class`, `category` and `status` are the triage's starting point, not its conclusion: they
  save the re-derivation, and verifying them (step 3) is what makes the ranking trustworthy.
- The result is deliberately a *proposal*: the ranking is an input to the user's decision, and the
  next artefact is an OpenSpec change, not a code edit.
