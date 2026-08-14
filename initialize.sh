#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "=== NaviVeylin Initialization ==="
echo ""

# ── 1. Git submodule: libosmscout ────────────────────────────
SUBMODULE_PATH="$SCRIPT_DIR/app/src/main/cpp/libosmscout"
SUBMODULE_KEY="app/src/main/cpp/libosmscout"
LIBS_OSMSCOUT_URL="https://github.com/Framstag/libosmscout"

if [ -d "$SUBMODULE_PATH/.git" ] || [ -f "$SUBMODULE_PATH/CMakeLists.txt" ]; then
    # Check if properly registered as submodule
    if git -C "$SCRIPT_DIR" submodule status "$SUBMODULE_KEY" &>/dev/null 2>&1; then
        echo "[1/3] Submodule 'libosmscout' already present at $SUBMODULE_PATH"
    else
        echo "[1/3] Submodule directory exists but not registered — fixing..."
        git -C "$SCRIPT_DIR" submodule add "$LIBS_OSMSCOUT_URL" "$SUBMODULE_PATH" 2>/dev/null || \
        git -C "$SCRIPT_DIR" config -f .gitmodules "submodule.$SUBMODULE_KEY.url" "$LIBS_OSMSCOUT_URL"
        git -C "$SCRIPT_DIR" submodule update --init --recursive
    fi
else
    echo "[1/3] Adding libosmscout as git submodule..."
    mkdir -p "$SCRIPT_DIR/app/src/main/cpp"
    git -C "$SCRIPT_DIR" submodule add "$LIBS_OSMSCOUT_URL" "$SUBMODULE_KEY"
    git -C "$SCRIPT_DIR" submodule update --init --recursive
    echo "      ✓ libosmscout cloned to $SUBMODULE_PATH"
fi

# ── 2. CMakeLists.txt ─────────────────────────────────────────
CMAKE_FILE="$SCRIPT_DIR/app/src/main/cpp/CMakeLists.txt"

echo "[2/3] Updating CMakeLists.txt..."

cat > "$CMAKE_FILE" << 'CMAKE_EOF'
cmake_minimum_required(VERSION 3.22.1)
project("naviveylin-native")

# ── vcpkg toolchain (optional) ───────────────────────────────
if(DEFINED ENV{VCPKG_ROOT} AND EXISTS "$ENV{VCPKG_ROOT}/scripts/buildsystems/vcpkg.cmake")
    set(CMAKE_TOOLCHAIN_FILE "$ENV{VCPKG_ROOT}/scripts/buildsystems/vcpkg.cmake"
        CACHE FILEPATH "vcpkg CMake toolchain")
    set(VCPKG_TARGET_TRIPLET arm64-android
        CACHE STRING "vcpkg triplet for Android")
    message(STATUS "Using vcpkg at $ENV{VCPKG_ROOT}")
endif()

# ── libosmscout (optional submodule) ─────────────────────────
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/libosmscout/CMakeLists.txt")
    message(STATUS "Building libosmscout from submodule")

    # Pre-generate configured headers
    foreach(_rel
        "libosmscout/libosmscout/include/osmscout/lib/CoreFeatures.h"
        "libosmscoutmap/include/osmscoutmap/MapFeatures.h"
        "libosmscoutmapcairo/include/osmscoutmapcairo/MapCairoFeatures.h"
        "libosmscoutclient/include/osmscoutclient/ClientFeatures.h"
    )
        set(_out "${CMAKE_CURRENT_SOURCE_DIR}/${_rel}")
        set(_tpl "${_out}.cmake")
        if(EXISTS "${_tpl}")
            file(REMOVE "${_out}")
            configure_file("${_tpl}" "${_out}")
            message(STATUS "Generated ${_rel}")
        endif()
    endforeach()

    # Configure libosmscout for Android (matching build_javascout.yml)
    # JNI bridge (OSMSCOUT_BUILD_CLIENT_JAVA) requires Cairo headers.
    # Enable it after running setup-vcpkg.sh.
    set(OSMSCOUT_BUILD_CLIENT_JAVA OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_MAP_CAIRO OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_MAP_AGG OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_MAP_OPENGL OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_MAP_QT OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_MAP_SVG OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_CLIENT_QT OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_DEMOS OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_IMPORT OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_GPX OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_TOOL_IMPORT OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_TOOL_DUMPDATA OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_TOOL_MCPSERVER OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_TOOL_PUBLICTRANSPORTMAP OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_TOOL_OSMSCOUT2 OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_TOOL_OSMSCOUTOPENGL OFF CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_TOOL_STYLEEDITOR OFF CACHE BOOL "" FORCE)

    add_subdirectory(libosmscout)
else()
    message(STATUS "libosmscout submodule not found — using placeholder")
    add_library(osmscout_client_placeholder SHARED placeholder.cpp)
endif()
CMAKE_EOF

echo "      ✓ CMakeLists.txt updated"

# ── 3. Summary ────────────────────────────────────────────────
echo ""
echo "[3/3] Done."
echo ""
echo "=== Next Steps ==="
echo ""
echo "  1. Build the app:"
echo "     ./gradlew :app:assembleDebug"
echo ""
echo "  2. Native dependencies:"
echo "     libosmscout requires: cairo, pango, harfbuzz, protobuf, libxml2, zlib"
echo "     Run ./setup-vcpkg.sh to cross-compile them for Android."
echo ""
echo "  CMakeLists.txt auto-detects submodule + vcpkg."
echo "  No manual edits needed."
