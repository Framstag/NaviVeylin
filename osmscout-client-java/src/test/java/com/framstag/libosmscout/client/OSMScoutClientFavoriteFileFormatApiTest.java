package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the favorite file-format natives of the Android override.
 * <p>
 * Spec: osmscout-jni — "The Android override declares the submodule's favorite file-format
 * natives". The override shadows the submodule's {@code OSMScoutClient.java}, so the app compiles
 * against this class, not the submodule's. When the submodule's favorites API grew its file-format
 * accessors, the hand-maintained override was not updated, and because the app never called them
 * the drift was silent: the missing declaration only shows as a {@link NoSuchMethodError} at the
 * call site. This case pins the declaration, so the drift cannot return unnoticed.
 */
class OSMScoutClientFavoriteFileFormatApiTest {

    @Test
    void favoriteFileFormatNativesAreDeclaredOnTheOverride() throws Exception {
        Method version = declaredNative("getFavoriteFileFormatVersion");
        assertEquals(int.class, version.getReturnType(),
            "getFavoriteFileFormatVersion returns the file's format version as an int");

        Method supported = declaredNative("isFavoriteFileFormatSupported");
        assertEquals(boolean.class, supported.getReturnType(),
            "isFavoriteFileFormatSupported returns a boolean");

        System.out.println("declared natives: " + version + " / " + supported);
    }

    private static Method declaredNative(String name) throws NoSuchMethodException {
        Method method = OSMScoutClient.class.getDeclaredMethod(name);
        assertTrue(Modifier.isNative(method.getModifiers()), name + " must be a native method");
        return method;
    }
}
