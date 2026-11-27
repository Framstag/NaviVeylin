# Design — the counted set, and the one read that covers it

## Root cause

One mechanism: `issues_issue_list` (`tools/issues/lib.sh:315`) fixed the window at 400, and
`issues_page_records` (`tools/issues/lib.sh:908`) derived *both* the read count and the number it compared
it against from that same answer (`count=$(… | jq 'length')`, `tools/issues/lib.sh:913`). A comparison of
two readings of one truncated answer is the same number on both sides, so the refusal at
`tools/issues/lib.sh:926`–`:928` could never fire, and 339 of today's 739 items are never examined.

## The single fix

Count the set, then read it — the shape `tools/issues/decisions.sh:71`–`:95` already carries for the
decision queue (#805), applied to the tracker-wide read instead of a label-scoped one:

1. `issues_set_count` asks `repository(owner, name) { issues(states: [OPEN, CLOSED]) { totalCount } }`
   and returns that number. No label scope, because this read is not about one label's set.
2. `issues_issue_list [<window>]` asks for the window it is given, and defaults to the counted set plus
   one so the two call sites that read the list directly for dedup (`tools/issues/create.sh:383`,
   `tools/issues/import.sh:54`) inherit the counted set with no edit of their own.
3. `issues_page_records` takes the count itself, reads `count + 1`, keeps the partial-page check as the
   answer's own length, and turns the vacuous comparison into a live one: a read of `count + 1` items is a
   read the tracker overran, refused as `issue-set-uncovered` (`tools/issues/lib.sh:951`–`:954`). The
   refusal goes to stderr, because this read is taken inside a command substitution: on stdout it would be
   captured as the number the caller thinks it was handed.
4. The two callers of that read (`tools/issues/prioritise.sh:103`, `tools/issues/classify.sh:85`) carry the
   read's own exit status with `|| exit $?`, so a refused read stops the pass instead of the pass examining
   whatever did arrive — the one line a live refusal needs in order to mean anything.

The stub `gh` used to refuse a `totalCount` query that carried no label, which is the arm this read needs;
the fix adds the repository-wide arm beside the label-scoped one
(`tools/issues/tests/fixtures/gh:235`), so a case can watch both. `GH_STUB_COUNT_LAG` is the existing race
fixture and needs no change.

## Blast radius

- `tools/issues/lib.sh` — two functions added/replaced, one guard made live, the refusal on stderr.
- `tools/issues/prioritise.sh`, `tools/issues/classify.sh` — the read's status carried out, one line each.
- `tools/issues/tests/fixtures/gh` — the graphql count arm gains the unlabelled (whole-set) branch.
- `tools/issues/tests/issue-tooling-cases.sh` — one case, registered in the index list and the dispatcher.
- `guidelines/Backlog.md` §6 — the "selection never samples" bullet gains the counted-set clause, because
  the rule it states is the one this change implements.

Every caller of `issues_page_records` (classify, prioritise) and of `issues_issue_list` (create, import)
keeps its call site; what each now pays is one graphql count per read, which is the price of a set read
that can be checked. Three other reads still name a constant window — `tools/issues/check-metadata.sh:150`
(`--live`), `tools/sensors/lib.sh:476` and `tools/loop/run.sh:674`'s `list.sh --limit 400`. They are not
callers of this read: each serves a different claim and owes its own case, and this change names them in
its report so the reviewer meets them named rather than as a gap in its own blast radius. `tools/issues/labels.sh:53`
counts labels, not items, so the delta does not reach it.

## Alternatives the evidence eliminated

- **Raise the window to a larger constant** (e.g. 2000): the defect is not the number. A tracker that
  outgrew the new constant would return to the same silent partial read, and the guard would stay vacuous
  because both sides would still come from the answer.
- **Page the list with `--paginate` and concatenate**: it removes the truncation but leaves the coverage
  unread — nothing states how many items the tracker holds, so nobody can tell a complete read from a
  cut one, and a case cannot assert the number that was read.
- **Count by reading `gh issue list` first with a `--limit 1` and a `--json number` total**: the CLI
  answers no total, so the count would have to come from a second full read — the same truncation.

## Rollback

The change is one commit over three files; reverting it restores the fixed window, and the only behaviour
that returns is the silent partial read (the guards keep comparing the read to itself).

## Red test

`prioritise#readsEveryIssueNotTheNewestPage` — a fixture tracker of 405 issues, all due, whose oldest item
is `#1` (and therefore outside the newest 400). On the base tree the pass reports `.read` = 400 and no
record for `#1`; with the fix it reports 405 and carries `#1`'s record. Its second half exports
`GH_STUB_COUNT_LAG=1` and asserts exit 3 with `issue-set-uncovered`.
