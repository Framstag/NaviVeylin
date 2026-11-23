# Which gate measured this change, and whose red the archived state carries

The change carries two gate records because this working tree answers differently when the change's own delta has
been merged, and one of the two readings is not this change's business. Both are real executions over HEAD
`b250d6c6` with base `e3a5afd8`; neither reproduces the other.

| record | tree it measured | identity | reading |
|---|---|---|---|
| `gate.log` | the change's own delta, its archive directory in place and its delta not yet merged | `head=b250d6c66ac4d25980c59bdc1c1f2b0617f96ac2 diff=91f84710a6916495` | 1260 tests over 14 steps, no failure, 18:09:56Z–18:21:52Z |
| `gate-at-archive.log` | the archived tree with the delta merged | `head=b250d6c66ac4d25980c59bdc1c1f2b0617f96ac2 diff=a065814660f9d343` | 1311 tests over 23 steps, one red: `bash tools/gen-feature-list.sh --check-classification` |
| `gate-archived-premerge.log` | the archive's directory with the delta's merge reverted | `head=b250d6c66ac4d25980c59bdc1c1f2b0617f96ac2 diff=91f84710a6916495` | 1272 tests over 23 steps, the same one red |

`gate.log`'s identity is the digest the round's diff carries — the round's diff and this record name one tree, which
is what `tools/loop/prompts/reviewer.md` item 3 pairs, and what `evidence/review-round-1.md` §3 read. The record is a
re-take: the run the change's red/green story was written from named the same identity, the same step set and the
same counts (`gate.log:1813`).

## The archive's move moves no digest; its merge does

`loop_tree_identity` rewrites a path under `<family>/archive/<prefix><name>/` to the change's own name, so where a
change's directory sits is not part of its digest (`tools/loop/lib.sh:650–680`, issue #837). Measured, both ways:

- the directory under `openspec/changes/archive/2026-11-23-fix-a-round-cut-twice-keeps-both-readings/` with
  `openspec/specs/agent-issue-loop/spec.md` at its base content → `diff=91f84710a6916495`, byte for byte the digest
  of the unarchived change;
- the same tree with the archive's merge in place → `diff=a065814660f9d343`.

One path differs between the two, and it is the merge: `openspec/specs/agent-issue-loop/spec.md`. A gate taken after
the archive therefore measures one requirement more than the gate taken before it — which is why the archived-state
reading selects the spec-scoped steps (23 of them) that the delta-state reading does not select at all.

## The archived state's one red belongs to two other open changes

`bash tools/gen-feature-list.sh --check-classification` names the ids it wants classified and the change that
introduced each:

```
gen-feature-list: unclassified spec id(s):
  idea-shaping (introduced by the open change shape-idea — classify it in that change, before it archives)
  location-search-api (introduced by the open change fix-compound-name-matching — classify it in that change, before it archives)
```

Measured with exactly those two open changes set aside for the length of one command: `unclassified: 0` and exit 0.
Neither id is this change's: the delta adds a requirement to the shipped capability `agent-issue-loop` and introduces
no spec id, so this change has nothing to classify. The step's red is a property of this working tree, which carries
two other open changes' unclassified deltas — and because the ignored change family is part of the digest (the
enumeration at `tools/loop/lib.sh:718–724`), the two readings of this tree cannot both be green: with the two changes
present the step is red, with them absent the digest is another number again.

The loop gates in a kept worktree that carries one change and not the working tree's other open ones
(`tools/loop/run.sh:1903–1907`: the change is completed, its delta merged and its directory archived *before* the gate
measures the tree). That venue is a clean tree; this one is not, which is what the second record is for.
