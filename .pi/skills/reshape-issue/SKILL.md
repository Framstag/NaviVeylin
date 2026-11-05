---
name: reshape-issue
description: Reads the shaping inbox — the open items carrying `agent/needs-shaping`, whose one comment states the run's verdict and the clause behind it — and proposes a reshape for each: either the single mechanism the item lacks or the children it splits into, every proposal naming a `file:line` read in the tree and the cost of taking it. Stops for the owner, writes nothing before the owner chooses, and leaves through `tools/issues/create.sh` and `tools/issues/update.sh`. Use when asked "what is waiting to be shaped", "work the shaping inbox", or when a run has handed an item back with `agent/needs-shaping` and nobody has touched it since.
---

# Reshape an item the inbox holds

The inbox is a **third state**: not a hold (a person owes a reshaping, not a wait) and not a decision (no
question was stated). `tools/issues/shaping.sh` lists it. This skill is its reader — the only path from the
label to work, because the run that wrote the label refuses to guess what the shape should be.

The act is one: **read the item, read the tree, propose a shape, stop.** The owner is the one who decides a
design question (`guidelines/Backlog.md` §5, "Who may write what"), and which mechanism is the defect is that
question. So this skill proposes and never decides.

## When to use it, and when not to

Use it when:

- the inbox is non-empty and a person asks what is in it, or to work it
- a run handed an item back and the label has sat since — the state's own author is the first reader who needs
  this path
- an epic in the inbox needs splitting and the children's causes have to be found before anything is filed

Do not use it to:

- **answer a decision.** An item carrying `agent/needs-decision` is the decision queue's —
  `tools/issues/decisions.sh` lists it, and `update-issue` records the answer.
- **rank or pick what to work on next.** The shaping inbox is a listing, not a ranking
  (`guidelines/Backlog.md` §5); that is `triage-todo`, and its items are exactly the ones no run may start.
- **write anything on its own judgement.** No label, no comment, no child before the owner chooses.
- **close an item.** A reshape that ends as "not worth doing" is the owner's closure, through `close-issue`.

## 1. Read the queue, in one call

```bash
tools/issues/shaping.sh --json            # every open item carrying the label, with its statement
```

Each entry carries the item's **verdict** and the **clause** behind it, taken from the item's own comment.
Repeat them as the queue printed them — quote the verdict word and the clause verbatim, never as a summary of
your own. An item the queue names as carrying no statement is proposed with that fact first: a reshape of an
item nobody can read is a question to the owner, not a guess.

Read the body of an item only once the queue has named it: the labels and the statement decide *which* item,
the body decides *what*.

## 2. Find the shape it is missing, in the tree

Two proposal forms, and every item takes exactly one:

- **One mechanism.** The item holds one defect and the verdict was only that nobody stated it — name the single
  mechanism that explains the symptom, in one sentence, with the `file:line` you read that shows it. Two
  mechanisms means this form does not fit.
- **A set of children.** The item is wider than one change: name each child as its own item-shaped statement,
  each with the `file:line` it rests on, and say what each one costs — the files it touches, the change it
  needs, the check that proves it.

Every proposal names the pointer it read **in this tree**, at the line it read, and the cost of taking it. A
proposal without a `file:line` is a guess, and the owner cannot price it. Where a proposal rests on a run or a
measurement rather than a pointer, name the command and quote its output instead.

The clause is the requirement the item was measured against: read the guideline section or the capability spec
it names, and propose the shape that makes the item falsifiable — a WHEN/THEN whose case can be run.

## 3. Say how each child would be filed

`tools/issues/create.sh` refuses a finding that carries no `cause` and no `change` (`shape-missing`): a child is
either a **tier-A finding** — a cause, i.e. a `file:line` whose defect explains it, plus the case that exercises
it — or it needs a change planned first, and the skill files it only once that plan exists.

So state, per child, which of the two it is:

- **filed now**: the child carries a cause, its pointer resolves in this tree, and a case names the behaviour
- **proposed only**: the cause is not yet proven — the child stays a proposal until it is planned

Never file a shape-less child to get it out of the inbox, and never re-shape a refusal into another finding: if
the claim changed, the child is a new item and the old one is closed with its reason.

## 4. Stop — it writes nothing before the owner chooses

Present the proposal: the item, the verdict and clause quoted, the one mechanism or the children with their
`file:line` and their cost, and per child which filing shape it has. Then stop.

It writes nothing before the owner chooses: no label, no comment, no body edit and no child item — so the item
keeps `agent/needs-shaping` and its statement, and the queue still lists it. The proposal is the session's output;
the reshape is the owner's.

## 5. On approval, leave through the writers that already exist

The owner picks one proposal, or amends it. Then, and only then:

```bash
tools/issues/create.sh ...                          # once per child, with its cause and its case
tools/issues/update.sh <issue> --body ... --status open   # once for the item's own new shape
```

`update.sh`'s write **takes the shaping label off** in the same call that records the new shape, so the queue
no longer lists the item — this is `shaping-inbox`'s "Leaving the inbox is an existing writer's act". No other
write path: no direct tracker call, no new verb, no second vocabulary of transitions.

If the owner rejects every proposal, say so and stop; the item keeps its label, and whether it is retired is the
owner's closure, not this skill's.

## What this skill never does

- writes a label, a comment or a body without the owner's choice
- files a child that carries neither a cause nor a change
- ranks the inbox, prices a band, or names what to start next
- decides which mechanism is the defect — it proposes, with a pointer, and the owner decides
- touches `tools/issues/shaping.sh`, `decisions.sh` or `answer.sh`: the queue and the decision path are read-only
  to it
