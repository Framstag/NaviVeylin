# Reason for the closure of #162

**Archived change:** `openspec/changes/archive/2026-10-18-fix-portability-value-set-and-preconditions/`
(the reference is available once the change is archived; before that,
`openspec/changes/fix-portability-value-set-and-preconditions/`).

## What was wrong

`tools/check-portability.sh` read its forbidden values from the profile's **category namespace only**, so the
rule it implements — "an engine names no project value" — held for one namespace of the vocabulary.
`tools/loop/worktree.sh` named the submodule path `app/src/main/cpp/libosmscout`, `tools/loop/prescreen.sh`
named the class and status values it selected from the profile, and `tools/loop/labels.sh` named the
`agent`/`status` values of its verdict→label mapping. Measured with the widened check: 78 leak lines in 9
engine files on a tree the category-only check called clean.

## What now makes it impossible

- `tools/check-portability.sh:150-187` reads **every** namespace's values and label forms from the profile
  (158-168), its `roles` section (170-173), and `loop.conf`'s `LOOP_SUBMODULE_PATH` (187), and forbids all of
  them in an engine; the reserved authorisation label is asserted as the single read-only exception and
  printed (`tools/check-portability.sh:288-318`).
- `tools/loop/prescreen.sh:57-69,116-141` reads `queuedVerdict`, `workedClasses`, `refusedClasses`,
  `inFlightStatus`, `deviceStatus`, `deviceAgent` and `settledStatuses` from the profile's `roles` section
  through `tools/loop/lib.sh`'s `loop_role` (`tools/loop/lib.sh:117-136`; the loader for the section,
  `loop_roles_load` at 123, and the reader `loop_role` at 134).
- `tools/loop/labels.sh:53-62,72-102` reads `holdStatus`, `closeStatus`, `inProgressAgent`, `deviceAgent`
  and `decisionAgent` the same way.
- `tools/loop/worktree.sh:70-76` reads `LOOP_SUBMODULE_PATH` from `tools/loop/loop.conf`.
- `tools/issues/resolve-stale.sh:99-105,235` reads `roles.holdStatus`.

## Evidence

- Red on HEAD: `evidence/red-on-head.log` — `check-portability-selftest: 5 of 26 cases failed`,
  `loop-selftest: 2 of 55 cases failed`, plus the measurement of the widened check's 78 lines.
- Green: `evidence/verify.md` — `check-portability: OK — 40 engines and 2 templates name no project value
  (91 value(s) read from the project)`; `check-portability-selftest: all 26 cases passed`;
  `loop-selftest: all 55 cases passed`.
- Falsification: `evidence/revert-checks.md` — the 13-mutation set (`tools/mutate.sh --mutations
  tools/portability-mutations.json`), each mutation red and restored green, including 6.1 (the namespace value
  set), 6.2 (the submodule path), 6.3 (the asserted exception) and 6.4/6.5 (the two preconditions).
