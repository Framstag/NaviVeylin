# Design — one source for "the run ended this at its own bound"

## Why an action and not a label

The loop's other outcomes reach a caller through the tracker: a block escalates a decision, a closure writes a
settled state. A cut is deliberately not one of those — it is the run's own clock, so nothing is asked and nothing is
written to the tracker. That leaves exactly one reader after the process is gone: the state file the report prints.
So the item's reappearance is an **action line**, not a label, and the report is where it belongs.

## Why the gate arm skips it

`re-run the gate for #N` exists because a gate is the item's own measurement: an item that ended without one left a
claim unverified, and a caller can finish it. A `cut` item's child never reached the gate — the run ended it first —
so the gate is not owed, and printing the line sends a person to re-run a measurement the item never claimed. The arm
is therefore `result != "cut"`, and the new source beside it names what a caller can actually do: resume.

## Why the note is quoted rather than summarised

The run already writes the mechanism into the item's note (`the run ended the worker at a bound — the child reached
the recorded turn bound of 250 turns`). A second vocabulary in the report would be a second rule; the report quotes
the run's own sentence, bounded by the action line's shape, and the transcript path is beside it for the rest.

## Why no second source for the leftovers

An item a bound ended keeps its work as a patch, and the report already names that patch under its own heading. The
resume line does not repeat it: one fact, one line, and the patch heading is the one that owns it.

## Reversal

Drop the `resume` block and restore `result != "cut"`'s absence from the gate arm. Both are two hunks, and the case
`run#aReviewerEndedAtItsTurnBoundIsNotARefusal` fails on either — it asserts the resume line is there and the gate
line is not.
