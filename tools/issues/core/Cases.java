import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The core's cases, run by {@code tools/issue-tooling-selftest.sh} (design D9). Each case is a named
 * method-shaped entry: the name is the contract — a task names the case that proves it, and the revert
 * checks in `tasks.md` mutate the code until a named case goes red.
 *
 * Usage: {@code java -cp <classes> Cases [--list | <Case#name>]}
 *
 * Environment: {@code ISSUES_REPO_ROOT} (default: cwd), {@code ISSUES_FIXTURE_DIR} (default:
 * {@code <root>/tools/issues/fixtures}).
 */
public final class Cases {

    private static final Path ROOT = Path.of(System.getenv().getOrDefault("ISSUES_REPO_ROOT", "."))
            .toAbsolutePath().normalize();
    private static final Path FIXTURES = Path.of(System.getenv().getOrDefault("ISSUES_FIXTURE_DIR",
            ROOT.resolve("tools/issues/fixtures").toString())).toAbsolutePath().normalize();
    private static final Path PROFILE = ROOT.resolve("tools/tooling.json");
    // The parser's subject is a file format, so the cases drive it over the committed fixtures. The live
    // backlog file is not part of this surface: it is what the migration removed, and a case that read it
    // would break the day it went (change `migrate-todo-to-github-issues`, D6).
    private static final Path STATUS_SHAPES = FIXTURES.resolve("todo-status-shapes.md");
    private static final Path RENAMES = FIXTURES.resolve("todo-renames.md");

    private Cases() {
    }

    public static void main(String[] args) {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("Profile#everySectionIsPresent", Cases::profileEverySectionIsPresent);
        cases.put("Taxonomy#vocabulariesAreClosed", Cases::taxonomyVocabulariesAreClosed);
        cases.put("Taxonomy#mapsEveryFixtureEntry", Cases::taxonomyMapsEveryFixtureEntry);
        cases.put("Taxonomy#renamesTheTwoProjectCategories", Cases::taxonomyRenamesTheTwoProjectCategories);
        cases.put("Taxonomy#unknownValueRefuses", Cases::taxonomyUnknownValueRefuses);
        cases.put("Policy#allowlistIsNotEmptyAndNamesTheAuthenticatedUser",
                Cases::policyAllowlistNamesTheAuthenticatedUser);
        cases.put("Policy#gateMatrix", Cases::policyGateMatrix);
        cases.put("WorkPackage#bodyCannotBreakOutOfTheDataBlock", Cases::workPackageBodyCannotBreakOut);
        cases.put("WorkPackage#oversizedBodyRefuses", Cases::workPackageOversizedBodyRefuses);
        cases.put("Fingerprint#stableAcrossWhitespaceAndLineNumbers", Cases::fingerprintStable);
        cases.put("Fingerprint#separatesDistinctEntries", Cases::fingerprintSeparates);
        cases.put("Bands#coversTheWholeRange", Cases::bandsCoversTheWholeRange);
        cases.put("Bands#labelSpellingComesFromTheProfile", Cases::bandsLabelSpelling);
        cases.put("Bands#refusesAScoreOutsideEveryBand", Cases::bandsRefusesOutsideRange);
        cases.put("TodoSections#parsesEveryStatusShape", Cases::todoSectionsParsesEveryStatusShape);

        if (args.length == 0 || args[0].equals("--list")) {
            cases.keySet().forEach(System.out::println);
            return;
        }
        String name = args[0];
        Runnable body = cases.get(name);
        if (body == null) {
            System.out.println("FAIL " + name + ": no such case");
            System.exit(1);
        }
        try {
            body.run();
            System.out.println("PASS " + name);
        } catch (Throwable failure) {
            System.out.println("FAIL " + name + ": " + failure.getMessage());
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void equals(Object expected, Object actual, String what) {
        expect(String.valueOf(expected).equals(String.valueOf(actual)),
                what + ": expected " + expected + ", got " + actual);
    }

    private static Profile profile() {
        try {
            return Profile.load(PROFILE);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + PROFILE + ": " + e.getMessage());
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + path + ": " + e.getMessage());
        }
    }

    private static Object issue(String author, List<String> labels, String title, String body) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("number", 7);
        issue.put("title", title);
        issue.put("body", body);
        issue.put("author", Map.of("login", author));
        List<Object> labelObjects = new ArrayList<>();
        for (String label : labels) {
            labelObjects.add(Map.of("name", label));
        }
        issue.put("labels", labelObjects);
        return issue;
    }

    // -------------------------------------------------------------------- cases

    private static void profileEverySectionIsPresent() {
        Profile profile = profile();
        List<String> problems = profile.problems();
        expect(problems.isEmpty(), "the committed profile is malformed: " + problems);
        for (String namespace : Profile.NAMESPACES) {
            expect(profile.namespaces().contains(namespace), "namespace missing from the profile: " + namespace);
        }
        expect(profile.namespaces().size() == Profile.NAMESPACES.size(),
                "the profile declares a namespace the engine does not know: " + profile.namespaces());
        for (String namespace : Profile.NAMESPACES) {
            if (Profile.EMPTY_ALLOWED.contains(namespace)) {
                continue;
            }
            expect(!profile.values(namespace).isEmpty(), "namespace is empty: " + namespace);
        }
        expect(!profile.allowedAuthors().isEmpty(), "allowedAuthors is empty");
        expect(!profile.requiredLabels().isEmpty(), "requiredLabels is empty");
        expect(!profile.reservedLabels().isEmpty(), "reservedLabels is empty");
        expect(!profile.statusRules().isEmpty(), "statusLabels is empty");
        expect(!profile.repo().isBlank(), "repo is empty");
    }

    private static void taxonomyVocabulariesAreClosed() {
        Profile profile = profile();
        for (String namespace : Profile.NAMESPACES) {
            for (String value : profile.values(namespace)) {
                expect(value.matches("^[a-z0-9-]+$"),
                        namespace + " has a value outside ^[a-z0-9-]+$: " + value);
            }
        }
        equals(4, profile.values("prio").size(), "the prio namespace holds exactly four bands");
        expect(profile.values("source").contains("ci"), "the source namespace has no ci provenance");
        expect(profile.namespaces().contains("sensor"), "the sensor namespace is not declared");
        expect(profile.reservedLabels().contains("agent/eligible"),
                "reservedLabels does not contain the opt-in label");
        for (String required : profile.requiredLabels()) {
            expect(profile.reservedLabels().contains(required),
                    "a required label is not reserved, so a write path could self-serve it: " + required);
        }
    }

    private static void taxonomyMapsEveryFixtureEntry() {
        Profile profile = profile();
        String text = read(STATUS_SHAPES);
        List<TodoSections.Entry> entries = TodoSections.parse(text);
        long declared = text.lines().filter(line -> line.startsWith("**id:**")).count();
        equals(declared, entries.size(), "every section carries a metadata line, and every one is parsed");
        expect(entries.size() > 0, "the backlog fixture parsed to no entries");

        List<String> renames = new ArrayList<>();
        int renamed = 0;
        for (TodoSections.Entry entry : entries) {
            List<String> labels = Taxonomy.labels(profile, entry.clazz, entry.category, entry.status, renames);
            for (String label : labels) {
                expect(Taxonomy.known(profile, label), "mapped a label outside the vocabulary: " + label);
            }
            if (!profile.values("category").contains(entry.category)) {
                renamed++;
            }
        }
        // This fixture's categories are already the profile's values, so nothing is renamed: a rename is
        // driven by the entry, never applied blanket-wise.
        equals(0, renamed, "no category of this fixture is off-vocabulary");
        expect(renames.isEmpty(), "a rename was applied to an entry that needed none: " + renames);
    }

    private static void taxonomyRenamesTheTwoProjectCategories() {
        Profile profile = profile();
        List<TodoSections.Entry> entries = TodoSections.parse(read(RENAMES));
        equals(2, entries.size(), "the rename fixture holds the two off-vocabulary categories");

        List<String> renames = new ArrayList<>();
        int renamed = 0;
        for (TodoSections.Entry entry : entries) {
            List<String> labels = Taxonomy.labels(profile, entry.clazz, entry.category, entry.status, renames);
            for (String label : labels) {
                expect(Taxonomy.known(profile, label), "mapped a label outside the vocabulary: " + label);
            }
            if (!profile.values("category").contains(entry.category)) {
                renamed++;
            }
        }
        equals(2, renamed, "both off-vocabulary categories are renamed");
        equals(renamed, renames.size(), "every off-vocabulary category is renamed exactly once");
        List<String> distinct = renames.stream().distinct().toList();
        equals(2, distinct.size(), "expected two distinct renames, got " + distinct);
        List<String> labels = Taxonomy.labels(profile, "bug", "rendering", "open", null);
        expect(labels.contains("category/map-rendering"), "rendering must become map-rendering: " + labels);
        labels = Taxonomy.labels(profile, "improvement", "build", "open", null);
        expect(labels.contains("category/build-and-harness"), "build must become build-and-harness: " + labels);
    }

    private static void taxonomyUnknownValueRefuses() {
        Profile profile = profile();
        try {
            Taxonomy.label(profile, "category", "wibble", null);
            throw new AssertionError("an unknown category was mapped instead of refused");
        } catch (Taxonomy.Refusal refusal) {
            equals("label-opaque", refusal.reason, "the refusal reason code");
            expect(refusal.detail.contains("wibble"), "the refusal must name the value: " + refusal.detail);
        }
        List<TodoSections.Entry> entries = TodoSections.parse(read(FIXTURES.resolve("todo-unknown-value.md")));
        equals(1, entries.size(), "the fixture holds one entry");
        try {
            Taxonomy.labels(profile, entries.get(0).clazz, entries.get(0).category, entries.get(0).status, null);
            throw new AssertionError("a fixture entry with an unknown category was mapped");
        } catch (Taxonomy.Refusal refusal) {
            equals("label-opaque", refusal.reason, "the refusal reason code for a fixture entry");
        }
    }

    private static void policyAllowlistNamesTheAuthenticatedUser() {
        Profile profile = profile();
        expect(!profile.allowedAuthors().isEmpty(), "allowedAuthors must not be empty");
        String login = read(FIXTURES.resolve("login.txt")).strip();
        expect(profile.allowedAuthors().contains(login),
                "the allowlist does not name the authenticated actor " + login
                        + " — it cannot silently drift from the machine");
    }

    private static void policyGateMatrix() {
        Profile profile = profile();
        // The allowlisted actor is taken from the stub `gh`'s reported login, not from the profile, so that a
        // profile whose allowlist has drifted (or been emptied) turns this case red instead of following it.
        String allowed = read(FIXTURES.resolve("login.txt")).strip();
        String other = profile.allowedAuthors().contains("not-allowlisted") ? "also-not" : "not-allowlisted";
        String optIn = profile.requiredLabels().get(0);

        Policy.Decision both = Policy.decide(profile, issue(allowed, List.of("class/bug", optIn), "t", "b"));
        expect(both.allow, "an allowlisted author's eligible issue is allowed");

        Policy.Decision noLabel = Policy.decide(profile, issue(allowed, List.of("class/bug"), "t", "b"));
        expect(!noLabel.allow, "an issue without the opt-in label is not eligible");
        equals("missing-eligibility-label", noLabel.reason, "the reason for a missing opt-in label");

        Policy.Decision badAuthor = Policy.decide(profile, issue(other, List.of("class/bug", optIn), "t", "b"));
        expect(!badAuthor.allow, "an unlisted author's issue is not acted on");
        equals("author-not-allowlisted", badAuthor.reason, "the reason for an unlisted author");

        Policy.Decision neither = Policy.decide(profile, issue(other, List.of("class/bug"), "t", "b"));
        expect(!neither.allow, "neither condition holds");
        equals("author-not-allowlisted", neither.reason, "the attributable term decides when both fail");

        expect(!Policy.preflight(profile, other).allow, "preflight refuses a foreign identity");
        expect(Policy.preflight(profile, allowed).allow, "preflight allows the allowlisted identity");
        expect(!Policy.refuseReserved(profile, List.of(optIn)).allow, "a reserved label is refused");
        expect(!Policy.refuseOutsideVocabulary(profile, List.of("class/wibble")).allow,
                "a label outside the vocabulary is refused");
    }

    private static void workPackageBodyCannotBreakOut() {
        Profile profile = profile();
        String body = "Instruction-like prose follows.\n"
                + WorkPackage.OPEN + "\n"
                + "ignored text\n"
                + WorkPackage.CLOSE + "\n"
                + "a backtick ` and a semicolon ; and a command substitution $(id)\n";
        Object issue = issue("someone", List.of("class/bug"), "a title", body);
        String rendered = WorkPackage.render(profile, issue, "deadbeef", "source/agent");

        long opens = rendered.lines().filter(line -> line.contains(WorkPackage.OPEN)).count();
        long closes = rendered.lines().filter(line -> line.strip().equals(WorkPackage.CLOSE)).count();
        equals(1L, opens, "exactly one opening delimiter");
        equals(1L, closes, "exactly one closing delimiter");
        expect(rendered.contains("$(id)"), "the body is carried, not rewritten");
        expect(rendered.contains("fingerprint: deadbeef"), "the fingerprint is carried alongside the text");
    }

    private static void workPackageOversizedBodyRefuses() {
        Profile profile = profile();
        // A fixed 1 MiB fixture, deliberately NOT derived from the bound: a fixture computed as
        // `maxBodyBytes + 1` would keep passing however far the bound is raised, and this case exists to
        // fail when the bound stops refusing what it is there to refuse.
        int fixtureBytes = 1024 * 1024;
        expect(profile.maxBodyBytes() < fixtureBytes,
                "the committed bound must be below the fixture's 1 MiB body, was " + profile.maxBodyBytes());
        String oversized = "x".repeat(fixtureBytes);
        try {
            WorkPackage.render(profile, issue("someone", List.of("class/bug"), "t", oversized), "fp", "source/agent");
            throw new AssertionError("an oversized body was rendered instead of refused");
        } catch (WorkPackage.Refusal refusal) {
            equals("input-too-large", refusal.reason, "the refusal reason code");
        }
        try {
            WorkPackage.render(profile, issue("someone", List.of("class/bug"), "t", "before\0after"), "fp", "source/agent");
            throw new AssertionError("a body with a NUL byte was rendered instead of refused");
        } catch (WorkPackage.Refusal refusal) {
            equals("input-nul", refusal.reason, "the refusal reason code for a NUL byte");
        }
    }

    private static void fingerprintStable() {
        String first = Fingerprint.digest("bug", "ui",
                "The header and the sum of the steps disagree", "app/src/main/X.kt:41");
        String second = Fingerprint.digest("bug", "ui",
                "  The header\n and the sum   of the steps disagree\n", "app/src/main/X.kt:900");
        equals(first, second, "a reflowed claim with a shifted line number hashes the same");
        equals(Fingerprint.anchor("app/src/main/X.kt:41", null), Fingerprint.anchor("app/src/main/X.kt:900", null),
                "the anchor drops the line number");
        expect(!Fingerprint.anchor("app/src/main/X.kt:41", null).contains("41"),
                "no line number survives in the anchor");
    }

    private static void fingerprintSeparates() {
        List<TodoSections.Entry> entries = TodoSections.parse(read(STATUS_SHAPES));
        expect(entries.size() >= 2, "the backlog fixture must hold at least two entries");

        List<String> digests = new ArrayList<>();
        for (int i = 0; i < 20 && i < entries.size(); i++) {
            TodoSections.Entry entry = entries.get(i);
            digests.add(Fingerprint.digest(entry.clazz, entry.category, entry.heading,
                    TodoAdapter.anchorIn(entry.body)));
        }
        expect(digests.stream().distinct().count() == digests.size(),
                "two distinct entries produced the same digest");

        // A deliberately near-identical pair: same kind, area and claim, different anchors.
        String claim = entries.get(0).heading;
        String a = Fingerprint.digest("bug", "ui", claim, "app/src/main/X.kt:First");
        String b = Fingerprint.digest("bug", "ui", claim, "app/src/main/X.kt:Second");
        expect(!a.equals(b), "the anchor separates two otherwise identical findings");
        equals(Fingerprint.canonical("bug", "ui", claim, "(none)"),
                Fingerprint.canonical("bug", "ui", claim, "(none)"),
                "the canonical string is stable");
        expect(Fingerprint.canonical("bug", "ui", claim, "app/src/main/X.kt:First")
                        .indexOf("anchor=") > 0,
                "the canonical string carries the anchor as its own field");
    }

    private static void bandsCoversTheWholeRange() {
        Profile profile = profile();
        List<Bands.Band> bands = Bands.bands(profile);
        int[] range = Bands.range(profile);
        expect(bands.size() > 1, "the profile declares no band edges");

        // The model's range is derived from the scoring tables, so a table edit that moves it is visible here
        // rather than showing up as a score with no band. severity·reach + confidence with (1..5, 1..3, 1..3).
        equals(2, range[0], "the model's lowest score");
        equals(18, range[1], "the model's highest score");

        // Totality: exactly one band per score in the derived range, and no band unreachable.
        Map<String, Integer> perBand = new LinkedHashMap<>();
        for (Bands.Band band : bands) {
            perBand.put(band.id, 0);
        }
        for (int score = range[0]; score <= range[1]; score++) {
            Bands.Band band = Bands.bandFor(profile, score);
            expect(band != null, "score " + score + " maps to no band");
            perBand.put(band.id, perBand.get(band.id) + 1);
        }
        for (Map.Entry<String, Integer> entry : perBand.entrySet()) {
            expect(entry.getValue() > 0, "band " + entry.getKey() + " is unreachable");
        }
        int covered = perBand.values().stream().mapToInt(Integer::intValue).sum();
        equals(range[1] - range[0] + 1, covered, "every score in the range is covered exactly once");

        // The bands are the vocabulary's own values, in the vocabulary's own order, and the edges are
        // contiguous: a gap between two bands would be a score the mapping refuses at run time. The order of
        // the *edges* is not asserted — the vocabulary's order is, and contiguity is checked by score.
        List<String> ids = bands.stream().map(band -> band.id).toList();
        equals(profile.values("prio"), ids, "the edges are the prio vocabulary, in order");
        List<Bands.Band> byScore = new ArrayList<>(bands);
        byScore.sort(Comparator.comparingInt(band -> band.min));
        for (int i = 1; i < byScore.size(); i++) {
            equals(byScore.get(i - 1).max + 1, byScore.get(i).min,
                    "the edges are contiguous between " + byScore.get(i - 1).id + " and " + byScore.get(i).id);
        }
        equals(range[0], byScore.get(0).min, "the lowest band starts at the model's minimum");
        equals(range[1], byScore.get(byScore.size() - 1).max, "the highest band ends at the model's maximum");
    }

    private static void bandsLabelSpelling() {
        Profile profile = profile();
        // The label is the profile's namespace plus the edge's own id — the engine names neither.
        for (Bands.Band band : Bands.bands(profile)) {
            String label = Bands.label(profile, band.min);
            equals(profile.label("prio", band.id), label, "the label of the band " + band.id);
            expect(Bands.label(profile, band.max).equals(label), "a band's ends share one label: " + label);
        }
        equals("prio/p3", Bands.label(profile, Bands.range(profile)[0]), "the lowest score's label");
        equals("prio/p0", Bands.label(profile, Bands.range(profile)[1]), "the highest score's label");
    }

    private static void bandsRefusesOutsideRange() {
        Profile profile = profile();
        int[] range = Bands.range(profile);
        for (int score : new int[] {range[0] - 1, range[1] + 1, 0, -5, 99}) {
            try {
                Bands.label(profile, score);
                throw new AssertionError("a score outside every band was labelled instead of refused: " + score);
            } catch (Bands.Refusal refusal) {
                equals("band-undefined", refusal.reason, "the refusal reason code for score " + score);
                expect(refusal.detail.contains(String.valueOf(score)),
                        "the refusal names the score: " + refusal.detail);
            }
        }
    }

    private static void todoSectionsParsesEveryStatusShape() {
        Profile profile = profile();
        List<TodoSections.Entry> entries = TodoSections.parse(read(STATUS_SHAPES));
        List<String[]> expected = List.of(
                new String[] {"1", "ui", "bug", "open", "open"},
                new String[] {"2", "verification", "improvement", "in-flight fix-example-change", "in-flight"},
                new String[] {"3", "route-and-navigation", "bug", "fixed-by `fix-example` — the native half is addressed", "fixed-by"},
                new String[] {"4", "specs-and-process", "improvement", "on-hold pick one of two shapes", "on-hold"},
                new String[] {"5", "car", "bug", "open (device)", "open-device"},
                new String[] {"6", "ui", "bug", "open (unproven on a device)", "unverified"},
                new String[] {"7", "build-and-harness", "improvement",
                        "retired — 2026-10-17: the package it guarded was removed", "retired"});
        equals(expected.size(), entries.size(), "one section per status shape");
        for (int i = 0; i < expected.size(); i++) {
            String[] want = expected.get(i);
            TodoSections.Entry entry = entries.get(i);
            equals(want[0], String.valueOf(entry.id), "parsed id");
            equals(want[1], entry.category, "parsed category of §" + want[0]);
            equals(want[2], entry.clazz, "parsed class of §" + want[0]);
            equals(want[3], entry.status, "parsed full status of §" + want[0]);
            equals(want[4], Taxonomy.statusValue(profile, entry.status), "status label of §" + want[0]);
        }
        throwsMalformedEntry();
    }

    private static void throwsMalformedEntry() {
        String text = "## 42. A heading with no metadata line\n\nno metadata here\n";
        try {
            TodoSections.parse(text);
            throw new AssertionError("a heading with no metadata line was parsed");
        } catch (TodoSections.Refusal refusal) {
            equals("malformed-entry", refusal.reason, "the refusal reason code");
        }
    }
}
