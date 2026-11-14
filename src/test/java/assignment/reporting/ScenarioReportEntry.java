package assignment.reporting;

import java.nio.file.Path;

/** Observed scenario result used to build the required four-column report. */
public record ScenarioReportEntry(
    int order,
    String scenarioName,
    String actual,
    String expected,
    boolean passed,
    boolean designedFailure,
    Path screenshotDirectory
) {}
