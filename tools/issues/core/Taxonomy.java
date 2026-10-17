import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <strong>The taxonomy</strong>: maps an item's structured facts (its kind, its area, its state) to labels,
 * drawing every value from the profile's closed vocabulary and refusing a value the profile does not
 * contain — a label created by a typo would silently hide an issue from every selector (design D5).
 *
 * A refusal carries one reason code: {@code label-opaque}. The engine names no value of its own; the
 * category renames come from the profile too (design D11).
 */
public final class Taxonomy {

    /** A refusal with the reason code the caller branches on, and the value that caused it. */
    public static final class Refusal extends RuntimeException {
        public final String reason;
        public final String detail;

        public Refusal(String reason, String detail) {
            super(reason + ": " + detail);
            this.reason = reason;
            this.detail = detail;
        }
    }

    private Taxonomy() {
    }

    /** The labels for one item: kind, area and state, each in its own namespace. */
    public static List<String> labels(Profile profile, String clazz, String category, String status, List<String> renames) {
        List<String> labels = new ArrayList<>();
        labels.add(label(profile, "class", clazz, renames));
        labels.add(label(profile, "category", category, renames));
        labels.add(profile.label("status", statusValue(profile, status)));
        return labels;
    }

    /** The {@code status} value for a raw metadata status string, or a refusal. */
    public static String statusValue(Profile profile, String status) {
        if (status == null) {
            throw new Refusal("label-opaque", "status (absent)");
        }
        for (String[] rule : profile.statusRules()) {
            if (Pattern.compile(rule[0]).matcher(status).lookingAt()) {
                return rule[1];
            }
        }
        throw new Refusal("label-opaque", "status " + status);
    }

    /** One namespaced label, renamed through the profile and checked against the closed vocabulary. */
    public static String label(Profile profile, String namespace, String value, List<String> renames) {
        if (value == null) {
            throw new Refusal("label-opaque", namespace + " (absent)");
        }
        if (!profile.namespaces().contains(namespace)) {
            throw new Refusal("label-opaque", "namespace " + namespace);
        }
        String resolved = value;
        Map<String, String> mapped = profile.renames(namespace);
        if (!profile.values(namespace).contains(resolved) && mapped.containsKey(resolved)) {
            String target = mapped.get(resolved);
            if (renames != null) {
                renames.add(namespace + " " + resolved + " → " + target);
            }
            resolved = target;
        }
        if (!profile.values(namespace).contains(resolved)) {
            throw new Refusal("label-opaque", namespace + " " + value);
        }
        return profile.label(namespace, resolved);
    }

    /** The namespace of a fully qualified label, or null when it has none the profile declares. */
    public static String namespaceOf(Profile profile, String label) {
        int cut = label.indexOf(profile.namespaceSeparator());
        if (cut <= 0) {
            return null;
        }
        String namespace = label.substring(0, cut);
        return profile.namespaces().contains(namespace) ? namespace : null;
    }

    public static String valueOf(Profile profile, String label) {
        int cut = label.indexOf(profile.namespaceSeparator());
        return cut < 0 ? label : label.substring(cut + profile.namespaceSeparator().length());
    }

    /** True when the label is inside the profile's closed vocabulary. */
    public static boolean known(Profile profile, String label) {
        String namespace = namespaceOf(profile, label);
        return namespace != null && profile.values(namespace).contains(valueOf(profile, label));
    }

    public static void main(String[] args) {
        Path profilePath = null;
        String clazz = null;
        String category = null;
        String status = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--profile")) {
                profilePath = Path.of(args[++i]);
            } else if (arg.startsWith("class=")) {
                clazz = arg.substring("class=".length());
            } else if (arg.startsWith("category=")) {
                category = arg.substring("category=".length());
            } else if (arg.startsWith("status=")) {
                status = arg.substring("status=".length());
            } else {
                System.err.println("Taxonomy: unknown argument " + arg);
                System.exit(2);
            }
        }
        if (profilePath == null) {
            System.err.println("Taxonomy: --profile <path> is required");
            System.exit(2);
        }
        try {
            Profile profile = Profile.load(profilePath);
            List<String> renames = new ArrayList<>();
            labels(profile, clazz, category, status, renames).forEach(System.out::println);
        } catch (Refusal refusal) {
            System.err.println(refusal.reason + ": " + refusal.detail);
            System.exit(3);
        } catch (Exception e) {
            System.err.println("Taxonomy: " + e.getMessage());
            System.exit(2);
        }
    }
}
