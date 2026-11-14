package assignment.tests;

import assignment.browser.BrowserSession;
import assignment.browser.RunPaths;
import assignment.data.StepDefinition;
import assignment.data.WorkbookData;
import assignment.reporting.ScenarioReportEntry;
import assignment.reporting.ScenarioReportStore;
import assignment.steps.ScenarioRunner;
import assignment.steps.ScreenshotManager;
import assignment.steps.StepEvidence;
import assignment.steps.StepExecutor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Executes the five assignment scenarios in one authenticated browser session. */
public class AssignmentScenarioTest {
    private WorkbookData workbook;
    private BrowserSession browser;
    private Path downloads;

    @BeforeClass
    public void startBrowser() throws Exception {
        workbook = WorkbookData.load(Path.of(System.getProperty("workbook")));
        var paths = new RunPaths(ScenarioReportStore.root());
        downloads = paths.freshDownloadDirectory("suite", "shared-browser-downloads");
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));
        // One browser preserves SSO after the single permitted Duo interaction.
        browser = BrowserSession.start(downloads, headless);
    }

    @AfterClass(alwaysRun = true)
    public void stopBrowser() {
        if (browser != null) browser.close();
    }

    @DataProvider(name = "scenarios", parallel = false)
    public Object[][] scenarios() throws Exception {
        if (workbook == null) workbook = WorkbookData.load(Path.of(System.getProperty("workbook")));
        List<Map<String, String>> scenarios = workbook.scenarios();
        Object[][] values = new Object[scenarios.size()][1];
        for (int index = 0; index < scenarios.size(); index++) values[index][0] = scenarios.get(index);
        return values;
    }

    @Test(dataProvider = "scenarios")
    public void runScenario(Map<String, String> scenario) throws Throwable {
        String id = scenario.get("scenario_id");
        int order = Integer.parseInt(scenario.get("execution_order"));
        String name = scenario.get("scenario_name");
        List<StepDefinition> steps = workbook.stepsFor(id);
        Map<String, String> data = workbook.dataValues();
        String expected = expectedSummary(steps, data);
        String actual = "Scenario did not start";
        boolean passed = false;
        Throwable failure = null;
        Path screenshots = null;

        try {
            var paths = new RunPaths(ScenarioReportStore.root());
            screenshots = paths.screenshotDirectory(id, name);
            var screenshotManager = new ScreenshotManager(browser.driver(), screenshots);
            var executor = new StepExecutor(browser.driver(), downloads, screenshotManager, data,
                workbook.locators(), data);
            List<StepEvidence> evidence = new ScenarioRunner(executor).run(steps);
            actual = actualSummary(evidence);
            passed = true;
        } catch (Throwable error) {
            failure = error;
            actual = failureSummary(error);
        } finally {
            // Record the observed result. intended_outcome never converts Scenario 4 into a pass.
            ScenarioReportStore.add(new ScenarioReportEntry(order, name, actual, expected, passed,
                "FAIL".equals(scenario.get("intended_outcome")), screenshots));
        }

        if (failure != null) throw failure;
    }

    private static String expectedSummary(List<StepDefinition> steps, Map<String, String> data) {
        var expected = new LinkedHashSet<String>();
        for (StepDefinition step : steps) {
            if (!step.expectedKey().isBlank()) expected.add(data.get(step.expectedKey()));
        }
        return expected.isEmpty() ? "All workbook-defined steps complete" : String.join(" | ", expected);
    }

    private static String actualSummary(List<StepEvidence> evidence) {
        var actual = new ArrayList<String>();
        for (StepEvidence step : evidence) {
            if (step.action().startsWith("ASSERT_") || step.action().startsWith("VERIFY_")) {
                actual.add(step.actual());
            }
        }
        return actual.isEmpty() ? "All " + evidence.size() + " steps completed" : String.join(" | ", actual);
    }

    private static String failureSummary(Throwable failure) {
        Throwable current = failure;
        while ((current.getMessage() == null || current.getMessage().isBlank()) && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
