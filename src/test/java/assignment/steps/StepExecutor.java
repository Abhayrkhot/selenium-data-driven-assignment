package assignment.steps;

import assignment.data.LocatorDefinition;
import assignment.data.StepDefinition;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NotFoundException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.PrintsPage;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
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
    private static final String SIGN_IN_PROMPTS = "post_duo_prompts";
    private final WebDriver driver;
    private final Path downloadDirectory;
    private final ScreenshotManager screenshots;
    private final Map<String, String> data;
    private final Map<String, LocatorDefinition> locators;
    private final Map<String, String> placeholders;
    private String activeFrameKey;
    private final List<String> answeredPrompts = new ArrayList<>();

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
                activeFrameKey = null;
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
            case "CHOOSE_OPTION" -> {
                String option = resolve(value(step.inputKey()));
                clickable(step).click();
                // Canvas (InstUI) dropdowns render their options in a separate listbox popup.
                By choice = By.xpath("//*[@role='option'][normalize-space(.)=" + xpathLiteral(option) + "]");
                wait(step).until(ignored -> driver.findElements(choice).stream()
                    .filter(WebElement::isDisplayed).findFirst().orElse(null)).click();
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
                // Keep Chrome's context menu visible briefly so the presentation shows
                // the required right-click before the PDF is generated.
                new Actions(driver).contextClick(element).pause(Duration.ofSeconds(2)).perform();
                yield "Right-clicked " + step.locatorKey();
            }
            case "SET_CHECKBOX" -> {
                WebElement checkbox = present(step);
                boolean desired = Boolean.parseBoolean(resolve(value(step.inputKey())));
                if (checkboxState(checkbox) != desired) {
                    // Trumba visually replaces its native checkbox, leaving the real input hidden.
                    if (checkbox.isDisplayed()) checkbox.click();
                    else ((JavascriptExecutor) driver).executeScript("arguments[0].click();", checkbox);
                }
                wait(step).until(ignored -> checkboxState(present(step)) == desired);
                Assert.assertEquals(checkboxState(present(step)), desired, "Checkbox state mismatch");
                yield Boolean.toString(desired);
            }
            case "SWITCH_TO_IFRAME" -> {
                wait(step).until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(by(step.locatorKey())));
                activeFrameKey = step.locatorKey();
                yield "Switched to " + step.locatorKey();
            }
            case "SWITCH_TO_DEFAULT_CONTENT" -> {
                activeFrameKey = null;
                driver.switchTo().defaultContent();
                yield "Switched to default content";
            }
            case "SWITCH_TO_NEW_WINDOW" -> switchToNewWindow(step);
            case "WAIT_FOR_AUTHENTICATION" -> waitForAuthentication(step);
            case "WAIT_FOR_LOADING_COMPLETE" -> waitForLoadingComplete(step);
            case "PRINT_TO_PDF" -> printToPdf(step);
            case "ASSERT_VISIBLE" -> {
                WebElement element = visible(step);
                Assert.assertTrue(element.isDisplayed(), "Element is not visible: " + step.locatorKey());
                String text = shownText(element);
                yield text.isBlank() ? "Visible: " + step.locatorKey() : text;
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
                switchToWindow(handle);
                return "Switched from " + original + " to new window";
            }).orElseThrow(() -> new IllegalStateException("No new browser window appeared"));
    }

    private void switchToWindow(String handle) {
        activeFrameKey = null;
        driver.switchTo().window(handle);
    }

    private String clickAndSwitch(StepDefinition step) {
        Set<String> before = driver.getWindowHandles();
        clickable(step).click();
        String newHandle = wait(step).until(ignored -> driver.getWindowHandles().stream()
            .filter(handle -> !before.contains(handle)).findFirst().orElse(null));
        switchToWindow(newHandle);
        return "Clicked " + step.locatorKey() + " and switched to its new window";
    }

    private String clickAndSwitchIfOpened(StepDefinition step) {
        Set<String> before = driver.getWindowHandles();
        clickable(step).click();
        try {
            String newHandle = new WebDriverWait(driver, Duration.ofSeconds(5)).until(ignored ->
                driver.getWindowHandles().stream().filter(handle -> !before.contains(handle))
                    .findFirst().orElse(null));
            switchToWindow(newHandle);
            return "Clicked " + step.locatorKey() + " and switched to its new window";
        } catch (RuntimeException noNewWindow) {
            return "Clicked " + step.locatorKey() + " in the current window";
        }
    }

    private String waitForAuthentication(StepDefinition step) {
        String authenticatedUrl = resolve(value("student_hub_url"));
        String expectedHost = URI.create(authenticatedUrl).getHost();
        // Duo approval on the phone remains the only manual interaction. The redirect is a more
        // reliable signal than forwarding terminal input through Maven during a live presentation.
        wait(step).until(ignored -> {
            if (expectedHost.equalsIgnoreCase(URI.create(driver.getCurrentUrl()).getHost())) return true;
            answerSignInPrompt();
            return false;
        });
        return "Authenticated at " + driver.getCurrentUrl()
            + (answeredPrompts.isEmpty() ? "" : "; answered prompts: " + String.join(", ", answeredPrompts));
    }

    private void answerSignInPrompt() {
        // Duo's "trust this browser" and Microsoft's "stay signed in" can appear after any SSO
        // redirect (login, Banner, Canvas). They come and go as redirects run, so a lookup or
        // click may race a navigation; the caller's next poll simply looks again.
        if (activeFrameKey != null || !locators.containsKey(SIGN_IN_PROMPTS)) return;
        try {
            for (WebElement prompt : driver.findElements(by(SIGN_IN_PROMPTS))) {
                if (!prompt.isDisplayed() || !prompt.isEnabled()) continue;
                String label = prompt.getText().isBlank() ? prompt.getAttribute("value") : prompt.getText();
                prompt.click();
                answeredPrompts.add("'" + label.trim() + "'");
                return;
            }
        } catch (WebDriverException transientDuringRedirect) {
            // The page changed under us.
        }
    }

    private String waitForLoadingComplete(StepDefinition step) {
        // Banner submits the transcript as soon as the second custom dropdown is selected.
        // Wait until its full-screen loading mask is gone before using the transcript page.
        wait(step).until(ignored -> {
            Object ready = ((JavascriptExecutor) driver).executeScript("return document.readyState");
            boolean loading = driver.findElements(By.cssSelector(
                    "[role='progressbar'], [aria-busy='true'], [class*='spinner'], [class*='loading']"))
                .stream().anyMatch(WebElement::isDisplayed);
            return "complete".equals(ready) && !loading;
        });
        return "Page loading completed at " + driver.getCurrentUrl();
    }

    private String printToPdf(StepDefinition step) {
        String filename = resolve(value(step.inputKey()));
        Path output = downloadDirectory.resolve(filename).normalize();
        if (!output.startsWith(downloadDirectory.normalize())) {
            throw new IllegalArgumentException("PDF filename must stay inside the download directory");
        }
        try {
            if (isVisibleRun()) savePdfThroughChromeUi(output, step.timeoutSeconds());
            else savePdfThroughSelenium(output);

            Path desktopCopy = copyToDesktop(output);

            // Display the saved result in Chrome. This gives the presenter and the
            // after-step screenshot visible evidence that the transcript became a PDF.
            driver.switchTo().newWindow(WindowType.TAB);
            driver.navigate().to(desktopCopy.toUri().toString());
            new WebDriverWait(driver, Duration.ofSeconds(step.timeoutSeconds())).until(ignored ->
                driver.getCurrentUrl().startsWith("file:")
                    && "complete".equals(((JavascriptExecutor) driver)
                        .executeScript("return document.readyState")));
            return "Saved report copy at %s; copied and opened %s"
                .formatted(output.toAbsolutePath(), desktopCopy);
        } catch (IOException error) {
            throw new IllegalStateException("Could not save printed transcript PDF", error);
        }
    }

    private boolean isVisibleRun() {
        return !Boolean.parseBoolean(System.getProperty("headless", "false"));
    }

    private void savePdfThroughChromeUi(Path output, int timeoutSeconds) throws IOException {
        try {
            Robot keyboard = new Robot();
            keyboard.setAutoDelay(150);

            // CONTEXT_CLICK provides the required visible right-click. Triggering print
            // from the page avoids operating-system-specific context-menu mnemonics while
            // still showing Chrome's browser-owned print preview to the presenter.
            ((JavascriptExecutor) driver).executeScript(
                "setTimeout(() => window.print(), 250); return true;");
            keyboard.delay(5000);

            // BrowserSession presets Save as PDF as the preview destination. Enter opens
            // the operating system's save dialog, whose filename field is selected by default.
            press(keyboard, KeyEvent.VK_ENTER);
            keyboard.delay(2000);
            chord(keyboard, selectAllModifier(), KeyEvent.VK_A);
            type(keyboard, output.getFileName().toString());
            press(keyboard, KeyEvent.VK_ENTER);

            waitForFile(output, timeoutSeconds);
        } catch (AWTException error) {
            throw new IllegalStateException(
                "Could not control Chrome's visible print dialog. Allow Java to send keyboard input on this computer.",
                error);
        }
    }

    private static int selectAllModifier() {
        int menuShortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        return (menuShortcut & InputEvent.META_DOWN_MASK) != 0
            ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
    }

    private void savePdfThroughSelenium(Path output) throws IOException {
        if (!(driver instanceof PrintsPage printable)) {
            throw new WebDriverException("The active browser does not support Selenium print-to-PDF");
        }
        Files.write(output, Base64.getDecoder().decode(
            printable.print(new PrintOptions()).getContent()));
    }

    private Path copyToDesktop(Path output) throws IOException {
        String configured = System.getProperty("transcriptOutputDirectory", "").trim();
        Path desktopDirectory = configured.isEmpty()
            ? Path.of(System.getProperty("user.home"), "Desktop", "Selenium")
            : Path.of(configured);
        Files.createDirectories(desktopDirectory);
        Path desktopCopy = desktopDirectory.toAbsolutePath().normalize()
            .resolve(output.getFileName()).normalize();
        if (!desktopCopy.startsWith(desktopDirectory.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("PDF filename must stay inside the Desktop output directory");
        }
        Files.copy(output, desktopCopy, StandardCopyOption.REPLACE_EXISTING);
        return desktopCopy;
    }

    private void waitForFile(Path output, int timeoutSeconds) throws IOException {
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(output) && Files.size(output) > 0) return;
            try {
                Thread.sleep(250);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the PDF save dialog", error);
            }
        }
        throw new IOException("Chrome did not save the PDF within " + timeoutSeconds + " seconds: " + output);
    }

    private static void press(Robot keyboard, int key) {
        keyboard.keyPress(key);
        keyboard.keyRelease(key);
    }

    private static void chord(Robot keyboard, int modifier, int key) {
        keyboard.keyPress(modifier);
        keyboard.keyPress(key);
        keyboard.keyRelease(key);
        keyboard.keyRelease(modifier);
    }

    private static void type(Robot keyboard, String text) {
        for (char character : text.toCharArray()) {
            int key = KeyEvent.getExtendedKeyCodeForChar(character);
            if (key == KeyEvent.VK_UNDEFINED) {
                throw new IllegalArgumentException("Unsupported PDF filename character: " + character);
            }
            press(keyboard, key);
        }
    }

    private String assertDownload(StepDefinition step, String expectedFilename) {
        Path expectedFile = downloadDirectory.resolve(expectedFilename).normalize();
        if (!expectedFile.startsWith(downloadDirectory.normalize())) {
            throw new IllegalArgumentException("Download filename must not leave its scenario directory");
        }
        FileTime started = FileTime.from(Instant.now());
        var wait = new WebDriverWait(driver, Duration.ofSeconds(step.timeoutSeconds()));
        try {
            // Stop once the expected file arrives or every download started meanwhile has
            // finished, so a wrong filename fails as soon as the real file is on disk.
            wait.until(ignored -> completeNonEmptyFile(expectedFile) || downloadsSettledSince(started));
        } catch (RuntimeException stillDownloading) {
            // Fall through: the listing below shows any partial download.
        }
        if (!completeNonEmptyFile(expectedFile)) {
            Assert.fail("Expected downloaded file '%s'; downloads folder has: %s"
                .formatted(expectedFilename, downloadedFiles()));
        }
        return expectedFile.toAbsolutePath().toString();
    }

    private boolean downloadsSettledSince(FileTime started) {
        try (Stream<Path> files = Files.list(downloadDirectory)) {
            List<Path> all = files.toList();
            boolean partial = all.stream().anyMatch(path -> PARTIAL_DOWNLOAD_SUFFIXES.stream()
                .anyMatch(suffix -> path.getFileName().toString().endsWith(suffix)));
            return !partial && all.stream().anyMatch(path -> modifiedTime(path).compareTo(started) > 0);
        } catch (IOException ignored) {
            return false;
        }
    }

    private String downloadedFiles() {
        try (Stream<Path> files = Files.list(downloadDirectory)) {
            List<String> listing = files
                .sorted(Comparator.comparing(StepExecutor::modifiedTime).reversed())
                .map(path -> "%s (%s)".formatted(path.getFileName(), readableSize(path)))
                .toList();
            return listing.isEmpty() ? "no files" : String.join(", ", listing);
        } catch (IOException error) {
            return "unreadable (" + error.getMessage() + ")";
        }
    }

    private static FileTime modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException error) {
            return FileTime.fromMillis(0);
        }
    }

    private static String readableSize(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
            return "%.1f MB".formatted(bytes / (1024.0 * 1024));
        } catch (IOException error) {
            return "size unknown";
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
        // React pages often keep hidden copies of controls in the DOM. Select the first
        // currently displayed match instead of letting Selenium stop at a hidden copy.
        return firstMatch(step, WebElement::isDisplayed);
    }

    private WebElement present(StepDefinition step) {
        return firstMatch(step, element -> true);
    }

    private WebElement firstMatch(StepDefinition step, Predicate<WebElement> usable) {
        return wait(step).until(ignored -> {
            try {
                var match = driver.findElements(by(step.locatorKey())).stream().filter(usable).findFirst();
                if (match.isPresent()) return match.get();
            } catch (NotFoundException | StaleElementReferenceException detached) {
                if (activeFrameKey == null) throw detached;
            }
            answerSignInPrompt();
            reenterActiveFrame();
            return null;
        });
    }

    private void reenterActiveFrame() {
        // Trumba replaces its iframe elements when a filter changes, which strands the
        // driver in a detached frame; follow the replacement by locating the frame again.
        if (activeFrameKey == null) return;
        driver.switchTo().defaultContent();
        driver.switchTo().frame(driver.findElement(by(activeFrameKey)));
    }

    private boolean checkboxState(WebElement element) {
        String checked = element.getAttribute("aria-checked");
        if (checked != null) return "true".equals(checked);
        return element.isSelected();
    }

    private WebElement clickable(StepDefinition step) {
        // A locator may intentionally cover desktop/mobile variants. Only return a control
        // that a user could click in the active layout.
        return firstMatch(step, element -> element.isDisplayed() && element.isEnabled());
    }

    private static String shownText(WebElement element) {
        // Selenium returns every option's text for a dropdown; a viewer only sees the selected one.
        String text = "select".equalsIgnoreCase(element.getTagName())
            ? new Select(element).getFirstSelectedOption().getText()
            : element.getText();
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String xpathLiteral(String text) {
        if (!text.contains("'")) return "'" + text + "'";
        if (!text.contains("\"")) return "\"" + text + "\"";
        return "concat('" + text.replace("'", "',\"'\",'") + "')";
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
