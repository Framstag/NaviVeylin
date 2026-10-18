---
name: update-issue
description: Change an existing tracker issue's labels, its status label or its body — and nothing else. Use when a classification, a state or a description is wrong. Do not use it to authorise work or to close an issue.
---

# update-issue

Three editable fields, one call, and a refusal for anything outside the project's closed vocabulary.

**Prerequisites.** `gh` authenticated as an allowlisted author, `jq`, a JDK ≥ 17 on `PATH`
(`tools/issue-tooling-selftest.sh` runs the same surface offline).

## When to use it, and when not to

Use it to **classify** (the kind or the area is wrong), to **move the state** (`status/*`), or to **fix the
description** (the body was wrong or incomplete).

Do not use it to:

- **authorise work.** `agent/eligible` is reserved: every write path refuses it (exit 3, `label-reserved`).
  A person applies it in the GitHub UI.
- **close an issue** — that is `close-issue`, and it requires evidence.
- **rewrite someone else's finding into another finding.** If the claim changed, file a new issue and let the
  old one be closed with its reason.

## The call

```bash
tools/issues/update.sh 42 --label category/car                  # add one label
tools/issues/update.sh 42 --status open-device                  # set the status value
tools/issues/update.sh 42 --body finding.md                     # replace the body from a file
tools/issues/update.sh 42 --label class/bug --label source/ci    # several labels at once
tools/issues/update.sh 42 --status open-device --json            # one JSON document
```

At least one of `--label`, `--status`, `--body` is required. `--status` is a **value** (`open`,
`open-device`, `unverified`, …), not a label; the script composes the namespaced label from the profile.

`--status` **replaces** the item's state: the script reads the item, removes every other value of the status
namespace in the same `gh issue edit` that adds the new one, and adds nothing when the item already carries the
value asked for (then it writes no label at all and says so on stderr). So one call can never leave two
`status/*` labels on an item, and a caller does not have to remember which state to remove. `--json` reports
the displaced values under `removedLabels` beside the ones the caller named. Naming two states in one call —
a `status/*` `--label` beside `--status`, or a `--remove-label` cancelling the value it sets — is refused with
exit 2 before anything is read or written, because which state the item would carry is a decision.
(`fix-status-label-replacement`; the invariant is I16 in `guidelines/Backlog.md` §4.)

## The closed vocabulary

Every label is checked against the profile (`tools/tooling.json`) before any `gh issue edit` is issued:

- a value the vocabulary does not contain → exit 3, `label-opaque`, and **no write happens**. A label created
  by a typo would silently hide the issue from every selector, so a typo is a refusal rather than a new label.
- a reserved label → exit 3, `label-reserved`, and no write happens.

## Which change owns the status transition

`update.sh` writes the label and completes the replacement; it does not decide the transition. The lifecycle is
owned by the capability that moves an item:

| transition | owner |
|---|---|
| `status/open` → `status/in-flight` | whatever is working the item (the loop's script, or a person) |
| `status/in-flight` → `status/fixed-by <change>` | the change that landed the fix |
| any status → closed | `close-issue` only, with a reason and evidence |
| a declared check reopening its own item | the check registry, and only a closure recorded as a completed fix |

Setting `status/fixed-by` is therefore a claim that a **specific named change** addressed the residue; put
the change name in the body, because a label cannot carry it.

## Exit codes

| code | meaning |
|---|---|
| 0 | updated |
| 2 | usage, nothing to change, or two states named in one call |
| 3 | policy refusal (`label-opaque`, `label-reserved`, `author-not-allowlisted`) — nothing was written |
| 1 | `gh` itself refused the edit — read its message |
