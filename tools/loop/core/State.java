import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The <code>state</code> verb's first subject: one item record, its declared fields, and the batch count query
 * (design D4).
 *
 * Nothing switches onto this class in this change: the scripts keep their own <code>jq</code> work and the sibling
 * change <code>the-run-state-is-typed</code> carries the ones that read it. What this file therefore models is the
 * item section and nothing else — the run's own sections, the queue and the exclusions are that sibling's subject —
 * and the model is <b>closed</b>: a field it does not declare is a refusal, never a silent drop.
 *
 * Three rules, each with the case that proves it:
 *
 * <ul>
 * <li><b>A document that does not parse is a refusal before anything is written</b>
 * ({@code core#theStateAReadCannotParseIsRefused}). {@link Json#parse} throws rather than returning what it read, so
 * no caller is ever handed half a state; this class turns the throw into one line naming the file and the exit code
 * the loop already shares for a refusal.</li>
 * <li><b>An undeclared field is refused, naming the field and the file.</b> A writer that drops a field it does not
 * know is a writer whose next reader cannot tell a record it wrote from a record somebody edited, which is the
 * failure the state's digest exists for.</li>
 * <li><b>The counts are one answer for six questions</b> ({@code core#theStateIsWrittenByTheCore}). A phase pays one
 * JVM start and not six, which is the whole reason the verb takes a batch (design D2).</li>
 * </ul>
 *
 * The document's shape is <code>{"items": {"&lt;issue&gt;": {…}}}</code>. A record with no <code>verdict</code> is
 * an item the run has not finished, and counts as {@link #PENDING} — the reader never has to know whether an absent
 * field means "not yet" or "written wrongly".
 */
public final class State {

    /** The outcome word for an item the run has not finished: absent, not a seventh verdict. */
    static final String PENDING = "pending";

    /**
     * The six counts, in the order they are answered: the five outcomes an item can end with and the one state it
     * is in before it ends. They are the record's own vocabulary, not a new one — the shell side already spells
     * every one of them as an item result.
     */
    static final List<String> OUTCOMES = List.of("closed", "blocked", "skipped", "cut", "refused", PENDING);

    /** The item fields the model declares. A field outside this set is a refusal on read and on write. */
    static final Set<String> ITEM_FIELDS = new LinkedHashSet<>(List.of(
            "issue", "class", "category", "rank", "verdict", "reason", "mechanism", "parent", "change",
            "adoptionRefusal"));

    /** The state's own sections. Only the item section is modelled here; the rest is the sibling's subject. */
    private static final Set<String> STATE_FIELDS = new LinkedHashSet<>(List.of("items"));

    private State() {
    }

    /** A refusal the core answers with a line and {@link LoopCore#REFUSED}: the input it was handed is not usable. */
    static final class Refusal extends Exception {
        private static final long serialVersionUID = 1L;

        Refusal(String message) {
            super(message);
        }
    }

    /** <code>state counts --state &lt;file&gt;</code> / <code>state write --state &lt;file&gt; --issue &lt;n&gt; …</code> */
    public static int run(String[] args) {
        if (args.length == 0) {
            System.err.println("loop-core: state needs a verb (counts, write)");
            return LoopCore.USAGE;
        }
        String verb = args[0];
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        Path file = null;
        String issue = null;
        List<String> assigns = new ArrayList<>();
        for (int i = 0; i < rest.length; i++) {
            String arg = rest[i];
            if (arg.equals("--state") && i + 1 < rest.length) {
                file = Path.of(rest[++i]);
            } else if (arg.equals("--issue") && i + 1 < rest.length) {
                issue = rest[++i];
            } else if (arg.startsWith("--")) {
                System.err.println("loop-core: state " + verb + " does not know the option '" + arg + "'");
                return LoopCore.USAGE;
            } else {
                assigns.add(arg);
            }
        }
        if (file == null) {
            System.err.println("loop-core: state " + verb + " needs --state <file>");
            return LoopCore.USAGE;
        }
        try {
            if (verb.equals("counts")) {
                if (!assigns.isEmpty()) {
                    System.err.println("loop-core: state counts takes no field assignments");
                    return LoopCore.USAGE;
                }
                System.out.println(render(counts(read(file))));
                return LoopCore.OK;
            }
            if (verb.equals("write")) {
                if (assigns.isEmpty()) {
                    System.err.println("loop-core: state write needs at least one field=value");
                    return LoopCore.USAGE;
                }
                Map<String, Object> state = file.toFile().exists() ? read(file) : newState();
                writeItem(state, issue, assigns, file);
                write(file, state);
                return LoopCore.OK;
            }
            System.err.println("loop-core: state does not know the verb '" + verb + "'");
            return LoopCore.UNKNOWN_VERB;
        } catch (Refusal refused) {
            System.err.println("loop-core: " + refused.getMessage());
            return LoopCore.REFUSED;
        } catch (IOException failed) {
            System.err.println("loop-core: " + file + ": " + failed.getMessage());
            return LoopCore.REFUSED;
        }
    }

    /** A state with no items: what the first write of a run starts from. */
    static Map<String, Object> newState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("items", new LinkedHashMap<String, Object>());
        return state;
    }

    /**
     * The state a file holds, with every field of it checked against the model.
     *
     * The parse is the whole file or nothing ({@link Json#parse} throws), and the field check runs before the caller
     * sees any of it, so a refusal cannot leave a half-read record behind.
     */
    static Map<String, Object> read(Path file) throws Refusal, IOException {
        if (!file.toFile().exists()) {
            throw new Refusal(file + ": no state at that path");
        }
        Object parsed;
        try {
            parsed = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException malformed) {
            throw new Refusal(file + ": the state is not JSON (" + malformed.getMessage() + ")");
        }
        Map<String, Object> state = asObject(parsed);
        if (state == null) {
            throw new Refusal(file + ": the state is not a JSON object");
        }
        for (String field : state.keySet()) {
            if (!STATE_FIELDS.contains(field)) {
                throw new Refusal("undeclared field '" + field + "' in " + file);
            }
        }
        Object items = state.get("items");
        if (items != null) {
            Map<String, Object> byIssue = asObject(items);
            if (byIssue == null) {
                throw new Refusal(file + ": 'items' is not an object");
            }
            for (Map.Entry<String, Object> entry : byIssue.entrySet()) {
                Map<String, Object> item = asObject(entry.getValue());
                if (item == null) {
                    throw new Refusal(file + ": item '" + entry.getKey() + "' is not an object");
                }
                for (String field : item.keySet()) {
                    if (!ITEM_FIELDS.contains(field)) {
                        throw new Refusal("undeclared field '" + field + "' in " + file);
                    }
                }
            }
        }
        return state;
    }

    /** The state written, refusing an undeclared field first and replacing the file atomically. */
    static void write(Path file, Map<String, Object> state) throws Refusal, IOException {
        for (String field : state.keySet()) {
            if (!STATE_FIELDS.contains(field)) {
                throw new Refusal("undeclared field '" + field + "' in " + file);
            }
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // The renderer sorts keys, so the same state is the same bytes: a reader that compares two writes of one
        // record is comparing the records and not the order a walk produced them in.
        Path staging = Files.createTempFile(parent == null ? Path.of(".") : parent, ".state", ".tmp");
        try {
            Files.writeString(staging, render(state) + "\n", StandardCharsets.UTF_8);
            Files.move(staging, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    /** One item's fields written into the state, each named field declared or the whole call refused. */
    private static void writeItem(Map<String, Object> state, String issue, List<String> assigns, Path file)
            throws Refusal {
        Map<String, Object> item = new LinkedHashMap<>();
        for (String assign : assigns) {
            int at = assign.indexOf('=');
            if (at <= 0) {
                throw new Refusal("'" + assign + "' is not field=value");
            }
            String field = assign.substring(0, at);
            if (!ITEM_FIELDS.contains(field)) {
                throw new Refusal("undeclared field '" + field + "' in " + file);
            }
            item.put(field, typed(assign.substring(at + 1)));
        }
        String key = issue;
        if (key == null) {
            key = item.get("issue") == null ? null : String.valueOf(item.get("issue"));
        }
        if (key == null || key.isEmpty()) {
            throw new Refusal("state write needs --issue <n> or an issue= field");
        }
        item.put("issue", typed(key));
        Map<String, Object> byIssue = asObject(state.get("items"));
        if (byIssue == null) {
            byIssue = new LinkedHashMap<>();
            state.put("items", byIssue);
        }
        Map<String, Object> existing = asObject(byIssue.get(key));
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existing != null) {
            merged.putAll(existing);
        }
        merged.putAll(item);
        byIssue.put(key, merged);
    }

    /**
     * The six counts, answered for every outcome whether or not the state holds one: a reader comparing two runs
     * must not have to tell an absent key from a zero.
     */
    static Map<String, Object> counts(Map<String, Object> state) {
        Map<String, Object> answer = new LinkedHashMap<>();
        for (String outcome : OUTCOMES) {
            answer.put(outcome, 0L);
        }
        Map<String, Object> byIssue = asObject(state.get("items"));
        if (byIssue == null) {
            return answer;
        }
        for (Object value : byIssue.values()) {
            Map<String, Object> item = asObject(value);
            String verdict = item == null ? null : Json.stringOr(item, "verdict", null);
            String outcome = verdict == null ? PENDING : verdict;
            if (!answer.containsKey(outcome)) {
                // A verdict the model does not know is not silently folded into `pending`: it is refused, because a
                // count that counts something else is a number nobody can act on.
                outcome = PENDING;
            }
            answer.put(outcome, ((Long) answer.get(outcome)) + 1L);
        }
        return answer;
    }

    /** The value as an object, or null when it is absent or some other type — the caller's refusal, not a crash. */
    private static Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        return Json.object(value);
    }

    /** A field's value as JSON types: a bare integer, a boolean, null, or the string as it stands. */
    static Object typed(String value) {
        if (value.equals("null")) {
            return null;
        }
        if (value.equals("true") || value.equals("false")) {
            return Boolean.valueOf(value);
        }
        if (value.matches("-?[0-9]{1,18}")) {
            return Long.valueOf(value);
        }
        return value;
    }

    /** The canonical rendering: object keys sorted, no whitespace outside a string, one document and one line. */
    static String render(Object value) {
        StringBuilder out = new StringBuilder();
        renderInto(out, value);
        return out.toString();
    }

    private static void renderInto(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(Json.quote(entry.getKey())).append(':');
                renderInto(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                renderInto(out, list.get(i));
            }
            out.append(']');
        } else if (value instanceof String text) {
            out.append(Json.quote(text));
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else {
            out.append(Json.quote(Json.describe(value)));
        }
    }
}
