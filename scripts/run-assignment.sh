#!/bin/sh
set -u

headless_value="${HEADLESS:-true}"
mvn --batch-mode --no-transfer-progress clean test -Dheadless="${headless_value}"
maven_status=$?

report_path="target/assignment-report/report.html"
if [ -f "${report_path}" ]; then
  printf '\nAssignment report: %s\n' "${report_path}"
else
  printf '\nThe assignment report was not generated. Review the Maven output above.\n' >&2
fi

exit "${maven_status}"
