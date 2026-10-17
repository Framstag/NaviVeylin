import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <strong>The source adapter for this project's prose backlog.</strong> It turns `TODO.md`'s own grammar
 * (`## N.` headings with their metadata line) into the neutral findings stream the generic reconciler
 * consumes, applying the profile's renames and value sets on the way (design D11).
 *
 * The grammar lives here and in {@link TodoSections}; the reconciler (`tools/issues/import.sh`) knows no
 * backlog format. A second project writes one adapter and reuses the reconciler.
 *
 * Usage: {@code java tools/issues/core/TodoAdapter.java <tooling.json> <TODO.md>}
 */
public final class TodoAdapter {

    private TodoAdapter() {
    }

    /** The neutral finding: everything the reconciler needs, and no backlog-specific field. */
    public static String findingJson(Profile profile, TodoSections.Entry entry, List<String> renames) {
        List<String> labels = new ArrayList<>(Taxonomy.labels(profile, entry.clazz, entry.category, entry.status, renames));
        labels.add(profile.label("source", "todo-import"));
        String claim = entry.heading;
        String anchor = anchorIn(entry.body);
        String fingerprint = Fingerprint.digest(entry.clazz, entry.category, claim, anchor);
        StringBuilder out = new StringBuilder("{");
        out.append("\"sourceId\":").append(Json.quote(String.valueOf(entry.id))).append(',');
        out.append("\"title\":").append(Json.quote("§" + entry.id + " " + entry.heading)).append(',');
        out.append("\"class\":").append(Json.quote(entry.clazz)).append(',');
        out.append("\"category\":").append(Json.quote(entry.category)).append(',');
        out.append("\"status\":").append(Json.quote(Taxonomy.statusValue(profile, entry.status))).append(',');
        out.append("\"claim\":").append(Json.quote(claim)).append(',');
        out.append("\"anchor\":").append(Json.quote(anchor)).append(',');
        out.append("\"fingerprint\":").append(Json.quote(fingerprint)).append(',');
        out.append("\"labels\":[");
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(Json.quote(labels.get(i)));
        }
        out.append("],");
        out.append("\"body\":").append(Json.quote(entry.body));
        return out.append('}').toString();
    }

    /**
     * The first `path:Symbol` the prose names — a path plus a symbol when it has one, never a line number.
     * This is a heuristic over prose, which is why it lives in the adapter rather than in the engine.
     */
    public static String anchorIn(String text) {
        if (text == null) {
            return "(none)";
        }
        Matcher matcher = Pattern.compile(
                "([A-Za-z0-9_][A-Za-z0-9_./-]*\\.(?:kt|kts|java|cpp|hpp|h|cc|xml|json|md|sh|py|gradle|toml|ya?ml))"
                        + "(?::([A-Za-z_][A-Za-z0-9_]*))?").matcher(text);
        if (!matcher.find()) {
            return "(none)";
        }
        return Fingerprint.anchor(matcher.group(1), matcher.group(2));
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: TodoAdapter.java <tooling.json> <TODO.md>");
            System.exit(2);
        }
        try {
            Profile profile = Profile.load(java.nio.file.Path.of(args[0]));
            String text = java.nio.file.Files.readString(java.nio.file.Path.of(args[1]),
                    java.nio.charset.StandardCharsets.UTF_8);
            List<TodoSections.Entry> entries = TodoSections.parse(text);
            List<String> renames = new ArrayList<>();
            List<String> findings = new ArrayList<>();
            for (TodoSections.Entry entry : entries) {
                findings.add(findingJson(profile, entry, renames));
            }
            StringBuilder out = new StringBuilder("{");
            out.append("\"source\":").append(Json.quote("todo-import")).append(',');
            out.append("\"count\":").append(findings.size()).append(',');
            out.append("\"renames\":[");
            for (int i = 0; i < renames.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(Json.quote(renames.get(i)));
            }
            out.append("],\"findings\":[");
            out.append(String.join(",", findings));
            out.append("]}");
            System.out.println(out);
        } catch (TodoSections.Refusal refusal) {
            System.err.println(refusal.reason + ": " + refusal.detail);
            System.exit(3);
        } catch (Taxonomy.Refusal refusal) {
            System.err.println(refusal.reason + ": " + refusal.detail);
            System.exit(3);
        } catch (Exception e) {
            System.err.println("TodoAdapter: " + e.getMessage());
            System.exit(2);
        }
    }
}
