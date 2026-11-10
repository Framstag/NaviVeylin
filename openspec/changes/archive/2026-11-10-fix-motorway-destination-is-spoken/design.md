# Design

## Root cause

`VoiceGuidance.sentenceFor` folds the sample ordinals into parts and joins them
(`app/src/main/java/com/naviveylin/navigation/VoiceGuidance.kt:55` on the base, where the signature
takes no destination); no caller passes anything but the sequence
(`NavigationEngine.kt:1200`, `:952` on the base). The destination the screens use is already on the
manoeuvre and already gated (`core/.../Manoeuvre.kt:129`, `ManoeuvreLabels.kt:83`–`:88`), but the
spoken path never reads the manoeuvre ahead, so the clause cannot exist.

## The single fix

1. `VoiceGuidance.announce(samples: IntArray, destination: String? = null)` and
   `internal fun sentenceFor(samples: IntArray, destination: String? = null)` gain the destination as
   an optional input (`VoiceGuidance.kt:29`, `:60`; the base's `:24`, `:55`). A non-blank value
   appends one part, `context.getString(R.string.voice_destination, destination)`, before the sentence
   is joined and title-cased — after the manoeuvre the agent emitted, which is where the sign names it.
2. The engine's turn call site (`NavigationEngine.kt:1214`; base `:1200`) passes
   `spokenDestination()` (`:991`), built from `_state.value.nextStep` through `manoeuvreFromStep`:
   `manoeuvre.destination` only when `ManoeuvreLabels.carriesDestination(manoeuvre)` holds. The GPS
   call site (`:954`; base `:952`) keeps its one-argument call, so a held GPS state can never name a
   destination.
3. `R.string.voice_destination` — `"towards %1$s"` in `values/strings.xml`, `"Richtung %1$s"` in
   `values-de/strings.xml`, beside the other `voice_*` fragments.

The gate stays in `:core` (`ManoeuvreLabels.carriesDestination`) and is not re-derived in `:app`;
`:app` only asks it what the screens already ask it.

## Blast radius

- `VoiceGuidance`: one optional parameter on two internal entry points; every existing caller keeps
  compiling because the parameter defaults to `null`, and the composed sentence for `null` is
  unchanged.
- `NavigationEngine`: one expression at the turn call site and one private helper; no state field, no
  coroutine, no threading change.
- Two resource files gain one key each; the German-completeness gate in `turn-instruction-localization`
  covers the second.
- No native code, no submodule, no JNI signature, no gitlink.

## Rollback

Revert the four production files and the two test files. Nothing is persisted, cached or migrated: the destination is read from
the step record the engine already holds, and the next session composes exactly today's sentences
again.

## Alternatives the evidence eliminated

- **Carry the destination in the native sample sequence.** Rejected: `VoiceSample` is a closed enum of
  ordinals with no text carrier (`VoiceInstructionAgent.h:49`–`:114`), so this is a submodule patch and
  a wire-format change for a value Kotlin already holds.
- **Map `KeepLeft`/`KeepRight` and word the fork from the move.** Rejected: the agent already words a
  motorway change as a leave to a side, and two sides of one fork are exactly the pair that must be
  told apart by the sign, not by the geometry (the `motorway-destination-hint` proposal, `## Why`).
- **Reuse `ManoeuvreLabels.leadsOnto` verbatim in the sentence.** Rejected: `leadsOnto` is the row's
  target phrase ("A8 towards München") composed for a written line; the spoken sentence already names
  the manoeuvre, so the clause is the destination alone. `carriesDestination` is reused, the wording
  function is not.
- **Pass the manoeuvre into `VoiceGuidance`.** Rejected: it widens the seam from one value to a route
  model for no gain, and it moves the gate's ownership out of the engine that reads the state.

## Why this change and not the handed `motorway-destination-hint`

The work package's `<<<ITEM_CHANGE` block names `motorway-destination-hint`, which is already archived
(`openspec/changes/archive/2026-11-06-motorway-destination-hint`) and landed in this base: `Manoeuvre.destination`,
`ManoeuvreLabels.leadsOnto`, the overlay's second line and the car's `road` all exist here, and its own proposal
rejected the spoken voice clause as requiring a submodule patch — the decision on #807 records that this
reading is wrong and that the fix is app-side. The item's recorded decision (2026-11-08T16:24:36Z, option 1)
says verbatim: re-point the item's change marker to `none`, so this run plans
`fix-motorway-destination-is-spoken` per decision A instead of copying the landed change and rejecting it at
Phase A. This change is that plan; applying the landed one again would produce an empty diff and no voice.
