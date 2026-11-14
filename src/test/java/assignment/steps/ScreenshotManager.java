package assignment.steps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/** Saves numbered before/after evidence in the active scenario folder. */
public final class ScreenshotManager {
    private final WebDriver driver;
    private final Path directory;

    public ScreenshotManager(WebDriver driver, Path directory) throws IOException {
        this.driver = driver;
        this.directory = directory;
        Files.createDirectories(directory);
    }

    public Path capture(int stepOrder, String action, String position) throws IOException {
        if (!(driver instanceof TakesScreenshot source)) {
            throw new IOException("WebDriver does not support screenshots");
        }
        String filename = "%02d-%s-%s.png".formatted(stepOrder, action.toLowerCase(), position);
        Path destination = directory.resolve(filename);
        Path temporary = source.getScreenshotAs(OutputType.FILE).toPath();
        Files.copy(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }
}
