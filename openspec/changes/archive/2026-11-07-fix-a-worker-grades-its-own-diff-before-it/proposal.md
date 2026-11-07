# A child grades its own diff before it hands it over, against the predicates the review already refuses on

## Why

Issue **#812**. The loop's first reader refuses most of what the change child hands it, and refuses it on grounds
the writer held in its hands before anyone else read a line:

```
$ for d in .pi/issue-loop/items/*/; do grep -m1 '^VERDICT:' "$d/reviewer-report-1.md" 2>/dev/null | cut -d' ' -f2; done | sort | uniq -c
    103 FAIL
     62 PASS
```

103 of the 165 round-1 reports the item archive holds refuse the diff (`FAIL`), and their **141** `BLOCKING:`
lines name the same three things the review contract already calls blocking
(`openspec/specs/agent-issue-loop/spec.md`, "A review finding is blocking or it is not"), in that order of
frequency:

```
$ grep -h '^BLOCKING:' .pi/issue-loop/items/*/reviewer-report-1.md | grep -cE 'case'      → 86
$ grep -h '^BLOCKING:' .pi/issue-loop/items/*/reviewer-report-1.md | grep -cE 'scenario'  → 54
$ grep -h '^BLOCKING:' .pi/issue-loop/items/*/reviewer-report-1.md | grep -cE 'evidence'  → 35
$ grep -h '^BLOCKING:' .pi/issue-loop/items/*/reviewer-report-1.md | grep -cE 'tasks\.md' → 24
$ grep -h '^BLOCKING:' .pi/issue-loop/items/*/reviewer-report-1.md | grep -cE 'design\.md'→ 20
```

**Root cause, in one sentence:** the three predicates are already the review contract's definition of *blocking*
and the reviewer reads them against the diff — but nothing in the change child's phase D asks the same question
of the same diff before its session ends, so the judgement that a diff answers for free is made later, by a fresh
reader, and paid for a second time by the rework and the gate that rework owes.

**The run of 2026-11-06 21:19Z is the same shape in miniature.** 3 of its 7 first rounds passed; #806's round-1
refusal is **two documentation findings and no code finding** — a stale owning guideline section whose absence
`design.md` asserted, and a `tasks.md` boundedness clause the diff contradicts twice:

> `design.md`'s Impact paragraph — "**No guideline section states the card's content wording**" — is false against
> the reading set this diff derives.

and the reviewer's own summary says what that cost:

> Neither finding touches the production code, the cases, the red-on-HEAD run or the revert-checks.

## What changes

`tools/loop/prompts/worker.md` gains one block at the end of phase D, before the line that ends the session:
**"Grade your own diff before you hand it over"** — the contract's three predicates, each with the one check that
answers it, plus the owning guideline section phase C named. A finding the child makes there is fixed in the
session, or recorded as a `NOTE:` in its report so the reviewer meets it already named.

- `tools/loop/prompts/worker.md` — the block (16 lines, no new placeholder, so no renderer change).
- `tools/loop-cases.sh` — `prompt#workerGradesItsOwnDiffBeforeItReports`, red on the prompt without the block and
  green with it; its `# files:` declaration is the one the driver case requires.
- `guidelines/Backlog.recipes.md` §7a — one sentence stating that the child grades its own diff first, pointing at
  the contract rather than restating a rule (the contract is the rule's one normative home).

The rule is the contract's, not this change's: the block quotes
`openspec/specs/agent-issue-loop/spec.md`, "A review finding is blocking or it is not".

## The red run, and the green one

```
$ bash tools/loop-selftest.sh --case prompt#workerGradesItsOwnDiffBeforeItReports     # prompt as HEAD~1 has it
FAIL prompt#workerGradesItsOwnDiffBeforeItReports
loop-selftest: 1 of 1 cases failed                                                    → evidence/red-run.log
$ bash tools/loop-selftest.sh --case prompt#workerGradesItsOwnDiffBeforeItReports     # with the block
PASS prompt#workerGradesItsOwnDiffBeforeItReports                                     → evidence/green-run.log
```

## What was checked, and what was not

Verified: the two cases above, `driver#everyCaseDeclaresTheFilesItCanReach`
(`evidence/declaration-check.log`), `tools/loop-mutations.sh --check` (266 records),
`check-doc-routes`, `check-todo-references`, `check-openspec-hygiene` (`evidence/checks.log`).

Not run, and owed by whoever lands it through the loop: the full gate (`./gradlew test` and the shell suites).
This change touches no compiled file — a prompt, one shell case and one guideline sentence — so the suite it owes
is the shell suite its case belongs to, which is what was run.

## Not claimed

That this removes the review round. It moves a judgement the diff already answers from behind the gate to in front
of it — the shape #758 established for the reviewer, applied to the writer who holds the diff for free. Whether the
refusal rate falls is the next run's measurement, and `report.sh` already prints `review: <passed> first rounds`.
