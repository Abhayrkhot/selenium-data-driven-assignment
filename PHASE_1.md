# Phase 1: Scope and scenario design

## Scope

Build a Java Maven project using Selenium WebDriver, TestNG, and Apache POI. No target website or detailed assignment scenarios have been supplied. The scenarios below are illustrative specifications for a local demo, not completed assignment tests. Real website automation requires replacing the demo inputs, locators, and scenario steps.

All test inputs, credentials, URLs, expected outcomes, and customizable locators will come from `data/scenarios.xlsx`. Java will contain framework behavior and action implementations, not test data. Build settings and browser execution options are infrastructure settings, not test data.

## Agreed execution order

1. **Open the demo catalog — positive.** Navigate to the workbook URL and assert that the catalog heading matches the workbook expectation. This establishes basic navigation and an initial assertion.
2. **Search for a dataset — positive.** Navigate to the catalog, enter a workbook search term, submit the search, and assert the expected result appears. Soft-verify the result's descriptive fields against workbook values.
3. **View dataset details — positive.** Independently navigate to the workbook dataset and assert its title; soft-verify its descriptive fields.
4. **Download a Dataset — intentionally failing.** Navigate to the dataset specified in Excel and attempt its download. The local demo will deliberately deny that download. Assert that the expected file appears in a fresh scenario download directory within a bounded wait; the absent file must produce a genuine TestNG failure. Record the observed denial and missing file in the report. Do not use an unconditional failure, an expected-exception annotation, a skip, or logic that converts this failure into a pass.
5. **Search for another dataset — positive.** Independently open the catalog and search using a second workbook data row. Assert the expected result and soft-verify its description. This demonstrates that execution continues after case 4 fails.

The desired demo result is **PASS, PASS, PASS, FAIL, PASS**. This is a design target, not a verified result yet. Download a Dataset is scenario 4, matching the assignment requirements. Scenario 5 demonstrates continuation after the failure.

## Execution and isolation

- Run scenarios sequentially with explicit TestNG ordering, without success dependencies between cases. Do not use fail-fast behavior or parallel execution.
- Use an independent browser session and fresh download directory per scenario in the demo. Each case supplies its own navigation and setup rather than relying on a previous case's state.
- Each declared step captures a screenshot immediately before the action and in a `finally` block afterward, including assertion failures. Use ordered filenames inside a folder named for the scenario.
- A hard assertion stops the current scenario. Soft verification collects mismatches and calls `assertAll()` before scenario completion so mismatches fail the scenario. Neither mechanism prevents the next scenario from running.
- If browser startup or screenshot capture fails, report the infrastructure error explicitly. Do not claim screenshots exist when capture was impossible, and preserve the original test failure.
- Leave the overall Maven test result unsuccessful when case 4 fails. A failed build does not mean execution stopped early; the report must show case 5 actually ran after the failure.
- The demo requires no manual input. If the eventual real site needs two-factor authentication, design one session-level Enter prompt at most and revisit browser isolation so authentication can be retained. Do not add a prompt to every scenario.

## Planned Excel workbook

- **Scenarios:** stable ID, execution order, scenario name, description, intended demo outcome.
- **Steps:** scenario ID, step order, action, locator key, input key, expected-value key, timeout in seconds. Action names distinguish hard assertions from soft verification.
- **Data:** named URLs, search terms, dataset names, expected text, download filenames, and any login information. Demo URLs will use a documented local-server placeholder resolved at runtime.
- **Locators:** named selector strategies and selectors, clearly labeled as local-demo selectors to replace for the real website.
- **DemoData:** local-demo catalog records and download availability. The demo's displayed business values also come from the workbook, preventing duplicate test data in Java or HTML.

Validate required sheets, references, unique scenario IDs, step order, supported actions, and required values before launching the browser. Blank or invalid data must produce a clear validation error, not a misleading scenario pass. Do not publish real credentials with the submission or include them in reports.

## Planned project layout

```text
selenium-assignment/
  pom.xml
  README.md
  PHASE_1.md
  testng.xml
  data/scenarios.xlsx
  src/test/java/assignment/
    data/          Excel reader and validation
    browser/       Browser and download-directory lifecycle
    steps/         Action dispatch, waits, assertions, screenshots
    reporting/     TestNG listener and HTML generation
    demo/          Local demo server
    tests/         Data-driven scenario runner
  target/runs/<run-id>/
    report.html
    screenshots/<scenario>/
    downloads/<scenario>/
```

This is the planned layout; the implementation and workbook will be created in later phases.

## Reporting contract

The custom HTML report must contain the columns **Scenario Name**, **Actual**, **Expected**, and **Pass-Fail**. Include all five cases in execution order, step evidence, and relative links to before/after screenshots. Escape workbook values and exception text before inserting them into HTML.

For case 4, Expected describes the successful download assertion, Actual describes the observed denial and missing file, and Pass-Fail remains FAIL. A separate note can identify the failure as intentional for the demo. Setup failures, skipped cases, and cases that never ran must be identified truthfully rather than shown as passes.

## Acceptance checks for later phases

1. One documented Maven command starts the local demo and executes all five cases without manual intervention.
2. The report shows PASS, PASS, PASS, FAIL, PASS, with evidence that case 5 executed after case 4.
3. Every executed scenario step has before/after screenshots unless capture itself failed, in which case the report records why.
4. A fresh download directory prevents a stale file from making case 4 pass.
5. Changing workbook inputs changes the test behavior without editing Java test data.
6. Soft-verification mismatches are included in the failure result, while later scenarios still execute.
7. The report and Maven exit status preserve the intentional failure.
8. Instructions distinguish a verified local demonstration from unimplemented target-website scenarios.

## Phase status

Phase 1 is complete: scope, execution order, workbook design, reporting behavior, and acceptance checks are defined. Phase 2 has added the Maven project, Excel workbook, and workbook validation tests. No browser tests have been implemented or run. See README.md for current implementation and verification status.
