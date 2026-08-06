package teralizer.processing.reports;

/**
 * How a surefire report names a test case, and how that name is compared against the qualified
 * name the pipeline stores.
 *
 * <p>A report identifies a test case either by fully qualified name — surefire before 3.0.2, and
 * every vintage-engine test — or by the engine's display name, which jqwik renders with
 * underscores as spaces and the package dropped. Both shapes occur in the corpus, because a
 * project-declared surefire plugin is floored to 2.22.2 while a project without one receives
 * 3.2.5.
 *
 * <p>This is the sole owner of that comparison. Both report ingestion and the pass/fail decision
 * for a generalized property resolve names here, so the two cannot drift apart.
 */
public final class SurefireReportNames {

    private SurefireReportNames() {
    }

    /**
     * A report name in the form the stored qualified names use: spaces become underscores, and an
     * argument list is left untouched so parameterized cases stay distinguishable. Idempotent, so
     * an already-normalized name passes through unchanged.
     *
     * <p>This inverts the display-name rendering, where a space stands for an underscore in the
     * declared name. A name whose spaces are not underscore substitutions — a JUnit 5
     * {@code @DisplayName} of free-form prose, for instance — carries no path back to its declared
     * name and does not match it. Selection then reports no matching test case, naming every
     * candidate file it inspected.
     */
    public static String normalize(String reportName) {
        if (reportName == null) {
            return null;
        }

        int firstParenIndex = reportName.indexOf('(');
        if (firstParenIndex > 0) {
            return reportName.substring(0, firstParenIndex).replace(" ", "_")
                + reportName.substring(firstParenIndex);
        }
        return reportName.replace(" ", "_");
    }

    /**
     * Whether a report name denotes the expected qualified name. The report name is normalized
     * first, then matched either exactly or as its package-less suffix; the {@code '.'} boundary
     * keeps a simple-name collision from matching.
     */
    public static boolean matches(String expectedQualifiedName, String reportName) {
        if (expectedQualifiedName == null || reportName == null) {
            return false;
        }

        String normalized = normalize(reportName);
        return expectedQualifiedName.equals(normalized)
            || expectedQualifiedName.endsWith("." + normalized);
    }

    /** The method-name portion of a report name, with any argument list removed. */
    public static String withoutArguments(String reportName) {
        return reportName == null ? null : reportName.replaceAll("\\(.*", "");
    }
}
