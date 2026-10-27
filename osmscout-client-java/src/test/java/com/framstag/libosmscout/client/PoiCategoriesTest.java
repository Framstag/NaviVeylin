package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the POI category table of the client API.
 * <p>
 * The table is the contract between the app's POI search and the stylesheet the database was imported
 * with: a category that maps to no type name is a search that silently returns nothing, and a type name
 * the stylesheet does not define is a search that can never match. The class had no test of its own, so
 * the table loaded nowhere in the module's suite.
 */
class PoiCategoriesTest {

    @Test
    void everyCategoryMapsToAtLeastOneOsmType() {
        Map<String, String[]> categories = PoiCategories.getCategoryTypes();

        assertEquals(14, categories.size(), "one entry per documented category id");
        for (Map.Entry<String, String[]> category : categories.entrySet()) {
            String[] types = category.getValue();
            assertNotNull(types, category.getKey() + " must name its type names");
            assertTrue(types.length > 0, category.getKey() + " must name at least one OSM type");
            for (String type : types) {
                assertNotNull(type, category.getKey() + " must not carry a null type name");
                assertTrue(type.contains("_"),
                    category.getKey() + " names " + type + ", which is not an OSM type name");
            }
        }
    }

    @Test
    void everyCategoryConstantIsAKeyOfTheTable() {
        String[] ids = {
            PoiCategories.HOTELS,
            PoiCategories.RESTAURANTS,
            PoiCategories.GROCERY,
            PoiCategories.VIEWPOINT,
            PoiCategories.MUSEUM,
            PoiCategories.FUEL,
            PoiCategories.CHARGING_STATION,
            PoiCategories.ATM,
            PoiCategories.TOURISM,
            PoiCategories.PARKING,
            PoiCategories.POLICE,
            PoiCategories.HOSPITAL,
            PoiCategories.DOCTORS,
            PoiCategories.PUBLIC_TRANSPORT
        };

        for (String id : ids) {
            assertNotNull(PoiCategories.getTypeNames(id),
                "the category constant " + id + " must be a key of the table");
        }
    }

    @Test
    void aCategorysTypeNamesAreClonedNotShared() {
        String[] first = PoiCategories.getTypeNames(PoiCategories.HOTELS);
        first[0] = "mutated_by_the_caller";

        String[] second = PoiCategories.getTypeNames(PoiCategories.HOTELS);

        assertEquals("tourism_hotel", second[0], "a caller must not be able to edit the table");
    }

    @Test
    void anUnknownCategoryHasNoTypeNames() {
        assertNull(PoiCategories.getTypeNames("grocery_store_that_does_not_exist"));
    }

    @Test
    void theCategoryTableIsUnmodifiable() {
        Map<String, String[]> categories = PoiCategories.getCategoryTypes();

        assertThrows(UnsupportedOperationException.class,
            () -> categories.put("invented", new String[]{"invented_type"}));
    }
}
