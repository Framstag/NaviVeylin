# Follow-up — CI caught a JDK-dependent launch of the Java core

Found by the first CI run of the tooling (run 39024448684 on `ceedad3`, step **Check issue tooling**), the
same failure reproduced locally under JDK 21. Fixed in the commit that adds this file.

## The defect

`tools/issues/lib.sh` ran the core as a single-file *source*:

```bash
java "$ISSUES_CORE_DIR/$class.java" "$@"       # the form that broke
```

The single-file launcher compiles that file in memory and resolves its **siblings** through a source path
whose default differs by JDK: JDK 27 finds `Json.java`, `TodoSections.java` and `Taxonomy.java` next to the
named file; JDK 21 (the CI toolchain) does not, so every class that referenced a sibling failed with
`cannot find symbol Json` and every core-backed script returned nothing useful.

Measured, before the fix:

| environment | tooling self-test |
|---|---|
| author's machine, JDK 27 | 33 of 33 cases passed |
| `temurin-21.0.12`, same commit | **13 of 33 failed** — identical to the CI log |

So the tooling had been verified on exactly one JDK, and the change's own report could not have caught it:
the case that exercised the adapter passed locally and its run was never repeated under a second JDK.

## The fix

The core is compiled once into a cache keyed by the sources' digest and by the release, then run from the
class path:

```bash
javac --release 17 -d "$staging" "$ISSUES_CORE_DIR"/*.java
java -cp "$classes" "$class" "$@"
```

- `--release 17` makes the class files loadable by any JDK that can run the self-test. The first attempt
  keyed the cache by the sources alone; a JDK-27 build then left class-file v71 behind and every JDK-21 run
  died on `UnsupportedClassVersionError`, so the key now carries the release as well.
- The cache directory's name carries **no project value** — `lib#noProjectValueInTheEngine` refused the first
  name (`/tmp/naviveylin-issues-core`), which is that guard doing its job.
- A caller that has already compiled the core hands its directory in (`ISSUES_CORE_CLASSES`), so the
  self-test compiles once and runs exactly the classes it verified.
- The case runner's own `core()` helper used the same launcher form and was fixed with it.

## The invariant, now locked

New case **`lib#coreIsCompiledNotLaunched`** (34 cases in the suite now):

- structural: every `java` invocation in the engine, the driver and its case file passes `-cp` and names no
  `.java` file — the check fails if it finds none, so a rename cannot make it vacuous;
- behavioural: a core-backed script runs from a directory that is not the core's own.

Falsification (mutation applied and restored in the working tree):

```
mutation: the launcher form restored in lib.sh
  FAIL lib#coreIsCompiledNotLaunched  → "the core is not run from a class path: …/lib.sh:127:  java "$ISSUES_CORE_DIR/$class.java" "$@""
restored:
  PASS lib#coreIsCompiledNotLaunched
```

## Verification after the fix

| check | result |
|---|---|
| `tools/issue-tooling-selftest.sh` under **JDK 17** (`openjdk-17.0.2`) | 34/34 |
| … under **JDK 21** (`temurin-21.0.12`, the CI toolchain) | 34/34 |
| … under **JDK 27** (the author's) | 34/34 |
| the real cache path, cold under JDK 21 then warm under JDK 27 | `list.sh --status open --json` → 75 items on both; `labels.sh --json` → `{toCreate: 0, toRemove: 0}` |
| `tools/check-todo-references.sh` and its self-test | unchanged, green |

The three JDK runs are the point: the fix's acceptance criterion is that the suite stops depending on which
JDK runs it, so one JDK is not evidence.
