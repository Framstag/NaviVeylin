# Design

See `proposal.md` — **Why** for the per-block measurement (23.4 KB of 38.8 KB duplicated) and
`specs/documentation-ownership/spec.md` for the requirements this change adds to the capability that
`scope-guideline-reads` introduces. This is an **outline**: the migration in §Migration Plan is planned,
its `tasks.md` is deliberately deferred until the two predecessor changes have landed (proposal
"Why now" and "Status of this change's artifacts").

## Context

Three changes edit `AGENTS.md`, and this is the third:

```
1  dedupe-test-rule-ownership   AGENTS.md:257,263 (two restated measurements)
2  scope-guideline-reads        AGENTS.md:9-11 (Documentation Map -> routing table)
3  slim-agents-md               AGENTS.md body sections + guidelines/Build.md §12
```

What change 3 can rely on once the others land:

- a routing table that maps a kind of change to the sections that own its conventions, so a pointer in
  `AGENTS.md` has somewhere to point (change 2);
- a convention that a section is named by number *and* heading text, which the slimming needs because
  `guidelines/MapRendering.md` numbers two sections `## 14.` (change 2, D2/D4);
- the reduced wording of the test blocks: the Robolectric classloader rule is scoped to its owner in
  `Build.md` §6 and `AGENTS.md` keeps only the stub facts (change 1, D3).

The document's own opening line is the standard this change holds it to: "project facts, build commands,
logging, stylesheet mechanics".

## Goals / Non-Goals

**Goals**

- `AGENTS.md` states facts and references rules; a rule's normative text lives in the document that owns
  it, next to the measurement that proves it and the change history that produced it.
- Every removed block is accounted for: its statement is found in its owner, or it is filed.
- The always-read cost drops by roughly 40 % (38.8 KB → ~20-22 KB, ~10.8k → ~6k tokens per session) with no
  rule lost.

**Non-Goals**

- Not shortening or editing any guideline section that owns a rule — only `Build.md` grows, and only to
  receive moved facts.
- Not touching any spec's existing requirements, any source, test, Gradle file, workflow or the
  `openspec/config.yaml` that change 2 owns.
- Not removing the safety one-liners: see D4.
- Not re-measuring anything, and not moving a measurement (the rule change 1 established).

## Decisions

### D1 — the three requirements go to `documentation-ownership`, not to `build-test-gate`

**Chosen.** This change *adds* requirements to `documentation-ownership`, the capability change 2
introduces. `build-test-gate` keeps the gate's instance of the rule ("a gate rule or measurement has one
documented home").

| Alternative | Why not |
|---|---|
| Extend `build-test-gate` | Its purpose is that a gate result is comparable between runs and workstations. What the entry document may state, and where a rule lives, is not a property of the gate; filing it there makes a gate capability own unrelated documents. |
| A third capability, e.g. `agents-md-hygiene` | Names the file rather than the behaviour; the behaviour is one thing — who owns a rule and what the always-read document may say — and change 2 already opened the right home. |
| `skip_specs: true` | False: this change does alter a documented contract (what the entry document may state), and the requirement is what makes the deferrable guard possible later. |

Consequence to record: because `documentation-ownership` is created by change 2's spec delta, **this
change cannot be archived before change 2 has been** — its delta adds to a capability that only exists
after that archive. The sequencing in `proposal.md` already satisfies that, and `openspec validate` on this
change is expected to depend on change 2 having landed.

### D2 — "fact" versus "rule", as a test a reviewer can apply

**Chosen definition.**

```
FACT  its truth does not constrain a future change.
      a path, a module name, a command, a log tag, a distribution fact,
      "the version state file is machine-local and gitignored"
RULE  it constrains what the code, the process or another document must do.
      "one SurfaceCallback registration per session", "no fault escapes the host path",
      "every host mutation goes through the guarded seam", "a cached run is not evidence"
```

Applied to the largest block: "one surface owner per session", "nothing native on a host callback" and "no
fault escapes the host path" are rules → one line each plus a pointer to `guidelines/Design.md` §8 and the
two car specs; the guard names, the entry point, the module that binds the host, the diagnostics tags and
the `NaviVeylinCarAppService` location are facts → the entry document keeps them.

Rationale: without the definition, "slim it down" becomes a matter of taste and the review cannot be
settled. With it, each of the ~23 KB is either kept as fact, reduced to a one-line rule summary, or moved.

### D3 — a pointer names a section, in the form change 2 fixed

**Chosen.** Every pointer names the document, the section number where it has one, and the heading text —
the same convention as the routing table (change 2, D2), so that `guidelines/MapRendering.md`'s duplicated
`## 14.` cannot mislead a pointer either.

**Alternative considered.** Point at the document ("see `guidelines/Design.md`"). Rejected: that is the
current instruction that costs a whole-document read, and it is what change 2 exists to replace.

### D4 — relocation, not rewriting; and the list that must not move

**Chosen.** A removed block's statement MUST already exist in its owner. The only new prose this change
writes is (a) the one-line rule summaries, (b) the pointers, and (c) `Build.md` §12, which receives the
vcpkg/CMake/ABI facts because no other document states them.

A block whose statement exists nowhere is not deleted: it is filed in `TODO.md` as a rule without an owner
(category `specs-and-process`), and the block stays until the owning document exists.

The do-not-move list — one sentence each, the owning section named — because a reader who follows no pointer
must still be safe:

```
the four car hard invariants        Design.md §8, specs car-host-fault-isolation /
                                    auto/screen-observation, Build.md §10 for diagnosis
measure before changing code        Build.md §2/§4/§11
never log coordinates               Regulatory.md §9, UI.md (gates), the build gate
the two distribution flavors        specs android-automotive-os, auto
the two database-open entries       spec native-database-open
the one builder per working tree    Build.md §2
```

### D5 — the summary of a rule is allowed, and bounded

**Chosen.** The entry document MAY summarise a rule in one sentence, and that summary MUST name the owning
section. The normative text — the rationale, the measurement, the refusal path — lives in the owner.

Rationale: the alternative (no rule sentence in the entry document at all) makes the always-read file
useless to the agent that has to act before it can read anything, which is how the copies grew in the first
place. A one-sentence summary with a pointer is a bounded exception with a checkable property: the sentence
carries a section reference, and it carries no measurement (the rule of change 1).

### D6 — verification is a relocation count plus a size measurement

**Chosen.** For each removed block: `grep` a distinctive phrase from the removed text and quote its hit in
the owner (or in `Build.md` §12). The accounting must close: *blocks removed = owner hits found*. Then
measure `wc -c AGENTS.md` before and after and quote both. No Gradle gate is owed — proven by the kind of
file the diff touches, as in change 2's task 5.1.

### D7 — threading, lifecycle, native boundary

Not applicable: documents only. No component, dispatcher, scope, surface or lifecycle owner is introduced,
and no JNI or native source is touched.

## Risks / Trade-offs

- **A hidden rule with no owner.** The vcpkg block is already known to be in this state; the move may
  surface others. → D4: file it, keep the block, do not delete. The accounting of D6 is what detects it.
- **A safety-critical rule becomes one hop away.** The car block exists because a host-process crash is
  unrecoverable. → D4's do-not-move list keeps the invariants as one-liners with named owners, and D5 makes
  the summary-plus-pointer form a requirement rather than a stylistic choice.
- **The one-line summaries drift from their owners**, which is the defect this change removes. → A summary
  carries no numbers and names its section, so it can be checked by `grep`; if a later change tightens the
  rule, the summary is wrong only in emphasis, never in fact.
- **The line numbers in this outline go stale** before it is applied (two predecessors edit the file).
  → Accepted: `tasks.md` is deferred exactly for this, and the migration below is written by section name,
  not by line number, wherever the section survives.
- **A reviewer reads the slimming as "less documentation".** → The measurement in D6 shows a size drop with
  a closed relocation count, and the spec requirement forbids deleting a statement that has no owner.
- **Two sessions in one file.** → Sequencing in the proposal; change 3 is applied last.

## Migration Plan

Written by section, so it survives the predecessors' line shifts:

```
1  build the relocation inventory: for each block in the proposal's table, one line naming the
   distinctive phrase to grep and the owner section that must contain it
2  guidelines/Build.md: add §12 "Native build and vcpkg" and move the vcpkg/CMake/ABI facts
   from AGENTS.md's "Native Build Details" + "vcpkg usage pattern (CI)" into it — the only new prose
3  AGENTS.md: replace each block with its facts + one-line rule summaries + section pointers,
   checking off the inventory as each block is removed
4  AGENTS.md: reduce Code Style to the convention reference and the command list to a pointer at
   Build.md §3
5  file any rule found without an owner in TODO.md (category specs-and-process)
6  verify: the relocation count closes; wc -c before/after; the kind-of-file check proving no Gradle
   gate is owed; a spot-read of the car block against Design.md §8
7  re-run tools/check-doc-routes.sh (change 2's check) so the pointers this change writes still
   resolve, and add every section this change's pointers name to the routing table if it is missing
```

**Rollback:** `git checkout` the two documents. Nothing was deleted from an owner, so a rollback restores
duplication, never a lost rule.

## Open Questions

1. **How far the car block can go.** Three invariants as one-liners plus pointers is the plan; whether the
   `HOST`/`SESSION`/`SCREEN` diagnostics tags stay in the entry document or move to `Build.md` §10 is a
   judgement about what a session-start reader needs. Deferrable: wording, not specs or approach.
2. **Whether `Build.md` is the right home for native build facts** or whether they deserve their own
   guideline once §12 grows. `Build.md` is already 98.8 KB; if §12 pushes it past the point where a
   section-scoped read is still cheap, the section can move into a document of its own without touching
   this change's requirements.
3. **Whether the summary-plus-pointer form should later be machine-checked** (every one-line rule summary
   in the entry document carries a section reference). Cheap to add once the form exists; not required by
   this change's scenarios.
