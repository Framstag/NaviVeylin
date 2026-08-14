package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesScreenMapperTest {

    @Test
    fun buildDescription_withAddress() {
        val fav = FavoriteLocation("Home", 51.5, 7.5)
        fav.attributes["address"] = "123 Main St"
        assertEquals("123 Main St", FavoritesScreenMapper.buildDescription(fav))
    }

    @Test
    fun buildDescription_withoutAddress() {
        val fav = FavoriteLocation("Home", 51.5, 7.5)
        assertEquals("", FavoritesScreenMapper.buildDescription(fav))
    }

    @Test
    fun buildDescription_emptyAddress() {
        val fav = FavoriteLocation("Home", 51.5, 7.5)
        fav.attributes["address"] = ""
        assertEquals("", FavoritesScreenMapper.buildDescription(fav))
    }
}
