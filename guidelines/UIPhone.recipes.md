# UIPhone — recipes (procedure)

The procedure layer of `guidelines/UIPhone.md`: the commands, the test recipes and the number tables a
section's rule is verified with. Read beside the section its `## §N` anchor names.

## §16 The drag-surface test recipes

Two recipes the drag tests must follow. A helper returning a **distance** is not a direction: compute the
delta from the two items (`chipLeft("Berlin") - chipLeft("Office")`), or the drag travels the wrong way and
commits nothing. And bound every drag to about one item width — a card dragged ~200 px past the grid's edge
starts the reorder library's drag auto-scroll, which keeps running and perturbs whatever test executes next
in the same JVM (bounding it turned navigation + that class from 1 failure in 3 into 4 consecutive green
runs). For a laziness test, capture the instance inside the lambda (`FavoriteRepository().also { built = it }`)
— `assertSame` against a second `dagger.Lazy.get()` compares two different objects and fails on identity,
not on laziness.

The surface that produced the suppression rule is the starred chip: it carries `longPressDraggableHandle()`
on the chip itself (the chip is its own click target, unlike the favorite row and the group card, whose
handles are siblings of the clickable content), so a long press followed by a release fired `onClick` as
well — holding a chip started navigation. The tap is delivered **before** `onDragStopped`, so the
`suppressNextTap` flag is set in `onDragStarted` (setting it later is too late).
