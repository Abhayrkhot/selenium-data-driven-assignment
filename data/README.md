# Workbook contract

`scenarios.xlsx` is the only runtime source of scenario data. The workbook is committed so the submitted script and its inputs can be reproduced together. Real credentials belong in an ignored local copy, not in repository history.

The workbook contains literal cells rather than formulas. Each sheet has its exact machine-readable headers on row 1. Additional rows can be added using the same columns; completely blank rows are ignored. Keys are case-sensitive and must be unique within their sheet. Preserve intentional whitespace in values. Orders and `timeout_seconds` must be positive integers; scenario orders and step orders within each scenario must be unique. Execution should sort numerically rather than rely on Excel row position.

## Implemented action contract

- `OPEN`: requires `input_key`, pointing to a URL in Data. Navigate to that URL.
- `TYPE`: requires `locator_key` and `input_key`. Wait for the input, clear it, and type the workbook value.
- `CLICK`: requires `locator_key`. Wait until clickable and click.
- `SELECT_TEXT`: choose a native dropdown option from an Excel value.
- `SCROLL_INTO_VIEW` and `CONTEXT_CLICK`: demonstrate scrolling and right-click.
- `SET_CHECKBOX`: change and assert an Excel-defined checkbox state.
- `SWITCH_TO_IFRAME`, `SWITCH_TO_DEFAULT_CONTENT`, and `SWITCH_TO_NEW_WINDOW`: handle embedded and newly opened pages.
- `WAIT_FOR_ENTER`: pauses once after the user approves Duo.
- `PRINT_TO_PDF`: saves the current transcript through Selenium's print API.
- `ASSERT_VISIBLE` and `VERIFY_VISIBLE`: hard and soft visibility checks.
- `ASSERT_URL_CONTAINS`: hard URL check.
- `ASSERT_TEXT`: requires `locator_key` and `expected_key`. Compare visible element text to the workbook expectation with a hard assertion.
- `VERIFY_TEXT`: requires `locator_key` and `expected_key`. Collect a text mismatch as a soft assertion, then call `assertAll()` at the end of the scenario.
- `ASSERT_DOWNLOAD`: requires `expected_key` for the filename and `locator_key` for the observed download status. Wait for a complete, nonempty file in the scenario's fresh download directory. Include observed status text in failure evidence. A denial message is not a successful download.

Action names encode hard versus soft assertion behavior; a separate assertion-mode column is unnecessary. Every action row includes its maximum wait duration in seconds. The screenshot wrapper captures before and after each attempted action, including assertion failures.

## Validation boundaries

The Java loader validates required sheets and headers, required fields, unique keys/orders, positive integer timeouts, action names, selector strategies, and all step references. Every scenario must have steps. Malformed data raises an explanatory exception before browser startup.

Validation alone does not establish that a URL works or that an authenticated selector matches the presenter's account. Public selectors were checked against the live pages; Student Hub, Banner, and Canvas must be calibrated during a logged-in run.

The `intended_outcome` column is documentation only. Never use it to suppress a failing assertion or label an unexecuted scenario as passed.
