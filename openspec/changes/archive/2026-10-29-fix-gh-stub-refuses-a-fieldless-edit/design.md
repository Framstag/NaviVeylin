# Design

## Root cause

The stub `gh`'s `edit` arm validates nothing: `tools/issues/fixtures/gh:272-276` routes every `issue edit` to
`record_write` and exits 0, so the stub answers an invocation the real CLI rejects — `field to edit flag
required when not running interactively`. The stub is the only `gh` the suite ever runs, so no case can see
that refusal, and a path that assembles an empty edit is green here until somebody runs the real client by
hand. #165 reached the tracker exactly this way; its tool-side clause is fixed (`edit_needed`,
`tools/issues/update.sh:192-196`), its fixture-side clause is this item.

## The single fix

`tools/issues/fixtures/gh` gains `check_edit_field`, beside `check_close_reason` and for the same reason: the
stub's job is to mirror the client's vocabulary, not to accept anything it is handed. It returns as soon as one
of `--add-label`, `--remove-label`, `--body-file` is present — the three flags every writer in the tree uses
(`update.sh:177-189`, `close.sh:175`, `reopen.sh:111-113`, `prioritise.sh:332`, `loop/labels.sh:133-135`) — and
otherwise prints one `stub gh:` line naming them and exits 1. It is called from the `edit` arm **before**
`record_write`, so a refused call leaves the log untouched, which is what makes "nothing was written" assertable.

Only those three flags are accepted, not the CLI's full `issue edit` surface (`--title`, `--body`,
`--add-assignee`, `--milestone`, …), because the item's expected behaviour names the three and no caller in the
tree uses another: a wider list would be invented surface with no case behind it. A future path that needs
`--body` adds it in one line, and its case fails first.

`exit 1` matches `check_close_reason`: the stub's refusals are all exit 1 with a `stub gh:` line, and the
`refuse()` helper's "unsupported invocation / add the fixture" wording would misdescribe this one — the
invocation is supported, its arguments are not.

## Blast radius

- The fixture: 20 inserted lines (one function, one call line, three header lines).
- `tools/issue-tooling-cases.sh`: one case, `surface#theStubRefusesAnEditWithNoField`, plus its two array
  entries.
- Five drivers put this stub on `PATH` — `tools/issue-tooling-selftest.sh`, `tools/loop-selftest.sh`,
  `tools/resolve-stale-selftest.sh`, `tools/sensor-selftest.sh`, `tools/check-portability-selftest.sh` — and 47
  assertions across their case files name `issue edit`
  (`grep -c 'issue edit' tools/issue-tooling-cases.sh tools/loop-cases.sh tools/resolve-stale-cases.sh
  tools/sensor-cases.sh` → 32, 5, 9, 1). Every one of them drives a writer that passes a flag, so every one
  records what it recorded before. `GH_STUB_FAIL` (`tools/issues/fixtures/gh:129-132`) is matched inside
  `record_write` and therefore still applies to a legal call.
- No production code, no Kotlin, no native, no engine.

## Rollback

Delete `check_edit_field` and the `[ "$sub" = "edit" ] && check_edit_field "$@"` line, drop the case and its
two array entries, and revert this change's delta. Nothing reads the stub outside the suites, and the fixture
carries no state.

## Alternatives the evidence eliminated

- **Make the tool-side guard the case's subject** — that is `update#commentOnlyReportsNoStatusWrite`,
  `tools/issue-tooling-cases.sh:1742`, and it is already green; it asserts the call log, not the client's
  vocabulary, so it cannot see a new path that assembles an empty edit.
- **Call the real `gh` from a case** — impossible in this suite: it runs offline with no token (design D9), and
  `lib#onlyTheSeamCallsTheRealCli` (`tools/issue-tooling-cases.sh:500`) keeps the real client behind the seam.
- **Refuse every `issue edit` the tooling does not issue** — a stub that enumerated the callers would go red
  when a caller legitimately gains a flag; the flag *set* is the invariant, not the exact invocation.
