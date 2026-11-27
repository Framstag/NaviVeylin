# Design — one read, counted, and `--limit` for a caller that wants a page

## Root cause

One mechanism: `tools/issues/list.sh:49` is the window both of the tool's reads ask for, and it was a fixed
`400` — the marker lookup at `:95` and the filter selection at `:147`. The list the CLI answers is newest-created
first, so on a tracker larger than 400 the oldest items are never in the answer, and the tool has no second
number to compare the read against: `--id N` for a marker below the boundary becomes the exit-4 "this entry
does not exist" answer, and every filtered selection is drawn from a page rather than from the set the labels
name. Nothing in the output names the window, so the caller cannot tell a small set from a truncated read.

## The single fix

Ask both questions of one answer that covers the counted set:

1. `tools/issues/lib.sh` — `issues_issue_list` (`:340`) asks for `state` and `issueType` beside the fields it
   already names, so the one answer serves the filter read's `.state` and `.issueType` queries as well as the
   marker read's bodies. Its window parameter is unchanged.
2. `tools/issues/lib.sh` — `issues_issue_list_whole` counts first (`issues_set_count`, `:315`), reads
   `count + 1` through `issues_issue_list`, and refuses an answer that full as `issue-set-uncovered`, naming the
   counted set and the read. The refusal goes to stderr, because the read is taken inside a command
   substitution. This is the shape `issues_page_records` (`:963`) already carries; that function keeps its own
   copy because it decodes bodies into a scratch directory, which a listing does not.
3. `tools/issues/list.sh` — `limit` defaults to empty instead of 400, and a helper beside the two branches takes
   the one read each of them answers from (they are exclusive, so an invocation still pays one call):
   `issues_issue_list "$limit"` when the caller named a window, `issues_issue_list_whole` otherwise.
4. `tools/loop/prescreen.sh` and `tools/loop/run.sh` — the three pins that passed `--limit 400` (and
   prescreen's default at `:39`) drop it, so those callers inherit the counted set instead of a page of it.

`--limit` is kept rather than removed: it is the caller's explicit word for "I want a window", and a tool that
refused to answer that could not serve a caller reading one page for display.

## Blast radius

- `tools/issues/lib.sh` — one field list widened, one function added.
- `tools/issues/list.sh` — the default window, one read instead of two, the doc header's account of it.
- `tools/loop/prescreen.sh` — `limit=400` at `:39` becomes empty, and the read at `:262` passes `--limit` only
  when the caller named one.
- `tools/loop/run.sh` — three `--limit 400` arguments dropped at `:674`, `:679`, `:684`.
- `tools/issues/tests/issue-tooling-cases.sh` — one case, registered in `ISSUES_SHELL_CASES` and in the
  dispatcher.
- No fixture change: the stub `gh` already slices newest-created first for `--limit` (`tools/issues/tests/fixtures/gh:351`–`:366`)
  and already answers the repository-wide `totalCount` query (`:225`–`:251`), both of which the sibling change
  `fix-the-whole-set-read-counts-its-set` added.

Every other caller of the list is unchanged: `tools/loop/prescreen.sh` reads the answer's items, not its
length, so a bigger answer costs it one graphql count per read and nothing else. The three `list.sh --limit 400`
reads in `tools/loop/run.sh` that compare `.length` against a cap now count the set they claim to count — which
is the point of the change, and the reason their old numbers were a sample.

## Rollback

Revert the four files and the case. `--limit 400` restores the previous window exactly, because the flag is
kept; nothing outside the tooling's own reads depends on the default, and no tracker state is written by this
change.

## Alternatives the evidence eliminated

- **Raise the constant** (2000, or the tracker's size at the time of writing): the defect is that the window is
  also the claim about the set. A tracker that outgrew the new number returns to the same silent partial read,
  and the tool would still have no second number to compare the read against.
- **Page with `gh --paginate` and concatenate**: it removes the truncation and leaves the tool without a
  coverage claim, so a read that stopped early (a transient failure on page 3) is indistinguishable from a
  complete one. The counted read is the shape the codebase already uses to make that distinction.
- **Count in `list.sh` and keep its own `gh issue list` calls** (the shape `decisions.sh:71`–`:95` carries for a
  label-scoped queue): it duplicates the read the whole-set reader exists to own, and two copies of
  "count, read, refuse" drift. The reader is asked for the set, and the one function that adds the coverage
  check sits beside it.
- **Leave `--limit 400` in the three loop callers and fix only `list.sh`**: the pins would keep passing a window
  explicitly, so the two halves the item measured — the `--id` lookup and the filtered selection — would be
  answered differently by the same tool depending on who called it.

## The guideline section this change owes

`guidelines/Backlog.md` §6 states the rule already, at `:398`–`:401`: selection "examines ... the tracker's own
**counted** set — counted before the read, and a read that comes back as full as the window it was asked for is
refused rather than reported as coverage". This change owes no edit there: the sentence was written by
`fix-the-whole-set-read-counts-its-set` for the page-records read, and this diff makes the listing read carry the
same rule, which is what the section already says. Adding a second sentence for the second reader would state one
rule twice.

## Not repaired here, named instead

The three reads in `tools/loop/run.sh:674`, `:679`, `:684` and `tools/loop/prescreen.sh:262` discard stderr
(`2>/dev/null`) and read the answer as a count, so a refusal on the counted read (`issue-set-uncovered`, or a
tracker that does not answer the count) reaches them as an empty list — the decision cap reads it as "no open
decisions", and the pre-screen as "no candidates". That tolerance predates this change (an unreachable tracker is
treated the same way there today), and making it loud is a decision about how a run ends, not about which set is
read. It is reported as a `NOTE` rather than repaired here.
