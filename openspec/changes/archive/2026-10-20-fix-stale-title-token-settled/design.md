## Root cause

The resolver decides what is due from a matrix built over the item's **title and body** —
`tools/issues/resolve-stale.sh:184`, `issues_citation_matrix_of "$title\n$body"`, the same pair the pre-screen
asks (`tools/loop/prescreen.sh:113`) — but the only thing it can write is the **body**:
`update.sh --body` (`:284`–`:288`), `update.sh --status` for a park (`:294`). Nothing in the file asks whether a
token it is about to repair actually stands in the body.

For an item repaired from the doubled-directory form the two sets differ in exactly one way. `#201`'s title is
"`create.sh` requires and validates …" — a bare name that ends exactly one tracked path, hence `suffix` — while
its body, after the earlier repair, cites `tools/issues/create.sh` in full. The suffix loop resolves the title's
token to the canonical path (`:204`), the token differs (`:211`), so it rewrites the body with `standalone=1`
(`:214`) — and `rewrite.awk` deliberately refuses a `/`-preceded occurrence, because that boundary rule is what
stopped a basename repair from giving the doubled form a third copy of its directory (#200). The body therefore
comes out byte-identical, and the verification block records that as `unresolved`
(`:258`, "the rewrite changed nothing") — the same accumulator a genuinely unresolvable citation feeds, since
that block exists to catch a repair that a later rewrite undid or whose target the tree does not hold.

`unresolved` decides the park: `due` is charged (`:280`) and the item is set to `status/on-hold` with the
comment at `:294`–`:300`. The same shape holds for an `absent` token that stands in the title alone: the ladder
finds a successor, the body is unchanged, and the item is parked with a reason that misdescribes what happened.

One mechanism, not two: **a body write is attempted for a token the body does not carry.** The no-op is the
symptom; the title/body asymmetry is the cause.

## The single fix

Ask the body's own citation set, and answer per token: `body_matrix=$(issues_citation_matrix_of "$body")` is
read once per item, `body_cites <token>` asks whether that token appears in it, and both loops consult it.

- The suffix loop drops a token the body does not carry (`body_cites "$tok" || continue`). A `suffix` citation
  is not stale by the classifier's definition, the body already reads as it should, and a body write can only
  affect the body — so the token is settled: no pair, no bullet, the item is not due, nothing is written.
- The absent loop cannot simply drop it: the item *is* stale (the pre-screen asks the title), and parking is the
  only record this tool can leave. It records `- \`<token>\` — the citation is in the title, which this tool
  does not rewrite` and takes the park branch, so the state a person queries is preserved and the reason is
  true.

Both branches consume the same predicate, so the "changed nothing" bullet now means only what it says: a token
that *is* in the body was rewritten and the result equals the input — the `/alpha.sh` case of
`resolver#repairWithoutAChangeIsNotReported`, which keeps that meaning.

The `due` guard is untouched: the settled item reaches neither write branch, so the existing line (`:280`)
counts it `skipped`, which is #199's rule unchanged — an item the run writes nothing for costs no slot.

## Blast radius

`tools/issues/resolve-stale.sh` (one matrix per item, one helper, one skip in each loop, one new bullet),
`tools/resolve-stale-cases.sh` (two cases), `tools/resolve-stale-mutations.json` (one record), the spec delta,
one clause in `guidelines/Backlog.md` §7. No change to the classifier, the ladder, the parking rule, the
ambiguity rule, the bound's semantics, the residue metric's measurement, or any item whose body carries the
token it cites. No compiled file, so no Gradle gate: the structural gate is `tools/resolve-stale-selftest.sh`.

Cost: one extra matrix pass per item that reaches the token loops — the same call per candidate token that
`issues_citation_class` already makes, and only for items that are not already settled. A run visits at most
`--max-items` such items.

## What the evidence eliminated

- **Rewrite the title too** (`update.sh --title`) so the bare token becomes canonical there. Eliminated by the
  tree: `tools/issues/update.sh` takes `--body`, `--comment`, `--status` and labels and has no title write
  (`update.sh`, the argument loop), and `Backlog.md` §5 gives the resolver exactly one kind of prose write, the
  body. Adding a title write is a different change — a widening of what the resolver may rewrite — not this one.
- **Treat any no-op rewrite as settled** (the item's own "failing that" clause: a no-op rewrite must not be
  appended to `unresolved`). Eliminated by the second shape of the same defect: a title-only `absent` token
  would then be skipped instead of parked, and the item would be refused by the pre-screen on every later run
  with nothing on the tracker saying why — the "refusal recorded nowhere" state `stale-citation-resolution`
  exists to remove (#163). The predicate has to distinguish *nothing to repair* from *repairable only elsewhere*,
  which is what `body_cites` does; the second red case holds that line.
- **Skip a token whose canonical path already occurs anywhere in the body** (the item's "fix candidate",
  literally). Eliminated by a counter-example in the same body: a body citing both `D/f` and the doubled `D/D/f`
  contains the canonical path and still carries a stale citation, so that rule would leave it stale forever —
  never repaired, never parked. The `body_cites` predicate asks about the *token*, which is the question the
  rewrite would ask.
- **Order the loops or re-run them to a fixed point.** Eliminated by measurement: the rewrite is already
  guarded by `standalone=1` (#200) so it is idempotent, and a second pass over an unchanged body produces the
  same no-op; the defect is the accounting, not the order.

## Rollback

Revert the commit. A title-only token is rewritten for the body again, the no-op lands in `unresolved` again,
and the item is parked on the run after its repair — the base behaviour. No tracker state exists to undo: this
change writes nothing to the tracker by itself, and the three items the 2026-10-21 pass parked by hand stay as
they are under either behaviour.
