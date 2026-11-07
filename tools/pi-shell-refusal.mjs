#!/usr/bin/env node
// pi-shell-refusal.mjs — the shell does not answer a tree question (issue #687).
//
// Why this file exists: of 16 987 `ctx_shell` calls over the loop's archive, 9 338 (55 %) carry a search or read
// verb — `grep` 4 997, `head` 4 955, `ls` 2 550, `sed` 2 208, `tail` 1 240, `wc` 1 077, `cat` 1 006, `awk` 533,
// `find` 393 — at a p50 of 52 such calls per item, and the calls chain (`grep -> grep` 1 017x in 132 items,
// `grep -> sed` 385x; `python3 tools/loop-spend.py`): one question costs three calls, and each call is one turn and
// one context re-read. The tools that answer it are there and unused (361 `ctx_grep` calls against ~1 700 shell
// `grep`s; no `ctx_compose` call at all), so the habit is what is missing, and a habit is not changed by a sentence
// in a prompt — the finding `tools/pi-trim-result.mjs` records for an oversized result, one seam over.
//
// So the refusal sits where the child is started, as a policy and not as advice: `tools/pi-child-events.sh` loads
// this file with every loop child, and the `tool_call` hook below blocks a shell call whose command searches the
// project's own tree. The analogue this project already carries is the shell allowlist that refuses `tmux` in an
// operator's session — a policy at the seam, not a request in prose.
//
// What it refuses, and what it deliberately leaves alone:
//
//   * **Not a range read.** `sed -n 'N,Mp'` and `tail -n <N> <file>` are how `guidelines/Build.md` §10 reads a log by
//     range and how a build's verdict is read back from `/tmp/build-app.log`. `sed` is not in the verb list at all,
//     and `tail` with a range flag is allowed whatever it names.
//   * **Not a stream filter.** `adb logcat -d | grep -E 'Diag/HOST'`, `jq … | awk -F'\t' …`, `echo "$L" | grep -c` read
//     another command's output, not the tree, and refusing them would delete recipes in `guidelines/Build.md` §10 and
//     in the skills. Only the **head** of a pipeline is classified.
//   * **Not an artefact.** A path under a generated tree (`build/`, `.gradle`, `.pi`, …), outside this repository
//     (`/tmp/build-app.log`, a device path) or behind a variable the policy cannot resolve is a known file, not the
//     tree a search is answered from — so `grep -E 'BUILD SUCCESSFUL' /tmp/run-tests.log | head` and
//     `grep -o 'tests="…"' core/build/reports/…xml | tail -1` stand.
//   * **Not a write.** `cat > file <<'EOF'` writes a file; the read verbs that feed it name no path of the tree.
//   * **Not inside quotes.** A quoted run is one opaque word — that is what keeps a pattern carrying `|` or `(` from
//     being read as a pipeline, and it is why `echo "$(ls app/src)"` is not classified. The policy is a bound on the
//     habit, not a sandbox: a child that wants `ls` inside quotes can still have it, and a child that means its tree
//     question now has a reason to reach for the tool that answers it.
//
// What it refuses is the case the measurement is about: `grep -rn` over `app/src`, `cat guidelines/Build.md`,
// `ls openspec/changes/archive`, `find . -name '*.kt'`, `awk … tools/x.sh`, `wc -l` of a source file. Each of those is
// one `tools/look.sh '<regex>'` (the whole tree and the submodule in one bounded call), one `ctx_grep`, one `ctx_read`.
//
// Two entry points, one rule, as `tools/pi-trim-result.mjs` has:
//
//   * as a pi extension (`pi --extension tools/pi-shell-refusal.mjs`), it registers the `tool_call` hook and blocks
//     the call with a reason naming the tool to use instead. The hook never throws: a policy that could fail a tool
//     mid-item would cost more than the spend it saves.
//   * as a CLI (`node tools/pi-shell-refusal.mjs --command '<shell command>'`), it applies the same rule and prints
//     `REFUSED <reason>` or `ALLOWED` — which is what the cases in `tools/loop-cases.sh` drive, so there is no second
//     implementation of the rule for them to drift from.
import { pathToFileURL } from "node:url";

/** The verbs whose shell form a tool answers better. `sed` is absent on purpose: `sed -n 'N,Mp'` is a range read. */
export const REFUSED_VERBS = ["grep", "cat", "head", "tail", "ls", "find", "wc", "awk"];

/** The tools that carry a shell command line. */
export const SHELL_TOOLS = ["ctx_shell", "shell", "bash"];

/** A directory whose contents are generated or machine-local rather than the tree a search is answered from. */
const NOT_THE_TREE = new Set(["build", ".gradle", ".kotlin", ".idea", ".cxx", ".pi", "node_modules", "vcpkg"]);

/** Flags of a refused verb that consume the word after them, so a flag's value is not read as a path. */
const VALUE_FLAGS = new Set(["-e", "-f", "--regexp", "--file", "-A", "-B", "-C", "-m", "--max-count"]);

/** How a command line is taken apart: a pipe is a pipeline, everything else ends a segment. */
const SEPARATORS = ";&(){}`\n";

/** One command line as its words, with a `|` and every other separator kept as a token of its own. */
export function words(text) {
  const source = String(text ?? "");
  const tokens = [];
  let word = "";
  const endWord = () => {
    if (word) tokens.push(word);
    word = "";
  };
  for (let i = 0; i < source.length; i += 1) {
    const ch = source[i];
    if (ch === '"' || ch === "'") {
      const close = source.indexOf(ch, i + 1);
      const stop = close === -1 ? source.length : close;
      word += source.slice(i, stop + 1);
      i = stop;
      continue;
    }
    if (ch === "$" && source[i + 1] === "(") {
      endWord();
      tokens.push(";"); // a command substitution is a command of its own, not a word of this one
      i += 1;
      continue;
    }
    if (ch === "|" && source[i + 1] === "|") {
      endWord();
      tokens.push(";");
      i += 1;
      continue;
    }
    if (ch === "|") {
      endWord();
      tokens.push("|");
      continue;
    }
    if (SEPARATORS.includes(ch) || /\s/.test(ch)) {
      endWord();
      continue;
    }
    word += ch;
  }
  endWord();
  return tokens.map((token) => token.replace(/^["']|["']$/g, "").replace(/^[(]+|[)]+$/g, ""));
}

/**
 * The command line as segments, in order: `{ pipeTail, words }`. A segment is a run of words between two separators,
 * and it is a pipe tail when the only thing before it was a `|` — which is the one thing a refusal must not treat as
 * a search of the tree, because it reads the previous command's output.
 */
export function commandSegments(command) {
  const segments = [];
  let current = [];
  let pipeTail = false;
  const close = () => {
    const group = current;
    current = [];
    if (!group.length) return;
    let i = 0;
    while (i < group.length && /^[A-Za-z_][A-Za-z0-9_]*=/.test(group[i])) i += 1; // `FOO=1 grep …` is grep
    if (i < group.length) segments.push({ pipeTail, words: group.slice(i) });
    pipeTail = false;
  };
  for (const token of words(command)) {
    if (token === "|") {
      close();
      pipeTail = true;
      continue;
    }
    if (token === ";") {
      close();
      continue;
    }
    current.push(token);
  }
  close();
  return segments;
}

/**
 * A path token is the tree when it names something inside this repository: relative, not escaped with `..`, and not
 * under a generated tree. What the policy cannot resolve — a variable, a home path, an absolute path outside the
 * working directory — is read as a known file elsewhere, which is the side that keeps a recipe working.
 */
export function inTheTree(token, cwd) {
  if (!token || token.startsWith("-")) return false;
  if (token.includes("$") || token.startsWith("~")) return false;
  let path = token;
  if (path.startsWith("/")) {
    const root = String(cwd ?? "").replace(/\/+$/, "");
    if (!root || !path.startsWith(root + "/")) return false;
    path = path.slice(root.length);
  }
  if (path.startsWith("../")) return false;
  path = path.replace(/^\.\//, "");
  return !path.split("/").some((segment) => NOT_THE_TREE.has(segment));
}

/** The words of one segment that are neither the verb nor a flag (nor the value a flag consumes). */
export function operands(segmentWords) {
  const named = [];
  let namedByFlag = false;
  for (let i = 1; i < segmentWords.length; i += 1) {
    const word = segmentWords[i];
    if (word.startsWith("-")) {
      if (["-e", "-f", "--regexp", "--file"].includes(word)) namedByFlag = true;
      if (VALUE_FLAGS.has(word)) i += 1;
      continue;
    }
    named.push(word);
  }
  return { named, namedByFlag };
}

/** `tail` with a range flag is a range read of a known file: the allowance the verbs keep. */
export function isRangeRead(segmentWords) {
  return segmentWords.slice(1).some((word) => /^-[nc]?\+?\d+$/.test(word) || /^-[nc]$/.test(word));
}

/** Whether one segment — the verb first, no pipe before it — is the tree search this file refuses. */
export function refusesSegment(segmentWords, cwd) {
  const verb = segmentWords[0];
  if (!REFUSED_VERBS.includes(verb)) return false;
  if (verb === "tail" && isRangeRead(segmentWords)) return false;
  // `cat > file` is a write, not a read of the tree; the heredoc body is not the child's command line.
  if (verb === "cat" && segmentWords.some((word) => word.startsWith(">"))) return false;
  const { named, namedByFlag } = operands(segmentWords);
  // `grep <pattern> <path…>`: the first word is the pattern unless `-e`/`-f` named it, so it is no path of its own.
  const paths = verb === "grep" && !namedByFlag ? named.slice(1) : named;
  if (paths.length === 0) return true; // no path named: the tree it stands in is the subject
  return paths.some((path) => inTheTree(path, cwd));
}

/** The reason a blocked call carries: the verb, and the one call that answers it. */
export function refusalReason(verb) {
  let instead;
  if (verb === "cat" || verb === "head") {
    instead = '`ctx_read` reads the file, or `ctx_read mode="lines:N-M"` a range of it';
  } else if (verb === "ls") {
    instead = "`ctx_ls` lists the directory, `tools/look.sh '<regex>' --files` names the files a pattern is in";
  } else {
    instead = "`tools/look.sh '<regex>'` searches the worktree and the submodule in one bounded call, `ctx_grep` searches one path";
  }
  return (
    `shell \`${verb}\` is not how this tree is read: ${instead}. A range of a known file stays shell's — `
    + "`sed -n 'N,Mp'`, `tail -n <N> <file>` — and so does a filter of another command's output (`… | grep`)."
  );
}

/** The rule over a whole command line: the head of each pipeline is classified, and the first refusal is the answer. */
export function refusal(command, cwd) {
  for (const segment of commandSegments(command)) {
    if (segment.pipeTail) continue;
    if (refusesSegment(segment.words, cwd)) return refusalReason(segment.words[0]);
  }
  return null;
}

/** The extension factory: one hook, and the refusal is the only thing it adds to the child. */
export default function (pi) {
  pi.on("tool_call", async (event) => {
    try {
      if (!SHELL_TOOLS.includes(event.toolName)) return undefined;
      const reason = refusal(event.input && event.input.command, process.cwd());
      return reason ? { block: true, reason } : undefined;
    } catch {
      return undefined; // the call stands as the child made it
    }
  });
}

/** The CLI: the same rule over one command line, with `$PWD` (or `--cwd`) as the tree. */
function cli(argv) {
  let command = null;
  let cwd = process.cwd();
  for (let i = 0; i < argv.length; i += 1) {
    const name = argv[i];
    if (name === "--command") {
      command = argv[i + 1] ?? "";
      i += 1;
    } else if (name === "--cwd") {
      cwd = argv[i + 1] ?? cwd;
      i += 1;
    } else if (name === "-h" || name === "--help") {
      process.stdout.write("usage: pi-shell-refusal.mjs --command '<shell command>' [--cwd <dir>]\n");
      return 0;
    } else {
      process.stderr.write(`pi-shell-refusal: unknown argument ${name}\n`);
      return 2;
    }
  }
  if (command === null) {
    process.stderr.write("pi-shell-refusal: --command takes the shell command to classify\n");
    return 2;
  }
  const reason = refusal(command, cwd);
  if (reason) {
    process.stdout.write(`REFUSED ${reason}\n`);
    return 3;
  }
  process.stdout.write("ALLOWED\n");
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = cli(process.argv.slice(2));
}
