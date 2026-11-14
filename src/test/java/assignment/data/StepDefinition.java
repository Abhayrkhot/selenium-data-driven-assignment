package assignment.data;

public record StepDefinition(
    String scenarioId,
    int order,
    String action,
    String locatorKey,
    String inputKey,
    String expectedKey,
    int timeoutSeconds
) {}
