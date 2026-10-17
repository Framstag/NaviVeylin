import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * <strong>The work package</strong>: how an issue's text reaches a consumer. Title, body and comments on a
 * public tracker are attacker-controlled, so the text is emitted inside a delimited, labelled data field
 * with its provenance and fingerprint alongside it — never in an instruction position — and the receiver's
 * contract is to re-verify every claim against the tree before acting (design D7).
 *
 * The bounds in the profile make an oversized or malformed body a refusal with a reason
 * ({@code input-too-large}, {@code input-nul}), not a partial parse.
 *
 * Usage: {@code java tools/issues/core/WorkPackage.java --profile <tooling.json> --issue <issue.json|->}
 */
public final class WorkPackage {

    public static final String OPEN = "<<<ISSUE_TEXT";
    public static final String CLOSE = "ISSUE_TEXT";

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
     * Escape the delimiters inside untrusted text so the rendered package has exactly one opening and one
     * closing delimiter, whatever the body contains.
     */
    public static String escape(String body) {
        String escaped = body.replace(OPEN, "<<< ISSUE_TEXT");
        StringBuilder out = new StringBuilder();
        String[] lines = escaped.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].strip().equals(CLOSE)) {
                out.append('\\').append(lines[i].strip());
            } else {
                out.append(lines[i]);
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
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
