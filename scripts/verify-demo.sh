#!/bin/sh
set -u

./scripts/run-assignment.sh
maven_status=$?
report_path="target/assignment-report/report.html"
screenshot_root="target/assignment-report/screenshots"
download_root="target/assignment-report/downloads"

fail() {
  printf 'Verification failed: %s\n' "$1" >&2
  exit 1
}

[ "${maven_status}" -ne 0 ] || fail "Maven returned success even though scenario 4 must fail"
[ -f "${report_path}" ] || fail "custom HTML report is missing"

scenario_rows=$(grep -Eo '<tr><td>[^<]+' "${report_path}" | wc -l | tr -d ' ')
pass_count=$(grep -Eo 'class="pass"' "${report_path}" | wc -l | tr -d ' ')
fail_count=$(grep -Eo 'class="fail"' "${report_path}" | wc -l | tr -d ' ')
status_sequence=$(grep -Eo 'class="(pass|fail)"' "${report_path}" | sed -E 's/class="([a-z]+)"/\1/' | paste -sd, -)
screenshot_count=$(find "${screenshot_root}" -type f -name '*.png' | wc -l | tr -d ' ')
download_count=$(find "${download_root}" -type f | wc -l | tr -d ' ')

[ "${scenario_rows}" = "5" ] || fail "expected 5 scenario rows, found ${scenario_rows}"
[ "${pass_count}" = "4" ] || fail "expected 4 passed scenarios, found ${pass_count}"
[ "${fail_count}" = "1" ] || fail "expected 1 failed scenario, found ${fail_count}"
[ "${status_sequence}" = "pass,pass,pass,fail,pass" ] || fail "unexpected status order: ${status_sequence}"
[ "${screenshot_count}" = "36" ] || fail "expected 36 assignment screenshots, found ${screenshot_count}"
[ "${download_count}" = "0" ] || fail "scenario 4 unexpectedly left a downloaded file"

printf '\nDemo verification passed.\n'
printf 'Scenario sequence: PASS, PASS, PASS, FAIL, PASS\n'
printf 'Screenshots: %s\n' "${screenshot_count}"
printf 'Report: %s\n' "${report_path}"
