import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <strong>The section parser of the prose backlog format</strong>: {@code ## N.} headings, each with its
 * metadata line under it, and the entry body verbatim. This is the project-specific half of the import and
 * it lives behind the source adapter (`tools/issues/import/todo-md.sh`); the reconciler knows no backlog
 * format (design D11).
 *
 * A heading with no metadata line is a refusal with reason {@code malformed-entry} — never a silently
 * skipped section.
 *
 * Usage: {@code java tools/issues/core/TodoSections.java <TODO.md> [--count|--json]}
 */
public final class TodoSections {

    public static final class Refusal extends RuntimeException {
        public final String reason;
        public final String detail;

        public Refusal(String reason, String detail) {
            super(reason + ": " + detail);
            this.reason = reason;
            this.detail = detail;
        }
    }

    public static final class Entry {
        public final int id;
        public final String heading;
        public final String category;
        public final String clazz;
        public final String status;
        public final String body;

        Entry(int id, String heading, String category, String clazz, String status, String body) {
            this.id = id;
            this.heading = heading;
            this.category = category;
            this.clazz = clazz;
            this.status = status;
            this.body = body;
        }

        public String toJson() {
            return "{"
                    + "\"id\":" + id + ","
                    + "\"heading\":" + Json.quote(heading) + ","
                    + "\"category\":" + Json.quote(category) + ","
                    + "\"class\":" + Json.quote(clazz) + ","
                    + "\"status\":" + Json.quote(status) + ","
                    + "\"body\":" + Json.quote(body)
                    + "}";
        }
    }

    private static final Pattern HEADING = Pattern.compile("^## (\\d+)\\.\\s*(.*)$");
    private static final Pattern METADATA = Pattern.compile(
            "^\\*\\*id:\\*\\*\\s*(\\d+)\\s*·\\s*\\*\\*category:\\*\\*\\s*(.*?)\\s*·\\s*\\*\\*class:\\*\\*\\s*(.*?)\\s*·\\s*\\*\\*status:\\*\\*\\s*(.*)$");

    private TodoSections() {
    }

    public static List<Entry> parse(String text) {
        List<Entry> entries = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int index = 0;
        while (index < lines.length) {
            Matcher heading = HEADING.matcher(lines[index]);
            if (!heading.matches()) {
                index++;
                continue;
            }
            int id = Integer.parseInt(heading.group(1));
            String title = heading.group(2).strip();
            int metadataIndex = index + 1;
            while (metadataIndex < lines.length && lines[metadataIndex].isBlank()) {
                metadataIndex++;
            }
            if (metadataIndex >= lines.length) {
                throw new Refusal("malformed-entry", "§" + id + " has no metadata line");
            }
            Matcher metadata = METADATA.matcher(lines[metadataIndex]);
            if (!metadata.matches()) {
                throw new Refusal("malformed-entry", "§" + id + " has no metadata line: " + lines[metadataIndex]);
            }
            int declaredId = Integer.parseInt(metadata.group(1));
            if (declaredId != id) {
                throw new Refusal("malformed-entry", "§" + id + " declares id " + declaredId);
            }
            int bodyStart = metadataIndex + 1;
            int bodyEnd = bodyStart;
            while (bodyEnd < lines.length && !HEADING.matcher(lines[bodyEnd]).matches()) {
                bodyEnd++;
            }
            entries.add(new Entry(id, title, metadata.group(2).strip(), metadata.group(3).strip(),
                    metadata.group(4).strip(), trimBody(lines, bodyStart, bodyEnd)));
            index = bodyEnd;
        }
        return entries;
    }

    private static String trimBody(String[] lines, int from, int to) {
        int end = to;
        while (end > from && isSeparator(lines[end - 1])) {
            end--;
        }
        StringBuilder body = new StringBuilder();
        for (int i = from; i < end; i++) {
            body.append(lines[i]);
            if (i < end - 1) {
                body.append('\n');
            }
        }
        return body.toString();
    }

    private static boolean isSeparator(String line) {
        String trimmed = line.strip();
        return trimmed.isEmpty() || trimmed.equals("---");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: TodoSections.java <file> [--count|--json]");
            System.exit(2);
        }
        try {
            List<Entry> entries = parse(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
            String mode = args.length > 1 ? args[1] : "--count";
            switch (mode) {
                case "--count" -> System.out.println(entries.size());
                case "--json" -> {
                    StringBuilder out = new StringBuilder("[");
                    for (int i = 0; i < entries.size(); i++) {
                        if (i > 0) {
                            out.append(',');
                        }
                        out.append(entries.get(i).toJson());
                    }
                    System.out.println(out.append(']'));
                }
                default -> {
                    System.err.println("TodoSections: unknown mode " + mode);
                    System.exit(2);
                }
            }
        } catch (Refusal refusal) {
            System.err.println(refusal.reason + ": " + refusal.detail);
            System.exit(3);
        } catch (IOException | RuntimeException e) {
            System.err.println("TodoSections: " + e.getMessage());
            System.exit(2);
        }
    }
}
