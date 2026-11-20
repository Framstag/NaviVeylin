# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A container that cannot advance takes no slot

The pre-screen SHALL judge a candidate's container as well as the candidate: a candidate that would otherwise be
queued SHALL be refused with the verdict `epic-stalled` when either fact the screen already holds makes its container
one the run cannot advance, and the record SHALL name what the container waits for. The first fact is that every
child the screen saw under the container is excluded with the decision verdict (issue #938): the container holds no
child the run can collect from. The second is that this run recorded a child of the container cut at a bound: the
container has spent its slot, so it takes no further one and a later pre-screen of the same run refuses any further
child of it, whether or not another of its children could be queued. The verdict SHALL write nothing to the tracker —
the decision it names is a label already there, and the cut it names is a record the run holds — and SHALL clear
without a write of the run's: the decision arm on the first run whose labels no longer say a decision stands, the
bound arm with the run that recorded the cut. A container with two children the screen would queue is screened as
before only while this run has recorded no child of it cut at a bound.

#### Scenario: A container whose remaining children await a decision is not worked

- **WHEN** the eligible set holds a child of a container whose other children the screen saw are all refused with
  the decision verdict
- **THEN** that child is refused as `epic-stalled` instead of being queued, and the reason names the items whose
  decisions the container waits for
- **AND** the children that await the decisions keep their own verdict, so the refusal is the child's and not a
  re-judgement of theirs
- **AND** an item in no container, and the only child of a container, are screened exactly as before
- **Case**: `prescreen#aContainerWhoseChildrenAllAwaitADecisionTakesNoSlot`

#### Scenario: A container whose child a bound cut takes no further slot

- **WHEN** the run recorded a child of a container as cut at a bound, and a later pre-screen of the same run reaches
  another child of that container, or the child the bound ended
- **THEN** the candidate is refused as `epic-stalled`, with the reason naming the child the bound ended and the
  `KEY=VALUE` it was cut at, so the container takes no second slot in that run
- **AND** the cut item's own record keeps the cut it made, because the refusal is the screen's and rewrites no state
- **Case**: `prescreen#aContainerCutAtABoundTakesNoFurtherSlot`
