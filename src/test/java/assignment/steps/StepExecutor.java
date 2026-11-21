package assignment.steps;

import assignment.data.LocatorDefinition;
import assignment.data.StepDefinition;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
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
import org.openqa.selenium.ElementClickInterceptedException;
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
    private static final String DROPDOWN_OPTION = "dropdown_option";
    private final WebDriver driver;
    private final Path downloadDirectory;
    private final ScreenshotManager screenshots;
    private final Map<String, String> data;
    private final Map<String, LocatorDefinition> locators;
    private final Map<String, String> placeholders;
    private String activeFrameKey;
    // Set when safeClick had to fall back to a JavaScript click during the current step.
    private boolean javaScriptClickUsed;
    public static final String JS_CLICK_NOTE = "(real click was intercepted; clicked with JavaScript instead)";
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
        javaScriptClickUsed = false;
        try {
            before = screenshots.capture(step.order(), step.action(), "before");
            actual = perform(step, expected, softAssert);
            if (javaScriptClickUsed) actual += " " + JS_CLICK_NOTE;
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
                safeClick(clickable(step));
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
                safeClick(clickable(step));
                // Canvas (InstUI) dropdowns render their options in a separate listbox popup.
                By choice = optionLocator(option);
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
        safeClick(clickable(step));
        String newHandle = wait(step).until(ignored -> driver.getWindowHandles().stream()
            .filter(handle -> !before.contains(handle)).findFirst().orElse(null));
        switchToWindow(newHandle);
        return "Clicked " + step.locatorKey() + " and switched to its new window";
    }

    private String clickAndSwitchIfOpened(StepDefinition step) {
        Set<String> before = driver.getWindowHandles();
        safeClick(clickable(step));
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
        // Pages such as Banner and LibCal keep working after the click that triggered them.
        // Wait until none of the workbook's loading indicators is still displayed.
        wait(step).until(ignored -> {
            Object ready = ((JavascriptExecutor) driver).executeScript("return document.readyState");
            boolean loading = driver.findElements(by(step.locatorKey()))
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
            String method;
            if (isVisibleRun()) {
                try {
                    savePdfThroughChromeUi(output, step.timeoutSeconds());
                    method = isWindows() ? "Chrome print (Save as PDF, kiosk printing on Windows)" : "Chrome print dialog";
                } catch (IOException | IllegalStateException uiSaveFailed) {
                    // No silent fallback: if the real print dialog did not save the PDF, the step fails
                    // and the report says so. Close the dialogs first so the next scenario can still run.
                    closeOpenDialogs();
                    throw new IllegalStateException("Chrome's print dialog did not save the PDF: "
                        + uiSaveFailed.getMessage(), uiSaveFailed);
                }
            } else {
                // Headless runs have no print dialog to drive, so they use Selenium's print API.
                savePdfThroughSelenium(output);
                method = "Selenium print API (headless run)";
            }

            Path desktopCopy = copyToDesktop(output);

            // Display the saved result in Chrome. This gives the presenter and the
            // after-step screenshot visible evidence that the transcript became a PDF.
            driver.switchTo().newWindow(WindowType.TAB);
            driver.navigate().to(desktopCopy.toUri().toString());
            new WebDriverWait(driver, Duration.ofSeconds(step.timeoutSeconds())).until(ignored ->
                driver.getCurrentUrl().startsWith("file:")
                    && "complete".equals(((JavascriptExecutor) driver)
                        .executeScript("return document.readyState")));
            return "Saved PDF via %s at %s; copied and opened %s"
                .formatted(method, output.toAbsolutePath(), desktopCopy);
        } catch (IOException error) {
            throw new IllegalStateException("Could not save printed transcript PDF", error);
        }
    }

    private boolean isVisibleRun() {
        return !Boolean.parseBoolean(System.getProperty("headless", "false"));
    }

    private void savePdfThroughChromeUi(Path output, int timeoutSeconds) throws IOException {
        if (isWindows()) {
            // On Windows, Chrome started by the test often cannot take keyboard focus from the
            // terminal, so keystrokes meant for the print dialog land in another window.
            // BrowserSession starts Chrome with kiosk printing instead: window.print() saves
            // straight to the preset "Save as PDF" destination with Chrome's own print engine.
            savePdfWithKioskPrinting(output, timeoutSeconds);
            return;
        }
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
            if (isWindows()) {
                // Windows' Save As dialog reopens the last folder it used, so a bare filename can
                // land anywhere. Pasting the full path saves exactly where the test looks.
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(output.toAbsolutePath().toString()), null);
                keyboard.delay(300);
                chord(keyboard, KeyEvent.VK_CONTROL, KeyEvent.VK_V);
            } else {
                type(keyboard, output.getFileName().toString());
            }
            keyboard.delay(500);
            press(keyboard, KeyEvent.VK_ENTER);

            waitForFile(output, timeoutSeconds);
        } catch (AWTException error) {
            throw new IllegalStateException(
                "Could not control Chrome's visible print dialog. Allow Java to send keyboard input on this computer.",
                error);
        }
    }

    private void closeOpenDialogs() {
        // Only used when the save already failed. Escape closes the Save As dialog, then Chrome's
        // print preview. Like the save keystrokes, Robot types into the window in front, which during
        // this step is Chrome's own print dialog.
        try {
            Robot keyboard = new Robot();
            keyboard.setAutoDelay(300);
            for (int i = 0; i < 2; i++) press(keyboard, KeyEvent.VK_ESCAPE);
            keyboard.delay(1000);
        } catch (AWTException ignored) {
            // Without keyboard control there is nothing to close; the Selenium save still runs.
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
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

    private void savePdfWithKioskPrinting(Path output, int timeoutSeconds) throws IOException {
        Path folder = output.getParent();
        Set<Path> existing = pdfsIn(folder);
        ((JavascriptExecutor) driver).executeScript("setTimeout(() => window.print(), 250); return true;");
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        while (System.nanoTime() < deadline) {
            for (Path pdf : pdfsIn(folder)) {
                if (existing.contains(pdf)) continue;
                long size = Files.size(pdf);
                pause(750);
                // Chrome names the file after the page title; rename it once it has finished writing.
                if (size > 0 && Files.exists(pdf) && Files.size(pdf) == size) {
                    if (!pdf.equals(output)) Files.move(pdf, output, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
            pause(250);
        }
        throw new IOException("Chrome's kiosk printing did not save a PDF in " + folder
            + " within " + timeoutSeconds + " seconds");
    }

    private static Set<Path> pdfsIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().toLowerCase().endsWith(".pdf"))
                .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the PDF", error);
        }
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

    private void safeClick(WebElement element) {
        // Center the element first so sticky headers, footers and banners do not cover it.
        // 'instant' matters: sites with CSS smooth scrolling would otherwise still be moving
        // when the real click fires.
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].scrollIntoView({block:'center', inline:'nearest', behavior:'instant'});", element);
        try {
            element.click();
        } catch (ElementClickInterceptedException covered) {
            // An overlay still covers the element: click it with JavaScript. This skips mouse
            // events, so it is recorded and shown in the report rather than hidden.
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            javaScriptClickUsed = true;
            System.out.println("JavaScript click fallback used: real click was intercepted (" + covered.getMessage().lines().findFirst().orElse("") + ")");
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

    private By optionLocator(String option) {
        // The workbook's dropdown_option selector marks where the option text goes with ${option}.
        LocatorDefinition template = locators.get(DROPDOWN_OPTION);
        if (template == null) throw new IllegalArgumentException("Missing locator: " + DROPDOWN_OPTION);
        return template.toBy(resolve(template.selector().replace("${option}", xpathLiteral(option))));
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