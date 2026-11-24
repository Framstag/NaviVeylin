# Design — a touched baselined file is migrated

## D1 — The rule is a review convention, not a gate arm

The gate arm would need the base ref, and a working-tree run does not have one: `:architecture:test` reads the
tree it is given. A check could diff against `origin/main`, but then a local run and a CI run would judge different
diffs, and a change that rebases would change its own verdict without changing a byte. The reviewer already reads
the diff against the base, so the arm that costs nothing is the one that reads the list the reviewer is already
looking at.

The recipe the reviewer runs:

```sh
comm -12 <(git diff --name-only "$base"...HEAD | sort -u) \
         <(grep -v '^#' architecture/baseline.tsv | grep -v '^row' | cut -f1 | sort -u)
```

## D2 — The baseline keeps its shape

No column, no marker, no "touched" flag. The entry already names the file, the rule and the owning change; the
rule adds a question a reviewer asks about those three fields and needs nothing new to answer it. A new column
would be a second declaration of the same fact and would have to be kept in step by hand.

## D3 — The rule costs a change nothing when it touches no baselined file

Most changes touch none. The rule is written as a condition on the diff, not as a step in every change's
procedure, so a change that touches no baselined file reads it and does nothing.

## D4 — The epic owns the queue, the rule owns the moment

The epic #1049 owns the 12 slices that empty the baseline. This rule owns the smaller thing: the moment a change
is already editing a file, the move is cheaper there than in a later change that has to re-read the file. The two
do not conflict — a child of #1049 clears entries wholesale, a change under this rule clears the one it touched.

## Open decisions

none
