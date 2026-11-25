import java.util.List;

/**
 * The loop's core: one verb per invocation, one JSON document on stdout (design D2, D4).
 *
 * The boundary this file opens is deliberately small. A phase of the run pays <b>one</b> JVM start, not one per
 * value read, so a verb answers in batches — <code>state counts</code> answers six counts at once. The scripts keep
 * their process boundary and every exit code they have today: nothing here is called by a script yet, and the
 * sibling change <code>the-run-state-is-typed</code> is the one that switches them on.
 *
 * The dispatch is the whole of the extension point. <code>state</code> is the only verb today; a sibling adds a verb
 * as one arm and leaves the boundary where it is. An unknown verb is named on stderr and exits
 * {@link #UNKNOWN_VERB}, never a silent success: a script that mistypes a verb must not read an empty answer as
 * a result.
 *
 * Usage: {@code java -cp <classes> LoopCore [--cases | <verb> [args…]]}
 *
 * The verbs are documented in {@link #usage()}; the exit codes are the ones every loop script already shares
 * ({@code lib.sh}: 0 ok, 2 usage, 3 stop, 5 refuse), so a caller needs no second table.
 */
public final class LoopCore {

    /** The status every loop script shares for "this went as asked" (`lib.sh`, `LOOP_OK`). */
    static final int OK = 0;
    /** A call that asked for something the core cannot do, with the reason on stderr (`LOOP_USAGE`). */
    static final int USAGE = 2;
    /** A verb the core does not know: the run's own "stop" status, because nothing about it can be answered. */
    static final int UNKNOWN_VERB = 3;
    /** An input the core refused: malformed, or carrying a field its model does not declare (`LOOP_REFUSE`). */
    static final int REFUSED = 5;

    /**
     * The cases the core owns, in the order the suite lists them.
     *
     * The list lives here, in the core, and the suite asks for it (<code>loop_core --cases</code>) rather than
     * spelling it a second time: a case that exists in one place and not the other is a case that exists but does
     * not run, which is the failure the driver's declaration check exists for (issue #929). The <b>bodies</b> are
     * shell cases in {@code tools/loop/tests/loop-cases.sh} — they drive this binary against fixtures and assert the
     * document it answers, because the suite already has one selection, one summary and one offline gate
     * (design D3), and a second harness inside the core would be a second gate.
     *
     * Each of those bodies opens with a {@code # files:} line naming {@code tools/loop/core/}: a case about the core
     * is affected by every file in it, and a declaration that omits it is refused by the case-list check.
     */
    private static final List<String> CASES = List.of(
            "core#theDigestIsTheCompileKey",
            "core#oneCompilePerDigest",
            "core#aMissingCompilerIsOneRefusal",
            "core#theAnswerIsOneJsonDocument",
            "core#aMalformedInputIsARefusal",
            "core#noPartialParseOnFailure",
            "core#theStateIsWrittenByTheCore",
            "core#theStateAReadCannotParseIsRefused",
            "core#thePortabilityCheckReadsTheCore",
            "core#theCoresCasesDeclareTheCore",
            "core#theCoreListsItsOwnCases",
            "core#oneSummaryCarriesBothSources",
            "core#aCoreCaseDeclaresItsFiles");

    private LoopCore() {
    }

    public static void main(String[] args) {
        System.exit(dispatch(args));
    }

    /** The one place a verb is chosen: an arm here is a verb, and nothing else in the core decides what runs. */
    static int dispatch(String[] args) {
        if (args.length == 0) {
            System.err.print(usage());
            return USAGE;
        }
        String verb = args[0];
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        switch (verb) {
            case "--cases":
                // Not a verb: an option that lists the cases the core owns, one per line, for the suite's pool.
                for (String name : CASES) {
                    System.out.println(name);
                }
                return OK;
            case "state":
                return State.run(rest);
            case "--help":
            case "help":
                System.err.print(usage());
                return USAGE;
            default:
                System.err.println("loop-core: unknown verb '" + verb + "'");
                System.err.print(usage());
                return UNKNOWN_VERB;
        }
    }

    private static String usage() {
        return String.join("\n",
                "usage: loop-core <verb> [args…]",
                "",
                "  state counts --state <file>                     the six outcome counts, as one JSON document",
                "  state write  --state <file> [--issue <n>] field=value…",
                "                                                 write one item's fields, refusing an undeclared one",
                "  --cases                                        list the cases the core owns, one per line",
                "",
                "exit: 0 ok, 2 usage, 3 unknown verb, 5 refused (malformed input or an undeclared field)",
                "");
    }
}
