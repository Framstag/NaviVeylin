# Design — the type is the tracker's field, the container is the profile's word, and the tier is neither

## Context

The backlog's contract is "everything a reader selects on is a label" (`guidelines/Backlog.md` §3), and it is enforced:
`tools/issues/lib.sh` validates every label against the profile's closed vocabulary and refuses one outside it, and
`tools/check-portability.sh` forbids each declared value in every engine. A GitHub **issue type** cannot enter that
gate — it is not a label, it has no namespace in the profile, and it is declared once per account rather than per
repository — so the question is not *which namespace it joins* but *what may write it and what may read it*.

Two facts about the tracker decide the rest, both measured 2026-11-12:

- `gh issue create --type <unknown>` files the item and then refuses (`issue #874`). The CLI is therefore not a guard,
  and a writer that trusts it leaves an untyped item behind on a typo.
- The account's type list is readable (`query { repository { issueTypes { nodes { name isEnabled } } } }`), so the
  writer can ask rather than keep a copy.

## Decisions

### D1 — The type and the parent are flags of the tracker's own, not labels

`create.sh --type/--parent`, `update.sh --type/--parent`, and `.type`/`.parent` in a finding. They do not pass through
`issues_check_labels`, because that gate's contract is the profile's vocabulary and a type name is the account's
configuration. The alternative — a `type/*` label mirroring GitHub's field — was rejected: it would be a second copy
of a value the tracker already holds, and the copies drift the moment somebody types an item in the web UI.

The shape is validated where the vocabulary cannot be: one word for a type, digits for a parent. Everything else is
the tracker's answer.

### D2 — The writer asks the account before it writes

`issues_declared_types` reads the enabled type names and both writers refuse an undeclared one with `type-opaque`,
naming the set, **before** any write. `gh`'s own refusal is not enough (D-context: it files first). A read that cannot
be made returns the seam's status — `75`, `ISSUES_EXIT_UNREACHABLE` — rather than an empty list, because "this account
declares no types" and "the tracker did not answer" are different facts and only the first may refuse a name
(invariant I17).

*Alternative eliminated:* create first and retype afterwards. That is exactly the half-write the probe produced, and a
failed retype would leave an item that looks filed.

### D3 — The reader's word is one verdict, and it is the engine's, not the profile's

`prescreen.sh` gives an item whose type is `roles.containerType` the verdict `container`, before the authorisation,
state and tier gates. Like `unreadable`, that verdict is not a profile role: it names no judgement about a value of the
project's vocabulary, it names the tracker's shape. An item of any other type — `Feature`, `Task`, `Bug`, untyped — is
work exactly as before, which the case asserts beside the refusal.

The value itself is read from the profile, so no engine spells a type name and a project whose account has none
declares the key empty.

### D4 — `tier` and `type` are two axes and the document says so

The body's `tier: a|b` says whether an item is worked as a cause or as a change. The issue type says what the tracker
calls it. An item may be either tier under any type: an Epic is a container whatever the shape of its children, and a
tier-B child of an Epic is an ordinary Task. The rule in `Backlog.md` §3 states this, and so does the profile's
`roles.comment`, because two names that share a word are how a later reader "unifies" them.

## Open decisions

none

## Verification

- **The writer's half, falsified.** With the type check made unconditional, `create#writesTheTypeItWasGiven` reports
  `an undeclared type name is refused: expected '3', got '0'` — exit 0 and an item created, which is #874 reproduced
  in the suite.
- **The reader's half, falsified.** With `container_type` emptied, `prescreen#aContainerTypeIsNotWork` reports
  `expected 'container', got 'eligible'`.
- **The gate.** `tools/issues/tests/issue-tooling-selftest.sh` 170/170; the loop suites' `prescreen` (37) and `run`
  (148) groups; `tools/loop/tests/loop-mutations.sh --check` 343/343; `check-doc-routes`, `check-portability`,
  `check-doc-values`, `check-todo-references` and `check-openspec-hygiene` green. `check-rule-routes` reports the two
  unrouted park rules it reports on `HEAD`, unchanged by this change.
- **No device evidence.** Nothing in the app, the car host or the native tree changes, so no logcat line, geometry or
  `pixel-check` verdict is owed. Named so the absence is a decision.
