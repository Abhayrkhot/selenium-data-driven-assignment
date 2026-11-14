package assignment.steps;

/** Adds step context while preserving the original Selenium failure as the cause. */
public final class StepExecutionException extends RuntimeException {
    public StepExecutionException(String message, Throwable cause) { super(message, cause); }
}
