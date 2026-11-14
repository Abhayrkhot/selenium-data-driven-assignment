package assignment.steps;

import assignment.data.LocatorDefinition;
import assignment.data.StepDefinition;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PrintsPage;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.print.PrintOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
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
            case "CLICK_AND_SWITCH" -> clickAndSwitch(step);
            case "CLICK_AND_SWITCH_IF_OPENED" -> clickAndSwitchIfOpened(step);
            case "SELECT_TEXT" -> {
                String option = resolve(value(step.inputKey()));
                new Select(visible(step)).selectByVisibleText(option);
                yield option;
            }
            case "SCROLL_INTO_VIEW" -> {
                WebElement element = visible(step);
                ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
                yield "Scrolled to " + step.locatorKey();
            }
            case "CONTEXT_CLICK" -> {
                WebElement element = visible(step);
                new Actions(driver).contextClick(element).perform();
                yield "Right-clicked " + step.locatorKey();
            }
            case "SET_CHECKBOX" -> {
                WebElement checkbox = visible(step);
                boolean desired = Boolean.parseBoolean(resolve(value(step.inputKey())));
                if (checkbox.isSelected() != desired) checkbox.click();
                Assert.assertEquals(checkbox.isSelected(), desired, "Checkbox state mismatch");
                yield Boolean.toString(checkbox.isSelected());
            }
            case "SWITCH_TO_IFRAME" -> {
                wait(step).until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(by(step.locatorKey())));
                yield "Switched to " + step.locatorKey();
            }
            case "SWITCH_TO_DEFAULT_CONTENT" -> {
                driver.switchTo().defaultContent();
                yield "Switched to default content";
            }
            case "SWITCH_TO_NEW_WINDOW" -> switchToNewWindow(step);
            case "WAIT_FOR_AUTHENTICATION" -> waitForAuthentication(step);
            case "PRINT_TO_PDF" -> printToPdf(step);
            case "ASSERT_VISIBLE" -> {
                WebElement element = visible(step);
                Assert.assertTrue(element.isDisplayed(), "Element is not visible: " + step.locatorKey());
                yield element.getText().isBlank() ? "Visible: " + step.locatorKey() : element.getText();
            }
            case "VERIFY_VISIBLE" -> {
                boolean displayed = visible(step).isDisplayed();
                softAssert.assertTrue(displayed, "Element is not visible: " + step.locatorKey());
                yield Boolean.toString(displayed);
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
            case "ASSERT_URL_CONTAINS" -> {
                String current = driver.getCurrentUrl();
                Assert.assertTrue(current.contains(resolve(expected)),
                    "URL did not contain expected text. Actual: " + current);
                yield current;
            }
            case "ASSERT_DOWNLOAD" -> assertDownload(step, resolve(expected));
            default -> throw new IllegalArgumentException("Unsupported action: " + step.action());
        };
    }

    private String switchToNewWindow(StepDefinition step) {
        String original = driver.getWindowHandle();
        Set<String> originalOnly = Set.of(original);
        wait(step).until(ignored -> driver.getWindowHandles().stream().anyMatch(handle -> !handle.equals(original)));
        return driver.getWindowHandles().stream().filter(handle -> !originalOnly.contains(handle)).findFirst()
            .map(handle -> {
                driver.switchTo().window(handle);
                return "Switched from " + original + " to new window";
            }).orElseThrow(() -> new IllegalStateException("No new browser window appeared"));
    }

    private String clickAndSwitch(StepDefinition step) {
        Set<String> before = driver.getWindowHandles();
        clickable(step).click();
        String newHandle = wait(step).until(ignored -> driver.getWindowHandles().stream()
            .filter(handle -> !before.contains(handle)).findFirst().orElse(null));
        driver.switchTo().window(newHandle);
        return "Clicked " + step.locatorKey() + " and switched to its new window";
    }

    private String clickAndSwitchIfOpened(StepDefinition step) {
        Set<String> before = driver.getWindowHandles();
        clickable(step).click();
        try {
            String newHandle = new WebDriverWait(driver, Duration.ofSeconds(5)).until(ignored ->
                driver.getWindowHandles().stream().filter(handle -> !before.contains(handle))
                    .findFirst().orElse(null));
            driver.switchTo().window(newHandle);
            return "Clicked " + step.locatorKey() + " and switched to its new window";
        } catch (RuntimeException noNewWindow) {
            return "Clicked " + step.locatorKey() + " in the current window";
        }
    }

    private String waitForAuthentication(StepDefinition step) {
        String authenticatedUrl = resolve(value("student_hub_url"));
        String expectedHost = URI.create(authenticatedUrl).getHost();
        // Duo remains the only manual interaction. The redirect is a more reliable signal than
        // forwarding terminal input through Maven and works during a live presentation.
        wait(step).until(ignored -> expectedHost.equalsIgnoreCase(
            URI.create(driver.getCurrentUrl()).getHost()));
        return "Authenticated at " + driver.getCurrentUrl();
    }

    private String printToPdf(StepDefinition step) {
        // Selenium cannot drive Chrome's operating-system print dialog. Its print endpoint
        // preserves the assignment's PDF result after the preceding right-click step.
        if (!(driver instanceof PrintsPage printable)) {
            throw new WebDriverException("The active browser does not support Selenium print-to-PDF");
        }
        String filename = resolve(value(step.inputKey()));
        Path output = downloadDirectory.resolve(filename).normalize();
        if (!output.startsWith(downloadDirectory.normalize())) {
            throw new IllegalArgumentException("PDF filename must stay inside the download directory");
        }
        try {
            Files.write(output, Base64.getDecoder().decode(
                printable.print(new PrintOptions()).getContent()));
            return output.toAbsolutePath().toString();
        } catch (IOException error) {
            throw new IllegalStateException("Could not save printed transcript PDF", error);
        }
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
        return locator.toBy(resolve(locator.selector()));
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
