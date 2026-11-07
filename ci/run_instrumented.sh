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

# The step log cannot be downloaded from every environment, and when the guest dies the guest's own
# log dies with it. Sample the HOST side too: total RAM, the emulator's RSS, and whether adb still
# sees a device. If qemu is being OOM-killed by the host, this is the only place it shows up.
health=ci/host-health.txt
: > "$health"
(
  while true; do
    {
      echo "--- $(date -u +%H:%M:%S) ---"
      free -m | head -2
      ps -eo rss,comm,args --sort=-rss 2>/dev/null | grep -E "qemu|emulator|java" | head -4 | cut -c1-160
      adb devices | tr '\n' ' '
      echo
    } >> "$health" 2>&1
    sleep 5
  done
) &
health_pid=$!

status=0
# Smaller heap and fewer workers than the build step: here the guest needs the RAM, and this step
# only compiles the test APK. Two heavy JVMs plus a software-GPU guest is what starves the host.
gradle --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2560m \
  connectedDebugAndroidTest 2>&1 | tee emulator-tests.log || status=$?

kill "$logcat_pid" "$health_pid" 2>/dev/null || true
wait "$logcat_pid" "$health_pid" 2>/dev/null || true
# Host OOM killer verdict, if any. Needs sudo on the runner; harmless when unavailable.
{ echo "--- dmesg: oom / qemu ---"; sudo dmesg 2>/dev/null | grep -iE "out of memory|oom-kill|killed process" | tail -20; } >> "$health" || true
grep -E "TestRunner|AndroidRuntime|com.neptune|permissioncontroller|ActivityTaskManager|lowmemorykiller|lmkd|FATAL|ANR in" \
  "$raw" > ci/logcat.txt || true
rm -f "$raw"

# A genuine gate: non-zero here fails the step, because this whole file is one shell.
python3 ci/check_instrumented.py || status=$?

echo "instrumented suite exit status: $status"
exit "$status"
