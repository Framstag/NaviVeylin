## Why

An author writing an OpenSpec change meets two artifact rules for the first time in the validator's output,
after the artifact is written:

```
Change 'fix-loop-blocked-attempt-is-work' has issues
  ✗ [MODIFIED] "Version control belongs to the run's script, not to the agent" omits scenario(s) the current spec
    still has: "A branch that carries a previous attempt is refused, named and recorded". The modified block has
    10 scenarios; the current spec has 9
```

```
✗ Requirement text is very long (>500 characters)
```

The #232 change needed three `openspec validate --strict` rounds for them — the scenario block rebuilt once and
the text cut twice (≈600 → ≈560 → ≈480 characters) — while the workflow section an author opens,
`AGENTS.md` §"OpenSpec Workflow", names only the three rules that are **CI-gated**
(`.github/workflows/build.yml`, step "Check OpenSpec artifact hygiene"). Measured on 2026-10-22T02:32:24Z,
`evidence/surface.txt`:

```
$ grep -rln --include='*.md' --include='*.sh' --include='*.yml' --include='*.yaml' -- '--strict' . \
    | grep -v openspec/changes/archive/ | grep -v app/src/main/cpp
                                        # no file: the command is named in no document an author reads
$ grep -rln 'artifact hygiene' .        # AGENTS.md, guidelines/Build.md, .github/workflows/build.yml
$ grep -c 'probe#' tools/*-cases.sh tools/*-selftest.sh   # no suite case asserts the rule's statement
```

**Root cause.** The two rules are enforced only by `openspec validate --strict`, which an author runs after
writing the artifact, so the statement that would have been read before the first write exists in no document:
the workflow section states the CI-gated rules because CI fails on them, and a rule whose only reporter is a
command run at the end of the work is stated nowhere.

**Red test.** No compiled file is in this delta, so there is no JUnit XML: the cases are the probe in
`evidence/probe.sh`, the structural check `guidelines/Build.md` §2 calls for in its scope block. On HEAD
(`80df3a3`), 2026-10-22T02:34:42Z, `bash probe.sh` → `probe: 0 passed, 7 failed`, exit 1
(`evidence/red-HEAD.txt`): the three read cases match no line of the workflow section, and the four validator
cases find no delta to drive. That line is round 1's, taken with the pre-archive probe path; the probe now
resolves the change open **or** archived, and its own red test after the round-2 corrections is the four
mutations in `evidence/round2-checks.log`. After the fix, all seven pass (`evidence/green.txt`); removing the
added block makes the three read cases fail again and restoring it makes them pass (`evidence/revert-check.txt`,
`4 passed, 3 failed` then `7 passed, 0 failed`).

The four validator cases are the mechanism, not a promise: `aRenamedScenarioIsReportedAsAnOmission` and
`theChangeSetIsValidatedByACommand` rename one shipped scenario title in this change's own delta and record what
`openspec validate <change> --strict` and `openspec validate --changes --strict` answer;
`theCompletedBlockKeepsTheRequirementWhole` checks that the restored block carries every shipped scenario title
and WHEN/THEN line; `anOverLongRequirementIsReported` lengthens a requirement body past the bound and records
the same. Each restores the file and re-validates.

**Owning requirement.** `openspec/specs/documentation-ownership/spec.md` — the capability that owns the
project's documents and the properties of them a command can check, and which already owns *A modified
requirement carries the scenarios its spec still has*. This change modifies that requirement and adds
*The validator's own rules are read before the first write*.

**Owning guideline section.** `AGENTS.md` §"OpenSpec Workflow" is the section an author reads before writing a
change's artifacts; it holds the list of artifact rules and is where the two are added. The no-gate rule for a
delta that touches no compiled file is `guidelines/Build.md` §2, its scope block.

## What Changes

- `AGENTS.md` §"OpenSpec Workflow" — two bullets beside the three CI-gated ones (a `MODIFIED` requirement
  repeats every shipped scenario verbatim and under their own titles, a renamed scenario is a removal plus an
  addition; a requirement's text stays within 500 characters of body, examples belong in a scenario), the line
  naming `openspec validate <change> --strict` as the command that **fails** on them where a plain
  `openspec validate` only warns, and the name of the requirement that owns both.
- `openspec/specs/documentation-ownership/spec.md` — one MODIFIED requirement (the scenario rule gains the
  title sentence and the scenario that proves it) and one ADDED requirement (the entry document states the two
  rules the strict command fails on and names it), 7 scenarios, each naming one case of `evidence/probe.sh`.
- No tool, no gate and no CI step changes: the reporter of both rules is the validator, and this change makes
  the statement readable before the artifact exists.

## Impact

- Specs changed: `documentation-ownership` (one MODIFIED requirement, every scenario it still has carried
  verbatim, plus one ADDED requirement).
- Additive, not breaking: no command changes behaviour, no gate gains a case, no existing scenario is withdrawn.
  An author who already ran `--strict` first reads the rules one round earlier.
- Rollback: revert the change directory and the `AGENTS.md` block. No data, no published document and no
  classification entry is written by this change.

## Round 2 — the review's three blocking corrections

Review round 1 refused the change on three statements that were wrong or broader than what their cases tested,
and this change was corrected in place — the delta and its merged copy both, so no sentence is re-archived:

- **`--strict` reports, plain `validate` stays silent** — false. Measured on the merged capability spec,
  2026-10-22T02:56:09Z: plain `openspec validate documentation-ownership --type spec` exits 0 and prints
  `⚠ [WARNING] requirements[11]` / `[13] … Requirement text is very long (>500 characters)`, so what `--strict`
  changes is fatal-versus-ignorable, not reported-versus-unreported. The `AGENTS.md` line, the ADDED
  requirement and the retitled scenario *An over-long requirement is a warning until the strict command* now
  say that, and the case asserts both halves: plain exit 0 with the `[WARNING]` line, `--strict` exit 1.
- **"each artifact rule that `openspec validate --strict` enforces"** — a universal the entry document does not
  deliver: it states two. The requirement now says "the two artifact rules below" and keeps stating exactly
  those two.
- **three read cases asserted a bare substring** while their scenarios stated more. Each case now asserts every
  clause of its THEN: `verbatim` **and** `under their own titles`; `500 characters` **and** `requirement body,
  not its scenarios` **and** `an example belongs in a scenario`; and the change-set case now greps the
  requirement's name as well as the change's and the omitted scenario's.

The four mutations in `evidence/round2-checks.log` show each widened case is falsifiable on its own clause —
removing `under their own titles`, `An example belongs in a scenario`, and restoring the round-1 `--strict`
sentence each turn exactly one case red (`6 passed, 1 failed`), and the whole block's removal turns the three
read cases red (`4 passed, 3 failed`). The two `⚠ [WARNING] requirements[11]` / `[13]` lines it records are the
capability's own pre-existing debt (bodies 724 and 529 characters, untouched by this change); the corrected
requirement's body is 483 characters and reports nothing.

## Surface (Phase A step 5)

surface: 0 files name the command, 0 suite cases assert either rule's statement, 7 probe cases added, 0 cases
rewritten, 0 fixtures realigned — `grep -rln --include='*.md' --include='*.sh' --include='*.yml' --include='*.yaml'
-- '--strict' . | grep -v openspec/changes/archive/ | grep -v app/src/main/cpp`, the same `grep` for
`artifact hygiene`, and `grep -c 'probe#' tools/*-cases.sh tools/*-selftest.sh` (`evidence/surface.txt`).

The single hit for `500 characters` in that log is this change's own probe, which asserts the statement's
absence. The rule's *subject* is stated in 3 live files (`AGENTS.md`, `guidelines/Build.md`,
`.github/workflows/build.yml`) and owned by 1 capability spec; 2 documents are edited.
