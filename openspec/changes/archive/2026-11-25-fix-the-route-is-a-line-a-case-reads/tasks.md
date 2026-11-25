# Tasks — the route is a line a case reads

Spec: `agent-issue-loop`, the two added requirements "The skeleton that owes a route states it as the line the check
resolves" and "The loop's suite reads a route against a project's document" (delta
`specs/agent-issue-loop/spec.md`). Scope: two case files, one prompt skeleton and one guideline paragraph, so the
gate owes the loop's shell suites and the mutation check — `./tools/loop/gate.sh --plan` — and it is run once, by
this session.

## 1. The red case

- [x] 1.1 `tools/loop/tests/loop-cases.sh`, `prompt#thePhasePromptsCarryTheirOwnRoute`: the assertion that the
  skeleton names the anchored form and that the change's markdown is the copy the check reads.
  *Verified*: on HEAD's `tools/loop/prompts/worker.md` (sha256 `3db85287…`) the case fails with
  `'LOOP-ROUTE: guidelines/<doc>.md §<id>' not found in: # Worker — one item, phases A–D`, and the file is restored
  byte for byte to sha256 `ec1eea44…` — `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`.

## 2. The skeleton states the line

- [x] 2.1 `tools/loop/prompts/worker.md`: Phase A step 4 gives the anchored form and says it goes in the report block
  and in the change's own markdown; the report block carries `LOOP-ROUTE: guidelines/<doc>.md §<id>`; one paragraph
  under the block says the markdown copy is the one C6 reads.
  *Verified*: task 1.1's case, green on this tree; `bash -n` is not applicable to markdown, and the skeleton's own
  paths were read by `prompt#thePhasePromptsCarryTheirOwnRoute`'s last loop.

## 3. The case that reads it

- [x] 3.1 `tools/loop/tests/loop-cases.sh`: `check#aRouteSectionResolvesAgainstItsDocument`, in `LOOP_SHELL_CASES`
  and the dispatcher, with `# files: tools/loop/ tools/loop/evidence-check.sh tools/loop/loop.conf
  tools/pi-child-events.sh tools/tooling.json`.
  *Verified*: `loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` — the declaration case, run first —
  and `loop-selftest.sh --case check#aRouteSectionResolvesAgainstItsDocument` prints `PASS`.
- [x] 3.2 The case asserts, against `evidence-check.sh` over a project built by `new_project`: the resolved count
  `1 of 1 route line(s) resolved`; `§99` refused, exit 1, with `its sections are: §1 §2a`; `## 1. The queue` resolved
  as `§1`; a paragraph quoting the form read as prose; no route line a `WARN C6` and exit 0 under `warn`, and
  `FAIL C6 no LOOP-ROUTE` and exit 1 under `fail`.
  *Verified*: `loop-selftest.sh --case check#aRouteSectionResolvesAgainstItsDocument` prints `PASS`, and each of the
  two arms named in task 4.1 goes red against its own record.

## 4. Falsifiability

- [x] 4.1 `tools/loop/tests/loop-mutations.sh`: records `974.1` and `974.2`, both naming
  `check#aRouteSectionResolvesAgainstItsDocument` — the heading comparison made vacuous, and the `fail` arm read under
  `warn`.
  *Verified*: `loop-mutations.sh --check --only '974.1 974.2'` — both patterns apply; `loop-mutations.sh --only '974.1 974.2'`
  prints `RED` for both with the case's own line and `restore … green again`, ending
  `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0` — `evidence/red-when-mutated.log`,
  `evidence/red-when-mutated.xml`.

## 5. The section this change routes to

- [x] 5.1 `guidelines/Backlog.recipes.md`: one paragraph after the route rule naming the skeleton's line and
  `check#aRouteSectionResolvesAgainstItsDocument`, with the two records — the section this change's `LOOP-ROUTE`
  names.
  *Verified*: this change's `LOOP-ROUTE: guidelines/Backlog.recipes.md §7c` resolves against that document's own
  headings — `bash tools/loop/evidence-check.sh openspec/changes/fix-the-route-is-a-line-a-case-reads --root .`.

## 6. The gate

- [x] 6.1 The change is completed — evidence copied, `evidence-check.sh <this change> --root .` clean, the delta
  archived — before the gate, and the gate is run once.
  *Verified*: `LOOP-GATE:` line printed by this session's report and the XMLs `tools/loop/gate.sh` wrote into
  `evidence/`.
