# An open change names the issue it is for

## Why

On 2026-11-15 two open changes described **#928** at once: `fix-a-case-wait-decided-by-its-record` (written 10:53 by
another session, naming the issue in its first sentence) and `fix-a-case-waits-on-the-state-the-run-writes` (written
off the merged tree by a second session). The second archived as
`2026-11-15-fix-a-case-waits-on-the-state-the-run-writes` and replaced the requirement
`A self-test case is held to a bound of its own`; the first then failed `openspec validate --strict`:

```
✗ change/fix-a-case-wait-decided-by-its-record
  agent-issue-loop: MODIFIED "A self-test case is held to a bound of its own" omits scenario(s) the current spec still has:
  "The stop wait is decided by the state the run writes"
```

The same collision is visible a second way in `fix-a-bound-ended-rework-spends-no-decision-slot`, whose `MODIFIED`
blocks now omit scenarios the shipped spec holds. Nothing in the tree states the relation *this change is the change
for issue #N* — the directory name sometimes says it (`fix-issue-898-…`) and mostly does not — and an open change
records no spec baseline, so a later archive invalidates an older change silently and only at the moment someone
tries to archive it.

Two of the eight `--changes --strict` failures on `e22d8503` are this class. The cost is a session's whole planning
half: a change written twice is a change written again from a spec that has moved.

## What Changes

1. **A change declares its item** (`openspec/changes/<name>/.openspec.yaml`): one field, `x-issue: <number>`, written
   by whoever files the change and refused by the hygiene check when a change directory carries neither that field
   nor a `fix-issue-<N>-` name that supplies one. `x-issue: none` is a declaration — a change that answers no tracker
   item — and the `x-` prefix is the CLI's own form for tool metadata: a bare `issue:` key is a WARNING that
   `--strict` counts as a failure, so it would make every change carrying it fail the validation this change's own
   last task requires.
2. **Two open changes cannot name one issue** (`tools/check-openspec-hygiene.sh`): the check reads every open
   change's field and refuses with exit 1 naming both directories when one number appears twice.
3. **The pre-screen refuses the item** (`tools/loop/prescreen.sh`): the item's own number is read against the open
   set before a branch is cut, from that same check's finding, so the collision is learned when the second change is
   written rather than by the archive that exposes it.
4. **The requirements** (`openspec/specs/documentation-ownership/spec.md`, `openspec/specs/agent-issue-loop/spec.md`):
   "An open change names the issue it is for" with three scenarios, and "The pre-screen refuses an item two open
   changes name" with one.
5. **The existing open set is backfilled** in the same change, because the refusal has no exceptions: the 9 changes
   open at this base get their field, 4 of them from the number their own proposal names and 5 declared `none`.

## Impact

- `openspec/changes/*/.openspec.yaml` — one field on each open change. `tools/check-openspec-hygiene.sh` — a fifth
  pass beside the four silent-failure classes. `tools/tests/check-openspec-hygiene-selftest.sh` — three cases.
  `tools/loop/prescreen.sh` — the refusal, and its comment on the item. `tools/loop/loop.conf` — the check's path as
  a project key. `tools/loop/tests/loop-cases.sh` — one case. `tools/loop/tests/loop-mutations.sh` — one record.
  `tools/issues/create.sh` — the refusal that sends a filer to `openspec new change` also states the field. No engine
  script beyond the screen, no guideline and no Android module is involved.
- The scaffolder option the first draft of this change planned is dropped: the pinned CLI refuses it (`openspec new
  change --issue 928 <name>` → `error: unknown option '--issue'`, openspec 2.0.0, a package outside this repository,
  measured in a throwaway `openspec/` root and creating nothing), so the field is written by whoever files the change
  and the refusal is the check.
- Rollback is the revert of the seven tracked files and the field: the field is additive, and a tree that carries it changes
  nothing about how a change archives.

## Evidence

- The finding: filed as issue #931, with both directories named and the refusal quoted verbatim.
- The two instances the finding was written from, as the item states them (neither is a directory of this tree):
  `openspec/changes/fix-a-case-wait-decided-by-its-record/` and
  `openspec/changes/fix-a-bound-ended-rework-spends-no-decision-slot/`, both red under `--changes --strict` on
  `e22d8503`.
- The rule this change obeys: `openspec/specs/documentation-ownership/spec.md`, "The validator's own strict rules are
  read before the first write" — the `ADDED` bodies are under 500 characters and the scenarios carry the detail.

surface: 4 scenarios over 2 delta files, 4 cases added (3 in `tools/tests/check-openspec-hygiene-selftest.sh`, 1 in
`tools/loop/tests/loop-cases.sh`), 9 open changes backfilled, 7 tracked files edited — counted with
`grep -c '^#### Scenario:' specs/*/spec.md`, `grep -c 'case_i=\|case_j=\|case_k=' tools/tests/check-openspec-hygiene-selftest.sh`,
`grep -H '^x-issue:' openspec/changes/*/.openspec.yaml | wc -l` and `git status --short | wc -l`.
