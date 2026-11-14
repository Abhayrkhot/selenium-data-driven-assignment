# Ten-minute presentation outline

## Before class

- Use the exact Git commit and workbook submitted to Canvas.
- Install JDK 17+, Maven 3.9+, and Google Chrome on the presentation computer.
- Run `mvn -version` and confirm Maven uses JDK 17 or later.
- Close Excel so the workbook is not locked.
- Create the ignored `data/scenarios.local.xlsx`, enter credentials and event data, and complete one logged-in selector-calibration run.
- Open the generated report and several screenshot links before class.

## Suggested timing

1. **Requirements and architecture — 1 minute.** Explain Maven, Java, Selenium, TestNG, Apache POI, Excel-only test data, and sequential continuation after a failure.
2. **Excel data model — 2 minutes.** Show Scenarios, Steps, Data, and Locators. Point out scenario 4 at execution order 4 and scenario 5 afterward.
3. **Framework — 2 minutes.** Explain explicit waits, hard assertions, soft verification, isolated downloads, and before/after screenshot capture in a `finally` block.
4. **Live run — 3 minutes.** Run `./scripts/run-assignment.sh`. Approve Duo and press Enter once. Explain that Maven's final failure status is expected because scenario 4 remains a real failure. Show scenario 5 continuing.
5. **Report — 1 minute.** Open `target/assignment-report/report.html`. Show the four required columns, scenario 4's denial, scenario 5's pass, and screenshot links.
6. **Questions — 1 minute.** Be ready to explain why test data stays in Excel, why scenario 4 is not converted into a pass, and how TestNG continues without success dependencies.

## Commands

Visible Chrome presentation run:

```sh
./scripts/run-assignment.sh
```

The presentation command runs the exact Java and Excel definitions in the repository, using only the ignored local workbook for credentials.
