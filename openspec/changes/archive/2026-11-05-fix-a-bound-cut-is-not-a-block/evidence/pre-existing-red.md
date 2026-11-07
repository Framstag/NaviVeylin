# The one red case of the full suite is not this change's

`evidence/loop-selftest.log` is the whole suite over this tree: **315 of 316 cases passed**.

```
FAIL gate#theMapCoversEveryPipelineStep
  the suite map carries none of these commands the pipeline's Check steps run: tools/loop-selftest.sh --group "$g"
loop-selftest: 1 of 316 cases failed
```

That command is `a32b1dfa`'s ("ci: the offline gates run beside the build, and the loop's suite in three legs",
`.github/workflows/build.yml:744`), and the open change `loop-suite-legs-across-runners` owns the map entry it owes.
The case reads `.github/workflows/build.yml` and `tools/loop/loop.conf`, neither of which this change touches, and it
fails identically with this change's four engine scripts reverted to `main`:

```
$ git stash push -- tools/loop/run.sh tools/loop/state.sh tools/loop/worktree.sh tools/loop/report.sh
$ bash tools/loop-selftest.sh --case 'gate#theMapCoversEveryPipelineStep'
  the suite map carries none of these commands the pipeline's Check steps run: tools/loop-selftest.sh --group "$g"
FAIL gate#theMapCoversEveryPipelineStep
```

Every case this change's paths declare is green: the `run` group (116 cases), `state` (23), `worktree` (22),
`prescreen` (30), `driver` (20), `report` (20), `labels` (8), `prompt` (15), `usage` (4), `phase` (2), `repeat` (4),
`evidence` (1), and the 23 of the `gate` group that do not read the pipeline's command list.
