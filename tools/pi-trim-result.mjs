#!/usr/bin/env node
// pi-trim-result.mjs — what a single tool result may put into a child's context: a bound on its size (issue #660),
// and one line instead of a file the child has already been handed (issue #685).
//
// Why this file exists: a child's tool result is injected whole and stays in its context for the rest of the
// session, so every later turn re-reads it, and nothing in this tree between the tool and the context bounded it.
// Measured over the archive the loop reads (`python3 tools/loop-spend.py`): 59.2 M characters of tool output injected
// once, 876.4 M tokens of it re-read — 53 % of everything the archive spent — with a 116.6 k `ctx_read` and 51.3 k
// `ctx_shell` at the top, while the median result is 825 characters (`evidence/result-sizes.py` in the change
// `2026-10-27-fix-a-tool-result-over-its-bound-is-trimmed`). The spend is the tail, so the bound is a rule about
// the tail: a result above it is cut and named, a result under it is handed over byte for byte.
//
// Prose does not do this job. The worker prompt has asked a child to read the section it needs rather than the
// whole document, and to cite a file rather than read it again, since #246 — and the archive still records
// `LOOP-SPEND-READS calls=4798 duplicate=2174`: 45 % of its reads are a second read of a path the child already
// read, with a 116.6 k single result standing beside them.
//
// The same archive says the bound is only half of it (issue #685): `LOOP-SPEND-READS calls=6021 duplicate=2847` —
// 47 % of the reads are a second read of a path that child already had, and every one of them is injected once more
// and re-read by every turn that follows. The bound does not know the child already holds the text. So the handler
// carries a second rule over the ledger the item keeps for its read tools: a repeat whose content sha matches
// the earlier read is replaced by one line naming the turn it was read at, its size and the range syntax, because a
// reader that wants part of that text again asks for the range and not for the file. A repeat whose sha differs is
// the session's own edit coming back — 728 of the archive's 2 042 per-session repeats follow an edit of that path —
// and that read is a verification, so it is handed over whole and becomes what the next repeat is measured against.
// What a repeat must never do is withhold what the child asked for silently: the line names the path, the size, the
// turn and the tool call that takes a range, and `LOOP_TOOL_RESULT_MAX_CHARS` recording `0` keeps both rules off. What
// it withheld is written down too (issue #831): the rule appends `LOOP-TOOL-RESULT-REPEAT: <tool> chars=<n> kept=<k>` to
// the record the bound already writes, so an item's evidence says how much of a repeat's own result the child was not
// handed, instead of leaving the report to call the raw volume re-injected.
//
// The ledger is the **item's**, not the child's (issue #858). One child process is one session, so a `Map` that lives as
// long as the hook is one child deep: an item split at its gate starts its gate child on an empty ledger, and every path
// the worker read is a first read again — whole. Measured on the run of 2026-11-11T11:37:08Z, the rule withheld 1 370 of
// the 486 700 characters the items' repeat results carried (0.3 %), and the two items that read most were the two that
// were split (#848 31 of 83 reads, #849 27 of 77). So the adapter names one ledger file beside the item's event stream
// (`LOOP_TOOL_RESULT_LEDGER`), every child of the item opens it and writes back what it read, and the line a repeat is
// replaced by names the **session** that read the path (`LOOP_CHILD_SESSION`): the child that follows is not that
// session, and a line saying "this session" would be a false one.
//
// Two entry points, one pair of rules:
//
//   * as a pi extension (`pi --extension tools/pi-trim-result.mjs`, which the adapter every loop child is started
//     through does), it registers the `tool_result` hook and replaces a text block above the bound, or a text block
//     this session has already been handed at that path. The hook never throws: an error leaves the result exactly
//     as the tool returned it, because a rule that could fail a tool mid-item would be worse than the spend it saves.
//   * as a CLI (`node tools/pi-trim-result.mjs --bound <n> [--tool <name>] [--record <file>] [--path <file>]
//     [--turn <n>] [--ledger <file>]`), it applies the same rules to stdin, which is what the cases in
//     `tools/loop/tests/loop-cases.sh` drive — so the rules they prove are the rules the hook applies, and there is no second
//     implementation of them.
//
// Where the values come from: the bound is `LOOP_TOOL_RESULT_MAX_CHARS` (`tools/loop/loop.conf`, a recorded value;
// `0` or absent means no bound and no repeat rule), and the record is `LOOP_TOOL_RESULT_TRIM_RECORD`, which the
// adapter names beside the child's own event stream so the trim is a fact of the item rather than of the child's
// memory.
import { appendFileSync, readFileSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { pathToFileURL } from "node:url";

/** The bound as the recorded key states it: a positive whole number of characters, or 0 for "no bound". */
export function recordedBound(raw) {
  const n = Number.parseInt(String(raw ?? "").trim(), 10);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

/** The line that tells the child what it is reading, and how to read the rest of it. */
export function trimNotice(tool, total, bound) {
  return `\n[LOOP-TRIMMED: ${tool} returned ${total} characters; the first ${bound} are shown — read the rest in`
    + ` ranges (ctx_read mode="lines:N-M", or sed -n 'N,Mp')]\n`;
}

/** One line of the record, appended where the item's own evidence stands. */
export function trimRecordLine(tool, total, bound) {
  return `LOOP-TOOL-RESULT-TRIM: ${tool} chars=${total} kept=${bound}\n`;
}

/**
 * One line of the same record for a repeat the rule replaced (issue #831): the size the result had and the size of the
 * one line the child was handed, so `chars - kept` is the volume the rule withheld. It names no path — the path is in
 * the notice the child reads, and a record line that carried one would be a coordinate in a log.
 */
export function repeatRecordLine(tool, total, kept) {
  return `LOOP-TOOL-RESULT-REPEAT: ${tool} chars=${total} kept=${kept}\n`;
}

/**
 * The rule: a result at or under the bound is returned untouched; one above it is cut to its first `bound`
 * characters — never in the middle of a surrogate pair — and carries one line naming the tool, the size and the
 * bound, and the trim is appended to `record` when a path names one.
 */
export function trimResult(text, bound, tool, record) {
  if (typeof text !== "string" || !(bound > 0) || text.length <= bound) return text;
  const total = text.length;
  let cut = text.slice(0, bound);
  const last = cut.charCodeAt(cut.length - 1);
  if (last >= 0xd800 && last <= 0xdbff) cut = cut.slice(0, -1);
  if (record) {
    try {
      appendFileSync(record, trimRecordLine(tool, total, bound));
    } catch {
      // A record that cannot be written is not a reason to hand the child a result it cannot afford to re-read.
    }
  }
  return cut + trimNotice(tool, total, bound);
}

/** The tools whose result is one named file's content, and so a thing the session can already hold (issue #685). */
export const READ_TOOLS = ["ctx_read", "read"];

/** The path a read tool was given, or "" when this call is not a read of one named file. */
export function readPath(tool, input) {
  if (!READ_TOOLS.includes(tool)) return "";
  const path = input && (input.path || input.file_path);
  return typeof path === "string" && path ? path : "";
}

/** What a ledger entry is measured by: the sha of the whole text the tool returned for that path. */
export function contentSha(text) {
  return createHash("sha256").update(text, "utf8").digest("hex").slice(0, 32);
}

/** The one line a repeat read is replaced by: the text the item holds, the session and turn it was read at, and how to
 * take a range of it instead. A read with no session named is the fallback wording this rule shipped with. */
export function alreadyReadNotice(path, size, turn, session = "") {
  const reader = session ? `in the ${session} session` : "of this session";
  return `[LOOP-ALREADY-READ: ${path} (${size} characters) was read at turn ${turn} ${reader} and is`
    + ` unchanged since — read a range instead of the whole file again (ctx_read mode="lines:N-M")]\n`;
}

/**
 * The repeat rule: the text of a read, or the one line naming the earlier read when the item already holds the
 * same content at the same path. A read whose sha differs from the ledger's is the session's own edit coming back, so
 * it is handed over whole — that read is a verification — and becomes what the next repeat is measured against. A
 * replacement is recorded beside the trims (`repeatRecordLine`), because what the child was not handed is a fact only
 * this rule knows (issue #831). The rule never throws: an error hands the text over as the tool returned it.
 */
export function repeatResult(text, read, bound, tool = "?", record = "") {
  try {
    if (!(bound > 0) || !read || !read.path || !read.ledger) return text;
    const sha = contentSha(text);
    const earlier = read.ledger.get(read.path);
    read.ledger.set(read.path, { sha, turn: read.turn, size: text.length, session: read.session || "" });
    if (!earlier || earlier.sha !== sha) return text;
    const notice = alreadyReadNotice(read.path, earlier.size, earlier.turn, earlier.session || "");
    if (record) {
      try {
        appendFileSync(record, repeatRecordLine(tool, text.length, notice.length));
      } catch {
        // A record that cannot be written costs the figure, never the read the child asked for.
      }
    }
    return notice;
  } catch {
    return text; // a rule that could not be applied is not a reason to fail the read it was asked about
  }
}

/**
 * The item's ledger as a second child process finds it again: `sha <tab> turn <tab> size <tab> session <tab> path` per
 * line, so the child started after this one repeats a read the way one child repeats it across two turns (issue #858).
 * A line of an older shape — four columns, when the ledger was one child's — is dropped: the read it recorded is then an
 * item's first read and is handed over whole, which is the side this rule may err on.
 */
export function readLedger(file) {
  const ledger = new Map();
  let text;
  try {
    text = readFileSync(file, "utf8");
  } catch {
    return ledger; // no ledger yet: this read is the item's first
  }
  for (const line of text.split("\n")) {
    const parts = line.split("\t");
    if (parts.length < 5 || !parts[0]) continue;
    ledger.set(parts.slice(4).join("\t"), {
      sha: parts[0],
      turn: Number.parseInt(parts[1], 10) || 0,
      size: Number.parseInt(parts[2], 10) || 0,
      session: parts[3],
    });
  }
  return ledger;
}

/** A ledger that cannot be written costs a repeat, never the result it was being kept for. */
export function writeLedger(file, ledger) {
  const lines = [];
  for (const [path, entry] of ledger) {
    lines.push(`${entry.sha}\t${entry.turn}\t${entry.size}\t${entry.session || ""}\t${path}`);
  }
  try {
    writeFileSync(file, lines.length ? `${lines.join("\n")}\n` : "");
  } catch {
    // the next read of that path is handed over whole: the side that keeps the child working
  }
}

/**
 * The content array the hook returns, with every repeat replaced by its one line and every oversized text block cut.
 * A changed result drops `structuredContent`: it no longer describes what the child is reading, and the agent's own
 * contract says so.
 */
export function trimContent(content, bound, tool, record, read) {
  let changed = false;
  const out = (content || []).map((block) => {
    if (!block || block.type !== "text" || typeof block.text !== "string") return block;
    const text = trimResult(repeatResult(block.text, read, bound, tool, record), bound, tool, record);
    if (text !== block.text) changed = true;
    return text === block.text ? block : { ...block, text };
  });
  return changed ? out : null;
}

/** The extension factory: one hook, the bound the run recorded, and the ledger of what this item's children read. */
export default function (pi) {
  // The ledger is the item's (issue #858): the adapter names the file beside the item's event stream, so the child
  // started after this one opens the reads this one made instead of meeting every one of them as a first read. With no
  // file named — a child started by hand — the ledger is this process's own, which is what the rule shipped with. The
  // turn is the number of the turn this process has started, and the session is what the notice names it by.
  const ledgerFile = process.env.LOOP_TOOL_RESULT_LEDGER || "";
  const session = process.env.LOOP_CHILD_SESSION || "";
  const ledger = ledgerFile ? readLedger(ledgerFile) : new Map();
  let turn = 0;
  try {
    pi.on("turn_start", async () => {
      turn += 1;
    });
  } catch {
    // An agent that reports no turn leaves every read on turn 1: the repeat is still bounded, its turn less exact.
  }
  pi.on("tool_result", async (event) => {
    try {
      const bound = recordedBound(process.env.LOOP_TOOL_RESULT_MAX_CHARS);
      if (!bound) return undefined;
      const record = process.env.LOOP_TOOL_RESULT_TRIM_RECORD || "";
      const tool = event.toolName || "?";
      const read = { path: readPath(tool, event.input), ledger, turn: turn || 1, session };
      const content = trimContent(event.content, bound, tool, record, read);
      // What the read tool returned is the item's now, so the child that follows this one finds it: written even when
      // the result itself was handed over unchanged, because a first read is what the next one is measured against.
      if (read.path && ledgerFile) writeLedger(ledgerFile, ledger);
      return content ? { content } : undefined;
    } catch {
      return undefined; // the result stands as the tool returned it
    }
  });
}

/**
 * The CLI: the same rules over stdin, for the cases and for a reader who wants to see what they would do. A read tool
 * call is named by `--path` (the path the tool was given) and `--turn`, and the ledger is the one the environment names
 * (`LOOP_TOOL_RESULT_LEDGER`, what the adapter hands every child of an item) or the one `--ledger` names, so a case
 * drives a read the way the item's second child meets the first one's.
 */
function cli(argv) {
  let bound = recordedBound(process.env.LOOP_TOOL_RESULT_MAX_CHARS);
  let tool = "?";
  let record = process.env.LOOP_TOOL_RESULT_TRIM_RECORD || "";
  let path = "";
  let turn = 1;
  let ledgerFile = process.env.LOOP_TOOL_RESULT_LEDGER || "";
  let session = process.env.LOOP_CHILD_SESSION || "";
  for (let i = 0; i < argv.length; i += 1) {
    const name = argv[i];
    const value = argv[i + 1];
    if (name === "--bound") {
      bound = recordedBound(value);
      i += 1;
    } else if (name === "--tool") {
      tool = value || tool;
      i += 1;
    } else if (name === "--record") {
      record = value || record;
      i += 1;
    } else if (name === "--path") {
      path = value || "";
      i += 1;
    } else if (name === "--turn") {
      turn = Number.parseInt(value ?? "", 10) || 1;
      i += 1;
    } else if (name === "--ledger") {
      ledgerFile = value || "";
      i += 1;
    } else if (name === "--session") {
      session = value || session;
      i += 1;
    } else if (name === "-h" || name === "--help") {
      process.stdout.write("usage: pi-trim-result.mjs --bound <chars> [--tool <name>] [--record <file>]"
        + " [--path <file>] [--turn <n>] [--ledger <file>] [--session <name>] < result\n");
      return 0;
    } else {
      process.stderr.write(`pi-trim-result: unknown argument ${name}\n`);
      return 2;
    }
  }
  const ledger = ledgerFile ? readLedger(ledgerFile) : new Map();
  const text = readFileSync(0, "utf8");
  const read = { path: readPath(tool, { path }), ledger, turn, session };
  process.stdout.write(trimResult(repeatResult(text, read, bound, tool, record), bound, tool, record));
  if (ledgerFile) writeLedger(ledgerFile, ledger);
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = cli(process.argv.slice(2));
}
