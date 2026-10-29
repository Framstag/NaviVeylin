# Evidence — task 2.3: the revert-check of the offset's start-node exception (2026-10-29)

Invariant: **the start node's cumulative distance stays zero**, so step 0 reads as the route's start
and owns no leg, while every later node carries the approach.

Single mutation, in `route_start.h`'s `RouteStartNodeDistanceMeters` — the exception is deleted so the
offset also applies to node 0:

```c++
  if (nodeIndex == 0) {
    // MUTATION (revert-check, task 2.3): the start-node exception is removed.
    return nodeDistanceMeters + approachMeters;
  }
```

Red — the named case fails with its own message, on the assertion that states the exception:

```
$ meson test -C hostbuild "Check route start" --verbose
../Tests/src/RouteStartTest.cpp:90: FAILED:
  REQUIRE( naviveylin::RouteStartNodeDistanceMeters(0,0.0,approach)==0.0 )
with expansion:
  200.0 == 0.0
test cases:  5 | 4 passed | 1 failed
assertions: 15 | 14 passed | 1 failed
1/1 libosmscout:Check route start FAIL            0.03s   exit status 42
```

Mutation restored (`if (nodeIndex == 0) { return nodeDistanceMeters; }`), forced green:

```
$ meson test -C hostbuild "Check route start"
1/1 libosmscout:Check route start OK              0.03s
Ok:                1
Fail:              0
```

The green run above is a real rebuild of the case — meson recompiles the target because the header
changed, so the restored tree is not answered `UP-TO-DATE`.
