# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A process the child's group signal cannot reach is ended by the tree it works in

The child cap SHALL end everything the child left running, not only the members of its process group: a
descendant that gave itself a session of its own is found by the working directory the kernel reports for it,
ended with the loop's own signal discipline, and named in the transcript before the item's outcome is written.

#### Scenario: A child the cap ends has left a writer working in the change root

- **WHEN** the wall-clock cap ends a child, and that child has started a process whose working directory lies
  inside the change root and which is a member of no group the cap signalled
- **THEN** the run ends that process too, so nothing of the child is still writing the tree when the item's
  outcome is written, and the transcript names each process it ended
  (`LOOP-ENDED: ended <pid> — its working directory is inside <tree>`)
- **Case**: `run#detachedProcessInTheWorktreeIsEndedByTheCap`
