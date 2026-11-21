package assignment.reporting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/** Writes the assignment-specific report even when a scenario fails. */
public final class AssignmentReportListener implements ISuiteListener {
    private static final Path REPORT_ROOT = Path.of("target", "assignment-report");

    @Override public void onStart(ISuite suite) {
        ScenarioReportStore.reset(REPORT_ROOT);
    }

    @Override public void onFinish(ISuite suite) {
        try {
            Files.createDirectories(REPORT_ROOT);
            List<ScenarioReportEntry> entries = ScenarioReportStore.entries();
            long passed = entries.stream().filter(ScenarioReportEntry::passed).count();
            StringBuilder rows = new StringBuilder();
            for (ScenarioReportEntry entry : entries) rows.append(row(entry));
            String body = """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Selenium assignment report</title><style>%s</style></head><body><main>
                <h1>Selenium assignment report</h1>
                <p class="summary">Executed %d scenarios: %d passed and %d failed.</p>
                <p>Scenario 4 is designed to fail. Its failure remains a failure. Scenario 5 demonstrates continuation.</p>
                <table><thead><tr><th>Test scenario name</th><th>Actual</th><th>Expected</th><th>Pass/Fail</th></tr></thead>
                <tbody>%s</tbody></table>
                </main></body></html>
                """.formatted(styles(), entries.size(), passed, entries.size() - passed, rows);
            Files.writeString(REPORT_ROOT.resolve("report.html"), body, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write assignment HTML report", error);
        }
    }

    private String row(ScenarioReportEntry entry) throws IOException {
        String status = entry.passed() ? "PASS" : "FAIL";
        String statusClass = entry.passed() ? "pass" : "fail";
        String designed = entry.designedFailure() ? "<div class=\"note\">Designed failure</div>" : "";
        String evidence = screenshotLinks(entry.screenshotDirectory());
        return "<tr><td>" + html(entry.scenarioName()) + "</td><td>" + html(entry.actual())
            + "<div class=\"evidence\">" + evidence + "</div>"
            + "</td><td>" + html(entry.expected()) + "</td><td class=\"" + statusClass + "\">"
            + status + designed + "</td></tr>";
    }

    private String screenshotLinks(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) return "No screenshots captured";
        try (var files = Files.list(directory)) {
            List<Path> screenshots = files.filter(path -> path.getFileName().toString().endsWith(".png"))
                .sorted(Comparator.comparing(AssignmentReportListener::screenshotSortKey)).toList();
            if (screenshots.isEmpty()) return "No screenshots captured";
            StringBuilder links = new StringBuilder();
            for (Path screenshot : screenshots) {
                String href = REPORT_ROOT.relativize(screenshot).toString().replace('\\', '/');
                if (!links.isEmpty()) links.append(' ');
                links.append("<a href=\"").append(html(href)).append("\">Screenshot ")
                    .append(html(screenshot.getFileName().toString())).append("</a>");
            }
            return links.toString();
        }
    }

    private static String screenshotSortKey(Path path) {
        return path.getFileName().toString().replace("-before.png", "-0-before.png")
            .replace("-after.png", "-1-after.png");
    }

    private static String styles() {
        return "body{font:15px system-ui;background:#f4f7fb;color:#172033;margin:0}main{max-width:1400px;margin:40px auto;padding:0 24px}h1{margin-bottom:8px}.summary{font-weight:700}table{width:100%;border-collapse:collapse;background:white;box-shadow:0 6px 24px #18315318}th{background:#243b53;color:white;text-align:left}th,td{padding:14px;border-bottom:1px solid #d9e2ec;vertical-align:top}td:nth-child(1){font-weight:700;min-width:180px}td:nth-child(2),td:nth-child(3){max-width:420px;word-break:break-word}.pass{color:#16794b;font-weight:800}.fail{color:#b42318;font-weight:800}.note{font-size:12px;font-weight:500;margin-top:4px}.evidence{margin-top:8px}a{display:inline-block;margin:2px;color:#1f5d99;font-size:12px}";
    }

    private static String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
