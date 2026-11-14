# Ten-minute presentation outline

## Before class

- Use the exact Git commit and workbook submitted to Canvas.
- Install JDK 17+, Maven 3.9+, and Google Chrome on the presentation computer.
- Run `mvn -version` and confirm Maven uses JDK 17 or later.
- Close Excel so the workbook is not locked.
- Run `./scripts/verify-demo.sh` once and open the generated report and several screenshot links.
- If presenting the real website, complete the target and 2FA customization checks first. Do not present the local demo as target-site completion.

## Suggested timing

1. **Requirements and architecture — 1 minute.** Explain Maven, Java, Selenium, TestNG, Apache POI, Excel-only test data, and independent scenario execution.
2. **Excel data model — 2 minutes.** Show Scenarios, Steps, Data, and Locators. Point out scenario 4 at execution order 4 and scenario 5 afterward.
3. **Framework — 2 minutes.** Explain explicit waits, hard assertions, soft verification, isolated downloads, and before/after screenshot capture in a `finally` block.
4. **Live run — 3 minutes.** Run `HEADLESS=false ./scripts/run-assignment.sh`. Explain that Maven's final failure status is expected because scenario 4 remains a real failure. Do not interrupt the run after that failure; show scenario 5 continuing.
5. **Report — 1 minute.** Open `target/assignment-report/report.html`. Show the four required columns, scenario 4's denial, scenario 5's pass, and screenshot links.
6. **Questions — 1 minute.** Be ready to explain why test data stays in Excel, why scenario 4 is not converted into a pass, and how TestNG continues without success dependencies.

## Commands

Visible Chrome presentation run:

```sh
HEADLESS=false ./scripts/run-assignment.sh
```

Repeatable headless verification:

```sh
./scripts/verify-demo.sh
```

Both commands run the same Java and Excel test definitions. The verifier adds post-run checks; it does not replace the assignment script.
