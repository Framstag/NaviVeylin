# Tasks — a served context is a pointer

Parent requirement for every task below: `documentation-ownership`, "The project context has one owner"
(this change's delta, `specs/documentation-ownership/spec.md`), and for 1.2's routing statement the shipped
requirement "The change-artifact guidance reads by section"
(`openspec/specs/documentation-ownership/spec.md:72`).

## 1. The pointer and the facts it points at

- [x] 1.1 Replace the `context:` block in `openspec/config.yaml` with three lines that name `AGENTS.md` and its
      "Documentation Map"; leave `rules:` and `operations.*.guidance` untouched. **Verification:**
      `openspec instructions apply --change a-served-context-is-a-pointer --json | jq -r .context` prints the
      pointer, and `jq '.operationGuidance | length'` still reports 23
      (`evidence/context-payload.txt`). Scenario "The served context still routes by section".
- [x] 1.2 Absorb the four facts the block alone carried into the sections that own them in `AGENTS.md`: the
      upstream repository and libosmscout's Android-free property into "Native Integration", the `python3` ban into
      "OpenSpec Workflow", the loop's eligibility rule into "Agent iteration loop". **Verification:**
      `grep -n 'Framstag/libosmscout\|Android-free\|python3\|exactly one fix' AGENTS.md` returns one hit per fact.
      Scenario "The served context still routes by section".
- [x] 1.3 Repoint the three skill citations that sent a reader to `openspec/config.yaml` for the `python3` ban
      (`.pi/skills/triage-todo/SKILL.md`, `.pi/skills/cleanup-todo/SKILL.md`,
      `.pi/skills/openspec-open-changes/SKILL.md`) at `AGENTS.md`. **Verification:**
      `grep -rn 'config.yaml.*forbids\|project rule, .openspec/config.yaml' .pi/skills/` returns nothing, and
      `tools/check-doc-routes.sh` still resolves every skill reference.

## 2. The check that holds the pointer

- [x] 2.1 Add the sixth class to `tools/check-openspec-hygiene.sh`: a `context:` block that names no `AGENTS.md`,
      or that carries more than three non-empty lines, is reported; an absent block is not a finding. Header,
      verdict sentence and `--help` range updated with the class. **Verification:** over the repository the check
      reports nothing for `openspec/config.yaml`; over a throwaway tree whose block is the six-line restatement it
      prints `the 'context:' block is 6 lines` (scenario "A copied context block is reported").
- [x] 2.2 Add case `artifact-hygiene#aContextBlockThatCopiesTheFactsIsRefused` to
      `tools/tests/check-openspec-hygiene-selftest.sh`, with both arms — the six-line copy and the three-line block
      that names no owner — and put the passing pointer into the clean tree. **Verification:**
      `bash tools/tests/check-openspec-hygiene-selftest.sh` → `all 11 cases passed` (`evidence/selftest.txt`);
      `--case aContextBlockThatCopiesTheFactsIsRefused` runs the two refused shapes alone. Scenario "A copied
      context block is reported" and scenario "A block that names no owner is reported".

## 3. Verification a delta with no compiled file owes

- [x] 3.1 Run the structural checks instead of a Gradle build, because the delta touches no compiled file
      (`guidelines/Build.md` §2, the scope block): `tools/check-doc-routes.sh` (356 section references, 7 skill
      references), `tools/check-rule-routes.sh` (651 named rules), `openspec doctor`, and
      `tools/check-openspec-hygiene.sh` over the repository. **Verification:** each exits 0 or reports only the
      pre-existing class-5 findings of the open change set (`evidence/structural-checks.txt`). Exclusions named: no
      `:app` build, no unit or instrumented suite, no ABI, no device run — none can be affected.
- [x] 3.2 Existing tests still pass for the artifact the change edits: the checker's own self-test, 11 cases, was
      10 before this change and none of the ten was modified. Exclusion: the Gradle suites are not run (no compiled
      file changed, §3.1).
- [x] 3.3 Revert-check for the new invariant: mutate `if (n > 3)` to `if (n > 99)` in the checker,
      `bash tools/tests/check-openspec-hygiene-selftest.sh --case aContextBlockThatCopiesTheFactsIsRefused` must
      fail, restore the line, and the case must pass again. **Verification:** the three runs recorded in
      `evidence/revert-check.txt` (red, restored, green).
- [x] 3.4 Map every scenario of the delta to the case or command that exercises it: "A copied context block is
      reported" and "A block that names no owner is reported" → the two arms of
      `artifact-hygiene#aContextBlockThatCopiesTheFactsIsRefused`; "A pointer passes and an absent block is not a
      finding" → `artifact-hygiene#aWrittenPurposePasses` over the clean tree that carries the pointer;
      "The served context still routes by section" → the `openspec instructions … --json` read-back of 1.1. No
      scenario is left to a task nobody runs.
- [x] 3.5 Update the guidelines the change supersedes: `AGENTS.md` is edited by 1.2 and states the pointer in its
      "OpenSpec Workflow" section; `guidelines/Build.md` and `guidelines/BuildEvidence.md` are read, not changed —
      their claims about the config's *guidance* are still true (`guidelines/Build.md:9`,
      `guidelines/BuildEvidence.md:369`). Verified with `grep -n 'config.yaml' guidelines/Build.md
      guidelines/BuildEvidence.md`.
