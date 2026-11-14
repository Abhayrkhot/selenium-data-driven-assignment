package assignment.steps;

import java.nio.file.Path;

public record StepEvidence(
    int order, String action, String actual, String expected, Path beforeScreenshot, Path afterScreenshot
) {}
