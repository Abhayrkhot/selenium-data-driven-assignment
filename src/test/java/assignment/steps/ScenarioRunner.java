package assignment.steps;

import assignment.data.StepDefinition;
import java.util.ArrayList;
import java.util.List;
import org.testng.asserts.SoftAssert;

public final class ScenarioRunner {
    private final StepExecutor executor;

    public ScenarioRunner(StepExecutor executor) { this.executor = executor; }

    public List<StepEvidence> run(List<StepDefinition> steps) {
        var softAssert = new SoftAssert();
        var evidence = new ArrayList<StepEvidence>();
        for (StepDefinition step : steps) evidence.add(executor.execute(step, softAssert));
        softAssert.assertAll();
        return List.copyOf(evidence);
    }
}
