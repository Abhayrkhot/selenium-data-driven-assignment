package assignment.steps;

import assignment.data.StepDefinition;
import java.util.ArrayList;
import java.util.List;
import org.testng.asserts.SoftAssert;

/** Executes steps and reports collected soft-verification failures at scenario end. */
public final class ScenarioRunner {
    private final StepExecutor executor;

    public ScenarioRunner(StepExecutor executor) { this.executor = executor; }

    public List<StepEvidence> run(List<StepDefinition> steps) {
        var softAssert = new SoftAssert();
        var evidence = new ArrayList<StepEvidence>();
        // A hard assertion stops this scenario; TestNG still invokes the next data row.
        for (StepDefinition step : steps) evidence.add(executor.execute(step, softAssert));
        softAssert.assertAll();
        return List.copyOf(evidence);
    }
}
