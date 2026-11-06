# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: An adoption the tree refuses is recorded as `unmergeable`, not escalated

An adoption of a previous attempt's branch that the branch's own inputs admit — one archived change of its own, a
green verification record, no reviewer's refusal and no review round left unanswered — but whose merge the project
tree, the base or the replay refuses SHALL be recorded with the item's mechanism `unmergeable`. The pre-screen's
entry for that item SHALL keep its reason, its verdict and the branch as the evidence, SHALL carry the adoption's
own refusal beside them, SHALL NOT be reported as a decision the owner owes and SHALL NOT be escalated on the
tracker. No item record SHALL be written for it, so the item neither enters the two-blocked streak nor counts
against the decision cap, and the branch SHALL be left where the next run's pre-screen finds it. An adoption refused
by the branch's own evidence SHALL stay the owner's decision, unchanged. The mechanism SHALL be printed beside the
exclusion in the run's report, so the run's own output says which refusal it decided for itself, and a pre-screen
that runs again during the same run SHALL NOT erase it from the entry it rewrites.

#### Scenario: A tree that refuses the merge leaves work, not a question

- **WHEN** the run adopts an excluded branch that carries exactly one archived change of its own and a green
  verification record, and the project tree the merge would land in is not clean
- **THEN** nothing is merged and nothing is closed, no `agent/needs-decision` label is written for the item and the
  report's decision list names none, while the run's own record carries the adoption's refusal with `unmergeable`
  as the excluded item's mechanism and the branch as its evidence — the item the next run retries, not a question
  only its owner can answer
- **Case**: `run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable`

#### Scenario: The refusal is recorded on the exclusion and on no other record

- **WHEN** the run records an adoption's refusal for an item the pre-screen excluded and has not worked
- **THEN** the entry keeps its verdict, its reason and the branch as its evidence and gains the mechanism
  `unmergeable` and the refusal's own text — kept there when a second pre-screen of the same run rewrites the
  exclusions — the table prints it as `#21 needs-decision (unmergeable)` and names no
  decision the owner owes, while an issue that is not one of the run's exclusions and an issue that already carries
  the item record the run wrote are both refused with their reason and the state is left unwritten
- **Case**: `state#anAdoptionRefusalIsRecordedBesideTheExclusion`

#### Scenario: A branch without a green verification stays the owner's decision

- **WHEN** the adoption of an excluded branch is refused because the change it carries has no green verification
  record
- **THEN** the exclusion stays the run's decision — the item is escalated and nothing is merged — so the refusal
  that is the branch's own is not read as one the tree made
- **Case**: `run#doesNotMergeAnAttemptWithoutAGreenVerification`
