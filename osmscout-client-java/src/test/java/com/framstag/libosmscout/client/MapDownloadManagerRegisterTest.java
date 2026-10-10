package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the map-directory registration entry point the repository download path uses.
 * <p>
 * Spec: map-download-infrastructure — "Repository download completes into the installed list".
 * The host test stub in {@code app/src/test/jniLibs} exports no JNI symbols, so this class pins the
 * contract instead of executing native code: the public delegating method exists, the native
 * function it delegates to is still declared (a rename would break JNI linkage silently), and the
 * method stays overridable so app-side tests can substitute it.
 */
class MapDownloadManagerRegisterTest {

    @Test
    void registerMapDirectoryIsPublicAndNotNative() throws Exception {
        Method method = MapDownloadManager.class.getMethod("registerMapDirectory", String.class);

        assertTrue(Modifier.isPublic(method.getModifiers()), "must be callable from the app");
        assertFalse(Modifier.isStatic(method.getModifiers()), "must be an instance call");
        assertFalse(Modifier.isNative(method.getModifiers()), "the public method delegates to the JNI function");
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    void registerMapDirectoryStaysOverridableForTests() throws Exception {
        Method method = MapDownloadManager.class.getMethod("registerMapDirectory", String.class);

        assertFalse(Modifier.isFinal(method.getModifiers()),
                    "app-side tests substitute this method; it must not be final");
    }

    @Test
    void nativeRegistrationFunctionIsStillDeclared() throws Exception {
        Method nativeMethod = MapDownloadManager.class.getDeclaredMethod("nativeRegisterMapDirectory",
                                                                          String.class);

        assertTrue(Modifier.isNative(nativeMethod.getModifiers()),
                   "the JNI function the public method wraps must remain a native declaration");
        assertEquals(boolean.class, nativeMethod.getReturnType());
    }

    @Test
    void subclassSeesTheRegisteredPath() {
        RecordingManager manager = new RecordingManager();

        assertTrue(manager.registerMapDirectory("/data/user/0/app/files/maps/berlin"));

        assertEquals(List.of("/data/user/0/app/files/maps/berlin"), manager.registered);
    }

    /** A manager that records what it was asked to register instead of executing native code. */
    private static final class RecordingManager extends MapDownloadManager {

        private final List<String> registered = new ArrayList<>();

        RecordingManager() {
            super(null);
        }

        @Override
        public boolean registerMapDirectory(String path) {
            registered.add(path);
            return true;
        }
    }
}
