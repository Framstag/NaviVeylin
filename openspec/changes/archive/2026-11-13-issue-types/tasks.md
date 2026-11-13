# Tasks

Reference: `proposal.md` for the axis and the measured state, `specs/issue-backlog/spec.md` for the contract, and
`design.md` for the four decisions. Every box below was executed before this file was written: the work is applied on
`feat/github-issue-types` (`f92d5bb8`, `2841dfa2`) and the gate results are quoted with it.

## 1. The profile and the writers

- [x] 1.1 Declare `roles.containerType` in `tools/tooling.json` (`Epic`) and register it among the loop's optional role
      keys, so a profile that omits it reads as "no type is a container". Verify: `jq -r '.roles.containerType'
      tools/tooling.json` → `Epic`; `tools/check-portability.sh` sweeps the value and stays green.
- [x] 1.2 Add `--type` and `--parent` to `tools/issues/create.sh` and `tools/issues/update.sh`, plus `.type`/`.parent`
      in a finding, and shape-validate both (one word, digits). Verify: cases
      `create#writesTheTypeItWasGiven`, `update#writesTheTypeItWasGiven`.
- [x] 1.3 Add `issues_declared_types` to `tools/issues/lib.sh` and refuse an undeclared name with `type-opaque` before
      any write in both writers. Verify: the same two cases' refusal arms; a `gh issue create --type Bogus` probe filed
      #874 on 2026-11-12 and is the reason the check exists.

## 2. The reader

- [x] 2.1 Ask the tracker for `issueType` and `parent` in `tools/loop/lib.sh`'s per-item read, and give an item whose
      type is `roles.containerType` the verdict `container` in `tools/loop/prescreen.sh`, before every other
      judgement. Verify: `prescreen#aContainerTypeIsNotWork` — the container is refused, its sibling is queued, and an
      emptied role refuses nothing.

## 3. The documents

- [x] 3.1 Add the rule to `guidelines/Backlog.md` §3, separating the type from the body's `tier:` record, route it in
      `tools/doc-routes.json` and name §3 in the `AGENTS.md` backlog row. Verify: `tools/check-doc-routes.sh` green and
      `tools/check-rule-routes.sh` no longer names the rule as unrouted.

## 4. Falsification and gate

- [x] 4.1 Revert-check the writer: make the type check unconditional, `create#writesTheTypeItWasGiven` fails with
      `expected '3', got '0'`; restore, green.
- [x] 4.2 Revert-check the reader: empty `container_type`, `prescreen#aContainerTypeIsNotWork` fails with
      `expected 'container', got 'eligible'`; restore, green.
- [x] 4.3 Run the suites the change touches and record the tallies: `tools/issues/tests/issue-tooling-selftest.sh`
      170/170; `tools/loop/tests/loop-selftest.sh --group prescreen` 37/37 and `--group run` 148/148;
      `tools/loop/tests/loop-mutations.sh --check` 343/343; `check-doc-routes`, `check-portability`,
      `check-doc-values`, `check-todo-references`, `check-openspec-hygiene` green; `check-rule-routes` reports the two
      unrouted park rules it reports on `HEAD`.

## 5. The first users

- [x] 5.1 Type item #838 `Epic` and its seven filed children `Task`, and attach the children as sub-issues, through
      the flags this change adds. Verify: `gh api graphql` reads `issueType.name` = `Epic` on #838, `Task` on
      #867–#873, and `subIssuesSummary` = 7. (Dropping #838's `status/on-hold` is a person's, once this lands.)
- [x] 5.2 Close the accidental probe issue #874 through `close.sh --not-planned`, with the measurement it produced as
      its evidence.

## Not this change

- Retyping existing items. Only the item this change was measured on and its children carry a type.
- Closing a container when its children are done: the tracker has no such rule, and neither does this change.
- A second exemption path: the type is not a label, so it adds nothing the baseline or the vocabulary gate has to
  count.
