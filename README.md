# Selenium data-driven assignment

A Java Maven scaffold for a five-scenario Selenium assignment. Uses Selenium WebDriver, TestNG, and Apache POI. Test data is stored in Excel.

**Current status: phase 2.** The Maven build and Excel workbook are implemented and validated. Browser automation, the local demo website, step screenshots, and the custom HTML scenario report are still planned. A successful build at this stage means the workbook checks passed; it does not mean the five assignment scenarios ran.

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

This runs four infrastructure checks: valid workbook loading, missing input references, duplicate scenario IDs, and duplicate step orders. The mutation checks work in memory and do not overwrite the workbook. Standard TestNG/Maven results are written under `target/surefire-reports/`. These are not the custom assignment report.

To validate a separate local workbook:

```sh
mvn test -Dworkbook=/absolute/path/to/scenarios.local.xlsx
```

No Python, JavaScript, browser, or driver installation is required to run phase 2. Chrome and browser setup will be introduced with the automation framework. If Maven rejects Java 8, point `JAVA_HOME` to your installed JDK 17+ and reopen your terminal as needed.

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

The `${demoBaseUrl}` text is a placeholder for a local server address that phase 3 will resolve. It is not a functioning website yet. For a real target, replace these URLs, locators, inputs, and scenario steps with the actual assignment definitions. Additional action types may require implementing corresponding Java behavior.

The action contract is documented in [data/README.md](data/README.md). Java currently reads and validates this contract; it does not execute the actions yet. Build dependencies and workbook file paths are infrastructure settings in Maven. Test inputs and login values must not be added to Java, XML configuration, or environment files. No login values are needed for the illustrative catalog; add any real login data only to an untracked `data/*.local.xlsx` copy.

## Structure and remaining phases

- `pom.xml`: pinned Java dependencies, compiler, build requirements, and TestNG launcher.
- `testng.xml`: sequential phase 2 validation suite.
- `src/test/java/assignment/data/WorkbookData.java`: read-only Excel loader with schema/reference validation.
- `src/test/java/assignment/data/WorkbookValidationTest.java`: checks that the workbook is usable and invalid references/orders are rejected.
- `PHASE_1.md`: scope and acceptance criteria.

Phase 3 adds browser management, waits, action execution, hard assertions, soft verification, and before/after step screenshots. Phase 4 wires all five scenarios and generates the HTML report with Scenario Name / Actual / Expected / Pass-Fail. Phase 5 verifies the complete demo and documents target customization.

## Verification

On November 14, 2026, the phase 2 Maven checks passed: **4 tests, 0 failures, 0 errors, 0 skipped**, using Maven 3.9.16 and JDK 26.0.2 with Java 17 compilation output. All five workbook sheets were rendered and visually reviewed. The workbook contains five scenarios and 18 steps. Browser scenarios have not run, and the Java 17 runtime itself has not been tested on this machine.

Official references: [Selenium](https://www.selenium.dev/documentation/webdriver/), [TestNG](https://testng.org/), [Apache POI](https://poi.apache.org/components/spreadsheet/), and [Maven Surefire](https://maven.apache.org/surefire/maven-surefire-plugin/).
