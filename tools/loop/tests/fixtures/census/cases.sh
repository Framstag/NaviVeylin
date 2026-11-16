#!/usr/bin/env bash
# A fixture cases file: it carries one guard's refusal phrase and none of the others.

case_the_open_guard() {
  # files: tools/loop/census.sh tools/loop/tests/fixtures/census/
  grep -Fq "the open patron's guard is held by a case" tools/loop/tests/fixtures/census/engine.sh
}
