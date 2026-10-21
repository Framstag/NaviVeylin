import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * <strong>The work package</strong>: how an issue's text reaches a consumer. Title, body and the item's
 * recorded decision (the comment the answer path writes) on a public tracker are attacker-controlled, so each
 * is emitted inside a delimited, labelled data field with its provenance and fingerprint alongside it —
 * never in an instruction position — and the receiver's contract is to re-verify every claim against the tree
 * before acting (design D7). The decision travels because nothing else carries it: the item's body still
 * states the question it settled (issue #196).
 *
 * The bounds in the profile make an oversized or malformed body a refusal with a reason
 * ({@code input-too-large}, {@code input-nul}), not a partial parse.
 *
 * Usage: {@code java tools/issues/core/WorkPackage.java --profile <tooling.json> --issue <issue.json|->}
 */
public final class WorkPackage {

    public static final String OPEN = "<<<ISSUE_TEXT";
    public static final String CLOSE = "ISSUE_TEXT";
    /** The recorded decision is a block of its own, so a worker can tell the answer from the claim. */
    public static final String DECISION_OPEN = "<<<ITEM_DECISION";
    public static final String DECISION_CLOSE = "ITEM_DECISION";
    /** The literal an item carries when nobody has answered it — present, never omitted. */
    public static final String DECISION_NONE = "recorded: none";
    /** The one marker that makes a comment an answer: the first line the answer path writes. */
    public static final String DECISION_PREFIX = "Decision:";
    /** What the answer path writes under the answer to say which question it answered. */
    static final String ANSWERS_OPEN = "<!-- answers:";
    static final String ANSWERS_CLOSE = "-->";
    /** The run's own marker for the sentence an escalation asks — its verdict word precedes it on another line. */
    public static final String QUESTION_MARKER = "**The decision:**";
    /**
     * One bound per field of the decision block. A comment's line is unbounded while the block is a record, not
     * the claim, so a long one is clipped and says so rather than refusing the whole package — an item nobody
     * can work because one comment is long is worse than a clipped quotation.
     */
    static final int FIELD_LIMIT = 2000;

    public static final class Refusal extends RuntimeException {
        public final String reason;
        public final String detail;

        public Refusal(String reason, String detail) {
            super(reason + ": " + detail);
            this.reason = reason;
            this.detail = detail;
        }
    }

    private WorkPackage() {
    }

    /**
     * Escape every block's delimiters inside untrusted text, so no field can open or close a block it does not
     * own: the rendered package has exactly one opening and one closing delimiter per block, whatever the text
     * contains.
     */
    public static String escape(String body) {
        String escaped = body.replace(OPEN, "<<< ISSUE_TEXT")
                .replace(DECISION_OPEN, "<<< ITEM_DECISION");
        StringBuilder out = new StringBuilder();
        String[] lines = escaped.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String stripped = lines[i].strip();
            if (stripped.equals(CLOSE) || stripped.equals(DECISION_CLOSE)) {
                out.append('\\').append(stripped);
            } else {
                out.append(lines[i]);
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    /** The first non-empty line of a comment — where the answer path and the queue fall back to a question. */
    static String firstLine(String body) {
        for (String line : body.split("\n", -1)) {
            String stripped = line.strip();
            if (!stripped.isEmpty()) {
                return stripped;
            }
        }
        return "";
    }

    /**
     * The question a comment carries (issue #243): the line the run marks `**The decision:**`, else the first
     * non-empty line. The escalation comment opens with its verdict word (`needs-decision`), so the first line
     * alone is the verdict the answer would then bind to. Restated from `tools/issues/lib.sh`
     * (`ISSUES_QUESTION_JQ`) because this core is standalone Java; the two assert the same fixture.
     */
    static String questionLine(String body) {
        String fallback = "";
        for (String line : body.split("\n", -1)) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.startsWith(QUESTION_MARKER)) {
                return stripped;
            }
            if (fallback.isEmpty()) {
                fallback = stripped;
            }
        }
        return fallback;
    }

    static String clip(String text) {
        if (text.length() <= FIELD_LIMIT) {
            return text;
        }
        return text.substring(0, FIELD_LIMIT) + " … (clipped at " + FIELD_LIMIT + " characters)";
    }

    /** The question an answer records for itself, or an empty string when the answer carries no such marker. */
    static String answersOf(String body) {
        for (String line : body.split("\n", -1)) {
            String stripped = line.strip();
            if (stripped.startsWith(ANSWERS_OPEN) && stripped.endsWith(ANSWERS_CLOSE)) {
                return stripped.substring(ANSWERS_OPEN.length(), stripped.length() - ANSWERS_CLOSE.length()).strip();
            }
        }
        return "";
    }

    /**
     * The item's last recorded decision and the question it answered. The answer's own marker is read first;
     * an answer written before that marker existed (issue #196's `Decision: 2`) falls back to the nearest earlier
     * comment that is not itself an answer, whose first line is the question by the same convention the
     * decision queue prints (`tools/issues/decisions.sh`).
     */
    static String decisionBlock(Object issue) {
        Object raw = Json.get(issue, "comments");
        List<Object> comments = raw instanceof List<?> ? Json.array(raw) : List.of();
        int at = -1;
        for (int i = 0; i < comments.size(); i++) {
            if (firstLine(Json.stringOr(comments.get(i), "body", "")).startsWith(DECISION_PREFIX)) {
                at = i;
            }
        }
        if (at < 0) {
            return DECISION_OPEN + "\n" + DECISION_NONE + "\n" + DECISION_CLOSE;
        }
        Object answer = comments.get(at);
        String body = Json.stringOr(answer, "body", "");
        String decision = firstLine(body).substring(DECISION_PREFIX.length()).strip();
        String question = answersOf(body);
        for (int i = at - 1; i >= 0 && question.isEmpty(); i--) {
            String earlier = Json.stringOr(comments.get(i), "body", "");
            if (!firstLine(earlier).startsWith(DECISION_PREFIX)) {
                question = questionLine(earlier);
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(DECISION_OPEN);
        lines.add("recorded: " + Json.stringOr(answer, "createdAt", "(no timestamp recorded)"));
        lines.add("question: " + (question.isEmpty() ? "(none recorded)" : clip(escape(question))));
        lines.add("decision: " + clip(escape(decision)));
        lines.add(DECISION_CLOSE);
        return String.join("\n", lines);
    }

    /** Bound check, so an oversized body refuses rather than truncating silently. */
    public static void checkBounds(Profile profile, String body) {
        if (profile.forbidNul() && body.indexOf('\0') >= 0) {
            throw new Refusal("input-nul", "the body contains a NUL byte");
        }
        int bytes = body.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > profile.maxBodyBytes()) {
            throw new Refusal("input-too-large", bytes + " bytes > " + profile.maxBodyBytes());
        }
        int lines = body.split("\n", -1).length;
        if (lines > profile.maxLines()) {
            throw new Refusal("input-too-large", lines + " lines > " + profile.maxLines());
        }
    }

    /** Render one issue as a delimited data block. */
    public static String render(Profile profile, Object issue, String fingerprint, String provenance) {
        String title = Json.stringOr(issue, "title", "");
        String body = Json.stringOr(issue, "body", "");
        checkBounds(profile, title);
        checkBounds(profile, body);
        Object number = Json.get(issue, "number");
        List<String> lines = new ArrayList<>();
        lines.add("---- issue work package ----");
        lines.add("fingerprint: " + fingerprint);
        lines.add("provenance: " + provenance);
        lines.add("issue: " + (number == null ? "(unfiled)" : "#" + number));
        lines.add("title: " + title.replace("\n", " "));
        lines.add(OPEN);
        lines.add(escape(body));
        lines.add(CLOSE);
        lines.add(decisionBlock(issue));
        lines.add("---- end issue work package ----");
        return String.join("\n", lines) + "\n";
    }

    public static void main(String[] args) {
        Path profilePath = null;
        String issueFile = null;
        String fingerprint = "";
        String provenance = "";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--profile" -> profilePath = Path.of(args[++i]);
                case "--issue" -> issueFile = args[++i];
                case "--fingerprint" -> fingerprint = args[++i];
                case "--provenance" -> provenance = args[++i];
                default -> {
                    System.err.println("WorkPackage: unknown argument " + args[i]);
                    System.exit(2);
                }
            }
        }
        if (profilePath == null || issueFile == null) {
            System.err.println("usage: WorkPackage.java --profile <tooling.json> --issue <issue.json|-> "
                    + "[--fingerprint <sha>] [--provenance <label>]");
            System.exit(2);
        }
        try {
            Profile profile = Profile.load(profilePath);
            String text = issueFile.equals("-")
                    ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(Path.of(issueFile), StandardCharsets.UTF_8);
            System.out.print(render(profile, Json.parse(text), fingerprint, provenance));
        } catch (Refusal refusal) {
            System.err.println(refusal.reason + ": " + refusal.detail);
            System.exit(3);
        } catch (IOException | RuntimeException e) {
            System.err.println("WorkPackage: " + e.getMessage());
            System.exit(2);
        }
    }
}
