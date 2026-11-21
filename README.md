# Northeastern Selenium assignment

Java 17, Maven, Selenium, TestNG, and Apache POI implementation of the five Fall 2026 assignment scenarios:

1. Download the latest transcript.
2. Add two Canvas calendar events.
3. Filter Snell Library to individual study, select the next day's first open slot, and verify it without submitting a reservation.
4. Download a DRS dataset (the full zip), then fail the Excel-defined filename assertion.
5. Uncheck an Academic Calendar filter and verify **Add to My Calendar** is visible.

Scenario 4 is fourth, remains `FAIL`, and does not prevent Scenario 5 from running. The scenarios share one Chrome session, so Duo approval on the phone happens once and is the only manual action. Every attempted step captures before and after screenshots, including failures. The HTML report has exactly the required columns: Test scenario name, Actual, Expected, and Pass/Fail. Screenshot links appear inside Actual, which also shows how the transcript PDF was saved and any click that needed the JavaScript fallback.

## Live-site boundary

The scenarios run against the live sites: MyNEU and Student Hub, Banner, Canvas, Northeastern Library and LibCal, Scholar OneSearch and DRS, and the registrar calendar (an embedded Trumba iframe). All locators live in the `Locators` worksheet. They were verified in full logged-in runs in November 2026; if a site changes its UI, update the matching Locators row rather than Java.

## Prepare the private workbook

The committed `data/scenarios.xlsx` is a safe template with placeholders, not credentials.

```sh
cp data/scenarios.xlsx data/scenarios.local.xlsx
```

Open the local copy and update the amber Data cells for `neu_username`, `neu_password`, the Canvas event values, and `event_calendar` (the calendar name exactly as Canvas shows it in the event's Calendar dropdown). Keep this file uncommitted. `.gitignore` excludes `data/*.local.xlsx`. Test data and login values belong only in Excel, not Java, XML, environment variables, or a config file.

Never copy the local workbook back over `data/scenarios.xlsx`: besides the credentials, Excel stores an email-address username as a hidden `mailto:` link.

## Run

Requirements: JDK 17+, Maven 3.9+, and Google Chrome. On macOS, the app that starts the run (Terminal, or whichever app launches it) needs **System Settings → Privacy & Security → Accessibility**, because Scenario 1 presses keys in Chrome's print and save dialogs.

```sh
./scripts/run-assignment.sh
```

Chrome opens visibly. Approve Duo on the phone when prompted. Selenium detects the authenticated Student Hub redirect and continues automatically, and answers Duo's "Trust this browser?" and Microsoft's "Stay signed in?" prompts itself (they can reappear when Banner opens). While Scenario 1 saves the PDF, leave the keyboard and mouse alone so the keystrokes reach Chrome. Scenario 4 pauses while its dataset zip downloads; do not close Chrome.

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

The Steps worksheet controls navigation and behavior. It supports navigation, typing, clicking, native dropdown selection (`SELECT_TEXT`), Canvas-style dropdowns (`CHOOSE_OPTION`), scrolling, right-click, iframe and new-window switching, waiting for login (`WAIT_FOR_AUTHENTICATION`) and for loading spinners to clear (`WAIT_FOR_LOADING_COMPLETE`), checkbox state changes, print-to-PDF, hard assertions, soft verification, and download assertions. Locator strings can reference Data keys such as `${event1_day}`.

A few Locators rows have fixed keys that the actions look up:

- `post_duo_prompts`: sign-in prompt buttons that are clicked automatically whenever a step is waiting.
- `dropdown_option`: one option of a `CHOOSE_OPTION` dropdown; `${option}` is replaced with the step's input value.
- `loading_indicators`: elements that `WAIT_FOR_LOADING_COMPLETE` waits to disappear (its step points at this row).

When a page replaces the iframe the driver is in (the registrar's Trumba calendar does this after a filter change), element lookups re-enter the frame from the last `SWITCH_TO_IFRAME` step. Each scenario starts in a live tab, so one scenario closing its tab cannot stop the next.

During a visible run, the transcript scenario opens Chrome's print preview, uses Save as PDF, completes the operating system's save dialog, and then opens the saved PDF in Chrome as visual evidence. Java's cross-platform keyboard automation drives the browser-owned controls on macOS, Windows, and Linux. A persistent copy is written to `Desktop/Selenium/latest-transcript.pdf`; set `-DtranscriptOutputDirectory=/another/path` to override that folder. Headless runs use Selenium's print API because no print dialog can be displayed.

## Calibrate before presentation

Run the authenticated calibration subset with the presenter's account:

```sh
./scripts/run-calibration.sh
```

This runs S01, S02, and S05 in workbook order. S01 waits for Duo's authenticated redirect; Canvas and the later Student Hub visit reuse that session. Update only authenticated selectors in the Locators sheet if Northeastern's current UI differs. Verify MyNEU login, Student Hub navigation, Banner transcript controls, all Canvas event fields, and Student Hub's Academic Calendar links. Also recheck the Trumba frame if the registrar changes its embed.

The Canvas date choices use the Excel day numbers `event1_day` and `event2_day`, picked from the current month in the event's date picker. Change them in the Data sheet rather than hard-coding a date in Java.

Do not submit the LibCal reservation. The assignment asks only to verify the selected slot. Scenario 3 moves to the next day before choosing a slot, so it works at any time of day.
