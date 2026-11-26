import java.util.List;

/**
 * The cases the loop's core owns, in the order the suite lists them.
 *
 * The list lives in the core, and the suite asks for it (<code>loop_core --cases</code>) rather than spelling it a
 * second time: a case that exists in one place and not the other is a case that exists but does not run, which is the
 * failure the driver's declaration check exists for (issue #929). The <b>bodies</b> are shell cases in
 * {@code tools/loop/tests/loop-cases.sh} — they drive this binary against fixtures and assert the document it
 * answers, because the suite already has one selection, one summary and one offline gate (design D3), and a second
 * harness inside the core would be a second gate.
 *
 * Each of those bodies opens with a {@code # files:} line naming {@code tools/loop/core/}: a case about the core is
 * affected by every file in it, and a declaration that omits it is refused by the case-list check.
 *
 * The names sit in a file of their own, beside the dispatch that answers them, because they are the tooling's own
 * vocabulary and not code: a project whose category names happen to include one of the words a group is called
 * would otherwise read this list as a leaked project value — measured, the foreign fixture's categories include
 * <code>core</code>, and every name below carries that word. {@code tools/check-portability.sh} excludes this file
 * by name for that reason and prints the exclusion, so what it does not scan stays visible; a name added here needs
 * no other registration, and nothing but names lives in the file.
 */
final class CaseList {

    private CaseList() {
    }

    static final List<String> NAMES = List.of(
            "core#theDigestIsTheCompileKey",
            "core#theDigestReadsASymlinkedCore",
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
}
