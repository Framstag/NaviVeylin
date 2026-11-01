# Tasks — the finding's shape at filing time

## 1. The body record and its readers

- [x] 1.1 `tools/issues/lib.sh`: `issues_tier_of` and `issues_change_of` beside `issues_occurrence`, both reading
      through `issues_body_field`, with the keys `tier` and `change`.
      Verification: `bash -c 'source tools/issues/lib.sh; issues_tier_of "x
      <!-- tier: b -->"'` prints `b`, and a body without the marker returns nothing.
- [x] 1.2 The writer side uses `issues_body_set` only — no second spelling of the marker.
      Verification: `grep -c '<!-- tier:' tools/issues/*.sh tools/issues/core/*.java` is 0 (the marker is composed,
      never written literally outside `issues_body_set`'s format).

## 2. The shape in the writer

- [x] 2.1 `tools/issues/create.sh` reads three optional finding fields — `cause`, `change`, `questions` — and
      derives the shape: none of them is `shape-missing`, both `cause` and `change` is `shape-ambiguous`, one is
      that tier.
      Verification: `tools/issue-tooling-cases.sh` cases `create#aShapelessFindingIsRefused` and
      `create#aFindingWithBothShapesIsRefused`.
- [x] 2.2 Tier A: `cause.file`, `cause.line` and `cause.case` are required; the pointer resolves through the
      citation rule the body already passes; the red case's tallies are optional but recorded when present.
      Verification: cases `create#aCauseWithAResolvingPointerIsFiled`, `create#aCauseWhosePointerIsGoneIsRefused`,
      `create#aCauseWithoutACaseIsRefused`.
- [x] 2.3 Tier B: the change directory must exist (`change-not-found`), the profile's `checks.changeReady` must
      report `ok` (`change-not-ready`, quoting its verdict line), and every question must carry its decision
      (`decision-missing`). An **empty** `checks.changeReady` refuses with `gate-not-configured`.
      Verification: cases `create#aNamedChangeThatDoesNotExistIsRefused`, `create#aChangeThatIsNotReadyIsRefused`,
      `create#aTierBWithNoDecisionIsRefused`, `create#anEmptyGatePathRefusesTierB`,
      `create#theDeclaredGatePathIsTheOneRun`.
- [x] 2.4 Tier B: the comment is composed in the form `tools/issues/answer.sh` writes — `Decision:` first
      line, the `<!-- answers: … -->` marker under it — and written **before** any state label; no
      `agent/needs-decision` is written.
      Verification: case `create#aTierBIsFiledWithItsDecisions` asserts the comment's first line and the absence of
      the label.
- [x] 2.5 The shape is recorded in the body record and reported in the command's output and in `--json`.
      Verification: cases `create#theShapeIsInTheBodyRecord`, `create#aTierAIsFiled`, `create#aTierBIsFiled`.
- [x] 2.6 The sensor exemption: a finding filed with `--sensor <id>` is exempt from the shape, and the item names
      that exemption in its body record beside `occurrence`.
      Verification: the sensor suite (`tools/sensor-selftest.sh`) still passes, and one case asserts the exemption's
      line on the item.

## 3. The configuration

- [x] 3.1 `tools/tooling.json`: `checks.changeReady` — `./tools/check-change-readiness.sh`, with the header stating
      that an empty value refuses tier B.
      Verification: `jq -r '.checks.changeReady' tools/tooling.json` prints the path; `tools/check-portability.sh`
      passes (the value is configuration, not engine text).
- [x] 3.2 The writer resolves it from the profile like every other project fact, and a missing file at that path
      is `gate-not-configured` rather than a silent pass.
      Verification: case `create#theDeclaredGatePathIsTheOneRun` with a path pointing at a deleted file.

## 4. The skill the sessions call

- [x] 4.1 `.pi/skills/file-finding/SKILL.md` — when to use it, what it gathers (the shape, the cause or the change,
      the answered forks), the one command it runs, and that a refusal is reported verbatim and not worked around.
      Verification: the skill names `tools/issues/create.sh` and quotes no refusal text of its own
      (`grep -c 'shape-missing' .pi/skills/file-finding/SKILL.md` is 0).
- [x] 4.2 `openspec-explore` and `openspec-propose` reference it as the filing step, one line each, so a session
      ends by filing rather than by remembering to.
      Verification: `grep -l 'file-finding' .pi/skills/openspec-explore/SKILL.md .pi/skills/openspec-propose/SKILL.md`
      names both.

## 5. The documents

- [x] 5.1 `guidelines/Backlog.md` §6 "How an item becomes work": the intake paragraph extended to the two shapes,
      naming the writer's refusals and the body-record keys — facts, not a restatement of the rules.
      Verification: `grep -n 'tier: a\|shape' guidelines/Backlog.md` finds §6, and the section still routes to the
      capability rather than restating it.
- [x] 5.2 `AGENTS.md`: the backlog row of the documentation map names `Backlog.md` §6.
      Verification: `tools/check-doc-routes.sh` passes and names no unmeasured sub-section.

## 6. Verification and archive

- [x] 6.1 `bash tools/issue-tooling-selftest.sh` and `tools/issues/metadata-mutations.sh --list` pass; every case
      this delta names appears in `--list`.
      Verification: the case count before and after is recorded in `evidence/tooling-cases.txt` with the command.
- [x] 6.2 `openspec validate finding-shape-at-filing --strict` exits 0 and
      `bash tools/loop/evidence-check.sh openspec/changes/finding-shape-at-filing --root .` resolves every pointer.
      Verification: both exit 0, and the outputs are copied into `evidence/`.
- [x] 6.3 Archive; confirm the four requirements merged into
      `openspec/specs/backlog-source-of-truth/spec.md`.
      Verification: `openspec show backlog-source-of-truth --type spec --json | jq
      '[.requirements[] | select(.text | test("tier A"))] | length'` is ≥ 1.
