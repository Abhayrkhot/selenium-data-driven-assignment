package assignment.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates filesystem-safe screenshot and download folders for the current run. */
public final class RunPaths {
    private final Path root;

    public RunPaths(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
    }

    public Path screenshotDirectory(String scenarioId, String scenarioName) throws IOException {
        return create("screenshots", scenarioId + "-" + safeName(scenarioName));
    }

    public Path freshDownloadDirectory(String scenarioId, String scenarioName) throws IOException {
        return create("downloads", scenarioId + "-" + safeName(scenarioName));
    }

    private Path create(String category, String name) throws IOException {
        // Each scenario gets its own evidence folder so report links remain unambiguous.
        Path path = root.resolve(category).resolve(name);
        Files.createDirectories(path);
        return path;
    }

    public static String safeName(String value) {
        // Avoid characters that are unsafe in filenames on common presentation systems.
        String safe = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return safe.isBlank() ? "unnamed" : safe;
    }
}
