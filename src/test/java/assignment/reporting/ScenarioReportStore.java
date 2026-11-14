package assignment.reporting;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Suite-level result store shared by TestNG invocations and the report listener. */
public final class ScenarioReportStore {
    private static final List<ScenarioReportEntry> ENTRIES = new ArrayList<>();
    private static Path root;

    private ScenarioReportStore() {}

    public static synchronized void reset(Path reportRoot) {
        ENTRIES.clear();
        root = reportRoot;
    }

    public static synchronized void add(ScenarioReportEntry entry) {
        ENTRIES.add(entry);
    }

    public static synchronized List<ScenarioReportEntry> entries() {
        return ENTRIES.stream().sorted(Comparator.comparingInt(ScenarioReportEntry::order)).toList();
    }

    public static synchronized Path root() {
        if (root == null) throw new IllegalStateException("Report store has not been initialized");
        return root;
    }
}
