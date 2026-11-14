# Customizing for the actual website

The current five scenarios are a verified local demonstration. They are not the unknown assignment website tests. Obtain the target URL, exact five scenario steps, expected results, selectors, login flow, download behavior, and 2FA behavior before claiming the assignment is complete.

## Work from a private workbook copy

Copy `data/scenarios.xlsx` to `data/scenarios.local.xlsx`. This pattern is ignored by Git so credentials are not committed. Run it with:

```sh
mvn clean test -Dworkbook=/absolute/path/to/data/scenarios.local.xlsx
```

Keep every runtime test value in Excel:

- Put URLs, usernames, passwords, search terms, filenames, and expected text in **Data**.
- Put selector strategies and selectors in **Locators**.
- Define the five names and execution order in **Scenarios**.
- Define ordered actions and key references in **Steps**.
- The **DemoData** sheet is required by the current workbook schema and local demo server. It does not describe a real target site. Leave valid placeholder rows until the demo dependency is removed in a target-specific revision.

Do not put login values or expected business text in Java, `pom.xml`, `testng.xml`, shell scripts, or environment files.

## Map the real steps

The framework currently supports these Excel actions:

- `OPEN`
- `TYPE`
- `CLICK`
- `ASSERT_TEXT`
- `VERIFY_TEXT`
- `ASSERT_DOWNLOAD`

Use stable `id`, `name`, or purpose-built attributes when the target provides them. CSS selectors are usually easier to maintain than long absolute XPath expressions. Confirm every selector in the browser before the presentation.

Use `ASSERT_TEXT` when a mismatch should stop the current scenario. Use `VERIFY_TEXT` when later steps in that same scenario should still run before the collected mismatch fails at `assertAll()`. Neither assertion type prevents the next scenario invocation from running.

For scenario 4, preserve a real failed success assertion. Do not change `intended_outcome`, catch the assertion as a pass, use `expectedExceptions`, or skip the test. Scenario 5 must remain independent and appear after scenario 4 in the report.

## Authentication and 2FA

The verified demo opens a fresh browser for every scenario. That isolation prevents state leakage and stale downloads. It also means a real site that requires 2FA could ask for authentication more than once.

The assignment allows one manual Enter action for 2FA, but the target login flow is not known and the framework does not currently implement a shared authenticated session or a one-time console prompt. Before using a 2FA-protected site, choose and test a target-specific approach:

1. Authenticate once in a setup browser, then reuse a supported authenticated profile or session state across the five scenario browsers.
2. Run all scenarios in one authenticated browser while explicitly resetting page state and using a fresh download directory for scenario 4.

Do not implement either approach until the site behavior is known. Browser profiles and session tokens can contain credentials and must not be committed or included in the submission ZIP.

## Validate the customized result

The supplied `scripts/verify-demo.sh` checks the exact local demonstration and should not be used unchanged as proof for a different website. After customization, update the verification rules to match the supplied scenario names and required outcomes, then verify:

1. The five scenarios appear in assignment order.
2. Scenario 4 is the only failure.
3. Scenario 5 runs after scenario 4.
4. Every executed Excel step has before and after screenshots.
5. Actual and Expected values come from observed behavior and Excel expectations.
6. The HTML report links open correctly on the presentation computer.
7. The same committed script and workbook are used for upload and presentation.
