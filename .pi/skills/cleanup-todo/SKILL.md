---
name: cleanup-todo
description: Cleans up TODO.md — removes entries already implemented on master, in an archived/in-flight OpenSpec change, or in a merged branch/PR, keeps the file's structure intact, and gives every entry an id, a category and a bug/improvement/feature class. Optionally clusters the backlog by category. Use when asked to clean up, prune, deduplicate, restructure or re-classify TODO.md, or to check which TODO entries are already done.
---

# Clean up `TODO.md`

`TODO.md` is the project's live backlog: numbered `## N.` sections of narrative findings plus the
feature-table groups. Entries accumulate, get fixed by changes that never come back to delete them,
lose their metadata, or end up in a pile that nobody can triage. This skill prunes what is provably
done, keeps the structure other tools depend on, and gives every entry the metadata triage needs.

It changes a **tracked** file, so it never deletes on a hunch: every removal carries evidence, and the
plan is approved before the first edit.

## When to use

- "Clean up `TODO.md`" / "prune the backlog" / "which TODO entries are already done?"
- After archiving one or more OpenSpec changes (their entries say "removed when the change is archived")
- A `TODO.md` session start where the file has grown past what a human will read
- Entries lack a category/class, or the same root cause appears as several sections

## When NOT to use

- Ranking or picking the next item → `triage-todo` (read-only; it never edits)
- Processing `ki_processing_failures.log` → `process-failure-log`
- Auditing whether the *changes* prove their requirements → `openspec-proof-gap-audit`
- The user wants one specific entry removed: do that, and say so — do not run a whole pass

## The structure contract (never break these)

| Element | Rule |
|---|---|
| Title + legend | Keep line 1 (`# NaviVeylin TODO`) and the legend line; **extend** the legend, never replace it |
| Id | The section number **is** the id. Never renumber to tidy up: change artifacts, task files and sessions reference `§N` in prose. Report a collision (`§79` occurs twice as of 2026-10-03); resolve it by giving the *duplicate* a fresh id and a note, never by shifting its neighbours |
| Order | Keep the existing order. Sorting is a restructure, so it needs explicit approval |
| Separator | Keep the `---` line between sections |
| Body | Keep the labelled bullets (`- **Observed** ℹ:`, `- **Consequence** ⏳:`, `- **Fix candidate**:`, `- **Fixed** ✅ by …`). This skill adds metadata and deletes proven-done entries; it does not rewrite findings |
| Feature tables | The `## N.` table groups keep their heading; each row is a `feature` unless its Notes cell starts with `[improvement]` or `[bug]` |

## Entry metadata (what a clean entry looks like)

One metadata line directly under the heading, every entry, no exceptions:

```markdown
## 28. Whole-level rounding in `computeAreaZoom` over-fits area favorites and POI search
**id:** 28 · **category:** map-rendering · **class:** bug · **status:** fixed-by `fix-area-fit-zoom-rounding`
```

- **id** — the section number (for table groups: the group id, with rows inheriting it), unique
- **category** — one value from the vocabulary below; add a value only with the user's agreement, and add
  it to the legend in the same pass
- **class** — exactly one of `bug` | `improvement` | `feature`
  - `bug` — current behaviour contradicts a spec, crashes, loses data, or silently fails a user action
  - `improvement` — behaviour is correct but a quality attribute or the process is poor (perf, memory,
    noise, testability, harness, docs, specs/process)
  - `feature` — capability absent entirely; nothing is broken because nothing exists
- **status** — `open` | `in-flight <change>` | `fixed-by <change|commit>` | `on-hold <decision>` |
  `open (device)` for an entry whose only remaining evidence needs hardware
- **cluster** (optional, only in cluster mode) — the grouping key, defaulting to the category

Category vocabulary (extend deliberately; keep it kebab-case):

```
map-rendering · stylesheets · route-and-navigation · search · favorites · location · car ·
persistence · ui · i18n · native-jni · data-and-maps · build-and-harness · verification ·
specs-and-process · licensing
```

Legend extension to add once (keep the existing `✗ / ⏳ / ✅` line and append):

```markdown
**Entry metadata:** every `##` section carries `**id:** … · **category:** … · **class:** bug|improvement|feature · **status:** …`
```

## Procedure

### 1. Inventory (read-only)

```bash
grep -c '^## ' TODO.md                       # sections
grep -n '^## ' TODO.md                       # ids + titles
grep -o '^## [0-9]*' TODO.md | sort | uniq -d   # id collisions
grep -c '^\*\*id:\*\*' TODO.md               # entries that already carry metadata
wc -l TODO.md
```

Report: sections, id collisions, entries without metadata, and the class distribution
(`grep -o '\*\*class:\*\* [a-z]*' TODO.md | sort | uniq -c`).

### 2. Build the verification sources

| Source | Command | Meaning here |
|---|---|---|
| Landed on master | `git log --oneline -30`, `git log -S'<symbol>' --oneline -- <path>` | fixed, entry removable |
| Commit messages that name the entry | `git log --oneline --grep='TODO <id>'`, `--grep='§<id>'` | strongest signal — this repo commits "(TODO 89, 117)" |
| In-flight changes | `openspec list --json` | `status: in-flight <change>`, **never removed** |
| Archived changes | `ls openspec/changes/archive | sort | tail -20`, `openspec list --json --archived` if available | landed; an entry saying "removed when X is archived" becomes removable |
| Main-repo PRs | `gh pr list --state all --limit 30` | none exist today, so this is usually empty — check anyway |
| Submodule PRs | `gh pr list -R Framstag/libosmscout --state all --limit 30`, `gh pr view <n> -R Framstag/libosmscout` | several entries are libosmscout follow-ups (`§81` PR #1849, `§82` PR #1773); merged ⇒ removable |
| A merged PR by number in prose | `git log --oneline --grep='#<n>'` | merges are referenced by number in the entries |
| The named code is gone | `git grep -n '<symbol or path>'` (empty) | the entry's premise no longer exists |

`gh` is authenticated for this machine (`gh auth status`); if it is not, say so and treat the PR
columns as unverified rather than assuming "open" or "merged".

### 3. Decide per entry (evidence, not vibes)

Removal requires **one** of:

1. an archived change that the entry itself names ("removed when `<change>` is archived" → the archive
   directory exists), or
2. a commit/PR on `main` that touches the named file/symbol **and** the behaviour the entry describes
   is now in the tree (open the file — the entry is a memory, not a fact), or
3. the named code/spec no longer exists at all.

Never removal:

- **tasks are checked but the change is still in `openspec/changes/`** — that is in-flight, not landed;
  mark `status: in-flight <change>` and leave the entry
- **a device-gated verification** whose code landed (`§83`, `§84` did exactly this) — `open (device)`
- **an entry whose premise you cannot read** (file moved, symbol renamed) — mark `verification: stale`
  and ask, rather than deleting the only record of it

Also classify in the same pass — for each surviving entry set/repair `category`, `class`, `status`.
An entry that is really a guardrail belonging in `guidelines/*` or CI is still an `improvement`, but
note the routing in its `Fix candidate` line instead of inventing a `process` class.

### 4. Plan, then ask (before the first edit)

```
## Cleanup plan — <date>  (N sections scanned)

Removals (evidence-backed):  <id> — <one-line reason + citation (commit/PR/archive dir)>
Reclassifications:           <id> — class bug → improvement; category — → car
Metadata added:              <count> entries, <count> table groups
Id collisions:               <id> — proposed fresh id for the duplicate
In-flight (kept):            <id> — in-flight <change>
Device-gated (kept):         <id> — open (device)
Stale/unknown (kept):        <id> — <what could not be verified>
Cluster proposal:            <mode + resulting groups>
```

Ask for approval of this scope. Removal is irreversible for the reader (the entry may be the only
record of the finding) — do not remove anything the plan did not name.

### 5. Apply

- Add the metadata line under every heading; extend the legend; keep order, ids, separators and prose
- Delete only the approved entries (with their separator, leaving no double `---`)
- Keep each edit self-contained; one file, and verify the file still ends with its last section

### 6. Structure self-check (must pass, then report)

```bash
grep -c '^## ' TODO.md                                  # unchanged minus removals
grep -o '^## [0-9]*' TODO.md | sort | uniq -d            # no collisions
awk '/^## /{ if (getline nxt > 0 && nxt !~ /^\*\*id:\*\*/) print "VIOLATION line "NR": "nxt }' TODO.md
# no output = every section is followed by its metadata line. Do NOT use `BEGIN{ok=1} … END{exit ok}`:
# `exit ok` inverts the verdict, so a healthy file exits non-zero and the check reports FAIL on it.
grep -c '^\*\*id:\*\*' TODO.md                          # == section count
grep '^\*\*id:\*\*' TODO.md | grep -o '\*\*class:\*\* [a-z]*' | sed 's/.*: //' | sort -u   # ⊆ {bug, feature, improvement}
# Vocabulary membership needs the explicit list, scoped to the metadata lines: the legend line itself
# carries a bare `**category:** …`, and a file-wide `grep -q "$c"` is vacuously true because the entry
# being checked contains the string — both make the check silently unfailable.
VOCAB="map-rendering stylesheets route-and-navigation search favorites location car persistence ui i18n native-jni data-and-maps build-and-harness verification specs-and-process licensing"
for c in $(grep '^\*\*id:\*\*' TODO.md | grep -o '\*\*category:\*\* [a-z-]*' | sed 's/\*\*category:\*\* //' | sort -u); do
  case " $VOCAB " in *" $c "*) ;; *) echo "category outside the vocabulary: $c";; esac; done
grep -n 'removed when' TODO.md                          # each one: does its archive exist?
# status histogram — compare against the approved plan, not just the class counts
grep '^\*\*id:\*\*' TODO.md | sed 's/.*\*\*status:\*\* //' | sort | uniq -c | sort -rn
git diff --stat TODO.md                                  # insertions ≈ sections; deletions only for approved removals

# the cluster index must agree with the per-entry metadata (every removal invalidates it)
sed -n '/^\*\*Clusters/,/^---$/p' TODO.md | grep '^- \*\*' > /tmp/cl.txt
awk '{ s=$0; sub(/^- \*\*/,"",s); cat=s; sub(/\*\*.*/,"",cat); rest=s; sub(/^.*— /,"",rest);
       cls=rest; sub(/:.*/,"",cls); ids=rest; sub(/^[a-z]*: /,"",ids); n=split(ids,a," ");
       for(i=1;i<=n;i++){ id=a[i]; gsub(/§/,"",id); if(id!="") print id"\t"cat"\t"cls } }' /tmp/cl.txt > /tmp/idx.tsv
awk -F'\t' 'FNR==NR{idx[$1]=$2"|"$3; next} /^\*\*id:\*\*/{ l=$0; id=l; sub(/^\*\*id:\*\* /,"",id); sub(/ ·.*/,"",id);
       c=l; sub(/^.*\*\*category:\*\* /,"",c); sub(/ ·.*/,"",c); k=l; sub(/^.*\*\*class:\*\* /,"",k); sub(/ ·.*/,"",k);
       nm++; if (idx[id]=="") { print "NOT IN INDEX §"id; miss++ } else if (idx[id]!=c"|"k) { print "MISMATCH §"id; bad++ } else ok++ }
     END{ printf "metadata=%d matching=%d not-in-index=%d mismatches=%d\n", nm, ok, miss+0, bad+0 }' /tmp/idx.tsv TODO.md
```

`matching` must equal the metadata count, with `not-in-index` and `mismatches` at 0 — that check is what
proves the index was regenerated after the removals, not just left in place.

Quote the counts in the report, and name every entry whose condition is still unmet.

## Cluster mode (optional restructure)

Two modes; the default is safe, the other is a restructure that needs explicit approval:

- **Index mode (default)** — leave the `## N.` sections in place and insert one generated index after
  the legend, grouped by `category` (then by `class`), each row `- <class> · §<id> <title>`. Nothing
  else moves, so every `grep '^## '` and every `§N` reference keeps working. This is what "build
  clusters" usually needs: a triage-shaped view over a flat file.
- **Section mode (ask first)** — regroup the entries under `## Cluster: <category>` blocks with the
  entries demoted to `### N.`. It reads better and **breaks the flat `##` convention** that
  `triage-todo`, the inventory commands above and any `grep '^## '` consumer rely on. If the user wants
  it, update those consumers in the same pass (`triage-todo` skill, this skill's commands, and any
  script that greps headings) and say which ones changed.

Never renumber while clustering: cluster membership is derived, ids are identity.

## Pitfalls

1. **A checked task is not a landed change.** `openspec list` shows 25/25 complete for a change that is
   still unarchived — its entries stay, marked `in-flight`.
2. **"FIXED by <change>" is not "delete now"** when the entry says the removal happens at archive time.
   Check `openspec/changes/archive/` for the directory.
3. **The entry is a memory, not a fact.** Open the file it names. Entries move, line numbers drift
   (`§49`'s citation pointed at the GPS-marker drawing after a refactor), and a stale premise is a
   stale entry — but that is a *report*, not a silent delete.
4. **Renumbering breaks prose.** `TODO.md` ids are quoted in commit messages, change tasks and
   guidelines. Fix a collision by renumbering the duplicate only.
5. **Do not fold several entries into one** while cleaning: one root cause with three sections is
   reported as a cluster and merged only with approval, because the ids are referenced.
6. **Keep the feature tables.** §1–§10's rows are the `feature` backlog; they are not "stale sections"
   to delete, and their `✗` is a missing capability, not a bug.
7. **Never edit `ki_processing_failures.log` or the archived change artifacts** in the same pass.
8. **Shell tools, not a blocked allowlist**: `grep`/`sed`/`awk`/`jq` are the house style for the commands
   above (and `openspec/config.yaml` forbids `python3` for OpenSpec interaction). The old claim that
   `python3`/`perl`/`tesseract` are allowlist-blocked is **false** — verified 2026-10-03: `python3` 3.14.7,
   `perl` and `tesseract` 5.5.3 all run in this shell. Keep the shell tools anyway: the checks stay
   copy-pasteable and cheap.
9. **Do not "clean" by deleting the sections whose owner is unknown.** An unverifiable entry is the most
   valuable thing in the file.
10. **A scripted metadata pass must be tab-driven.** Driving the insertion from a map file with `awk`'s
    default whitespace splitting silently truncates multi-word values — on the first run of this pass
    `open (device)` became `open` and `fixed-by <change> (in-flight)` lost the change entirely, while the
    section/metadata counts still looked perfect. Use `-F'\t'`, then spot-check one `open (device)` and one
    `fixed-by …` entry, and read the status histogram from step 6. Keep a backup of the pre-pass file: the
    restore is one `mv` away, and a "successful" count is not a correctness check.
11. **A removal invalidates the cluster index** — and a stale index is invisible (`grep '^## '` and the
    counts stay green). Regenerate the affected cluster line in the same pass and run the
    index-vs-metadata check from step 6; also reword the *in-file* citations of a removed id (a
    `(§100)` inside another entry's body, a "removed when …" sentence) and any committed reference to it
    (`guidelines/*.md`) — change artifacts keep their historical citations, since editing an archived one
    is forbidden.
12. **A self-check that cannot fail — or cannot pass — is worse than none.** Two of this skill's own
    step-6 checks were broken: `awk 'BEGIN{ok=1} … END{exit ok}'` inverts the verdict (a healthy file
    exits non-zero, so it reported FAIL), and the category loop's file-wide `grep -q "$c"` was vacuously
    true because the entry under check contains the string, with the legend's bare `**category:** …`
    matching as an empty category. Both are fixed in step 6: scope a check to the metadata lines, compare
    against the explicit vocabulary list, and perturb one entry once to confirm the check can go red.
    Also remember **a class change is an index change** — reclassifying `§46` from `bug` to `improvement`
    left the cluster index stale until it was regenerated, which is exactly what the index check catches.

## References

- `.pi/skills/triage-todo/SKILL.md` — the read-only ranking this skill feeds (it never edits `TODO.md`;
  this skill is the cleanup its "When NOT to use" points at)
- `.pi/skills/process-failure-log/SKILL.md` — the guardrail entries' home
- `openspec/changes/archive/` — the archive whose existence releases the "removed when archived" entries
- `guidelines/Build.md` §4, `.pi/skills/run-tests/SKILL.md` — the evidence discipline (`TODO.md` §17: a
  cached run is not a verdict) applies to a removal proof too
