# Phase 4: Five-scenario execution and HTML report

Phase 4 connects the Excel workbook, local demo website, Selenium framework, and custom report.

## Execution behavior

The Java demo server starts on a random loopback port. It reads the catalog heading, dataset records, descriptions, filenames, download permissions, and denial message from `data/scenarios.xlsx`. It does not represent the unknown assignment website.

TestNG receives the five scenario rows in numeric Excel order and runs them sequentially with `parallel="none"`. Each invocation creates its own Chrome session, screenshot folder, and empty download folder. The invocations do not use TestNG success dependencies, so a failed scenario does not skip the next one.

Scenario 4 clicks the download button. The demo displays the workbook denial message and creates no file. `ASSERT_DOWNLOAD` waits for `weather.csv`, then fails with both the expected filename and observed denial. The framework does not convert that failure into a pass. Scenario 5 opens a new browser and completes afterward.

## Custom report

The suite listener always writes `target/assignment-report/report.html`, including when Maven reports a test failure. The required columns appear first:

- **Scenario Name**
- **Actual**
- **Expected**
- **Pass-Fail**

An additional **Evidence** column links every before/after screenshot using relative paths. Workbook and exception text are HTML-escaped. The report labels scenario 4 as a designed failure while preserving its FAIL status.

## Verified result

On November 14, 2026, the full command produced the expected nonzero Maven result:

```text
Tests run: 12, Failures: 1, Errors: 0, Skipped: 0
```

The 12 TestNG results consist of seven phase 2/3 infrastructure checks and five assignment scenarios. The custom report records the scenario result sequence:

```text
PASS, PASS, PASS, FAIL, PASS
```

It contains five scenario rows and 36 assignment screenshots: two images for each of the 18 Excel-defined steps. The scenario 4 after-screenshot visibly shows the denial message, and the scenario 5 after-screenshot visibly shows the transit result. The report was rendered in Chrome and visually inspected.

## Running phase 4

```sh
mvn --batch-mode --no-transfer-progress clean test
```

The command is expected to exit unsuccessfully because the assignment explicitly requires scenario 4 to fail. Review `target/assignment-report/report.html` for the complete outcome. A zero exit code would be incorrect for the current illustrative workbook.

Use `-Dheadless=false` to display Chrome during a presentation:

```sh
mvn clean test -Dheadless=false
```

The unknown target website and its actual five scenarios are still not implemented. Replace the illustrative Excel values and locators only after those requirements are supplied.
