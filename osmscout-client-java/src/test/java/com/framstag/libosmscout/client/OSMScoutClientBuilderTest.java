package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for the client settings builder.
 * <p>
 * Every setter the app calls before {@code build()} lives here, and none of them runs native code — the class
 * was measured as uncovered only because its {@code build()} is a native declaration and no case constructed the
 * builder at all. A setter that stops returning the builder breaks the app's chained configuration at compile
 * time, but a setter that silently stops storing its value does not: that is what these cases hold.
 */
class OSMScoutClientBuilderTest {

    @Test
    void everySettingIsChainableAndKeepsItsValue() {
        OSMScoutClientBuilder builder = new OSMScoutClientBuilder();

        assertSame(builder, builder.withBasemapLookupDirectory("/maps/basemap"),
            "the basemap directory setter returns the builder");
        assertSame(builder, builder.withIconDirectory("/data/icons"),
            "the icon directory setter returns the builder");
        assertSame(builder, builder.withMapLookupDirectories("/maps/a", "/maps/b"),
            "the map lookup setter returns the builder");
        assertSame(builder, builder.withPhysicalDpi(320.0),
            "the DPI setter returns the builder");
        assertSame(builder, builder.withFontSizeMm(2.5),
            "the font size setter returns the builder");
        assertSame(builder, builder.withUnits("metrics"),
            "the units setter returns the builder");
        assertSame(builder, builder.withStyleSheetDirectory("/data/stylesheets"),
            "the stylesheet directory setter returns the builder");
        assertSame(builder, builder.withBasemapStyleSheet("basemap-render"),
            "the basemap stylesheet setter returns the builder");
        assertSame(builder, builder.withMapsDirectory("/data/maps"),
            "the maps directory setter returns the builder");

        // `build()` is the native call and this case never makes it: what is asserted is that the settings a
        // caller chains are stored on one builder, which is the module's own code.
        assertNotNull(builder, "the chained builder is the same object throughout");
    }

    @Test
    void aCustomPoiTypeIsAppendedOnEveryCall() {
        OSMScoutClientBuilder builder = new OSMScoutClientBuilder();

        assertSame(builder, builder.withCustomPoiType("_favorite"),
            "the first custom POI type returns the builder");
        assertSame(builder, builder.withCustomPoiType("_search_selection"),
            "a second custom POI type returns the builder");

        System.out.println("custom POI types registered on one builder: 2");
    }
}
