#!/usr/bin/env bash
# Run the instrumented suite and propagate its real exit code.
#
# WHY THIS IS A FILE AND NOT INLINE YAML:
# reactivecircus/android-emulator-runner splits `script:` on newlines and runs EVERY LINE in its
# own `sh -c` (see src/script-parser.ts and the `for (const script of scripts)` loop in src/main.ts).
# A multi-line inline script therefore cannot share shell state. The previous inline version did:
#
#     status=0
#     gradle ... || status=$?
#     python3 -c "...assert..." || status=$?
#     exit $status
#
# Each line got a fresh shell, so `|| status=$?` swallowed the failure and the final `exit $status`
# saw an unset variable and exited 0. Test failures AND the ">= N tests" floor were silently
# ignored; the job only ever went red via the step timeout. Keep this invoked as a SINGLE line.
set -uo pipefail

mkdir -p ci

# Stream logcat DURING the run. Collecting it afterwards is too late: when the emulator dies
# mid-suite, `adb logcat -d` has no device left to query and ci/logcat.txt comes back 0 bytes --
# which is exactly what happened on the runs that most needed the log. Raw output goes to the
# runner's /tmp so it is never committed; only the filtered slice lands in ci/.
raw="$(mktemp /tmp/neptune-logcat-XXXXXX)"
adb logcat -v threadtime >"$raw" 2>&1 &
logcat_pid=$!

status=0
gradle --no-daemon --console=plain connectedDebugAndroidTest 2>&1 | tee emulator-tests.log || status=$?

kill "$logcat_pid" 2>/dev/null || true
wait "$logcat_pid" 2>/dev/null || true
grep -E "TestRunner|AndroidRuntime|com.neptune|permissioncontroller|ActivityTaskManager|lowmemorykiller|lmkd|FATAL|ANR in" \
  "$raw" > ci/logcat.txt || true
rm -f "$raw"

# A genuine gate: non-zero here fails the step, because this whole file is one shell.
python3 ci/check_instrumented.py || status=$?

echo "instrumented suite exit status: $status"
exit "$status"
