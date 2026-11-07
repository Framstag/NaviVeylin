cmake_minimum_required(VERSION 3.22.1)
project("naviveylin-native")

# ── 16 KB page size alignment (Android 15+ requirement) ───
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# ── vcpkg paths (for dependencies) ───────────────────────────
set(_vcpkg_root "$ENV{VCPKG_ROOT}")
if(NOT _vcpkg_root)
    get_filename_component(_vcpkg_root "${CMAKE_CURRENT_SOURCE_DIR}/../../../../vcpkg" ABSOLUTE)
endif()

# Map ANDROID_ABI to vcpkg triplet
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(VCPKG_TRIPLET "arm64-android")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(VCPKG_TRIPLET "arm-neon-android")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(VCPKG_TRIPLET "x64-android")
else()
    message(WARNING "Unknown ANDROID_ABI '${ANDROID_ABI}', falling back to arm64-android")
    set(VCPKG_TRIPLET "arm64-android")
endif()

set(VCPKG "${_vcpkg_root}/installed/${VCPKG_TRIPLET}")
if(EXISTS "${VCPKG}")
    message(STATUS "Using vcpkg deps at ${VCPKG} (triplet: ${VCPKG_TRIPLET}, ABI: ${ANDROID_ABI})")
    # Help find_package find vcpkg-installed packages via pkg-config
    set(ENV{PKG_CONFIG_PATH} "${VCPKG}/lib/pkgconfig")
    set(ENV{PKG_CONFIG_LIBDIR} "${VCPKG}/lib/pkgconfig")
endif()

# ── libosmscout (from source submodule) ──────────────────────
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/libosmscout/CMakeLists.txt")
    message(STATUS "Building libosmscout from submodule")

    # ── Feature headers: features.cmake decides ─────────────────
    # libosmscout renders its four `*Features.h` — CoreFeatures, MapFeatures,
    # MapCairoFeatures, ClientFeatures — from the tracked `*.cmake` templates
    # beside them, out of what `cmake/features.cmake` detected for *this*
    # configuration, and `cmake/ProjectConfig.cmake` writes each one into this
    # build tree and puts its module's build-tree `include` on the module's
    # include path.
    #
    # This file used to render the same four headers itself, from the cache
    # values that stood before `add_subdirectory(libosmscout)` — marisa on and
    # every other feature off — and to prepend that directory to the include
    # path, so the effective Android feature set was that block rather than what
    # the library computed: FreeType, cairo-ft and Fontconfig stayed off although
    # the Android vcpkg triplets install them (§305; the include-order mechanism
    # that made the app's directory win is §202, issue #136). It renders nothing
    # now: the library's computed values are the build's values.
    #
    # A generated header that sits in the submodule *source* tree would still
    # shadow the one this build generates — every module's target lists its own
    # source tree before its binary include directory — in *every* build of this
    # working tree, so `:app:checkSubmoduleFeatureHeaders` removes such a copy and
    # fails once, naming what it removed.
    #
    # `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` is a `check_cxx_source_compiles`
    # result and CMake caches those, so a build directory that configured while a
    # shadowing header said the define would keep the probe's old answer instead
    # of probing this configuration; `OSMSCOUT_HAVE_LIB_MARISA` is what this file
    # used to pin in the cache. Clearing both makes the feature set a function of
    # the configuration rather than of the cache's history.
    unset(OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT CACHE)
    unset(OSMSCOUT_HAVE_LIB_MARISA CACHE)

    # Our FindJNI for the NDK (the built-in one searches a JVM that Android has not)
    list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

    # Java/JNI for libosmscout-client-java
    if(DEFINED ENV{JAVA_HOME})
        set(Java_JAVA_EXECUTABLE "$ENV{JAVA_HOME}/bin/java" CACHE FILEPATH "")
        set(Java_JAVAC_EXECUTABLE "$ENV{JAVA_HOME}/bin/javac" CACHE FILEPATH "")
        set(Java_JAR_EXECUTABLE "$ENV{JAVA_HOME}/bin/jar" CACHE FILEPATH "")
    endif()

    # Pre-set JNI variables so libosmscout's find_package(JNI QUIET) skips search
    # (built-in FindJNI fails under NDK cross-compilation)
    set(_ndk_jni_dir "${CMAKE_ANDROID_NDK}")
    if(NOT EXISTS "${_ndk_jni_dir}/sysroot/usr/include/jni.h")
        # NDK 27+ moved sysroot into toolchain
        set(_ndk_jni_dir "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64")
    endif()
    if(EXISTS "${_ndk_jni_dir}/sysroot/usr/include/jni.h")
        set(JNI_FOUND TRUE CACHE BOOL "" FORCE)
        set(JNI_INCLUDE_DIRS "${_ndk_jni_dir}/sysroot/usr/include" CACHE PATH "" FORCE)
        set(JNI_LIBRARIES "" CACHE STRING "" FORCE)
        set(JAVA_INCLUDE_PATH "${_ndk_jni_dir}/sysroot/usr/include" CACHE PATH "" FORCE)
        set(JAVA_INCLUDE_PATH2 "${_ndk_jni_dir}/sysroot/usr/include" CACHE PATH "" FORCE)
        set(JAVA_AWT_INCLUDE_PATH "${_ndk_jni_dir}/sysroot/usr/include" CACHE PATH "" FORCE)
        message(STATUS "JNI pre-set: headers at ${JNI_INCLUDE_DIRS}")
    else()
        message(WARNING "jni.h not found under NDK root")
    endif()

    # Configure for Android
    set(OSMSCOUT_BUILD_CLIENT_JAVA ON CACHE BOOL "" FORCE)
    set(OSMSCOUT_BUILD_MAP_CAIRO ON CACHE BOOL "" FORCE)
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
    set(OSMSCOUT_BUILD_WEBPAGE OFF CACHE BOOL "" FORCE)

    # Pre-set find paths for vcpkg deps
    if(VCPKG)
        set(PNG_LIBRARY "${VCPKG}/lib/libpng.a" CACHE FILEPATH "")
        set(PNG_PNG_INCLUDE_DIR "${VCPKG}/include" CACHE PATH "")
        set(ZLIB_LIBRARY "${VCPKG}/lib/libz.a" CACHE FILEPATH "")
        set(ZLIB_INCLUDE_DIR "${VCPKG}/include" CACHE PATH "")
        set(FREETYPE_LIBRARY "${VCPKG}/lib/libfreetype.a" CACHE FILEPATH "")
        # The freetype port (2.14.x) moves the headers out of include/freetype2 into
        # include/ (ft2build.h) and include/freetype/, and rewrites its config module
        # accordingly; the presets below must point at the same prefix, and FORCE
        # corrects a build directory whose cache still holds the old freetype2 path.
        set(FREETYPE_INCLUDE_DIRS "${VCPKG}/include" CACHE PATH "" FORCE)
        set(FREETYPE_INCLUDE_DIR_ft2build "${VCPKG}/include" CACHE PATH "" FORCE)
        set(FREETYPE_INCLUDE_DIR_freetype2 "${VCPKG}/include" CACHE PATH "" FORCE)
        set(LIBXML2_LIBRARY "${VCPKG}/lib/libxml2.a" CACHE FILEPATH "")
        set(LIBXML2_INCLUDE_DIR "${VCPKG}/include/libxml2" CACHE PATH "")
        set(PROTOBUF_LIBRARY "${VCPKG}/lib/libprotobuf.a" CACHE FILEPATH "")
        set(PROTOBUF_INCLUDE_DIR "${VCPKG}/include" CACHE PATH "")
        # Marisa: pre-set to skip FindMarisa.cmake search (find_path/find_library
        # use system paths which fail under NDK cross-compilation)
        set(MARISA_FOUND 1 CACHE BOOL "" FORCE)
        set(MARISA_INCLUDE_DIRS "${VCPKG}/include" CACHE PATH "")
        set(MARISA_LIBRARIES "${VCPKG}/lib/libmarisa.a" CACHE FILEPATH "")
        # Cairo: pre-set to skip FindCairo.cmake search (uses find_library which fails under NDK)
        set(CAIRO_INCLUDE_DIRS "${VCPKG}/include/cairo" CACHE PATH "")
        set(CAIRO_INCLUDE_DIR "${VCPKG}/include/cairo" CACHE PATH "")
        set(CAIRO_LIBRARIES
            "${VCPKG}/lib/libcairo.a"
            "${VCPKG}/lib/libpixman-1.a"
            "${VCPKG}/lib/libfontconfig.a"
            "${VCPKG}/lib/libfreetype.a"
            "${VCPKG}/lib/libpng16.a"
            "${VCPKG}/lib/libz.a"
            "${VCPKG}/lib/libexpat.a"
            "${VCPKG}/lib/libbz2.a"
            "${VCPKG}/lib/libbrotlidec.a"
            "${VCPKG}/lib/libbrotlicommon.a"
            CACHE FILEPATH "")
        set(CAIRO_LIBRARY_RELEASE "${VCPKG}/lib/libcairo.a" CACHE FILEPATH "")
    endif()

    add_subdirectory(libosmscout)

    # ── The FreeType closure of the static freetype port ─────────
    # libosmscout-map links Freetype::Freetype as soon as the library found it
    # (`libosmscout-map/CMakeLists.txt`), and `features.cmake` finds it here —
    # FreeType is one of the features §305 stopped forcing off, so the map
    # library now calls into it (`FontNameResolution`, for a family read out of a
    # configured font file). This vcpkg freetype is a static archive whose PNG,
    # zlib, bzip2 and brotli decoders are not inside it, and a static archive does
    # not carry its dependencies: they follow it on the link line, in the same
    # order and with the same entries `CAIRO_LIBRARIES` above lists for the cairo
    # backend. The link is unconditional because `Freetype::Freetype` is created
    # by `find_package` inside `add_subdirectory(libosmscout)` and an imported
    # target is directory-scoped, so this directory cannot ask whether it exists.
    if(VCPKG)
        foreach(_target OSMScoutMap OSMScoutMapCairo)
            if(TARGET ${_target})
                target_link_libraries(${_target}
                    "${VCPKG}/lib/libpng16.a"
                    "${VCPKG}/lib/libz.a"
                    "${VCPKG}/lib/libbz2.a"
                    "${VCPKG}/lib/libbrotlidec.a"
                    "${VCPKG}/lib/libbrotlicommon.a")
            endif()
        endforeach()
    endif()

    # iconv stub for Android (API < 28 lacks iconv in libc)
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/iconv_stub.c")
        add_library(iconv_stub STATIC "${CMAKE_CURRENT_SOURCE_DIR}/iconv_stub.c")
        target_link_libraries(OSMScoutMapCairo iconv_stub)
    endif()

    # ── Native log bridge ──────────────────────────────────────
    # App-owned code (not libosmscout): forwards osmscout::log output to
    # Android Logcat. Only place in the native build allowed to use
    # Android logging APIs. Links the client-java JNI lib so the osmscout
    # log symbols resolve once the shared libs are loaded.
    add_library(naviveylin_log_bridge SHARED
        "${CMAKE_CURRENT_SOURCE_DIR}/native_log_bridge.cpp"
    )
    target_link_libraries(naviveylin_log_bridge
        PRIVATE
            OSMScout::OSMScout
            osmscout_client_java
            log
    )
else()
    message(FATAL_ERROR "libosmscout submodule not found. Run ./initialize.sh first.")
endif()
