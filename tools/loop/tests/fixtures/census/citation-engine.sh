#!/usr/bin/env bash
# Two citation shapes: the engine's form is read, a bare number in prose is not.

# issue #101 is the patron
stop_run "the cited guard is held under its patron"

# #102 appears here without the word issue, so it is prose and not a patron
stop_run "the prose number is not a patron"
