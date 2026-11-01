# A change declares what it leaves open, and a checker reads the declaration

## Why

Nothing in the tree today asks whether a **change** still leaves a fork for an unattended worker. The loop asks it
of an **item**, in Phase B of a child process that has already started and already spent part of its budget
(`tools/loop/prompts/worker.md`, gate 3 — `needs-decision`), which is measured: five items carry a decision label
today and one of them, #47 *"Avoid tolls"*, states five options with a recommendation after a worker spent an item's
budget discovering the fork.

The same tests — one change follows, falsifiable WHEN/THEN, one capability delta, spec-shaped — are what a change
must already satisfy *before* anyone authorises work on it. They are not written anywhere as a test over the
change's own artifacts, and the checks that do exist over those artifacts are about different failures:
`tools/check-openspec-hygiene.sh` reports three *syntactic* silent failures of the OpenSpec CLI,
`openspec validate --strict` reports structure and length, and `tools/loop/evidence-check.sh` resolves the pointers a
change cites. None of them asks the one question that decides whether a worker can be silent.

**The absence.** Measured on the tree:

- `grep -rln 'Open decisions' openspec/ tools/ guidelines/` returns nothing — the declaration this change
  introduces does not exist in any form;
- of the 15 open changes that carry a `design.md`, **8** carry a non-empty `## Open Questions`, and every one of
  them is a deliberate deferral, not a fork (`deliberate`, `deferrable`, `no spec change`, `not part of this
  change`) — `grep -A6 -i '^## open questions' openspec/changes/*/design.md`. A checker that refuses a non-empty
  `## Open Questions` would refuse **53 %** of the changes in flight.

That second measurement is why this change judges a **declaration** and never the meaning of prose: the tree has
already paid for a body-reading rule that was wrong (`guidelines/Backlog.md` §6 — the citation extraction before the
shared rule was "wrong about 48 of 49 verdicts", and "two thirds of an authorised set was excluded over prose").

**Acceptance scenario.** A change whose `design.md` states `## Open decisions` / `none`, whose every scenario names
its case and whose proposal records its `surface:` count is reported `ok`; a change that omits the declaration, or
declares a fork, is refused with the line that names it — and a change carrying a deferrable `## Open Questions`
entry is not refused at all.

**Owning guideline.** `guidelines/Backlog.md`, §6 "How an item becomes work" states the split this change extends —
"a script decides what can be decided cheaply … interpretation is spent only where it is unavoidable".
`guidelines/Build.md` §4a owns what a gate's verdict means. Section 6 is not yet reached from the routing table;
naming it there is C2's job (`finding-shape-at-filing`), because that is the change that makes a person *use* the
declaration.

**Surface.** The declaration has no writer and no reader today, so the surface is the new checker, its suite
wiring, and the capability spec. Measured with
`grep -rn 'check-change-readiness\|Open decisions' tools/ openspec/specs/ .github/workflows/`: no hit.

surface: 0 cases added, 0 rewritten, 4 code files created, 0 guideline paragraphs rewritten, 0 fixtures realigned —
`grep -rn 'check-change-readiness\|Open decisions' tools/ openspec/specs/ .github/workflows/`

## What Changes

- **`tools/check-change-readiness.sh`** — a read-only checker over one change directory or every open change. It
  answers five structural questions and prints one machine-readable verdict line:
  `LOOP-CHANGE-READY: ok` or `LOOP-CHANGE-READY: refused <name>=<detail>` per finding. It reads `design.md`'s
  declaration, the `**Case**:` line of every scenario in the change's spec delta, and the proposal's `surface:`
  line. It never reads a section for its meaning.
- **`tools/check-change-readiness-selftest.sh`** — the cases, offline, in a scratch tree: one per refusal and the
  three passes (declared `none`, a deferrable `## Open Questions`, a scenario whose case is `none` with a reason).
- **`tools/check-change-readiness-mutations.json`** — one record per refusal, so a refusal that stops firing is
  caught by the project's own mutation harness (`tools/mutate.sh`).
- **`tools/loop/loop.conf`** — three `LOOP_GATE_SUITES` entries mapping the checker's own path to the checker, its
  self-test and its mutation list, so editing the checker runs the tests that cover it.
- **`openspec/specs/change-readiness/spec.md`** — a new capability, five requirements.

No caller yet: the writing path that invokes this checker is `finding-shape-at-filing` (C2), and the loop-side
consumer is `loop-consumes-a-prepared-change` (C3). This change lands the test first, so that the caller has
something to call and so that the refusal vocabulary is fixed before it is quoted by another tool.

## Capabilities

### New Capabilities

- `change-readiness`: what it means for a change to be ready to be worked unattended — the decisions it declares,
  the case each scenario owes, the surface it records, and the rule that its prose is never judged.

### Modified Capabilities

None. No shipped requirement changes: this capability is new, and `agent-issue-loop`,
`backlog-source-of-truth` and `documentation-ownership` keep their text until a caller exists (C2, C3).

## Impact

| path | what changes |
|---|---|
| `tools/check-change-readiness.sh` | new — the checker |
| `tools/check-change-readiness-selftest.sh` | new — its cases, offline |
| `tools/check-change-readiness-mutations.json` | new — one record per refusal |
| `tools/loop/loop.conf` | three `LOOP_GATE_SUITES` entries |
| `openspec/specs/change-readiness/spec.md` | new capability, after archive |

Additive, no behaviour change and no interface change: no existing checker, refusal message, recorded key or
default is touched, and nothing runs the new checker until C2. Rollback path: delete the four files and the three
suite entries; nothing else refers to them.

Scope: **general process tooling**, not phone and not Android Auto — no `:app`, `:auto`, `:core`, `buildSrc`,
Gradle, manifest, resource or native path is touched. No submodule patch and no bridge override: this is shell
under `tools/`.

## Not in this change

- **Who calls it.** `create.sh` invoking the checker, at a path read from `tools/tooling.json`, is
  `finding-shape-at-filing` (C2) — including the decision that an empty path refuses the shape rather than
  filing it unchecked.
- **The tier vocabulary.** `tier: a|b` as a body-record key, the `agent/needs-shaping` label and the inbox are C2
  and `shaping-inbox` (C4). This change names no tier.
- **The loop's consumption of a change.** `loop-consumes-a-prepared-change` (C3).
- **Bringing the 15 in-flight changes up to the declaration.** Run over the tree on 2026-10-30, the checker refuses
  15 of the 19 open changes: each of them predates this capability, so it carries no `## Open decisions` section,
  no `surface:` line and no `**Case**:` line on its delta's scenarios
  (`evidence/readiness-on-open-changes.txt` carries the command and one line per change). The four that pass are
  this change and its three siblings (C2, C3, C4). None of the 15 is repaired here: nothing calls the checker until
  C2's filing path writes the declaration, and re-diagnosing changes already in flight is not this change's work.
- **Re-labelling the 39 bare holds** already in the tracker: they drain through C4's inbox, and re-diagnosing them
  is `cleanup-todo`'s work, not this change's.
