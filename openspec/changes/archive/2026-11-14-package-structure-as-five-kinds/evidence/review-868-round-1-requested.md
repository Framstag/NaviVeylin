The builder session finalizing item #868 announced review round 1 of this item's branch before its reviewer started.

Recorded at 2026-11-15T06:45Z on the branch `fix/issue-868-no-shrink-only-baseline-exists-so-the-co`, commit
`77a51af1` (the finalization's own fix) beside `062810f2` (the item's work) and the commit that carries this file,
because a branch is what a
later session can see: a round whose answer never arrives must not read like a round that passed, and this file
is the only record that the round existed at all.

The round reads `git diff 5215e545...HEAD` — the change `package-structure-as-five-kinds` slice #868 owes: tasks
1.5 (the `concept` and `port` layer rules), 1.7 (the baseline half only: the allow-list, `staleExemptionFails`
and `exemptionWithoutChangeFails`), 1.8 (the four green-run figures and `emptyDeclarationFails`) and 1.10
(`suppressionAnnotationDoesNotExempt`, `baselinedFileIsCountedNotReported`) — with falsifications 3.1 and 3.5
recorded in this directory, and the finalization's own finding, that the green run measured nested copies of
`app/src/main/java/…` instead of the tree (case `aNestedCopyOfASourceRootIsNotTheTree`, red at HEAD in
`evidence/red-on-HEAD-nested-copy.xml`, fixed in the same branch).
