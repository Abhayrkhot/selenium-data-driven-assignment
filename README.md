# Northeastern Selenium assignment

Java 17, Maven, Selenium, TestNG, and Apache POI implementation of the five Fall 2026 assignment scenarios:

1. Download the latest transcript.
2. Add two Canvas calendar events.
3. Select an individual study spot in Snell Library without submitting a reservation.
4. Download a DRS dataset and fail the Excel-defined filename assertion.
5. Uncheck an Academic Calendar filter and verify **Add to My Calendar** is visible.

Scenario 4 is fourth, remains `FAIL`, and does not prevent Scenario 5 from running. The scenarios share one Chrome session so Northeastern/Duo authentication happens once. Every attempted step captures before and after screenshots, including failures. The HTML report has exactly the required columns: Scenario Name, Actual, Expected, and Pass-Fail. Screenshot links appear inside Actual.

## Live-site boundary

This repository no longer contains or starts a fake website. Scenarios 3 and 4 use live selectors inspected against Northeastern Library, LibCal, Scholar OneSearch, and DRS. The registrar calendar is an embedded Trumba iframe and is handled explicitly.

Student Hub, Banner transcript, and Canvas are authenticated and account-dependent. Their locators are isolated in the `Locators` worksheet and marked for verification during the first logged-in calibration run. Until that run is completed with the presenter's account, do not claim that those selectors are presentation-ready or that all five scenarios passed live.

## Prepare the private workbook

The committed `data/scenarios.xlsx` is a safe template with placeholders, not credentials.

```sh
cp data/scenarios.xlsx data/scenarios.local.xlsx
```

Open the local copy and update the amber Data cells for `neu_username`, `neu_password`, the Canvas event values, and `event_calendar` if Canvas displays a different label. Keep this file uncommitted. `.gitignore` excludes `data/*.local.xlsx`. Test data and login values belong only in Excel, not Java, XML, environment variables, or a config file.

## Run

Requirements: JDK 17+, Maven 3.9+, and Google Chrome.

```sh
./scripts/run-assignment.sh
```

Chrome opens visibly. Approve Duo, then press Enter once in the terminal when prompted. This is the only manual intervention built into the run.

The suite should return a nonzero Maven status because Scenario 4 is an actual failed assertion. Scenario 5 should still execute. Outputs are written under `target/assignment-report/` and `target/surefire-reports/`.

Compile without logging in:

```sh
mvn --batch-mode --no-transfer-progress -DskipTests test
```

Validate the workbook without starting Chrome:

```sh
mvn --batch-mode --no-transfer-progress -Dsurefire.suiteXmlFiles=testng-validation.xml test
```

## Browser actions

The Steps worksheet controls navigation and behavior. It supports navigation, typing, clicking, native dropdown selection, scrolling, right-click, iframe and new-window switching, checkbox state changes, print-to-PDF, hard assertions, soft verification, and download assertions. Locator strings can reference Data keys such as `${event1_date_label}`.

Selenium's print API saves the transcript PDF reliably after the required right-click demonstration. It avoids Chrome's operating-system print dialog, which Selenium cannot control portably.

## Calibrate before presentation

Run the authenticated calibration subset with the presenter's account:

```sh
./scripts/run-calibration.sh
```

This runs S01, S02, and S05 in workbook order. S01 performs the one permitted Duo pause; Canvas and the later Student Hub visit reuse that session. Update only authenticated selectors in the Locators sheet if Northeastern's current UI differs. Verify MyNEU login, Student Hub navigation, Banner transcript controls, all Canvas event fields, and Student Hub's Academic Calendar links. Also recheck the Trumba frame if the registrar changes its embed.

The Canvas date choices intentionally use the Excel values `event1_date_label` and `event2_date_label`. During calibration, confirm that the current React date picker exposes those dates through an accessible label. If it exposes a different format, copy the exact label from the browser into the two Data cells rather than hard-coding a date in Java.

Do not submit the LibCal reservation. The assignment asks only to verify the selected slot.
