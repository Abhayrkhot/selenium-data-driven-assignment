# Phase 5: Submission preparation and final verification

Phase 5 adds repeatable execution, artifact verification, target-site customization guidance, presentation instructions, and a clean source package.

## Added submission tools

- `scripts/run-assignment.sh` runs the Maven suite, prints the report location, and preserves Maven's nonzero result for the required scenario 4 failure.
- `scripts/verify-demo.sh` runs the same suite and then checks five report rows, the exact PASS/PASS/PASS/FAIL/PASS order, 36 screenshots, and no downloaded file. It returns success only when the expected demonstration behavior is present.
- `CUSTOMIZE_TARGET.md` explains how to replace the illustrative Excel values and locators without moving test data into code. It also identifies the unresolved target-specific 2FA decision.
- `PRESENTATION.md` provides a ten-minute presentation outline and pre-class checklist.

## Final local verification

On November 14, 2026, `./scripts/verify-demo.sh` completed successfully after Maven produced its required one-failure result. The generated report contained five ordered scenario rows, four passes, one failure, 36 screenshot files, and no downloaded file. Scenario 5 remained the final PASS.

The source package excludes `.git`, Maven `target` output, local workbooks, editor files, and credentials. Running the package regenerates the report and screenshots.

## Completion boundary

The reusable assignment scaffold and illustrative demonstration are complete. Target-site completion remains blocked by missing information rather than an implementation defect: no target website, exact scenario steps, expected results, login details, selectors, or 2FA flow have been supplied. The project must continue to state that distinction in its submission unless those details are provided and implemented.
