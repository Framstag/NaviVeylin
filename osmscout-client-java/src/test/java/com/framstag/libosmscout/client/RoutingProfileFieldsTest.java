package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the maximum speed field of {@link RoutingProfile}.
 *
 * The value is consumed by the JNI bridge (native side cannot be exercised on the host JVM), so this
 * test pins the Java-side contract the bridge reads: the field's name and type, the sentinel that means
 * "use the routing engine's built-in profile", and the fact that the constructors which existed before
 * the field do not ask for a speed.
 */
class RoutingProfileFieldsTest {

    @Test
    void maxSpeedFieldExistsAsDouble() throws NoSuchFieldException {
        Field field = RoutingProfile.class.getField("maxSpeedKmh");

        assertEquals(double.class, field.getType(), "maxSpeedKmh must be a double field");
    }

    @Test
    void vehicleAndSpeedConstructorCarriesTheSpeed() {
        RoutingProfile profile = new RoutingProfile(Vehicle.BICYCLE, 20.0);

        assertEquals(Vehicle.BICYCLE, profile.vehicle);
        assertEquals(20.0, profile.maxSpeedKmh);
        assertFalse(profile.avoidTolls);
        assertFalse(profile.avoidFerries);
        assertFalse(profile.avoidUnpaved);
    }

    @Test
    void fullConstructorCarriesTheSpeed() {
        RoutingProfile profile = new RoutingProfile(Vehicle.PEDESTRIAN, true, false, true, 4.0);

        assertEquals(Vehicle.PEDESTRIAN, profile.vehicle);
        assertEquals(4.0, profile.maxSpeedKmh);
        assertTrue(profile.avoidTolls);
        assertFalse(profile.avoidFerries);
        assertTrue(profile.avoidUnpaved);
    }

    @Test
    void legacyConstructorsRequestTheBuiltInProfile() {
        assertEquals(0.0, new RoutingProfile().maxSpeedKmh);
        assertEquals(0.0, new RoutingProfile(Vehicle.BICYCLE).maxSpeedKmh);
        assertEquals(0.0, new RoutingProfile(Vehicle.CAR, true, true, true).maxSpeedKmh);
    }

    @Test
    void legacyConstructorsKeepTheirOtherFieldDefaults() {
        RoutingProfile profile = new RoutingProfile();

        assertEquals(Vehicle.CAR, profile.vehicle);
        assertFalse(profile.avoidTolls);
        assertFalse(profile.avoidFerries);
        assertFalse(profile.avoidUnpaved);
    }
}
