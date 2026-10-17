import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * <strong>The project profile reader</strong> — every project-specific fact the engine needs, read from one
 * committed file (`tools/tooling.json`). No engine file names a project value; this class is how a
 * repository name, an allowlisted author, a vocabulary value or a bound reaches the engine as data
 * (design D11, invariant I13).
 *
 * Usage: {@code java tools/issues/core/Profile.java <file> <section>} prints one value per line.
 */
public final class Profile {

    /** Every namespace the tooling knows. A profile naming fewer is malformed. */
    public static final List<String> NAMESPACES =
            List.of("class", "category", "status", "source", "agent", "prio", "sensor");

    /** Namespaces whose values may legitimately be empty in a profile that is still well formed. */
    public static final List<String> EMPTY_ALLOWED = List.of("sensor");

    private static final Pattern VALUE_NAME = Pattern.compile("^[a-z0-9-]+$");

    private final Map<String, Object> root;

    private Profile(Map<String, Object> root) {
        this.root = root;
    }

    public static Profile parse(String text) {
        return new Profile(Json.object(Json.parse(text)));
    }

    public static Profile load(Path path) throws IOException {
        return parse(java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8));
    }

    public String repo() {
        return Json.stringOr(root, "repo", "");
    }

    public String namespaceSeparator() {
        return Json.stringOr(root, "namespaceSeparator", "/");
    }

    /**
     * One top-level section as raw JSON, for the readers that own their own shape (the `bands` section is
     * `issue-metadata-loop`'s, and its validation lives beside its mapping in {@code Bands}).
     */
    public Object section(String name) {
        return Json.get(root, name);
    }

    public List<String> allowedAuthors() {
        return Json.strings(root, "allowedAuthors");
    }

    public List<String> requiredLabels() {
        return Json.strings(root, "requiredLabels");
    }

    public List<String> reservedLabels() {
        return Json.strings(root, "reservedLabels");
    }

    /** The declared namespace names, the profile's own set — never the engine's constant. */
    public List<String> namespaces() {
        Object namespaces = Json.get(root, "namespaces");
        if (namespaces == null) {
            return List.of();
        }
        return new ArrayList<>(new TreeSet<>(Json.object(namespaces).keySet()));
    }

    public List<String> values(String namespace) {
        return Json.strings(Json.get(root, "namespaces"), namespace);
    }

    /** The renames a namespace is allowed to apply, {@code from -> to}. */
    public Map<String, String> renames(String namespace) {
        Map<String, String> out = new LinkedHashMap<>();
        Object renames = Json.path(root, "sourceRenames", namespace);
        if (renames != null) {
            for (Map.Entry<String, Object> entry : Json.object(renames).entrySet()) {
                out.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return out;
    }

    /** The status prefix rules, in order; each entry is {@code {regex, label}}. */
    public List<String[]> statusRules() {
        List<String[]> out = new ArrayList<>();
        Object rules = Json.get(root, "statusLabels");
        if (rules != null) {
            for (Object rule : Json.array(rules)) {
                out.add(new String[] {Json.string(rule, "match"), Json.string(rule, "label")});
            }
        }
        return out;
    }

    public long maxBodyBytes() {
        return Json.integer(Json.get(root, "bounds"), "maxBodyBytes", 0);
    }

    public int maxLines() {
        return (int) Json.integer(Json.get(root, "bounds"), "maxLines", 0);
    }

    public boolean forbidNul() {
        return Json.bool(Json.get(root, "bounds"), "forbidNul", true);
    }

    /**
     * The validation report: empty when the profile declares everything the engine needs. A missing or
     * empty section is reported by name, so a half-adapted profile fails loudly instead of defaulting.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        require(problems, "repo", repo().isEmpty());
        require(problems, "allowedAuthors", allowedAuthors().isEmpty());
        require(problems, "requiredLabels", requiredLabels().isEmpty());
        require(problems, "reservedLabels", reservedLabels().isEmpty());
        require(problems, "namespaceSeparator", namespaceSeparator().isEmpty());
        require(problems, "statusLabels", statusRules().isEmpty());

        if (Json.get(root, "bounds") == null) {
            problems.add("section bounds is missing");
        } else {
            if (maxBodyBytes() <= 0) {
                problems.add("bounds.maxBodyBytes is missing or not positive");
            }
            if (maxLines() <= 0) {
                problems.add("bounds.maxLines is missing or not positive");
            }
        }

        for (String namespace : NAMESPACES) {
            if (!namespaces().contains(namespace)) {
                problems.add("namespace " + namespace + " is missing");
                continue;
            }
            List<String> values = values(namespace);
            if (values.isEmpty() && !EMPTY_ALLOWED.contains(namespace)) {
                problems.add("namespace " + namespace + " is empty");
            }
            for (String value : values) {
                if (!VALUE_NAME.matcher(value).matches()) {
                    problems.add("namespace " + namespace + " has a value outside ^[a-z0-9-]+$: " + value);
                }
            }
        }

        for (String namespace : namespaces()) {
            for (Map.Entry<String, String> rename : renames(namespace).entrySet()) {
                if (!values(namespace).contains(rename.getValue())) {
                    problems.add("rename " + namespace + " " + rename.getKey() + " -> " + rename.getValue()
                            + " targets a value the vocabulary does not contain");
                }
            }
        }

        for (String[] rule : statusRules()) {
            if (rule[0] == null || rule[1] == null) {
                problems.add("a statusLabels entry is missing match or label");
                continue;
            }
            if (!values("status").contains(rule[1])) {
                problems.add("statusLabels maps to a value outside the status vocabulary: " + rule[1]);
            }
            try {
                Pattern.compile(rule[0]);
            } catch (RuntimeException e) {
                problems.add("statusLabels has an invalid match pattern: " + rule[0]);
            }
        }

        // The band section's own shape and totality: the mapping is the project's data, so a hole or a band
        // id that is not a vocabulary value is reported here rather than surfacing as a wrong label.
        problems.addAll(Bands.problems(this));
        return problems;
    }

    private static void require(List<String> problems, String section, boolean missing) {
        if (missing) {
            problems.add("section " + section + " is missing or empty");
        }
    }

    /** The fully qualified label for a namespace value, e.g. {@code class/bug}. */
    public String label(String namespace, String value) {
        return namespace + namespaceSeparator() + value;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: Profile.java <tooling.json> <section>");
            System.exit(2);
        }
        try {
            Profile profile = load(Path.of(args[0]));
            switch (args[1]) {
                case "repo" -> System.out.println(profile.repo());
                case "namespaceSeparator" -> System.out.println(profile.namespaceSeparator());
                case "namespaces" -> profile.namespaces().forEach(System.out::println);
                case "allowedAuthors" -> profile.allowedAuthors().forEach(System.out::println);
                case "requiredLabels" -> profile.requiredLabels().forEach(System.out::println);
                case "reservedLabels" -> profile.reservedLabels().forEach(System.out::println);
                case "maxBodyBytes" -> System.out.println(profile.maxBodyBytes());
                case "maxLines" -> System.out.println(profile.maxLines());
                case "problems" -> {
                    List<String> problems = profile.problems();
                    problems.forEach(System.out::println);
                    if (!problems.isEmpty()) {
                        System.exit(1);
                    }
                }
                default -> {
                    if (args[1].startsWith("namespaces.")) {
                        profile.values(args[1].substring("namespaces.".length())).forEach(System.out::println);
                    } else {
                        System.err.println("Profile: unknown section " + args[1]);
                        System.exit(2);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Profile: " + e.getMessage());
            System.exit(2);
        }
    }
}
