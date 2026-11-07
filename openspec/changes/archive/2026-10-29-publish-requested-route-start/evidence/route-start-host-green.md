# Evidence — task 2.1 / 2.2: the host helper and its cases (2026-10-29)

## 2.1 — the header compiles alone, warning-free

A translation unit that includes nothing but the header, compiled with the flags the
JNI fast check of §66 uses plus two stricter ones:

```
$ cd app/src/main/cpp/libosmscout/libosmscout-client-java/src
$ printf '#include "route_start.h"\nint main(){return 0;}\n' > /tmp/route_start_tu.cpp
$ c++ -std=c++20 -fsyntax-only -Wall -Wextra -Wpedantic -Wconversion -Wshadow -I. /tmp/route_start_tu.cpp
syntax-only: clean (no warning, no error)
```

The header carries no libosmscout include — `route_length.h` beside it is the precedent — so the
host test includes it alone as well.

## 2.2 — the new cases, and the whole host suite, green

Registered in `Tests/meson.build` beside `SearchBreakerTest` (the dependency-free-helper group,
`include_directories('../libosmscout-client-java/src')`). The test name meson computes is
`libosmscout:Check route start` — `meson test -C hostbuild "Check route start"`, not a target path
(the §40.48 recipe: this build directory has no `CTestTestfile.cmake`, so `ctest` answers "No tests
were found").

```
$ meson test -C hostbuild "Check route start"
1/1 libosmscout:Check route start OK              0.02s
Ok:                1
Fail:              0
```

Five cases, 16 assertions: the approach of a start that is the snapped node is zero; the approach is
the great-circle length between the two positions (11 119 m for a tenth of a degree of latitude, and
the longitude step at 60° north is 0.50 of the equatorial one); a start inside the snap tolerance is
not prepended (including exactly at the tolerance — the comparison is strict); a start that is not a
position has no approach; the offset leaves the start node at zero and adds the approach to every
later node.

The whole suite, with the doubled per-test budget the §6g timeout record asks for:

```
$ meson test -C hostbuild --timeout-multiplier 2 --print-errorlogs
168/169 libosmscout:Check threaded database                                  OK   84.56s
169/169 libosmscout:Check number set performance                             OK  218.84s
Ok:                169
Fail:              0
```

`Check threaded database` passes in 84.56 s under the doubled budget — quoted because §6g records it
being killed at the 30 s × 2 default.
