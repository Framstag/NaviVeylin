import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <strong>The fingerprint</strong>: what says two findings are the same one, computed from the structured
 * fields only — kind, area, the normalised first claim, and the primary anchor. It is never a committed
 * ledger: the check reads the live tracker and matches there (design D4).
 *
 * The anchor never carries a line number. Line numbers drift, and a fingerprint that changes when the
 * entry is re-read would defeat the very dedup it exists for.
 */
public final class Fingerprint {

    /** The marker an issue body carries, so the live check can match without re-deriving the claim. */
    public static final String MARKER = "fingerprint";

    private static final Pattern DIGITS_SUFFIX = Pattern.compile(":\\d+(-\\d+)?$");
    private static final Pattern MARKER_IN_BODY =
            Pattern.compile("<!--\\s*" + MARKER + ":\\s*([0-9a-f]{64})\\s*-->");
    private static final int CLAIM_LIMIT = 200;

    private Fingerprint() {
    }

    /** Lowercase, whitespace collapsed, every non-alphanumeric run a single space, trimmed. */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** The first sentence of a claim, so that a reflowed body hashes the same. */
    public static String firstSentence(String claim) {
        if (claim == null) {
            return "";
        }
        String text = claim.strip();
        int cut = text.length();
        Matcher boundary = Pattern.compile("[.!?](\\s|$)").matcher(text);
        if (boundary.find()) {
            cut = boundary.start() + 1;
        }
        String sentence = text.substring(0, cut).strip();
        if (sentence.length() > CLAIM_LIMIT) {
            sentence = sentence.substring(0, CLAIM_LIMIT);
            int space = sentence.lastIndexOf(' ');
            if (space > CLAIM_LIMIT / 2) {
                sentence = sentence.substring(0, space);
            }
        }
        return sentence;
    }

    /** A path, plus a symbol when the finding names one — never a line number. */
    public static String anchor(String path, String symbol) {
        if (path == null || path.isBlank()) {
            return "(none)";
        }
        String cleaned = stripLineNumbers(path.strip());
        if (symbol != null && !symbol.isBlank()) {
            cleaned = cleaned + ":" + stripLineNumbers(symbol.strip());
        }
        return cleaned;
    }

    /** Drop a trailing `:41` / `:41-52` — line numbers drift, and a drifted hash is not a fingerprint. */
    public static String stripLineNumbers(String text) {
        return text == null ? "" : DIGITS_SUFFIX.matcher(text).replaceFirst("");
    }

    /** The canonical string a digest is taken over; exposed so a test can show which field separates. */
    public static String canonical(String clazz, String category, String claim, String anchor) {
        return "class=" + normalize(clazz)
                + "|category=" + normalize(category)
                + "|claim=" + normalize(firstSentence(claim))
                + "|anchor=" + normalize(stripLineNumbers(anchor));
    }

    public static String digest(String clazz, String category, String claim, String anchor) {
        return sha256(canonical(clazz, category, claim, anchor));
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** The body marker that carries a digest, so an existing issue can be matched without prose parsing. */
    public static String marker(String digest) {
        return "<!-- " + MARKER + ": " + digest + " -->";
    }

    /** The digest a body carries, or null. */
    public static String digestIn(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = MARKER_IN_BODY.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * The number of the live issue carrying this digest, or null. Matching is on the marker, never on the
     * text, so a substring of another item's claim cannot produce a false duplicate.
     */
    public static Long match(List<Object> issues, String digest) {
        for (Object issue : issues) {
            if (digest.equals(digestIn(Json.string(issue, "body")))) {
                Object number = Json.get(issue, "number");
                return number instanceof Number n ? n.longValue() : null;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        if (args.length == 2 && args[0].equals("--match")) {
            // The live dedup query: the issue list arrives as JSON on stdin, the digest comes in as an
            // argument, and the matching issue's number is printed — matching on the marker, never on text.
            try {
                Object parsed = Json.parse(new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
                Long match = match(Json.array(parsed), args[1]);
                if (match != null) {
                    System.out.println(match);
                }
            } catch (Exception e) {
                System.err.println("Fingerprint: " + e.getMessage());
                System.exit(2);
            }
            return;
        }
        String clazz = "";
        String category = "";
        String claim = "";
        String anchor = "";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("class=")) {
                clazz = arg.substring(6);
            } else if (arg.startsWith("category=")) {
                category = arg.substring(9);
            } else if (arg.startsWith("claim=")) {
                claim = arg.substring(6);
            } else if (arg.startsWith("anchor=")) {
                anchor = arg.substring(7);
            } else {
                System.err.println("Fingerprint: unknown argument " + arg);
                System.exit(2);
            }
        }
        System.out.println(digest(clazz, category, claim, anchor));
    }
}
