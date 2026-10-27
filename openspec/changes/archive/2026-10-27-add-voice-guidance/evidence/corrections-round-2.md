# Round 2 — corrections only

Review round 1 (`evidence/review-round-1-requested.md`, `LOOP-BLOCKING: 1`, `LOOP-CORRECTIONS-ONLY: yes`)
refused the change for one blocking finding and a set of notes. This round changed documentation only:
no production code, no test case, no assertion, no new claim.

## Blocking finding — the universal quantifier

The requirement promised "every non-empty native voice sample sequence", which the implementation does
not guarantee: a second sentence that arrives before TTS init replaces the pending one
(`AndroidVoiceOutput.pending = text`), `speakNow` speaks with `TextToSpeech.QUEUE_FLUSH`, and
`sentenceFor` returns `""` for a sequence with no recognised sample.

Corrected subtractively in both copies, which are now byte-identical:

- `openspec/specs/voice-guidance/spec.md` (requirement "Navigation speaks the native voice instruction")
- `openspec/changes/archive/2026-10-27-add-voice-guidance/specs/voice-guidance/spec.md`

```
While a navigation session is active, the engine SHALL announce each recognised native voice
instruction through the device's text-to-speech output, replacing any utterance in progress. It SHALL
compose the sample identifiers into one localized instruction sentence in the order the native agent
emitted them, SHALL stay silent when a sequence carries no recognised instruction, and SHALL NOT speak
when no navigation session is active.
```

Neither scenario, its `Case:` pointer, nor the shipped Purpose changed.

## Notes corrected

| Note (review round 1) | Correction |
|---|---|
| `tasks.md` 1.1 "XML recorded before production code" overstates the red run's ordering | now "red before the engine consumed the callback (`evidence/red-before-wiring.xml`; the seam classes were already compiled at that point)" |
| `proposal.md` red-case sentence "observes no utterance on HEAD" has the same overstatement | now cites the red XML as recorded before the engine consumes the callback |
| `tasks.md` 2.1 names an unnamed "structural check" | verification is now the new sources compiling in the red run (`evidence/red-before-wiring.xml`) |
| `tasks.md` 2.3 names a non-existent `check-*` string-completeness script | now names `app/src/test/java/com/naviveylin/i18n/GermanTranslationCompletenessTest.kt`, which the gate's gradle step runs |
| `tasks.md` 4.3 and 5.1 point at `openspec/changes/add-voice-guidance/…`, which the archive removed | both now name `openspec/changes/archive/2026-10-27-add-voice-guidance/…` (5.1 notes the open path it ran against before archiving) |
| `proposal.md` Impact folds `AndroidVoiceOutput` into `VoiceOutput.kt` | now two file entries, `VoiceOutput.kt` (contract) and `AndroidVoiceOutput.kt` (production output) |
| `tasks.md` Evidence quotes "26 classes, 164 cases" from a run no output in the record carries | removed; the bullet had no archived command behind it |
| `design.md` Blast radius quotes "25 existing engine test classes" with no command | count dropped; the sentence keeps the mechanism (no-op default in `EngineUnderTest`) |
| `design.md` Alternatives claims "Android TTS is already on every supported device" with no evidence | claim removed; the alternative is rejected on the assets argument alone |
| `guidelines/Design.md` §4 release-point enumeration omitted the new utterance stop | enumeration now ends "… releases the location lease and stops the current voice utterance." |

## Verification of this round (documentation-only change, so no compiled file and no gate)

No compiled source, no test source and no resource changed, so the gate was not re-run; the archived
round-1 gate stands (`evidence/gate.log` carries the `LOOP-GATE` line, tallies in
`evidence/gate-tallies/`).

```
$ bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-27-add-voice-guidance --root .
ok   C1 §references: 7 checked, 0 exempted as archived pointers
ok   C2 test tallies: 2 pairs confirmed, 0 exempted (not retained), 0 unparsable, 0 drift
ok   C3 /tmp pointers: none cited
ok   C4 mutations: no REVERT-CHECK MUTATION marker under 1 configured path(s): app/src
ok   C5 file:line: 242 resolved and in range, 0 unresolved or ambiguous
evidence-check: PASS

$ bash tools/check-openspec-hygiene.sh
Every capability spec states its Purpose.
OpenSpec artifacts are consistent: 0 open change(s) carry task markers, the operation guidance is quoted, and every capability spec states its Purpose.

$ bash tools/check-doc-routes.sh
check-doc-routes: all 154 section references resolve, all 4 skill references resolve
```

Measure block for the correction round, taken once from the change root with
`./tools/loop/measure.sh --change 2026-10-27-add-voice-guidance`:

```
LOOP-MEASURE-SUITE cases=254 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-CHANGE 2026-10-27-add-voice-guidance scenarios=2 cases=2 unnamed=0
LOOP-MEASURE-END
```

Both spec scenarios still name their case (`unnamed=0`), and the archived round-1 tallies quoted above
remain the change's gate evidence.
