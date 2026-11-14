package assignment.reporting;

import java.nio.file.Path;

public record ScenarioReportEntry(
    int order,
    String scenarioName,
    String actual,
    String expected,
    boolean passed,
    boolean designedFailure,
    Path screenshotDirectory
) {}
