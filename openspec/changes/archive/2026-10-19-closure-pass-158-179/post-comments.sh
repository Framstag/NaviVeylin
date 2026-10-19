#!/usr/bin/env bash
# Closure pass — one reason comment per item, naming the commit that fixed it and the command that verified it.
set -uo pipefail
cd /home/tim/projects/NaviVeylin || exit 1

post() { # number body
  printf '#%s ' "$1"
  timeout 120 bash tools/issues/update.sh "$1" --comment "$2" 2>&1 | tail -1
}

post 165 "Fixed in \`b6bb419\` (\`fix: status-label-replacement (#178)\`) as a side effect of the status-write rework — the change was made for #178 and closes this defect too, which is why no commit names this item.

**The defect**: a \`--comment\`-only call fell through to a bare \`gh issue edit\` with no flag, which real \`gh\` refuses, so the script printed gh's help and still claimed \`updated: true\`.

**What the current code does**, both measured in \`tools/issues/update.sh\`:
- line 130 refuses a call that changes nothing: \`update: nothing to change — pass --label, --remove-label, --status, --body or --comment\`
- lines 181-186 compute \`edit_needed\` and skip the \`gh issue edit\` when no label and no body are in play, reporting \`update: #N already carries <status> — no label write\`
- line 210 posts the comment through \`gh issue comment\`, never through \`edit\`

**Verification**: \`bash tools/issue-tooling-selftest.sh\` — **all 77 cases passed**, including \`check#selfCheckIsGreen\`; recorded in \`openspec/changes/archive/2026-10-19-closure-pass-158-179/evidence/suites.log\`.

Closure is the owner's: this item carries no \`agent/eligible\`, and \`tools/issues/update.sh\` refuses to add it (\`label-reserved\` — only a person authorises automation)."

post 166 "Fixed in \`d3f9889\` (\`fix: fix-sensor-late-tick-window (#166)\`).

**The defect**: a cadence was matched against the exact minute, so a scheduled tick GitHub fires late selected no row and the run did nothing.

**The fix**: the sensor takes a \`--within\` late-tick window, so a tick that fires after its minute still selects the row it was scheduled for.

**Verification**: \`bash tools/sensor-selftest.sh\` — recorded in \`openspec/changes/archive/2026-10-19-closure-pass-158-179/evidence/suites-2.log\`; the change's own scoped cases are in \`tools/sensor-selftest.sh\` and pass.

Closure is the owner's: no \`agent/eligible\`, and the tooling refuses to add it."

post 167 "Fixed in \`89ea05b\` (\`fix: attribute the loop's commits to the identity the run acts as (#167)\`).

**The defect**: \`foreign#emptyGateCommandStillRunsThePhases\` failed on the runner while passing on the developer host — the loop's commits were attributed to an identity the host happened to have configured, so the case depended on the machine rather than the tree.

**The fix**: the run writes the commits under the identity it acts as, so the case no longer reads host git config.

**Verification**: \`bash tools/check-portability-selftest.sh\` — **all 26 cases passed** (\`evidence/suites.log\`), the suite that owns the \`foreign#*\` cases.

Closure is the owner's: no \`agent/eligible\`, and the tooling refuses to add it."

post 169 "Fixed in \`6cf9afa\` (\`fix(loop): start agent processes where the project's policy applies, and record a refusal instead of dropping the item (#169, #170, #171)\`) — one change closes this item and its two neighbours, because all three were the same failed hand-off seen from three sides.

**The defect**: a fresh child started in the worktree found no project tool policy, so every tool call resolved as an approval it could not ask for and the worker could not run.

**The fix**: the worker process is started where the project's policy applies, and a refusal is recorded on the item instead of the item being dropped silently.

**Verification**: \`bash tools/loop-selftest.sh\` — recorded in \`evidence/suites-2.log\`; the run's own decision paths are the cases it owns.

\`status/on-hold\` was also removed: the item now carries one status value, as the label namespace requires.

Closure is the owner's: no \`agent/eligible\`, and the tooling refuses to add it."

post 170 "Fixed in \`6cf9afa\` (\`fix(loop): … (#169, #170, #171)\`), together with #169 and #171.

**The defect**: a branch a blocked item left behind made the item unstartable, and the run ended silently with nothing recorded.

**The fix**: \`--reset-worktree\` (part of the same change) clears the leftover branch, and the blocked arm records its verdict on the item rather than exiting without a trace.

**Verification**: \`bash tools/loop-selftest.sh\` — \`evidence/suites-2.log\`; the blocked-item and leftover-branch paths are its cases.

Closure is the owner's: no \`agent/eligible\`, and the tooling refuses to add it."

post 171 "Fixed in \`6cf9afa\` (\`fix(loop): … (#169, #170, #171)\`), together with #169 and #170.

**The defect**: a skipped item kept \`agent/in-progress\`, so the tracker showed an item being worked while no run held it.

**The fix**: the skip path calls the label helper's \`unpick\`, so the mark is cleared on that exit path as the \`labels.sh\` contract requires of every exit path.

**Verification**: \`bash tools/loop-selftest.sh\` — \`evidence/suites-2.log\`; the case \`run#aSkipClearsTheInProgressMark\` is the defect's red case.

Closure is the owner's: no \`agent/eligible\`, and the tooling refuses to add it."

echo "done"
