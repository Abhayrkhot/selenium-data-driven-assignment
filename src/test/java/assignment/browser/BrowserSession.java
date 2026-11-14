package assignment.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/** Owns one isolated browser and download directory for a single scenario. */
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
        preferences.put("safebrowsing.enabled", true);
        var options = new ChromeOptions();
        options.setExperimentalOption("prefs", preferences);
        options.addArguments("--window-size=1440,1000", "--disable-search-engine-choice-screen");
        if (headless) options.addArguments("--headless=new");
        return new BrowserSession(new ChromeDriver(options), downloadDirectory);
    }

    public WebDriver driver() { return driver; }
    public Path downloadDirectory() { return downloadDirectory; }

    @Override public void close() {
        driver.quit();
    }
}
