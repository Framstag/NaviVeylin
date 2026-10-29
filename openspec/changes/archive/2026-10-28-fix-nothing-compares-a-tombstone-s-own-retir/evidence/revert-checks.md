# Revert check — settled-record-not-checked (#683)

The mutation targets the production invariant, not the case: `tools/issues/metadata-mutations.sh` patches
`tools/issues/check-metadata.sh` so the settled-record predicate is never true
(`if issues_records_settled_state "$body" "$settled_record"; then` → `if false; then`), runs the probe, and
restores the file through its own `trap` whatever happens.

Run on the final tree (the corrections of review round 1 applied, the branch merged with `main`), 2026-10-29
20:13:32 +0100 = 2026-10-29T19:13:32Z:

```
$ bash tools/issues/metadata-mutations.sh
── basis-drops-labels — the basis must move when a kind label changes (task 8.1, D3)
  the rule fired with the mutation in place
  and it is quiet again after the restore
── basis-hashes-the-raw-body — a reflowed body must not move the basis (task 8.2, D3)
  the rule fired with the mutation in place
  and it is quiet again after the restore
── basis-drops-the-anchor-flag — the basis must move when the cited code goes away (task 8.3, D3)
  the rule fired with the mutation in place
  and it is quiet again after the restore
── basis-carries-the-run-timestamp — a repeated run with nothing changed must score nothing (task 8.4, D3, D9)
  the rule fired with the mutation in place
  and it is quiet again after the restore
── band-edge-leaves-a-hole — every score the model can produce falls in exactly one band (task 8.5, D2)
  the rule fired with the mutation in place
  and it is quiet again after the restore
── classify-writes-the-opt-in — the pass cannot write the automation opt-in (task 8.6, D7)
  the rule fired with the mutation in place
  and it is quiet again after the restore
── the-writer-skips-the-marker — no band label is written or removed without the body that leaves the pair intact (task 8.7, D1; the write gate of #671)
  the rule fired with the mutation in place
  and it is quiet again after the restore
── settled-record-not-checked — an item whose body keeps a settled state must carry a settled label, or the displaced state is invisible (#683)
  the rule fired with the mutation in place
  and it is quiet again after the restore
metadata-mutations: 8 of 8 mutations noticed, and every probe quiet again
$ echo $?
0
```

Restore, checked rather than assumed: `git status --porcelain tools/issues/check-metadata.sh` prints nothing and
`git diff --stat tools/issues/check-metadata.sh` prints nothing after the run, so the file is byte-identical to the
committed one.

The red the harness reports is red-by-status: its probe passes the case's exit code to the caller and does not
quote the failure message. The message itself is `evidence/settled-record-red.xml`, the same case run against the
pre-fix tree: `expected [1] got [0]` on `a tombstone whose state is not settled exits non-zero`, `tests="1"
failures="1" errors="0"`.
