#!/usr/bin/env bash
# Starts the self-test suite with `SIGHUP` **ignored**, the disposition the gate's `nohup` hands every step of its own
# (issue #978). `nohup` is not a program this session may start, and an ignored disposition is the one signal property
# POSIX keeps across `exec`, so `trap '' HUP` here reads the same to every process below it as `nohup` does.
set -u
trap '' HUP
exec /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/tests/loop-selftest.sh "$@"
