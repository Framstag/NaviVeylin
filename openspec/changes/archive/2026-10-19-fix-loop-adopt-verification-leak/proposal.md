# Rename the adopt path's variable so the portability gate is green again

## Why

Measured on HEAD `a45fcc4` (`fix-loop-merge-finished-attempt`, issue #176):

```
$ bash tools/check-portability.sh
check-portability: FAIL tools/loop/worktree.sh:241:  [ -n "$verification" ] ||: names the project value 'verification' (tools/tooling.json namespaces.category)
check-portability: FAIL tools/loop/worktree.sh:261:    … "$change" "$verification": names the project value 'verification' (tools/tooling.json namespaces.category)
check-portability: 2 leak(s) — a project value belongs in a config, never in an engine
$ echo $?
1
```

**Root cause**: the new `cmd_adopt_item` names its local variable `verification`, after the green
verification record it accepts a branch for. `verification` is also a **category** value of the project
profile (`tools/tooling.json`, `namespaces.category`), so the check reads the engine as naming a project
value — which is exactly what `openspec/specs/tooling-portability/spec.md` ("An engine names no project
value") requires it not to do. The collision is accidental: the word is an ordinary English noun here, not
a reference to the category, but the check's rule is over the value set and cannot tell the two apart.

**Consequence, measured**: `Check portability` is a step of the build workflow, so HEAD is red there; no
change's gate can be green, and — because the schedule is live again — every scheduled sensor tick reads a
red build.

**The one fix**: name the local variable so the engine names no category value. The declaration, the
emptiness check and the `adopted:` line change; nothing else does.

## What changes

1. `tools/loop/worktree.sh`, `cmd_adopt_item`: the local variable `verification` is renamed `evidence` in
   its declaration, in the `[ -n … ]` refusal check and in the `adopted: … evidence %s` line. The value is
   unchanged, so the printed record still names the record the branch was accepted for and the refusal
   message is untouched.
2. Nothing else names the value: `grep -rn verification tools/loop/` finds the function name
   `carried_verification` and prose in comments — neither is a named value (the function name is part of an
   identifier, the prose is a sentence), which the check confirms by reporting no leak after the rename.

## Impact

| file | change |
|---|---|
| `tools/loop/worktree.sh` | the local variable in `cmd_adopt_item` is renamed; no behaviour change |

No compiled file changes. The gates are `tools/check-portability.sh`, the portability suite
(`tools/check-portability-selftest.sh`) and the loop suite (`tools/loop-selftest.sh`).

**Previous specifications changed**: none. The requirement "An engine names no project value" already owns
this rule and its scenario "The check passes on the project it guards" already states the contract the
defect broke; the defect is a violation of that requirement, not a gap in it, so no spec delta is declared.

**Guidelines affected**: `guidelines/Backlog.md` §10 "Portability" (the rule) — stated, not changed.

**Additive or breaking**: additive. A local variable's name is not an interface, no state or output
changes, and no caller observes it.

**Rollback**: restore the name `verification`. `bash tools/check-portability.sh` then reports the two leaks
again and exits 1 — the red state this change removes.
