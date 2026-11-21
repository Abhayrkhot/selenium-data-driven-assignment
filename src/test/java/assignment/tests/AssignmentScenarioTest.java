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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebDriver;
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
        Set<String> selected = selectedScenarioIds();
        if (!selected.isEmpty()) {
            // Calibration runs can exercise only authenticated pages while preserving workbook order.
            scenarios = scenarios.stream().filter(row -> selected.contains(row.get("scenario_id"))).toList();
            if (scenarios.isEmpty()) throw new IllegalArgumentException("scenarioIds did not match any workbook scenario");
        }
        Object[][] values = new Object[scenarios.size()][1];
        for (int index = 0; index < scenarios.size(); index++) values[index][0] = scenarios.get(index);
        return values;
    }

    private static Set<String> selectedScenarioIds() {
        String configured = System.getProperty("scenarioIds", "").trim();
        if (configured.isEmpty()) return Set.of();
        return Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
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
            useOpenWindow(browser.driver());
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

    private static void useOpenWindow(WebDriver driver) {
        // A failed scenario can leave the driver on a tab Chrome has since closed;
        // the next scenario must still start in a live tab.
        try {
            driver.getWindowHandle();
        } catch (NoSuchWindowException closed) {
            List<String> open = new ArrayList<>(driver.getWindowHandles());
            if (open.isEmpty()) throw new IllegalStateException("All browser windows were closed before this scenario started", closed);
            driver.switchTo().window(open.get(open.size() - 1));
        }
        driver.switchTo().defaultContent();
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
            // Checks, how the PDF was saved, and any click that needed the JavaScript fallback
            // all appear in the report's Actual column.
            if (step.action().startsWith("ASSERT_") || step.action().startsWith("VERIFY_")
                    || "PRINT_TO_PDF".equals(step.action())) {
                actual.add(step.actual());
            } else if (step.actual().contains(StepExecutor.JS_CLICK_NOTE)) {
                actual.add("Step " + step.order() + ": " + step.actual());
            }
        }
        return actual.isEmpty() ? "All " + evidence.size() + " steps completed" : String.join(" | ", actual);
    }

    private static String failureSummary(Throwable failure) {
        // Show the whole chain ("Step 16 PRINT_TO_PDF failed: Chrome's print dialog did not save ...")
        // so the report says why a step failed, not just which one.
        var messages = new ArrayList<String>();
        for (Throwable current = failure; current != null && messages.size() < 3; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null || message.isBlank()) continue;
            String firstLine = message.lines().findFirst().orElse(message).trim();
            if (messages.stream().noneMatch(m -> m.contains(firstLine))) messages.add(firstLine);
        }
        return messages.isEmpty() ? failure.getClass().getSimpleName() : String.join(": ", messages);
    }
}
