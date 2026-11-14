package assignment.data;

/** Immutable representation of one Excel Steps row. */
public record StepDefinition(
    String scenarioId,
    int order,
    String action,
    String locatorKey,
    String inputKey,
    String expectedKey,
    int timeoutSeconds
) {}
