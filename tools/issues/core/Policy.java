import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * <strong>The gate</strong>. An item may be acted on only when its author is on the committed allowlist
 * <em>and</em> the item carries the project's required opt-in label, and the tooling refuses to run at all
 * when the identity it runs as is not on that allowlist — rather than reporting an empty working set, which
 * would read as "the backlog is clean" (design D2).
 *
 * The write paths additionally refuse a reserved label in their input. That is what makes the opt-in
 * unwritable by the surface under test, and therefore a control rather than a conjunction of two terms that
 * share one actor.
 */
public final class Policy {

    /** A decision is either an allow or a refusal with exactly one reason code. */
    public static final class Decision {
        public final boolean allow;
        public final String reason;
        public final String detail;

        Decision(boolean allow, String reason, String detail) {
            this.allow = allow;
            this.reason = reason;
            this.detail = detail;
        }

        public static Decision allow() {
            return new Decision(true, "allow", "");
        }

        public static Decision refuse(String reason, String detail) {
            return new Decision(false, reason, detail);
        }

        public String toJson() {
            return "{"
                    + "\"decision\":" + Json.quote(allow ? "allow" : "refuse") + ","
                    + "\"reason\":" + Json.quote(reason) + ","
                    + "\"detail\":" + Json.quote(detail) + "}";
        }
    }

    private Policy() {
    }

    /** Refuses unless the identity the tooling runs as is on the allowlist. */
    public static Decision preflight(Profile profile, String login) {
        if (login == null || login.isBlank()) {
            return Decision.refuse("author-not-allowlisted", "(no authenticated login)");
        }
        return profile.allowedAuthors().contains(login)
                ? Decision.allow()
                : Decision.refuse("author-not-allowlisted", login);
    }

    /** The author condition alone, read from the issue — the only attributable term GitHub exposes. */
    public static Decision authorOf(Profile profile, Object issue) {
        Object author = Json.path(issue, "author", "login");
        String login = author == null ? null : String.valueOf(author);
        if (login == null || login.isBlank()) {
            return Decision.refuse("author-not-allowlisted", "(no author)");
        }
        return profile.allowedAuthors().contains(login)
                ? Decision.allow()
                : Decision.refuse("author-not-allowlisted", login);
    }

    /** Both gate conditions for one issue: an allowlisted author, and the required opt-in label. */
    public static Decision decide(Profile profile, Object issue) {
        Decision author = authorOf(profile, issue);
        if (!author.allow) {
            return author;
        }
        List<String> labels = labelsOf(issue);
        for (String required : profile.requiredLabels()) {
            if (labels.contains(required)) {
                return Decision.allow();
            }
        }
        return Decision.refuse("missing-eligibility-label",
                String.join(",", profile.requiredLabels()) + " absent");
    }

    /** Refuses a reserved label in a write path's input, reading the set from the profile. */
    public static Decision refuseReserved(Profile profile, List<String> labels) {
        for (String label : labels) {
            if (profile.reservedLabels().contains(label)) {
                return Decision.refuse("label-reserved", label);
            }
        }
        return Decision.allow();
    }

    /** Refuses a label outside the closed vocabulary. */
    public static Decision refuseOutsideVocabulary(Profile profile, List<String> labels) {
        for (String label : labels) {
            if (!Taxonomy.known(profile, label)) {
                return Decision.refuse("label-opaque", label);
            }
        }
        return Decision.allow();
    }

    /** The label names of an issue, accepting both `["class/bug"]` and `[{"name":"class/bug"}]`. */
    public static List<String> labelsOf(Object issue) {
        List<String> labels = new ArrayList<>();
        Object value = Json.get(issue, "labels");
        if (value == null) {
            return labels;
        }
        for (Object item : Json.array(value)) {
            if (item instanceof String text) {
                labels.add(text);
            } else if (item instanceof java.util.Map<?, ?>) {
                String name = Json.string(item, "name");
                if (name != null) {
                    labels.add(name);
                }
            }
        }
        return labels;
    }

    public static void main(String[] args) {
        Path profilePath = null;
        String login = System.getenv().getOrDefault("ISSUES_LOGIN", "");
        String mode = null;
        String issueFile = null;
        List<String> labels = new ArrayList<>();
        boolean json = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--profile" -> profilePath = Path.of(args[++i]);
                case "--login" -> login = args[++i];
                case "--json" -> json = true;
                case "preflight", "check", "labels" -> mode = args[i];
                default -> {
                    if (args[i].startsWith("-") && !args[i].equals("-")) {
                        System.err.println("policy: unknown argument " + args[i]);
                        System.exit(2);
                    }
                    if (mode != null && mode.equals("labels")) {
                        labels.add(args[i]);
                    } else {
                        issueFile = args[i];
                    }
                }
            }
        }
        if (profilePath == null || mode == null) {
            System.err.println("usage: policy.sh preflight | check <issue.json> | labels <label>... "
                    + "[--profile <path>] [--login <login>] [--json]");
            System.exit(2);
        }
        try {
            Profile profile = Profile.load(profilePath);
            Decision decision = switch (mode) {
                case "preflight" -> preflight(profile, login);
                case "labels" -> {
                    Decision reserved = refuseReserved(profile, labels);
                    yield reserved.allow ? refuseOutsideVocabulary(profile, labels) : reserved;
                }
                default -> {
                    String text = issueFile == null || issueFile.equals("-")
                            ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
                            : Files.readString(Path.of(issueFile), StandardCharsets.UTF_8);
                    yield decide(profile, Json.parse(text));
                }
            };
            if (json) {
                System.out.println(decision.toJson());
            } else if (decision.allow) {
                System.out.println("allow");
            } else {
                System.out.println(decision.reason + ": " + decision.detail);
            }
            if (!decision.allow) {
                System.exit(3);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("policy: " + e.getMessage());
            System.exit(2);
        }
    }
}
