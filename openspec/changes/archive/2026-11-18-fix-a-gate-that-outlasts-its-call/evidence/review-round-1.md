# Round 1 — read-only review of the diff, and what it corrected

The reviewer was a fresh, read-only child (`reviewer` role) handed the uncommitted diff and the two ADDED requirements of
this change; its full report is `review-round-1-full.md`.

| item | verdict |
|---|---|
| `prompt#theGatePromptNamesTheWallAndTheWayThrough` covers its scenario, and only the two skeletons that hand a child a gate carry the rule | PASS |
| `gate#aSecondGateRefusesWhileOneRuns` still holds, `tools/loop/gate.sh` untouched | PASS |
| `prompt#aValueArrivesAsTheTextItWasHanded`: the `shopt -q` guard is right on a bash without the option, and the option is restored — no leak found | PASS |
| the new prompt text asks for nothing the child's policy refuses (`node tools/pi-shell-refusal.mjs --command …` answers `ALLOWED` for both the start and the poll) | PASS |
| blocking findings | one |

**Blocking, and corrected.** The added requirement's second sentence promised that a `{{` or a `}}` in a *value* reaches
the prompt as itself. It does not, and must not: `run.sh`'s `gate_step_text` substitutes `{{GATE_STEP}}` first precisely
so that the section it inserts may name `{{GATE_CMD}}` and `{{EVIDENCE_DIR}}` and have those resolved by the later pairs —
which is what makes both skeletons of this change correct. The sentence now states the rule the engine has: `&` and `\`
in a value arrive as themselves, and a `{{KEY}}` a value carries is read only as the placeholder a later pair names. The
shipped spec and the archived delta carry the same text.

**Report-only, and corrected.** The poll paragraph claimed that `grep`, `cat` and `head` over the gate's log are the
tree search the child's shell refuses. Not true: the log sits under the item's own directory (`.pi/issue-loop/items/<n>`),
which the refusal exempts as *not the tree* (`tools/pi-shell-refusal.mjs`). The paragraph now says what is true — the
poll is a wait and a range read of a file under the item's directory, and reading its end is no search of the project's
tree. Both skeletons carry the corrected sentence; the case's assertions (`sleep `, `tail -n`) are unchanged.

Also confirmed by the review: no case here is vacuous (each has a revert-check log), and the gate's remaining red is the
pre-existing `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` (`evidence/gate-runs.md`).
