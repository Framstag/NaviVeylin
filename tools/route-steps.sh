#!/usr/bin/env bash
# RouteSteps wrapper — run the submodule's route diagnostic without remembering the host build.
#
# The tool itself is `app/src/main/cpp/libosmscout/Demos/src/RouteSteps.cpp`: it prints one route's
# description nodes and the published step model beside each other, so a motorway manoeuvre that
# words no signposted destination can be told from a destination record that sits on another node.
#
#   tools/route-steps.sh --build                  # configure + build the host tree once (slow)
#   tools/route-steps.sh --routes roads.txt       # then run it (seconds)
#   tools/route-steps.sh --routes roads.txt --all-nodes
#
# Map data comes from --map-dir, else $NAVIVEYLIN_MAPS. A directory must hold a `types.dat`.
#
# The build is a host meson build of the submodule with every render backend, Qt and the test suite
# turned off: the tool needs the routing and client libraries only. Nothing in the Android build is
# touched, and the build directory is `build/route-steps` (gitignored), never `hostbuild`.
set -u

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
submodule="${repo_root}/app/src/main/cpp/libosmscout"
build_dir="${ROUTE_STEPS_BUILD:-${repo_root}/build/route-steps}"
binary_name="RouteSteps"

usage() {
  sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  cat <<'EOF'

Options:
  --build            configure and build the host tree, then run nothing
  --print-binary     print the tool's path and exit
  --help             this text

Everything else is passed to RouteSteps unchanged (--map-dir, --routes, --foot, --bicycle,
--all-nodes).
EOF
}

find_binary() {
  # meson puts a demo at <build>/Demos/RouteSteps; tolerate an install or a renamed build dir.
  local candidate
  for candidate in "${build_dir}/${binary_name}" "${build_dir}/Demos/${binary_name}"; do
    if [ -x "${candidate}" ]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done
  find "${build_dir}" -maxdepth 3 -type f -name "${binary_name}" -perm -u+x 2>/dev/null | head -1
}

do_build() {
  if [ ! -d "${submodule}" ]; then
    echo "ERROR: the libosmscout submodule is not checked out at ${submodule}" >&2
    return 1
  fi

  if [ ! -f "${build_dir}/build.ninja" ]; then
    echo "configuring ${build_dir} (host, no render backends, no Qt, no tests)" >&2
    meson setup "${build_dir}" "${submodule}" \
      -DenableTests=false \
      -DenableMapAgg=false \
      -DenableMapCairo=false \
      -DenableMapDirectX=false \
      -DenableMapIOSX=false \
      -DenableMapOpenGL=false \
      -DenableMapQt=false \
      -DenableMapSkia=false \
      -DenableMapSvg=false \
      -DenableClientQt=false \
      -DenableXML=false \
      -DenableGpx=false \
      -DenablePiper=false \
      -DbuildDemos=true >&2 || return 1
  fi

  echo "building ${binary_name} (the libraries it links dominate the first build)" >&2
  ninja -C "${build_dir}" "Demos/${binary_name}" >&2
}

build_only=0
pass_through=()
have_map_dir=0

while [ $# -gt 0 ]; do
  case "$1" in
    --build) build_only=1 ;;
    --print-binary)
      binary="$(find_binary || true)"
      if [ -z "${binary}" ]; then
        echo "ERROR: ${binary_name} is not built yet; run: tools/route-steps.sh --build" >&2
        exit 1
      fi
      printf '%s\n' "${binary}"
      exit 0
      ;;
    --help|-h) usage; exit 0 ;;
    --map-dir) have_map_dir=1; pass_through+=("$1" "${2:-}"); shift ;;
    *) pass_through+=("$1") ;;
  esac
  shift
done

if [ "${build_only}" -eq 1 ]; then
  do_build
  exit $?
fi

binary="$(find_binary || true)"
if [ -z "${binary}" ]; then
  echo "ERROR: ${binary_name} is not built yet; run: tools/route-steps.sh --build" >&2
  exit 1
fi

if [ "${have_map_dir}" -eq 0 ]; then
  if [ -n "${NAVIVEYLIN_MAPS:-}" ]; then
    pass_through+=(--map-dir "${NAVIVEYLIN_MAPS}")
  else
    cat >&2 <<'EOF'
ERROR: no map directory. Pass --map-dir DIR or set NAVIVEYLIN_MAPS.

The maps the app itself routes on live in the app's private storage. To pull them off a running
emulator or device (the debug build is debuggable, so `run-as` can read them):

  adb shell run-as com.framstag.naviveylin ls files/maps
  adb exec-out run-as com.framstag.naviveylin tar cf - files/maps | tar xf - -C /tmp/naviveylin-maps
  NAVIVEYLIN_MAPS=/tmp/naviveylin-maps/maps/regions/... tools/route-steps.sh --routes roads.txt

Each directory handed to --map-dir must hold a `types.dat`.
EOF
    exit 1
  fi
fi

exec "${binary}" "${pass_through[@]}"
