# Selenium data-driven assignment

A Java Maven scaffold for a five-scenario Selenium assignment. Uses Selenium WebDriver, TestNG, and Apache POI. Test data is stored in Excel.

**Current status: phase 5.** The reusable Java assignment scaffold, illustrative local demo, five-scenario execution, custom report, repeatable verifier, presentation guide, and submission package are complete. The actual assignment website and scenario details have not been supplied, so these remain illustrative tests rather than completed target-site automation.

No target website or detailed scenario specifications have been provided. The included workbook contains illustrative local-demo definitions and clearly marked locators to customize.

## Setup and run

Install JDK 17 or later and Maven 3.9 or later. Internet access is needed for the first dependency download. Check the Java version reported by Maven, since it may differ from the `java` command in your terminal:

```sh
java -version
mvn -version
```

From the repository root:

```sh
mvn --batch-mode --no-transfer-progress clean test
```

This runs four workbook checks, three Chrome framework smoke checks, and all five illustrative assignment scenarios. Scenario 4 must fail, so Maven is expected to return a nonzero status after scenario 5 finishes. The custom report is written to `target/assignment-report/report.html`; its 36 assignment screenshots are stored beside it in scenario folders. Standard TestNG results remain under `target/surefire-reports/`.

For an automated check of the expected demonstration result:

```sh
./scripts/verify-demo.sh
```

For a visible presentation run:

```sh
HEADLESS=false ./scripts/run-assignment.sh
```

To validate a separate local workbook:

```sh
mvn test -Dworkbook=/absolute/path/to/scenarios.local.xlsx
```

Google Chrome is required for phase 3. Selenium Manager resolves the matching driver automatically and may need internet access on its first run. No Python or Node.js installation is required. If Maven rejects Java 8, point `JAVA_HOME` to your installed JDK 17+ and reopen your terminal as needed.

## Planned scenario order

1. Open the demo catalog: positive.
2. Search for a dataset: positive.
3. View dataset details: positive.
4. Download a Dataset: intentionally failing, as specified in the assignment.
5. Search for another dataset: positive and independent of scenario 4.

The future demo should produce **PASS, PASS, PASS, FAIL, PASS**. `intended_outcome` in Excel describes this design; it is never evidence of an actual test result and must not override assertions. A denied download must fail its success assertion, remain FAIL in the report, and leave Maven with a nonzero exit status after scenario 5 completes.

## Excel is the test-data source

Edit `data/scenarios.xlsx` in Excel or another compatible spreadsheet editor. Close the workbook before running tests. Preserve header names and sheet names.

- **Scenarios:** five scenario definitions, their order, and intended demo outcomes.
- **Steps:** 18 ordered actions with references to inputs, locators, expectations, and wait durations.
- **Data:** URLs, search inputs, expected text, download filename, and demo messages.
- **Locators:** selector strategy and selector text. All current selectors belong to the planned local demo.
- **DemoData:** synthetic catalog records and download permissions for the planned demo server. These describe what the demo displays; independent expectations live in Data.

Amber cells are editable data. Headers are frozen and action/outcome dropdowns reduce editing mistakes. Numeric orders and timeouts are Excel numbers. No formulas are needed or accepted. Empty input/expected/locator references are permitted only when the action does not need them.

The `${demoBaseUrl}` text is resolved to the local Java demo server at runtime. The framework rejects unresolved placeholders. For a real target, replace these illustrative URLs, locators, inputs, and scenario steps with the actual assignment definitions. Additional action types may require corresponding Java behavior.

The action contract is documented in [data/README.md](data/README.md). Java reads, validates, and can execute this contract. Build dependencies, output paths, browser mode, and workbook file paths are infrastructure settings rather than test data. Test inputs and login values must not be added to Java, XML configuration, or environment files. No login values are needed for the illustrative catalog; add any real login data only to an untracked `data/*.local.xlsx` copy.

## Structure and remaining phases

- `pom.xml`: pinned Java dependencies, compiler, build requirements, and TestNG launcher.
- `testng.xml`: sequential phase 2 validation suite.
- `src/test/java/assignment/data/WorkbookData.java`: read-only Excel loader with schema/reference validation.
- `src/test/java/assignment/data/WorkbookValidationTest.java`: checks that the workbook is usable and invalid references/orders are rejected.
- `src/test/java/assignment/browser/`: isolated Chrome and run-directory lifecycle.
- `src/test/java/assignment/steps/`: Excel action execution, waits, assertions, soft verification, screenshots, and smoke checks.
- `src/test/java/assignment/demo/`: local workbook-backed illustrative website.
- `src/test/java/assignment/tests/`: independent sequential execution of all five scenarios.
- `src/test/java/assignment/reporting/`: scenario result store and custom HTML report.
- `PHASE_1.md`: scope and acceptance criteria.
- `PHASE_3.md`: browser-framework behavior, verification, and phase boundary.
- `PHASE_4.md`: end-to-end behavior, expected failure, report, and verified results.
- `PHASE_5.md`: submission packaging, repeatable verification, and completion boundary.
- `CUSTOMIZE_TARGET.md`: real-site and 2FA customization checklist.
- `PRESENTATION.md`: ten-minute presentation plan.
- `scripts/`: presentation runner and post-run demo verifier.

The scaffold is ready for submission as an illustrative implementation. Target-site customization begins when the real website and detailed scenario requirements are available.

## Verification

On November 14, 2026, the phase 4 command completed with the required failure result: **12 tests, 1 failure, 0 errors, 0 skipped**, using Maven 3.9.16 and JDK 26.0.2 with Java 17 compilation output. The five scenario results were **PASS, PASS, PASS, FAIL, PASS**. All 36 assignment screenshots were created, scenario 5 ran after scenario 4, and the custom report was rendered and visually inspected. These results apply to the illustrative local demo, not an unspecified target website.

Official references: [Selenium](https://www.selenium.dev/documentation/webdriver/), [TestNG](https://testng.org/), [Apache POI](https://poi.apache.org/components/spreadsheet/), and [Maven Surefire](https://maven.apache.org/surefire/maven-surefire-plugin/).
