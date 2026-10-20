# revert-check — mutations 5.12, 5.13, 5.14 of `tools/resolve-stale-mutations.json` — 2026-10-20T10:16:28Z

$ bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --only 5.12
5.12	resolver#basenameRepairLeavesALongerPathAlone	a basename repair refusing an occurrence that stands inside a longer path, so the two loops cannot cancel (#200)
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/rewrite.awk
  GREEN(exit 0)  restored, and the case passes again

mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
exit=0

$ bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --only 5.13
5.13	resolver#repairWithoutAChangeIsNotReported	a repair counting only when the produced body differs from the input (#200)
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/resolve-stale.sh
  GREEN(exit 0)  restored, and the case passes again

mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
exit=0

$ bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --only 5.14
5.14	resolver#repairIsVerifiedByReclassification	a repair counting only when its rewritten citation re-classifies as a path the tree holds (#200)
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/resolve-stale.sh
  GREEN(exit 0)  restored, and the case passes again

mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
exit=0
