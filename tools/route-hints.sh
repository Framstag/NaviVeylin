#!/usr/bin/env bash
# Route-hint tools — what a route is *supposed* to say, and what navigation actually says.
#
# Three modes, all driven by coordinate pairs and the app's own installed maps:
#
#   tools/route-hints.sh --routes roads.txt
#       Dump every step of each route beside every surface's wording of it
#       (`RouteHintDumpDeviceTest`): the model's `destination`, `ManoeuvreLabels.short`/`leadsOnto`,
#       the phone overlay's second line, the car hint's text. Ad-hoc inspection.
#
#   tools/route-hints.sh --record --routes roads.txt
#       Record the expected instruction list of each route once, from the calculated route, and pull
#       the fixtures into `app/src/androidTest/assets/route-fixtures/`. Commit them: they are the test
#       setup. The fixture is keyed on `kind` + kilometres from the route start — never on a node id,
#       a file offset or a database id, which an import renumbers.
#
#   tools/route-hints.sh --replay <label>
#       Calculate the route again, drive the real NavigationEngine along its polyline, capture every
#       manoeuvre it announces and diff each against the committed fixture: does the announced step
#       still carry the recorded destination, and does the wording still name it. Prints
#       `matched= destination_missing= wording_differs= unannounced= unexpected=`.
#
# Routes file, one route per line (`#` starts a comment):
#
#   # label        startLat startLon targetLat targetLon
#   dortmund-essen 51.5177  7.4592    51.4510   7.0130
#
# A `destination_missing>0` in the replay is a defect below the bridge (the step carries no record);
# `tools/route-steps.sh` then says which description node holds the record instead. A
# `destination_missing=0` with a wrong `carhint` is a defect above it.
set -u

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
fixture_dir="${repo_root}/app/src/androidTest/assets/route-fixtures"
routes=""
mode="dump"
fixture=""
build=1
device_routes="/data/local/tmp/route-hints.txt"
runner="com.framstag.naviveylin.test/androidx.test.runner.AndroidJUnitRunner"
app_id="com.framstag.naviveylin"

usage() {
  sed -n '2,33p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [ $# -gt 0 ]; do
  case "$1" in
    --routes) routes="${2:-}"; shift ;;
    --record) mode="record" ;;
    --replay) mode="replay"; fixture="${2:-}"; shift ;;
    --no-build) build=0 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "ERROR: unknown argument '$1'" >&2; exit 1 ;;
  esac
  shift
done

if ! adb get-state >/dev/null 2>&1; then
  echo "ERROR: no device or emulator attached (adb devices)" >&2
  exit 1
fi

cd "${repo_root}" || exit 1

if [ "${mode}" != "replay" ] && [ -z "${routes}" ]; then
  echo "ERROR: --routes FILE is required" >&2
  exit 1
fi
if [ "${mode}" = "replay" ] && [ -z "${fixture}" ]; then
  echo "ERROR: --replay needs a fixture label (e.g. dortmund-essen)" >&2
  exit 1
fi
if [ "${mode}" != "replay" ] && [ ! -f "${routes}" ]; then
  echo "ERROR: no routes file at '${routes}'" >&2
  exit 1
fi

if [ "${build}" -eq 1 ]; then
  ./gradlew :app:installMobileDebug :app:installMobileDebugAndroidTest -q || exit 1
fi

run_instrumentation() {
  adb shell am instrument -w "$@" "${runner}"
}

case "${mode}" in
  dump)
    adb push "${routes}" "${device_routes}" >/dev/null || exit 1
    adb shell chmod 644 "${device_routes}"
    adb logcat -c
    run_instrumentation \
      -e class com.naviveylin.route.RouteHintDumpDeviceTest \
      -e routesFile "${device_routes}"
    echo >&2
    echo "--- RouteHints ---" >&2
    adb logcat -d -s RouteHints:* | sed 's/^.*RouteHints *: //'
    ;;
  record)
    adb push "${routes}" "${device_routes}" >/dev/null || exit 1
    adb shell chmod 644 "${device_routes}"
    adb logcat -c
    run_instrumentation \
      -e class com.naviveylin.route.RouteGuidanceFixtureDeviceTest#record \
      -e mode record \
      -e routesFile "${device_routes}"
    echo >&2
    echo "--- RouteFixture ---" >&2
    adb logcat -d -s RouteFixture:* | sed 's/^.*RouteFixture *: //'

    mkdir -p "${fixture_dir}"
    pulled=0
    while read -r label _; do
      case "${label}" in ''|'#'*) continue ;; esac
      if adb exec-out run-as "${app_id}" cat "files/route-fixtures/${label}.expected.txt" \
          > "${fixture_dir}/${label}.expected.txt" 2>/dev/null; then
        if [ -s "${fixture_dir}/${label}.expected.txt" ]; then
          echo "pulled ${fixture_dir}/${label}.expected.txt" >&2
          pulled=$((pulled + 1))
        else
          rm -f "${fixture_dir}/${label}.expected.txt"
        fi
      fi
    done < <(sed 's/#.*//' "${routes}")
    echo "fixtures pulled=$pulled — review and commit them: they are the test setup" >&2
    ;;
  replay)
    adb logcat -c
    run_instrumentation \
      -e class com.naviveylin.route.RouteGuidanceFixtureDeviceTest#replay \
      -e mode replay \
      -e fixture "${fixture}.expected.txt"
    echo >&2
    echo "--- RouteFixture ---" >&2
    adb logcat -d -s RouteFixture:* | sed 's/^.*RouteFixture *: //'
    ;;
esac
