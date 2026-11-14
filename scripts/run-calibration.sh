#!/bin/sh
set -u

workbook_path="${WORKBOOK:-data/scenarios.local.xlsx}"
if [ ! -f "${workbook_path}" ]; then
  printf 'Missing private workbook: %s\nCopy data/scenarios.xlsx to data/scenarios.local.xlsx and enter the Excel login/event values.\n' "${workbook_path}" >&2
  exit 2
fi

# S01 waits for Duo's authenticated redirect; S02 and S05 reuse the session.
mvn --batch-mode --no-transfer-progress clean test -Dheadless=false \
  -Dworkbook="${workbook_path}" -DscenarioIds=S01,S02,S05
