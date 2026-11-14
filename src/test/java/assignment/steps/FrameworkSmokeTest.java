package assignment.steps;

import assignment.browser.BrowserSession;
import assignment.data.LocatorDefinition;
import assignment.data.StepDefinition;
import assignment.data.WorkbookData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Browser smoke checks for framework mechanics; these are not assignment scenario results. */
public class FrameworkSmokeTest {
    private BrowserSession browser;
    private Path root;
    private Path fixture;
    private WorkbookData workbook;

    @BeforeClass
    public void startBrowser() throws Exception {
        workbook = WorkbookData.load(Path.of(System.getProperty("workbook")));
        root = Files.createTempDirectory(Path.of("target"), "phase3-smoke-");
        Path downloads = root.resolve("downloads");
        Map<String, String> inputs = workbook.dataValues();
        String displayedHeading = inputs.get("demo_heading");
        var weather = workbook.rows("DemoData").stream()
            .filter(row -> row.get("dataset_id").equals(inputs.get("search_first"))).findFirst().orElseThrow();
        fixture = root.resolve("fixture.html");
        Files.writeString(fixture, """
            <!doctype html><html><body>
            <h1 data-testid="catalog-heading">%s</h1>
            <input data-testid="search-input">
            <button data-testid="search-submit" type="button">Search</button>
            <h2 data-testid="result-title"></h2>
            <p data-testid="result-description"></p>
            <script>
              document.querySelector('[data-testid="search-submit"]').addEventListener('click', () => {
                const query = document.querySelector('[data-testid="search-input"]').value;
                if (query === '%s') {
                  document.querySelector('[data-testid="result-title"]').textContent = '%s';
                  document.querySelector('[data-testid="result-description"]').textContent = '%s';
                }
              });
            </script>
            </body></html>
            """.formatted(escapeHtml(displayedHeading), escapeJavaScript(inputs.get("search_first")),
                escapeJavaScript(weather.get("title")), escapeJavaScript(weather.get("description"))),
            StandardCharsets.UTF_8);
        browser = BrowserSession.start(downloads, true);
    }

    @AfterClass(alwaysRun = true)
    public void stopBrowser() {
        if (browser != null) browser.close();
    }

    @Test
    public void executesExcelBackedHardAssertionWithBeforeAndAfterScreenshots() throws Exception {
        Map<String, String> data = withFixtureUrl(workbook.dataValues());
        Path screenshots = root.resolve("successful-step-screenshots");
        var runner = runner(data, screenshots);
        List<StepEvidence> evidence = runner.run(workbook.stepsFor("S02"));
        Assert.assertEquals(evidence.size(), 5);
        Assert.assertEquals(countPngs(screenshots), 10);
        Assert.assertEquals(evidence.get(3).actual(), data.get("dataset_first_title"));
    }

    @Test(dependsOnMethods = "executesExcelBackedHardAssertionWithBeforeAndAfterScreenshots")
    public void hardAssertionFailureStillCapturesAfterScreenshot() throws Exception {
        Map<String, String> data = new LinkedHashMap<>(withFixtureUrl(workbook.dataValues()));
        data.put("wrong_heading", "Deliberately wrong framework-smoke expectation");
        Path screenshots = root.resolve("hard-failure-screenshots");
        browser.driver().navigate().to(fixture.toUri().toString());
        var runner = runner(Map.copyOf(data), screenshots);
        Assert.expectThrows(AssertionError.class, () -> runner.run(List.of(
            new StepDefinition("SMOKE", 1, "ASSERT_TEXT", "catalog_heading", "", "wrong_heading", 5)
        )));
        Assert.assertEquals(countPngs(screenshots), 2);
    }

    @Test(dependsOnMethods = "hardAssertionFailureStillCapturesAfterScreenshot")
    public void softVerificationIsCollectedUntilScenarioEnd() throws Exception {
        Map<String, String> data = new LinkedHashMap<>(withFixtureUrl(workbook.dataValues()));
        data.put("wrong_heading", "Deliberately wrong framework-smoke expectation");
        Path screenshots = root.resolve("soft-failure-screenshots");
        browser.driver().navigate().to(fixture.toUri().toString());
        var runner = runner(Map.copyOf(data), screenshots);
        AssertionError error = Assert.expectThrows(AssertionError.class, () -> runner.run(List.of(
            new StepDefinition("SMOKE", 1, "VERIFY_TEXT", "catalog_heading", "", "wrong_heading", 5)
        )));
        Assert.assertTrue(error.getMessage().contains("Visible text mismatch"));
        Assert.assertEquals(countPngs(screenshots), 2);
    }

    private ScenarioRunner runner(Map<String, String> data, Path screenshots) throws Exception {
        var manager = new ScreenshotManager(browser.driver(), screenshots);
        var executor = new StepExecutor(browser.driver(), browser.downloadDirectory(), manager,
            data, workbook.locators(), Map.of());
        return new ScenarioRunner(executor);
    }

    private Map<String, String> withFixtureUrl(Map<String, String> original) {
        var data = new LinkedHashMap<>(original);
        data.put("fixture_url", fixture.toUri().toString());
        data.put("catalog_url", fixture.toUri().toString());
        return Map.copyOf(data);
    }

    private static long countPngs(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".png")).count();
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeJavaScript(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r")
            .replace("\n", "\\n").replace("</", "<\\/");
    }
}
