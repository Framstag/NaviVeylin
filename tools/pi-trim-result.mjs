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
// carries a second rule over the per-session ledger it keeps for the read tools: a repeat whose content sha matches
// the earlier read is replaced by one line naming the turn it was read at, its size and the range syntax, because a
// reader that wants part of that text again asks for the range and not for the file. A repeat whose sha differs is
// the session's own edit coming back — 728 of the archive's 2 042 per-session repeats follow an edit of that path —
// and that read is a verification, so it is handed over whole and becomes what the next repeat is measured against.
// What a repeat must never do is withhold what the child asked for silently: the line names the path, the size, the
// turn and the tool call that takes a range, and `LOOP_TOOL_RESULT_MAX_CHARS` recording `0` keeps both rules off.
//
// Two entry points, one pair of rules:
//
//   * as a pi extension (`pi --extension tools/pi-trim-result.mjs`, which the adapter every loop child is started
//     through does), it registers the `tool_result` hook and replaces a text block above the bound, or a text block
//     this session has already been handed at that path. The hook never throws: an error leaves the result exactly
//     as the tool returned it, because a rule that could fail a tool mid-item would be worse than the spend it saves.
//   * as a CLI (`node tools/pi-trim-result.mjs --bound <n> [--tool <name>] [--record <file>] [--path <file>]
//     [--turn <n>] [--ledger <file>]`), it applies the same rules to stdin, which is what the cases in
//     `tools/loop-cases.sh` drive — so the rules they prove are the rules the hook applies, and there is no second
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

/** The one line a repeat read is replaced by: the text the session holds, where it read it, and how to take a range. */
export function alreadyReadNotice(path, size, turn) {
  return `[LOOP-ALREADY-READ: ${path} (${size} characters) was read at turn ${turn} of this session and is`
    + ` unchanged since — read a range instead of the whole file again (ctx_read mode="lines:N-M")]\n`;
}

/**
 * The repeat rule: the text of a read, or the one line naming the earlier read when this session already holds the
 * same content at the same path. A read whose sha differs from the ledger's is the session's own edit coming back, so
 * it is handed over whole — that read is a verification — and becomes what the next repeat is measured against. The
 * rule never throws: an error hands the text over exactly as the tool returned it.
 */
export function repeatResult(text, read, bound) {
  try {
    if (!(bound > 0) || !read || !read.path || !read.ledger) return text;
    const sha = contentSha(text);
    const earlier = read.ledger.get(read.path);
    read.ledger.set(read.path, { sha, turn: read.turn, size: text.length });
    if (!earlier || earlier.sha !== sha) return text;
    return alreadyReadNotice(read.path, earlier.size, earlier.turn);
  } catch {
    return text; // a rule that could not be applied is not a reason to fail the read it was asked about
  }
}

/**
 * The session's ledger as a CLI invocation finds it again: `sha <tab> turn <tab> size <tab> path` per line, so the
 * case that drives this file repeats a read across two processes the way one child repeats it across two turns.
 */
export function readLedger(file) {
  const ledger = new Map();
  let text;
  try {
    text = readFileSync(file, "utf8");
  } catch {
    return ledger; // no ledger yet: this read is the session's first
  }
  for (const line of text.split("\n")) {
    const parts = line.split("\t");
    if (parts.length < 4 || !parts[0]) continue;
    ledger.set(parts.slice(3).join("\t"), {
      sha: parts[0],
      turn: Number.parseInt(parts[1], 10) || 0,
      size: Number.parseInt(parts[2], 10) || 0,
    });
  }
  return ledger;
}

/** A ledger that cannot be written costs a repeat, never the result it was being kept for. */
export function writeLedger(file, ledger) {
  const lines = [];
  for (const [path, entry] of ledger) lines.push(`${entry.sha}\t${entry.turn}\t${entry.size}\t${path}`);
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
    const text = trimResult(repeatResult(block.text, read, bound), bound, tool, record);
    if (text !== block.text) changed = true;
    return text === block.text ? block : { ...block, text };
  });
  return changed ? out : null;
}

/** The extension factory: one hook, the bound the run recorded, and the reads this session has already been handed. */
export default function (pi) {
  // The ledger is the session's: one child process is one session, so a Map that lives as long as the hook is
  // per-session, and the turn a read happened at is the number of the turn this process has started.
  const ledger = new Map();
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
      const read = { path: readPath(tool, event.input), ledger, turn: turn || 1 };
      const content = trimContent(event.content, bound, tool, record, read);
      return content ? { content } : undefined;
    } catch {
      return undefined; // the result stands as the tool returned it
    }
  });
}

/**
 * The CLI: the same rules over stdin, for the cases and for a reader who wants to see what they would do. A read tool
 * call is named by `--path` (the path the tool was given) and `--turn`, and `--ledger` carries the session's ledger
 * from one invocation to the next, so a case repeats a read the way a child repeats one across two turns.
 */
function cli(argv) {
  let bound = recordedBound(process.env.LOOP_TOOL_RESULT_MAX_CHARS);
  let tool = "?";
  let record = process.env.LOOP_TOOL_RESULT_TRIM_RECORD || "";
  let path = "";
  let turn = 1;
  let ledgerFile = "";
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
    } else if (name === "-h" || name === "--help") {
      process.stdout.write("usage: pi-trim-result.mjs --bound <chars> [--tool <name>] [--record <file>]"
        + " [--path <file>] [--turn <n>] [--ledger <file>] < result\n");
      return 0;
    } else {
      process.stderr.write(`pi-trim-result: unknown argument ${name}\n`);
      return 2;
    }
  }
  const ledger = ledgerFile ? readLedger(ledgerFile) : new Map();
  const text = readFileSync(0, "utf8");
  const read = { path: readPath(tool, { path }), ledger, turn };
  process.stdout.write(trimResult(repeatResult(text, read, bound), bound, tool, record));
  if (ledgerFile) writeLedger(ledgerFile, ledger);
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = cli(process.argv.slice(2));
}
