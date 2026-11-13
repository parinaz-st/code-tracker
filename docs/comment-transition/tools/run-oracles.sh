#!/usr/bin/env bash
# Runs the four oracle tests (block, method, variable, attribute) on two checkouts, one after the other,
# and keeps each run's log and surefire reports. Phase 5 of the comment-transition rework (ADR 0007).
#
# Usage:  run-oracles.sh <out-dir> <final-checkout> [<baseline-checkout>]
#   e.g.  run-oracles.sh /d/ct-oracle /d/codeTracker /d/ct-baseline
# The baseline checkout is the Phase 0 commit 81dcf47 (git worktree add --detach <dir> 81dcf47). It needs the
# oracle cache: link <dir>/oracle to D:\codeTracker\oracle with a directory junction (see HANDOFF.md).
#
# A temporary pom aligns all JUnit artifacts to 5.10.0: the committed pom mixes 5.8.2 and 5.10.0, which makes
# every @MethodSource test (all *OracleTest) fail before running. The temporary pom is deleted afterwards.
# Do not compile or run other tests in a checkout while its oracle run is going.
set -u
OUT="$1"; FINAL="$2"; BASE="${3:-}"
TESTS='BlockOracleTest,MethodOracleTest,VariableOracleTest,AttributeOracleTest'
mkdir -p "$OUT"

run() {
  local name="$1" dir="$2"
  cd "$dir" || exit 1
  sed 's#<version>5.8.2</version>#<version>5.10.0</version>#g' pom.xml > pom-oracle-tmp.xml
  rm -rf target/surefire-reports
  echo "[$name] start $(date '+%F %T')"
  mvn -o -f pom-oracle-tmp.xml test -Dtest="$TESTS" -Dsurefire.failIfNoSpecifiedTests=false > "$OUT/oracle-$name.log" 2>&1
  echo "[$name] exit $? end $(date '+%F %T')"
  rm -rf "$OUT/reports-$name" && cp -r target/surefire-reports "$OUT/reports-$name"
  rm -f pom-oracle-tmp.xml
  grep -E "Tests run:.*in org" "$OUT/oracle-$name.log"
}

run final "$FINAL"
[ -n "$BASE" ] && run base "$BASE"
echo "compare with: java CompareOracle.java $OUT/reports-base $OUT/reports-final"
