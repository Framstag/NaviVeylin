# The closure of #233

`tools/issues/close.sh 233 --reason … --evidence …` — exit 0, commented then closed, `state_reason` `completed`.

```
https://github.com/Framstag/NaviVeylin/issues/233
closed #233 (completed)
```

Verified after the call:

```
state=CLOSED reason=COMPLETED closedAt=2026-10-24T09:08:26Z
labels=agent/eligible,category/build-and-harness,class/bug,source/agent,status/fixed-by
```

The landed commit is `f947886a` on `main` ("fix: a-suite-that-ends-normally-leaves-no-process (#233)"), the
archived change `2026-10-24-fix-loop-suite-normal-exit-leftover`. The reason and the evidence the closure
carries name the new case, the revert-check, the gate's attributed red set and the residual filed as #295.
