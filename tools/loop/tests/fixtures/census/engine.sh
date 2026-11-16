#!/usr/bin/env bash
# A fixture engine for the census's offline cases: two guards with patrons and one whose reason nobody wrote down.
# It is never run; the census only reads it.

# issue #101
stop_run "the closed patron's guard is listed under both"

# issue #102
stop_run "the open patron's guard is held by a case"

stop_run "the guard with no patron is counted, not refused"
