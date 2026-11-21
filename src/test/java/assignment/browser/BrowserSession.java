package assignment.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/** Owns the shared browser used by all five scenarios in presentation order. */
public final class BrowserSession implements AutoCloseable {
    private final WebDriver driver;
    private final Path downloadDirectory;

    private BrowserSession(WebDriver driver, Path downloadDirectory) {
        this.driver = driver;
        this.downloadDirectory = downloadDirectory;
    }

    public static BrowserSession start(Path downloadDirectory, boolean headless) throws IOException {
        Files.createDirectories(downloadDirectory);
        try (var contents = Files.list(downloadDirectory)) {
            if (contents.findAny().isPresent()) {
                throw new IllegalStateException("Download directory must be empty: " + downloadDirectory);
            }
        }
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("download.default_directory", downloadDirectory.toAbsolutePath().toString());
        preferences.put("download.prompt_for_download", false);
        preferences.put("download.directory_upgrade", true);
        preferences.put("savefile.default_directory", downloadDirectory.toAbsolutePath().toString());
        preferences.put("printing.print_preview_sticky_settings.appState", """
            {"recentDestinations":[{"id":"Save as PDF","origin":"local","account":""}],
             "selectedDestinationId":"Save as PDF","version":2}
            """);
        preferences.put("safebrowsing.enabled", true);
        var options = new ChromeOptions();
        options.setExperimentalOption("prefs", preferences);
        options.addArguments("--window-size=1440,1000", "--disable-search-engine-choice-screen",
            "--disable-notifications");
        if (headless) options.addArguments("--headless=new");
        // Windows: keystrokes cannot reliably reach Chrome's print dialog (Chrome often cannot take
        // keyboard focus from the terminal), so print straight to the preset "Save as PDF"
        // destination. macOS and Linux keep the visible print dialog.
        if (!headless && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            options.addArguments("--kiosk-printing");
        }
        return new BrowserSession(new ChromeDriver(options), downloadDirectory);
    }

    public WebDriver driver() { return driver; }
    public Path downloadDirectory() { return downloadDirectory; }

    @Override public void close() {
        driver.quit();
    }
}