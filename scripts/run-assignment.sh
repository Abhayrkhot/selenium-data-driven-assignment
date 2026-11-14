#!/bin/sh
set -u

workbook_path="${WORKBOOK:-data/scenarios.local.xlsx}"
if [ ! -f "${workbook_path}" ]; then
  printf 'Missing private workbook: %s\nCopy data/scenarios.xlsx to data/scenarios.local.xlsx and enter the Excel login/event values.\n' "${workbook_path}" >&2
  exit 2
fi

mvn --batch-mode --no-transfer-progress clean test -Dheadless=false -Dworkbook="${workbook_path}"
maven_status=$?

report_path="target/assignment-report/report.html"
if [ -f "${report_path}" ]; then
  printf '\nAssignment report: %s\n' "${report_path}"
else
  printf '\nThe assignment report was not generated. Review the Maven output above.\n' >&2
fi

exit "${maven_status}"
