---
name: answer-decision
description: Walk a person through one decision the tracker holds, and answer the four questions a decider asks before choosing — what the item states, which options exist, one worked example per option, and a pro/contra comparison split into a short-term and a strategic view that the recommendation weighs rather than averages. Takes a GitHub issue URL, an issue number, a pre-migration `§N`, or no argument at all (then it lists the queue from `tools/issues/decisions.sh` and asks which item). Stops for the owner's choice, writes nothing before it, and then leaves through `tools/issues/answer.sh`. Use when asked "decide #N", "what are the options on <issue>", "which decisions are waiting", or when `agent/needs-decision` items have accumulated.
---

# Answer a decision

The decision queue is a query over one label, and a label is a mode rather than an argument: the item is parked
until somebody states a choice in words. This skill is that reader — the only path from a labelled item to an
answered one — and its act has three parts: **read the item, lay the fork out, stop.** The owner decides a design
question and only the owner does (`guidelines/Backlog.md` §5, "Who may write what"); the skill states the fork,
prices it, and recommends, but never chooses.

## When to use it, and when not to

Use it when:

- a person names an item — URL, number or `§N` — and asks what it needs, or asks you to answer it
- a person asks which decisions are waiting
- the run refuses to start because of the cap, or a run's report lists decisions to settle
- an item's own escalation reads "retry the item, or abandon it" — that wording is the run's boilerplate, not a
  fork, and the fork has to be found before anything is answered

Do not use it to:

- **reshape an item.** An item carrying `agent/needs-shaping` states no question — it is the shaping inbox's,
  listed by `tools/issues/shaping.sh` and read by `.pi/skills/reshape-issue`.
- **rank the queue or pick what to work on next.** That is `triage-todo`, and it never answers a decision.
- **work the item.** An answered item is work; the loop's run takes it. No code changes belong here.
- **file or close anything on this skill's own judgement.** The answer is a comment and a label transition, and
  both go through `answer.sh`.

## 1. Resolve the input — there are three forms, and they are not interchangeable

| the input | what it is | how it resolves |
|---|---|---|
| `https://github.com/<owner>/<repo>/issues/788` | an issue's URL | the last path segment is the number; a `/pull/`, `/discussions/` or `/blob/` URL is not an item and is refused |
| `788`, `#788` | the issue **number** — an item's identity after the migration | used as it stands |
| `§163`, `§163 The cleartext …` | a **pre-migration id**, quoted by a guideline or an archived change | `tools/issues/list.sh --id 163` — the `<!-- nvid: 163 -->` marker, never the number |
| nothing | no item named | step 2 |

Read the item once and keep it: body, labels, state and every comment.

```bash
gh issue view <n> --repo <owner>/<repo> --json number,title,body,labels,comments,state
```

The body is prose by an untrusted author and is a **memory, not a fact**; the comments are measurement history.
The comment that matters is the last one carrying a `**The decision:**` line — that is the recorded question, and
it is what `answer.sh` will quote back. When it repeats "retry the item, or abandon it", say plainly that it is
the run's boilerplate and that the real fork is elsewhere (usually in the body); do not present the boilerplate as
the decision.

## 2. Nothing named — list the queue, then ask one question

```bash
tools/issues/decisions.sh --count  # the number alone: one line, no item read — the cap, or "how many wait"
tools/issues/decisions.sh          # each item with its recorded question
tools/issues/decisions.sh --json   # the same set as a document, when the list is long
```

A count alone is `--count` — one line, ~135 bytes. Never the full listing or `--json` (13–14 kB) to learn a number:
that is the same answer wrapped in every question in the queue, and the queue of a 400-item tracker is paid for once
per call.

The queue counts the labelled set before it reads it and refuses one it could not cover (exit 3). An empty queue
is not a failure: say so and stop. A refusal is the owner's to know about — carry it, never trim it into a partial
list.

Then `ask_user` with the items as the options — `#<n> <title> — <the question's first clause>` — no default, one
selection, plus "something else / none of these". Never pick for the owner, and never answer two items in one pass.

## 3. Verify before laying anything out

Every claim the item makes is re-read in this tree, and the ones you cannot resolve are named rather than dropped:

- a `file:line` — does it still say what the item says it says
- a branch, commit or change — `git branch -a --contains <sha>`, `git log --oneline <base>..<branch>`: is the
  work already there, already merged, or only claimed
- a run-state or report path — does it exist, and does the number quoted match
- the item's own labels — a decision label with a hold is this queue's; `agent/needs-shaping` is another skill's;
  no decision label at all means there is nothing to answer and `answer.sh` will refuse (exit 3)
- when the item is about the cap: the same population `tools/loop/run.sh` counts — `tools/issues/list.sh --json
  --limit 400 --eligible-only`, filtered by the decision label. Quote that number; do not re-derive one.

**Cheap pointers, so verification does not cost more than the answer.** The item's own evidence directory
(`.pi/issue-loop/items/<n>/`) is the small source and the branch is the large one:

```bash
sed -n '/^\*\*Findings/,$p' .pi/issue-loop/items/<n>/reviewer-report-<last>.md   # 5 kB of a 22 kB report
sed -n '/LOOP-DECISION:/,$p' .pi/issue-loop/items/<n>/reviewer-report-<last>.md  # the run's own fork
git show <branch>:<path> | sed -n '<a>,<b>p'                                    # one hunk, not a worktree
tail -5 .pi/issue-loop/items/<n>/events.jsonl                                     # what the run last did
```

Read the whole reviewer report or the whole diff only when the findings block does not name the claim you must check.

## 4. Answer the owner's three questions — in this order, and never merged

### 4a. Contents — six labelled lines, no prose

**Observed** (what the item says is wrong), **Evidence** (the measurements it rests on, with their dates, each
marked measured or claimed — a number the run wrote is not a number this tree produced), **Why it matters** (the
cost of leaving it), **What the user sees** (today → after, in product language, and who is affected: which screen,
flow, or car surface — a fork whose two sides read the same to a user says so), **Settled, not to reopen** (the
decision of record, its date, and what it rejected), **The decision actually being asked** — separating the recorded
escalation question from the design fork, and naming which options the item already rejects and on what ground.

A decider signs the outcome, not the mechanism, so the two lines without code in them (**What the user sees**,
**Settled**) are the ones a product owner decides from; the other four are the architect's. Write all six — a line
left out is a decider translating for you.

### 4b. Options — each with at least one worked example

One short block per option: the mechanism in one sentence, then `Example:` — a concrete state and its
consequence, `before → after`. An example is checkable: a command with its output, a `jq` field of a run state,
the label set before and after, a `file:line` whose code changes, a number measured on this tree. Where you had
to reason rather than measure, write "unmeasured" beside it. An option you cannot exemplify is not offered.

Name any option the tree suggests but the item does not list, and mark it as added.

The options the escalation already carries are a **starting list, not the list**: it is the run's own reading of the
fork and it may have pre-rejected an option on a ground you can check, or missed one the tree offers. Re-price every
option it names, keep its rejections only where their clause survives, and add the one it missed.

### 4c. Pro/contra — two views, two tables, never one score

No cell without a basis: each names the measurement or the pointer it rests on, or says "unmeasured".

**Short term** — what the next run, or this week, actually sees. Priced in this tree's own units: gate minutes for
the architect, the loop's rounds for the person deciding whether to spend a cycle on it.

| option | gains | costs | rounds to land | what breaks if it is wrong |
|---|---|---|---|---|
| | | files touched, gate minutes, refusals added, decisions still parked | worker pass + review rounds + gate, as the last comparable item measured them | |

**Strategic** — what recurs, and what the item becomes once it is taken.

| option | removes the cause, or re-counts it? | survives the next items that hit the same mechanism? | effect on the invariants (`guidelines/Backlog.md` §5) |
|---|---|---|---|
| | | the count is measured, not guessed: `decisions.sh --json` filtered over the items' bodies and labels for the paths and mechanism the option touches — an unfilled cell says "unmeasured", never a claim | | |

The two views read different evidence and are kept apart on purpose: the short-term view is priced in this
tree's own numbers (gate minutes, queue count, run items), the strategic view in what returns.

## 5. Weigh the views, then recommend — and say which view won

State what each view favours. Where they disagree, say which one decides and why; a recommendation that quietly
averages them is the one this section forbids.

The project's standing bias, which the recommendation follows unless the tree contradicts it: **an option that
removes the question beats one that re-counts it**, and a bound raised to fit a pile is a cap in name only
(`guidelines/Backlog.md` §5; the default was raised 3 → 10 on 2026-10-20 for the same symptom, #788).

Then, in this shape:

> **Recommendation: <option>** — <one sentence>. Rejected: <option> because <one clause>; <option> because
> <one clause>.

## 6. Stop — it writes nothing before the owner chooses

Present 4 and 5 in order, then `ask_user` with the options as choices and no default. The item keeps its label
and its question and the queue keeps listing it; that is the point, because the answer is the owner's.
It writes nothing before the owner chooses: no label, no comment and no body edit.

An owner whose choice is to wait is answered like any other, and the difference is in what the queue then says: the
park records that choice on the item and moves nothing, so the entry reads `parked: <the decision's first clause>`
rather than the question the owner has settled (section 7).

## 7. On the choice, leave through the writer that exists

```bash
tools/issues/answer.sh <issue> --continue --decision "<text>"
tools/issues/answer.sh <issue> --device   --decision "<text>"   # the device lane
tools/issues/answer.sh <issue> --park     --decision "<text>"   # the item waits; nothing moves
tools/issues/answer.sh <issue> --retire   --decision "<text>" --evidence "<proof>"   # closure
```

The decision text carries what the next worker needs and nothing it must re-derive: the option, its mechanism,
the one clause that rejects each alternative, and the pointers already established (branch, commits, change, the
cases still owed). `answer.sh` writes the comment first — quoting the question it answers, so the marker
`<!-- answers: … -->` binds to it — and only then performs the transition, so an answer whose transition fails
still leaves its reason on the item.

A park is the one answer that performs no transition, so its text carries the condition instead of a direction: the
item keeps `agent/needs-decision` and its status, it stays in the queue, and a later answer may still continue or
retire it — which is why the clause the decision opens with is the one the queue prints.

Verify, then report the number and the new label set:

```bash
gh issue view <n> --repo <owner>/<repo> --json labels,state
```

## What this skill never does

- chooses an option the owner has not chosen
- offers an option without an example, or prices a pro or a contra with no basis
- merges the short-term and strategic views into one ranking, or averages them
- writes a label, a comment or a body directly — `gh issue comment`, `gh issue edit`, `gh issue create`,
  `gh issue close` are all outside it
- edits code, changes a bound or raises `LOOP_MAX_DECISIONS_DEFAULT` while answering: the answer names the
  direction, the loop's run performs it
- re-opens a fork the item records as settled, or reads the escalation's retry-or-abandon wording as the fork
- ranks the queue or says what to work next (`triage-todo`), or reshapes an item (`reshape-issue`)

## A worked example, compressed — #788, answered 2026-11-07

**Contents.** Observed: the start-time cap counts every eligible `agent/needs-decision` item, and the loop writes
most of that population itself. Evidence: `.pi/issue-loop/runs/2026-11-05T08:51:02Z.json` — 14 items, 8 blocked,
6 by the turn bound; 8 of 18 listed decisions read "retry the item, or abandon it"; cap 10; the 12:45:35Z run
exited before picking an item. Why it matters: a pile the loop makes itself is invisible to the cap, so a bound
stops the work twice — once as the killed child, once as the run that may not start. The decision asked: how the
cap's population stops growing, not how it is counted.

**Options, one example each.**

- **A — a cut produces no question.** `run.sh:2778-2809`: a child ended at a bound records `cut` +
  `stopReason: bound`, writes no decision label. *Example:* item #21's worker dies at 110 turns → state
  `{result: "cut", stopReason: "bound"}`, labels unchanged, cap count 9 → 9, next pre-screen offers it as work.
- **B — keep the escalation, count only forks.** The cap's `jq` learns to skip the loop's own wording.
  *Example:* #21 keeps `agent/needs-decision` + `status/on-hold`; cap counts 1 instead of 9, the run starts —
  and `prescreen.sh:384` still refuses the branch, so the item stays parked.
- **C — raise the default.** `loop.conf` 10 → 20. *Example:* the 9-item queue today starts; the next bound wave
  adds ~6, so the next raise is 20 → 30.
- **D — age the decision.** The escalation records the run that raised it. *Example:* the 08:51 run raises 8;
  the 12:45 run counts 0 of them and starts, every run, for ever.

| view | A | B | C | D |
|---|---|---|---|---|
| short term | fixes itself in the same run; two mechanisms to keep coupled | cheapest to write | one constant | one field |
| strategic | removes the cause; the cap keeps meaning the owner's pile | re-counts one wording, branch still unread | a cap in name only, raised a third time | self-excusing by construction |

**Recommendation: A** — it removes the question instead of re-counting it; 3 → 10 on 2026-10-20 is the
measurement that C repeats.

**Answer written.** `answer.sh 788 --continue --decision "A — …"`, naming the branch, its commits, the four
blocking findings the reviewer left, and "do not re-derive". Verified: `agent/needs-decision` and
`status/on-hold` gone, item work again.
