package assignment.steps;

import assignment.data.LocatorDefinition;
import assignment.data.StepDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;

/** Executes workbook-defined steps and always attempts before/after screenshots. */
public final class StepExecutor {
    private static final List<String> PARTIAL_DOWNLOAD_SUFFIXES = List.of(".crdownload", ".part", ".tmp");
    private final WebDriver driver;
    private final Path downloadDirectory;
    private final ScreenshotManager screenshots;
    private final Map<String, String> data;
    private final Map<String, LocatorDefinition> locators;
    private final Map<String, String> placeholders;

    public StepExecutor(WebDriver driver, Path downloadDirectory, ScreenshotManager screenshots,
                        Map<String, String> data, Map<String, LocatorDefinition> locators,
                        Map<String, String> placeholders) {
        this.driver = driver;
        this.downloadDirectory = downloadDirectory;
        this.screenshots = screenshots;
        this.data = data;
        this.locators = locators;
        this.placeholders = placeholders;
    }

    public StepEvidence execute(StepDefinition step, SoftAssert softAssert) {
        Path before = null;
        Path after = null;
        Throwable failure = null;
        String actual = "";
        String expected = value(step.expectedKey());
        try {
            before = screenshots.capture(step.order(), step.action(), "before");
            actual = perform(step, expected, softAssert);
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                after = screenshots.capture(step.order(), step.action(), "after");
            } catch (Throwable screenshotError) {
                if (failure == null) failure = screenshotError;
                else failure.addSuppressed(screenshotError);
            }
        }
        if (failure != null) {
            if (failure instanceof AssertionError assertion) throw assertion;
            throw new StepExecutionException("Step %d %s failed".formatted(step.order(), step.action()), failure);
        }
        return new StepEvidence(step.order(), step.action(), actual, expected, before, after);
    }

    private String perform(StepDefinition step, String expected, SoftAssert softAssert) {
        return switch (step.action()) {
            case "OPEN" -> {
                String url = resolve(value(step.inputKey()));
                driver.navigate().to(url);
                yield driver.getCurrentUrl();
            }
            case "TYPE" -> {
                String input = resolve(value(step.inputKey()));
                WebElement element = visible(step);
                element.clear();
                element.sendKeys(input);
                yield element.getAttribute("value");
            }
            case "CLICK" -> {
                clickable(step).click();
                yield "Clicked " + step.locatorKey();
            }
            case "ASSERT_TEXT" -> {
                String text = visible(step).getText();
                Assert.assertEquals(text, resolve(expected), "Visible text mismatch");
                yield text;
            }
            case "VERIFY_TEXT" -> {
                String text = visible(step).getText();
                softAssert.assertEquals(text, resolve(expected), "Visible text mismatch");
                yield text;
            }
            case "ASSERT_DOWNLOAD" -> assertDownload(step, resolve(expected));
            default -> throw new IllegalArgumentException("Unsupported action: " + step.action());
        };
    }

    private String assertDownload(StepDefinition step, String expectedFilename) {
        Path expectedFile = downloadDirectory.resolve(expectedFilename).normalize();
        if (!expectedFile.startsWith(downloadDirectory.normalize())) {
            throw new IllegalArgumentException("Download filename must not leave its scenario directory");
        }
        var wait = new WebDriverWait(driver, Duration.ofSeconds(step.timeoutSeconds()));
        try {
            wait.until(ignored -> completeNonEmptyFile(expectedFile));
        } catch (RuntimeException timeout) {
            String status = observedStatus(step);
            Assert.fail("Expected downloaded file '%s'; observed status: %s".formatted(expectedFilename, status), timeout);
        }
        return expectedFile.toAbsolutePath().toString();
    }

    private String observedStatus(StepDefinition step) {
        try {
            return driver.findElement(by(step.locatorKey())).getText();
        } catch (RuntimeException missingStatus) {
            return "status element unavailable (" + missingStatus.getClass().getSimpleName() + ")";
        }
    }

    private boolean completeNonEmptyFile(Path expectedFile) {
        try {
            if (!Files.isRegularFile(expectedFile) || Files.size(expectedFile) == 0) return false;
            try (Stream<Path> files = Files.list(downloadDirectory)) {
                return files.noneMatch(path -> PARTIAL_DOWNLOAD_SUFFIXES.stream()
                    .anyMatch(suffix -> path.getFileName().toString().endsWith(suffix)));
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    private WebElement visible(StepDefinition step) {
        return wait(step).until(ExpectedConditions.visibilityOfElementLocated(by(step.locatorKey())));
    }

    private WebElement clickable(StepDefinition step) {
        return wait(step).until(ExpectedConditions.elementToBeClickable(by(step.locatorKey())));
    }

    private WebDriverWait wait(StepDefinition step) {
        return new WebDriverWait(driver, Duration.ofSeconds(step.timeoutSeconds()));
    }

    private By by(String key) {
        LocatorDefinition locator = locators.get(key);
        if (locator == null) throw new IllegalArgumentException("Unknown locator key: " + key);
        return locator.toBy();
    }

    private String value(String key) {
        if (key == null || key.isBlank()) return "";
        String value = data.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown data key: " + key);
        return value;
    }

    private String resolve(String value) {
        String resolved = value;
        for (var entry : placeholders.entrySet()) {
            resolved = resolved.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        if (resolved.matches(".*\\$\\{[^}]+}.*")) {
            throw new IllegalArgumentException("Unresolved placeholder in value: " + resolved);
        }
        return resolved;
    }
}
