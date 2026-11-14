# Phase 3: Browser automation framework

Phase 3 implements the reusable Java browser layer. It does not execute the five assignment scenarios or generate the custom assignment report; those remain phase 4 work.

## Implemented behavior

- Starts an isolated Chrome session with a dedicated, empty download directory for each future scenario.
- Uses Selenium Manager through `ChromeDriver`, so no driver path is stored in code or Excel.
- Converts Excel locator strategies (`css`, `id`, `xpath`, and `name`) to Selenium locators.
- Sorts and exposes Excel steps by scenario and resolves workbook data keys at execution time.
- Supports `OPEN`, `TYPE`, `CLICK`, `ASSERT_TEXT`, `VERIFY_TEXT`, and `ASSERT_DOWNLOAD`.
- Uses explicit waits for visibility and clickability. Each step supplies its timeout from Excel.
- Uses TestNG hard assertions for `ASSERT_TEXT` and file-download success.
- Collects `VERIFY_TEXT` mismatches with `SoftAssert`, then calls `assertAll()` at scenario completion.
- Captures a PNG immediately before and in a `finally` block after every attempted step. An after-screenshot is still attempted when the action or assertion fails.
- Preserves the original action/assertion failure if after-screenshot capture also fails by attaching the screenshot error as a suppressed exception.
- Accepts named runtime placeholders such as `${demoBaseUrl}` and rejects unresolved placeholders.
- Treats a download as successful only when the expected file exists, is nonempty, and no partial-download files remain. The filename cannot escape its scenario download directory.

## Verification performed

The Maven suite contains four workbook validation checks and three Chrome framework smoke checks. The successful smoke path executes the five Excel-defined steps from scenario 2 against a temporary local HTML fixture generated from workbook values. It covers navigation, typing, clicking, a hard text assertion, a soft text verification, and ten before/after screenshots.

Two additional smoke paths deliberately use an incorrect expectation. One verifies that a failed hard assertion still produces its after-screenshot. The other verifies that a soft mismatch is collected until scenario completion and also produces both screenshots. These expected failures are caught by the smoke tests and do not represent assignment scenario outcomes.

On November 14, 2026, `mvn --batch-mode --no-transfer-progress clean test` passed with **7 tests, 0 failures, 0 errors, and 0 skipped**. The screenshot output was visually inspected. The complete five-scenario result sequence has not been run.

## Phase 4 boundary

Phase 4 will add the local demo server, create one independent browser session per assignment scenario, run all five scenarios sequentially without success dependencies, retain scenario 4 as a genuine failure, prove scenario 5 executes afterward, and generate the required HTML report with **Scenario Name / Actual / Expected / Pass-Fail** and screenshot links.
