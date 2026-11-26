# Tasks — the findings quote keeps every whole line at its cap

## 1. The red case

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: `evidence#aCutAtTheCapKeepsEveryWholeLine` — registered in
  `LOOP_SHELL_CASES` and in the `loop_run_shell_case` dispatch, with its own `# files:` declaration. The body writes
  the throwaway project a script that sources `tools/loop/evidence.sh` and calls `review_findings_part`, then asserts
  both edges: a cap that lands on a boundary keeps the whole line it ends (`NOTE: two` present, `BLOCKING: three`
  absent, the marker reading `2 of the 3 finding lines`), and a cap inside a line keeps the whole lines before it and
  hands no fragment (`1 of the 3`, no `NOT` line, the report named).
  *Verified*: `driver#everyCaseDeclaresTheFilesItCanReach` green (`evidence/green-declaration.log`).
- [x] 1.2 Run it against the base `62d62e46` with the engine unmodified and keep the log:
  `evidence/red-on-HEAD-aCutAtTheCapKeepsEveryWholeLine.log`, and its XML beside it, transcribed from that log.
  *Verified*: the log's last line reads `FAIL evidence#aCutAtTheCapKeepsEveryWholeLine`, the first failure being
  `'NOTE: two' not found in: BLOCKING: one`.

## 2. The engine

- [x] 2.1 `tools/loop/evidence.sh:760`: `review_findings_part` cuts at the line boundary the cap falls on — the
  newlines inside the bytes `head -c` kept are the whole lines, `head -n` hands exactly those — and the
  `[LOOP-CUT: …]` marker names how many of the report's finding lines the quote carries beside the report.
  *Verified*: the case green (`evidence/green-aCutAtTheCapKeepsEveryWholeLine.log`), and the two reading cases it
  feeds unchanged (`run#aRoundCutTwiceKeepsBothReadingsFindings`,
  `run#aRestartQuotesTheRetriedReadingItReplaced`).
- [x] 2.2 `tools/loop/tests/loop-mutations.sh`: the `1046.x` record — `1046.1` drops the line the cap ends as well,
  the unconditional last-line drop this item removed.
  *Verified*: `tools/loop/tests/loop-mutations.sh --only '1046.1'` red-when-mutated and green again after the restore
  (`evidence/mutations.log`).

## 3. The declaration and the document

- [x] 3.1 `layout.tsv` and `events.tsv`: nothing moves — `review_findings_part` is declared by both already, so the
  change adds no name and no `owns` cell.
  *Verified*: `driver#anOwnedFunctionIsInItsOwnersFile` and `driver#everyPrintOnlySiteIsConverted` are covered by the
  gate; the function's name and its print site are the ones the two tables already carried.
- [x] 3.2 `guidelines/Backlog.recipes.md`, in the section the input bound is stated in, after its paragraph on the
  cap: what a **findings**
  quote owes — cut at the line boundary, the marker naming the count — which the section never stated (issue #1046).
  *Verified*: `tools/check-doc-routes.sh` and the gate's own checks over `guidelines/**`.

## 4. Evidence

- [x] 4.1 `evidence/red-on-HEAD-aCutAtTheCapKeepsEveryWholeLine.log` / `.xml` — the red on the base.
- [x] 4.2 `evidence/green-aCutAtTheCapKeepsEveryWholeLine.log` — the case green on the change.
- [x] 4.3 `evidence/mutations.log` — `1046.1` noticed, and green again after the restore.
- [x] 4.4 `evidence/green-declaration.log` — the declaration case green with the new body.
- [x] 4.5 The change's evidence-check green, then `openspec archive --yes` so the delta merges into
  `openspec/specs/agent-issue-loop/spec.md`, and the gate run once over the archived tree.
