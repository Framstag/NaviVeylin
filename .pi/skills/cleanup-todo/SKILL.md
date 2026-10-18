---
name: cleanup-todo
description: Tracker hygiene — repairs an issue's labels and status against the closed vocabularies, finds duplicates by fingerprint and `nvid` collisions, and closes the items whose work is provably done on master, in an archived change or in a merged PR. Closures carry evidence; nothing is ever deleted. Use when asked to clean up, prune or repair the backlog, to deduplicate it, or to check which items are already done.
---

# Tracker hygiene

The tracker is the project's live backlog: one GitHub issue per item, each carrying exactly one
`class/*`, one `category/*`, one `status/*` and one `source/*` label. Items accumulate — a change fixes
one and never comes back to close it, a label is set by hand outside the vocabulary, the same finding is
filed twice, an id collides. This skill repairs what is provably wrong and closes what is provably done.

The tracker is **not** a file, and the tooling has no delete path by design: an item is the only record of
a finding, and closure is reversible. So this pass **never deletes on a hunch**: every closure carries
evidence, and the plan is approved before the first write.

## When to use

- "Clean up the backlog" / "prune the tracker" / "which items are already done?"
- After archiving one or more OpenSpec changes (their items say "removed when the change is archived")
- A session start where the backlog has grown past what anyone reads item by item
- Items are missing a label, carry two of the same namespace, or two items describe one root cause

## When NOT to use

- Ranking or picking the next item → `triage-todo` (read-only; it never writes)
- Recomputing a priority band or a score → `issue-metadata-loop`
- Processing `ki_processing_failures.log` → `process-failure-log`
- Auditing whether the *changes* prove their requirements → `openspec-proof-gap-audit`
- The user wants one specific item closed: do that, and say so — do not run a whole pass

## The contract (never break these)

| Element | Rule |
|---|---|
| The identity | `nvid` in the body for an imported item, the issue number for a newer one. **Never** edit, move or reuse it — a `§N` citation resolves through it |
| The fingerprint | the `<!-- fingerprint: … -->` marker, written by `create.sh`; it is how a duplicate is found, so never hand-write or edit one |
| The priority marker | the `<!-- prio: … -->` marker belongs to `issue-metadata-loop`; read it, never rewrite it |
| One label per namespace | exactly one `class/*`, one `category/*`, one `status/*`, one `source/*` |
| The vocabularies are closed | the values in `tools/tooling.json` (44 values across `class`, `category`, `status`, `source`, `agent`, `prio`). A value outside them is repaired or reported, never invented |
| `agent/eligible` | reserved for a human: no pass of this skill may grant it, and `update.sh`/`create.sh` refuse it |
| Closure, not deletion | there is no delete path. An item that must stop being work is **closed** with a reason and its evidence |
| The body's prose | this skill repairs labels, markers it must not touch, and closures. It does not rewrite an item's findings |

## Procedure

### 1. Inventory (read-only)

```bash
tools/issues/labels.sh --json                        # the taxonomy vs what the tracker holds (a dry-run diff)
tools/issues/list.sh --json > /tmp/tracker.json      # every issue: number, title, labels, author

jq -r '[.[].labels[].name] | group_by(.) | map({l: .[0], n: length}) | .[] | "\(.n)\t\(.l)"' /tmp/tracker.json | sort -rn
jq '[.[] | select(([.labels[].name | select(startswith("class/"))] | length) != 1) | .number]' /tmp/tracker.json
jq '[.[] | select(([.labels[].name | select(startswith("category/"))] | length) != 1) | .number]' /tmp/tracker.json
jq '[.[] | select(([.labels[].name | select(startswith("status/"))] | length) != 1) | .number]' /tmp/tracker.json
jq '[.[] | select(([.labels[].name | select(startswith("source/"))] | length) != 1) | .number]' /tmp/tracker.json
jq -r '[.[] | select(.body | test("agent/eligible")) | .number]' /tmp/tracker.json   # must be empty
```

Report: item count, the label histogram, the items violating the one-label rule, the count per `status/*`,
and the duplicates below.

**Duplicates by fingerprint** — two issues whose marker is equal describe the same finding:

```bash
jq -r '.[] | [.number, ((.body | capture("<!-- fingerprint: (?<f>[0-9a-f]+) -->")? // {f:""}).f)] | @tsv' /tmp/tracker.json |
  awk -F'\t' '$2!="" {print $2}' | sort | uniq -d
```

**`nvid` collisions** — two issues carrying one imported id (only possible if one was hand-created):

```bash
jq -r '.[] | [.number, ((.body | capture("<!-- nvid: (?<id>[0-9]+) -->")? // {id:""}).id)] | @tsv' /tmp/tracker.json |
  awk -F'\t' '$2!="" {print $2}' | sort | uniq -d
```

Report a collision; do not resolve it by rewriting a marker. The id belongs to the item that was imported
with it, and the other item is the one to repair (an issue number is already an identity).

### 2. Build the verification sources

| Source | Command | Meaning here |
|---|---|---|
| Landed on master | `git log --oneline -30`, `git log -S'<symbol>' --oneline -- <path>` | fixed; a closure candidate |
| Commit messages that name the item | `git log --oneline --grep='TODO <id>'`, `--grep='§<id>'`, `--grep='#<number>'` | the strongest signal — this repo commits "(TODO 89, 117)" |
| In-flight changes | `openspec list --json` | `status/in-flight`, **never closed** |
| Archived changes | `ls openspec/changes/archive | sort | tail -20` | landed; an item saying "removed when X is archived" becomes closable |
| Main-repo PRs | `gh pr list --state all --limit 30` | none exist today, so this is usually empty — check anyway |
| Submodule PRs | `gh pr list -R Framstag/libosmscout --state all --limit 30`, `gh pr view <n> -R Framstag/libosmscout` | several imported items are libosmscout follow-ups (`§81` PR #1849, `§82` PR #1773); merged ⇒ closable |
| The named code is gone | `git grep -n '<symbol or path>'` (empty) | the item's premise no longer exists |

`gh` is authenticated for this machine (`gh auth status`); if it is not, say so and treat the PR columns
as unverified rather than assuming "open" or "merged".

The item's own text is read per candidate (`tools/issues/list.sh --id N --json`), never in bulk.

### 3. Decide per item (evidence, not vibes)

A **closure** requires **one** of:

1. an archived change that the item itself names ("removed when `<change>` is archived" → the archive
   directory exists), or
2. a commit/PR on `main` that touches the named file/symbol **and** the behaviour the item describes is
   now in the tree (open the file — the item is a memory, not a fact), or
3. the named code/spec no longer exists at all.

Never a closure:

- **tasks are checked but the change is still in `openspec/changes/`** — that is in-flight, not landed;
  set `status/in-flight` and leave it open
- **a device-gated verification whose code landed** (`§83`, `§84` did exactly this) — `status/open-device`
- **an item whose premise you cannot read** (file moved, symbol renamed) — report it as *stale* and ask,
  rather than closing the only record of it
- **a `status/retired` tombstone** — it exists so that a citation resolves; it is not work and not a
  closure candidate

A **repair** in the same pass, per surviving item:

| finding | repair |
|---|---|
| no `class/*`, or two of them | set the one the evidence supports (`update.sh --class …`); say which and why |
| a `category/*` outside the vocabulary | map it to the vocabulary's nearest value and report the mapping (the migration's own renames were `rendering → map-rendering`, `build → build-and-harness`) |
| no `status/*`, or a status the item's own text contradicts | set the status the evidence supports |
| `status/open-device` whose code has not landed | leave it; the device pass owns it |
| an item that is really a guardrail for `guidelines/*` or CI | still `class/improvement`; note the routing, do not invent a category |

### 4. Plan, then ask (before the first write)

```
## Tracker hygiene plan — <date>  (N items scanned)

Closures (evidence-backed):  #<n> — <one-line reason + citation (commit/PR/archive dir)>
Label repairs:               #<n> — class improvement → bug; category → car
Status repairs:              #<n> — open → fixed-by <change>
Duplicates by fingerprint:   #<a> and #<b> — one finding
nvid collisions:             #<a> and #<b> — <id> occurs twice
Vocabulary gaps:             labels.sh --json → <n> to create, <n> to remove
Stale/unknown (kept):        #<n> — <what could not be verified>
Retired (kept):              <count> tombstones — not work, never closed here
```

Ask for approval of this scope. A closure is reversible, but it is the wrong answer if the evidence is
thin — and the plan is what the report quotes afterwards.

### 5. Apply

```bash
tools/issues/labels.sh --apply                                  # vocabulary drift only
tools/issues/update.sh --issue <n> --class bug --category car    # a label or a status repair
tools/issues/update.sh --issue <n> --status fixed-by --value '<change>'
tools/issues/close.sh --issue <n> --reason finished --evidence '<archived change | commit | report>'
```

`update.sh` refuses a reserved label and a value outside the vocabulary, and `close.sh` refuses a closure
without both a reason and its evidence — if either refuses, that is the answer, not an obstacle to work
around. One item per call, and re-read the item afterwards (`list.sh --id N`) to confirm the state.

### 6. Structure self-check (must pass, then report)

A tracker pass edits no compiled file, so its gate is this self-check, not a build
(`guidelines/Build.md` §2, the scope block).

```bash
bash tools/issue-tooling-selftest.sh                 # the tooling's own surface, offline
bash tools/check-todo-references.sh                  # every §N citation still resolves

gh issue list --state all --limit 400 --json number,title,labels,body,state > /tmp/tracker.json
jq '[.[] | select(([.labels[].name | select(startswith("class/"))] | length) != 1)] | length' /tmp/tracker.json
jq '[.[] | select(([.labels[].name | select(startswith("category/"))] | length) != 1)] | length' /tmp/tracker.json
jq '[.[] | select(([.labels[].name | select(startswith("status/"))] | length) != 1)] | length' /tmp/tracker.json
jq '[.[] | select(([.labels[].name | select(startswith("source/"))] | length) != 1)] | length' /tmp/tracker.json
jq '[.[] | select([.labels[].name] | index("agent/eligible") != null)] | length' /tmp/tracker.json   # 0
jq -r '.[] | [.number, ((.body | capture("<!-- nvid: (?<id>[0-9]+) -->")? // {id:""}).id)] | @tsv' /tmp/tracker.json |
  awk -F'\t' '$2!="" {print $2}' | sort | uniq -c | awk '$1>1'            # no nvid collision
gh issue list --state closed --limit 400 --json number,title | jq 'length' # == the closures this pass made
```

Every `jq` above must print `0` (except the last, which must equal the plan's closure count). Quote the
numbers in the report, and name every item whose condition is still unmet.

## Selection, not an index

The backlog is selected by query, so no index is maintained by hand and nothing regenerates one:

```bash
tools/issues/list.sh --class bug --status open          # the open bugs
tools/issues/list.sh --class improvement --status open  # the open improvements
tools/issues/list.sh --category car --status open       # one area
```

Those two first queries are the tracker's two saved searches. A hand-maintained index is what drifted
before, and a generated one drifts again the moment an item is filed by hand — so when a reader asks for a
view, name the query rather than building a list.

## Pitfalls

1. **A checked task is not a landed change.** `openspec list` shows 25/25 complete for a change that is
   still unarchived — its items stay open, marked `in-flight`.
2. **"FIXED by `<change>`" is not "close now"** when the item says the work lands at archive time. Check
   `openspec/changes/archive/` for the directory.
3. **The item is a memory, not a fact.** Open the file it names. Items move, line numbers drift (`§49`'s
   citation pointed at the GPS-marker drawing after a refactor), and a stale premise is a stale item — but
   that is a *report*, not a silent closure.
4. **Never edit a marker.** The `nvid` is identity, the fingerprint is how duplicates are found, the `prio`
   marker is the metadata pass's. Repairing a label never touches a body.
5. **Do not fold several items into one.** One root cause with three items is reported as a cluster and
   closed together only with approval, because each number is referenced from somewhere.
6. **A tombstone is not work.** `status/retired` items exist so that an old citation resolves; ranking them
   or closing them destroys the guarantee the migration paid for.
7. **Never edit `ki_processing_failures.log` or the archived change artifacts** in the same pass.
8. **Never grant `agent/eligible`.** It is the human's authorisation; a hygiene pass that could set it
   would let the backlog authorise its own automation.
9. **Shell tools, not a blocked allowlist**: `grep`/`sed`/`awk`/`jq` are the house style for the commands
   above (and `openspec/config.yaml` forbids `python3` for OpenSpec interaction). `python3`, `perl` and
   `tesseract` do run in this shell (verified 2026-10-03) — keep the shell tools anyway: the checks stay
   copy-pasteable and cheap.
10. **Do not "clean" by closing the items whose owner is unknown.** An unverifiable item is the most
    valuable thing in the backlog.

## A stale citation in an item's body

An item whose prose cites a path that no longer resolves is refused by the loop's pre-screen (`stale`) and
never worked. Two commands own that:

```bash
tools/issues/resolve-stale.sh                 # dry run: what it would repair or park, and why not
tools/issues/resolve-stale.sh --apply --max-items 25
```

What it may and may not do (`stale-citation-resolution`, `Backlog.md` §5): it rewrites **the citation token**
to the full tracked path when the repository states it (a suffix of a tracked path, a rename in the history, a
single surviving declaration, or the new directory of an archived change), it parks an item nothing accounts
for (`status/on-hold`, with the attempts in one comment), and it posts at most one comment per item per run.
It never writes `agent/*`, never classifies, never closes and never files.

Read a quarantine as a queue item for a person, not a defect: `status/on-hold` plus the comment says what was
tried. The residue is also a declared check (`stale-citations`) — the pipeline reports the count, so a pile
that grows is the alarm that the repair stopped reaching something, and the row's issue is where that shows up.
