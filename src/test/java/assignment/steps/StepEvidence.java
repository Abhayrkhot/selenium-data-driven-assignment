package assignment.steps;

import java.nio.file.Path;

/** Actual/expected values and screenshot paths produced by one completed step. */
public record StepEvidence(
    int order, String action, String actual, String expected, Path beforeScreenshot, Path afterScreenshot
) {}
