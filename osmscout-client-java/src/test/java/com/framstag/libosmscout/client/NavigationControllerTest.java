package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for the navigation controller the client hands back after a route has been calculated.
 * <p>
 * The class's constructor is protected because JNI creates it by reflection and because app-side tests
 * substitute a controller for the native one; the declaration is the contract those two callers rely on.
 * Without this case the class was loaded nowhere in the module's suite.
 */
class NavigationControllerTest {

    @Test
    void aControllerCanBeSubstitutedByASubclass() {
        RecordingController controller = new RecordingController();

        controller.processLocation(51.5, 7.4, 12.0, 5.0, 1_700_000_000_000L);
        controller.stop();

        assertEquals(1, controller.locations, "a substituting subclass sees the updates the engine feeds");
        assertEquals(1, controller.stops);
        assertEquals(NavigationController.class, RecordingController.class.getSuperclass());
    }

    /** Stands in for the native controller, as the app-side tests do. */
    private static final class RecordingController extends NavigationController {
        private int locations;
        private int stops;

        @Override
        public void processLocation(double lat, double lon, double speed,
                                    double accuracy, long timestamp) {
            locations++;
        }

        @Override
        public void stop() {
            stops++;
        }
    }
}
