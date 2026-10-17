import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader and a few accessors, so the core needs no dependency and no build system: the
 * policy core is run as single-file Java (`java tools/issues/core/Policy.java …`), which cannot resolve a
 * jar from a Gradle cache (design D1).
 *
 * Objects become {@link Map}, arrays become {@link List}, scalars become String/Long/Double/Boolean/null.
 * Malformed input throws {@link IllegalArgumentException} — a caller turns that into a refusal, never a
 * partial parse (design D7).
 */
public final class Json {

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json reader = new Json(text);
        reader.skipWhitespace();
        Object value = reader.readValue();
        reader.skipWhitespace();
        if (reader.at != text.length()) {
            throw new IllegalArgumentException("trailing content at offset " + reader.at);
        }
        return value;
    }

    public static Object parseFile(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- accessors

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("not a JSON object: " + describe(value));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalArgumentException("not a JSON array: " + describe(value));
    }

    /** The value at {@code key}, or null when the object has no such key or it is JSON null. */
    public static Object get(Object object, String key) {
        return object(object).get(key);
    }

    /** Walk a path of object keys; null as soon as a step is missing or not an object. */
    public static Object path(Object object, String... keys) {
        Object current = object;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?>)) {
                return null;
            }
            current = get(current, key);
        }
        return current;
    }

    public static String string(Object object, String key) {
        Object value = get(object, key);
        return value == null ? null : String.valueOf(value);
    }

    public static String stringOr(Object object, String key, String fallback) {
        String value = string(object, key);
        return value == null ? fallback : value;
    }

    public static List<String> strings(Object object, String key) {
        List<String> out = new ArrayList<>();
        Object value = get(object, key);
        if (value == null) {
            return out;
        }
        for (Object item : array(value)) {
            out.add(String.valueOf(item));
        }
        return out;
    }

    public static long integer(Object object, String key, long fallback) {
        Object value = get(object, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s && s.matches("-?[0-9]+")) {
            return Long.parseLong(s);
        }
        return fallback;
    }

    public static boolean bool(Object object, String key, boolean fallback) {
        Object value = get(object, key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    public static Map<String, Object> newObject() {
        return new LinkedHashMap<>();
    }

    public static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    public static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        String text = String.valueOf(value);
        return value instanceof String ? quote(text) : text;
    }

    // ----------------------------------------------------------------- parsing

    private void skipWhitespace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private Object readValue() {
        if (at >= text.length()) {
            throw new IllegalArgumentException("unexpected end of input");
        }
        char c = text.charAt(at);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, at)) {
            throw new IllegalArgumentException("expected " + literal + " at offset " + at);
        }
        at += literal.length();
        return value;
    }

    private Map<String, Object> readObject() {
        Map<String, Object> out = new LinkedHashMap<>();
        at++; // {
        skipWhitespace();
        if (at < text.length() && text.charAt(at) == '}') {
            at++;
            return out;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            out.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return out;
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected , or } at offset " + (at - 1));
            }
        }
    }

    private List<Object> readArray() {
        List<Object> out = new ArrayList<>();
        at++; // [
        skipWhitespace();
        if (at < text.length() && text.charAt(at) == ']') {
            at++;
            return out;
        }
        while (true) {
            skipWhitespace();
            out.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return out;
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected , or ] at offset " + (at - 1));
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                char escape = next();
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) {
                            throw new IllegalArgumentException("truncated \\u escape at offset " + at);
                        }
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape \\" + escape + " at offset " + (at - 1));
                }
                continue;
            }
            out.append(c);
        }
    }

    private Object readNumber() {
        int start = at;
        while (at < text.length() && "-+.eE0123456789".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        String token = text.substring(start, at);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("not a value at offset " + start);
        }
        if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
            return Long.valueOf(token);
        }
        return Double.valueOf(token);
    }

    private char next() {
        if (at >= text.length()) {
            throw new IllegalArgumentException("unexpected end of input");
        }
        return text.charAt(at++);
    }

    private void expect(char expected) {
        char c = next();
        if (c != expected) {
            throw new IllegalArgumentException("expected " + expected + " at offset " + (at - 1));
        }
    }
}
