import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <strong>The band mapping</strong>: score → band, and the totality the profile's `bands` section has to
 * satisfy.
 *
 * The mapping is mechanical and total: every score the scoring model can produce falls in exactly one band,
 * and every band is reachable (design D2, spec "The range is fixed and total"). The edges are the profile's
 * data — no edge, no band id and no range bound is a constant here, so a re-tuned model is one committed file
 * and not a code change. The range is *derived* from the scoring table rather than assumed: severity
 * {@code min·reach min + confidence min} … {@code severity max·reach max + confidence max}, which is what
 * makes a table edit that moves the range a red case instead of a silent hole.
 *
 * Usage: {@code java -cp <classes> Bands label --profile <file> --score 9}   → {@code prio/p2}
 *        {@code java -cp <classes> Bands edges --profile <file>}             → one band per line
 *        {@code java -cp <classes> Bands range --profile <file>}             → {@code 2 18}
 *        {@code java -cp <classes> Bands meta  --profile <file>}             → the section's scalars
 */
public final class Bands {

    /** A refusal with its reason code, the tooling's convention (exit 3). */
    public static final class Refusal extends RuntimeException {
        public final String reason;
        public final String detail;

        public Refusal(String reason, String detail) {
            super(reason + ": " + detail);
            this.reason = reason;
            this.detail = detail;
        }
    }

    /** One band: the vocabulary value its label carries, and the score range it covers. */
    public static final class Band {
        public final String id;
        public final int min;
        public final int max;

        Band(String id, int min, int max) {
            this.id = id;
            this.min = min;
            this.max = max;
        }

        @Override
        public String toString() {
            return id + " " + min + ".." + max;
        }
    }

    private Bands() {
    }

    private static Object section(Profile profile) {
        return profile.section("bands");
    }

    public static List<Band> bands(Profile profile) {
        List<Band> out = new ArrayList<>();
        Object edges = Json.get(section(profile), "edges");
        if (edges == null) {
            return out;
        }
        for (Object edge : Json.array(edges)) {
            out.add(new Band(Json.string(edge, "id"),
                    (int) Json.integer(edge, "min", 0), (int) Json.integer(edge, "max", 0)));
        }
        return out;
    }

    /** {@code {min, max}} of the scoring model, derived from its three tables. */
    public static int[] range(Profile profile) {
        Object scoring = Json.get(section(profile), "scoring");
        int severityMin = (int) Json.integer(Json.get(scoring, "severity"), "min", 0);
        int severityMax = (int) Json.integer(Json.get(scoring, "severity"), "max", 0);
        int reachMin = (int) Json.integer(Json.get(scoring, "reach"), "min", 0);
        int reachMax = (int) Json.integer(Json.get(scoring, "reach"), "max", 0);
        int confidenceMin = (int) Json.integer(Json.get(scoring, "confidence"), "min", 0);
        int confidenceMax = (int) Json.integer(Json.get(scoring, "confidence"), "max", 0);
        return new int[] {severityMin * reachMin + confidenceMin, severityMax * reachMax + confidenceMax};
    }

    public static long ttlDays(Profile profile) {
        return Json.integer(section(profile), "ttlDays", 0);
    }

    /**
     * The namespaces whose labels a score depended on, so the basis hashes exactly those. Read here rather
     * than written in the shell, because which namespaces carry a score's context is a profile fact and not
     * an engine constant (the engine names no profile value — {@code lib#noProjectValueInTheEngine}).
     */
    public static List<String> basisNamespaces(Profile profile) {
        return Json.strings(section(profile), "basisNamespaces");
    }

    /**
     * The confidence caps the status imposes, value → cap. A status that means "not reproduced" cannot
     * support a high confidence, so the pass applies the cap the profile declares instead of trusting the
     * interpreter's number.
     */
    public static Map<String, Long> statusCaps(Profile profile) {
        Map<String, Long> caps = new LinkedHashMap<>();
        Object capsObject = Json.get(Json.get(section(profile), "scoring"), "statusCaps");
        for (Map.Entry<String, Object> entry : Json.object(capsObject).entrySet()) {
            Long cap = Json.object(entry.getValue()) == null ? null
                    : Json.integer(entry.getValue(), "confidence", -1);
            if (cap != null && cap >= 0) {
                caps.put(entry.getKey(), cap);
            }
        }
        return caps;
    }

    /** The components' own bounds, in the order severity, reach, confidence — the pass refuses numbers
     * outside them rather than scoring an out-of-model value. */
    public static int[][] componentBounds(Profile profile) {
        Object scoring = Json.get(section(profile), "scoring");
        return new int[][] {
            {(int) Json.integer(Json.get(scoring, "severity"), "min", 0),
             (int) Json.integer(Json.get(scoring, "severity"), "max", 0)},
            {(int) Json.integer(Json.get(scoring, "reach"), "min", 0),
             (int) Json.integer(Json.get(scoring, "reach"), "max", 0)},
            {(int) Json.integer(Json.get(scoring, "confidence"), "min", 0),
             (int) Json.integer(Json.get(scoring, "confidence"), "max", 0)},
        };
    }

    /** The band a score falls in; a score outside every band is a refusal, never a default. */
    public static Band bandFor(Profile profile, int score) {
        for (Band band : bands(profile)) {
            if (score >= band.min && score <= band.max) {
                return band;
            }
        }
        throw new Refusal("band-undefined", "no band covers score " + score);
    }

    /** The fully qualified label a score's band carries, e.g. {@code prio/p2}. */
    public static String label(Profile profile, int score) {
        Object namespace = Json.get(section(profile), "namespace");
        String name = namespace == null ? "" : String.valueOf(namespace);
        return profile.label(name, bandFor(profile, score).id);
    }

    /**
     * The band section's own validation: reported by {@link Profile#problems()} so a half-adapted profile
     * fails loudly (the same rule the rest of the profile follows), and asserted by
     * {@code Bands#coversTheWholeRange}.
     */
    public static List<String> problems(Profile profile) {
        List<String> problems = new ArrayList<>();
        if (section(profile) == null) {
            problems.add("bands is missing");
            return problems;
        }
        if (ttlDays(profile) <= 0) {
            problems.add("bands.ttlDays is missing or not positive");
        }
        Object namespace = Json.get(section(profile), "namespace");
        String name = namespace == null ? "" : String.valueOf(namespace);
        if (name.isEmpty() || !profile.namespaces().contains(name)) {
            problems.add("bands.namespace names no declared namespace: " + name);
        }
        List<Band> bands = bands(profile);
        if (bands.isEmpty()) {
            problems.add("bands.edges is empty");
            return problems;
        }
        List<String> basisNamespaces = basisNamespaces(profile);
        if (basisNamespaces.isEmpty()) {
            problems.add("bands.basisNamespaces is empty — the basis has no labels to hash");
        }
        for (String basisNamespace : basisNamespaces) {
            if (!profile.namespaces().contains(basisNamespace)) {
                problems.add("bands.basisNamespaces names no declared namespace: " + basisNamespace);
            }
        }
        for (String capped : statusCaps(profile).keySet()) {
            boolean declared = false;
            for (String namespaceOfCapped : profile.namespaces()) {
                declared |= profile.values(namespaceOfCapped).contains(capped);
            }
            if (!declared) {
                problems.add("bands.scoring.statusCaps names no declared value: " + capped);
            }
        }
        int[] range = range(profile);
        Set<String> ids = new LinkedHashSet<>();
        for (Band band : bands) {
            if (band.id == null || band.id.isEmpty()) {
                problems.add("a bands.edges entry has no id");
                continue;
            }
            if (!ids.add(band.id)) {
                problems.add("two bands share the id " + band.id);
            }
            if (!profile.values(name).contains(band.id)) {
                problems.add("band " + band.id + " is not a value of the " + name + " vocabulary");
            }
            if (band.max < band.min) {
                problems.add("band " + band.id + " has max below min");
            }
        }
        // Totality, checked here as well as in the case: a hole is the failure the case exists to catch.
        for (int score = range[0]; score <= range[1]; score++) {
            int covering = 0;
            for (Band band : bands) {
                if (score >= band.min && score <= band.max) {
                    covering++;
                }
            }
            if (covering != 1) {
                problems.add("score " + score + " falls in " + covering + " bands");
            }
        }
        if (ids.size() != profile.values(name).size()) {
            problems.add("the bands and the " + name + " vocabulary have different sizes: "
                    + ids.size() + " vs " + profile.values(name).size());
        }
        return problems;
    }

    public static void main(String[] args) {
        Path profilePath = null;
        String mode = null;
        int score = Integer.MIN_VALUE;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--profile" -> profilePath = Path.of(args[++i]);
                case "--score" -> score = Integer.parseInt(args[++i]);
                case "label", "edges", "range", "meta" -> mode = args[i];
                default -> {
                    System.err.println("Bands: unknown argument " + args[i]);
                    System.exit(2);
                }
            }
        }
        if (profilePath == null || mode == null) {
            System.err.println("usage: Bands <label|edges|range|meta> --profile <tooling.json> [--score <n>]");
            System.exit(2);
        }
        try {
            Profile profile = Profile.load(profilePath);
            switch (mode) {
                case "label" -> {
                    if (score == Integer.MIN_VALUE) {
                        System.err.println("Bands: label needs --score");
                        System.exit(2);
                    }
                    System.out.println(label(profile, score));
                }
                case "edges" -> bands(profile).forEach(System.out::println);
                case "meta" -> {
                    int[] range = range(profile);
                    int[][] bounds = componentBounds(profile);
                    System.out.println("namespace=" + Json.get(section(profile), "namespace"));
                    System.out.println("ttl_days=" + ttlDays(profile));
                    System.out.println("range=" + range[0] + " " + range[1]);
                    System.out.println("severity=" + bounds[0][0] + " " + bounds[0][1]);
                    System.out.println("reach=" + bounds[1][0] + " " + bounds[1][1]);
                    System.out.println("confidence=" + bounds[2][0] + " " + bounds[2][1]);
                    System.out.println("basis_namespaces=" + String.join(",", basisNamespaces(profile)));
                    List<String> caps = new ArrayList<>();
                    for (Map.Entry<String, Long> cap : statusCaps(profile).entrySet()) {
                        caps.add(cap.getKey() + ":" + cap.getValue());
                    }
                    System.out.println("status_caps=" + String.join(",", caps));
                }
                default -> {
                    int[] range = range(profile);
                    System.out.println(range[0] + " " + range[1]);
                }
            }
        } catch (Refusal refusal) {
            System.err.println(refusal.reason + ": " + refusal.detail);
            System.exit(3);
        } catch (java.io.IOException | RuntimeException e) {
            System.err.println("Bands: " + e.getMessage());
            System.exit(2);
        }
    }
}
