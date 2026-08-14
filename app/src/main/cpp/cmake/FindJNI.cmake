# Custom FindJNI for Android NDK
# Overrides CMake's built-in FindJNI which looks for AWT/JVM libs not available on Android.
#
# The NDK provides jni.h in sysroot. No JVM/AWT libraries exist on Android —
# they are provided by the Android runtime (libnativehelper) at runtime.

if(NOT JNI_FOUND)
    set(_JNI_HINTS)

    # Try ANDROID_NDK_HOME env var first
    if(DEFINED ENV{ANDROID_NDK_HOME})
        list(APPEND _JNI_HINTS "$ENV{ANDROID_NDK_HOME}")
    endif()

    # Try CMAKE_ANDROID_NDK
    if(CMAKE_ANDROID_NDK)
        list(APPEND _JNI_HINTS "${CMAKE_ANDROID_NDK}")
    endif()

    # Try NDK under SDK
    if(DEFINED ENV{ANDROID_SDK_ROOT})
        file(GLOB _NDK_VERSIONS RELATIVE "$ENV{ANDROID_SDK_ROOT}/ndk" "$ENV{ANDROID_SDK_ROOT}/ndk/*")
        list(SORT _NDK_VERSIONS)
        list(REVERSE _NDK_VERSIONS)
        if(_NDK_VERSIONS)
            list(APPEND _JNI_HINTS "$ENV{ANDROID_SDK_ROOT}/ndk/${_NDK_VERSIONS[0]}")
        endif()
    endif()

    find_path(JAVA_INCLUDE_PATH
        jni.h
        PATHS ${_JNI_HINTS}
        PATH_SUFFIXES sysroot/usr/include
        NO_DEFAULT_PATH
    )

    if(JAVA_INCLUDE_PATH)
        set(JAVA_INCLUDE_PATH2 "${JAVA_INCLUDE_PATH}" CACHE PATH "")
        set(JAVA_AWT_INCLUDE_PATH "${JAVA_INCLUDE_PATH}" CACHE PATH "")

        # No JVM/AWT libs on Android — set to NOTFOUND to satisfy REQUIRED
        set(JAVA_JVM_LIBRARY "JAVA_JVM_LIBRARY-NOTFOUND" CACHE FILEPATH "")
        set(JAVA_AWT_LIBRARY "JAVA_AWT_LIBRARY-NOTFOUND" CACHE FILEPATH "")

        set(JNI_FOUND TRUE)
        set(JNI_INCLUDE_DIRS "${JAVA_INCLUDE_PATH}")
        set(JNI_LIBRARIES "")
    endif()

    mark_as_advanced(JAVA_INCLUDE_PATH JAVA_INCLUDE_PATH2 JAVA_AWT_INCLUDE_PATH
                     JAVA_JVM_LIBRARY JAVA_AWT_LIBRARY)
endif()
