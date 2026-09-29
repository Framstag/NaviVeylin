package com.naviveylin.core

/**
 * Reconciles a group order with the groups a list actually holds.
 *
 * The order arrives on its own channel (`FavoriteRepository.groupOrder`,
 * `AutoFavoritesProvider.groupOrder`) because a group map cannot express "same
 * contents, different order" — so the two are read from separate flows and can be
 * observed a moment apart. [orderedGroupNames] resolves that pairing:
 *
 * - every name the order lists that the map still holds, in the stored sequence;
 * - then any group the order does not name, in the map's own order, so a group can
 *   never disappear from a list just because the order channel lags it.
 *
 * Pure, so both surfaces (phone grid, car place list) share one tested rule instead
 * of two similar loops.
 */
fun orderedGroupNames(order: List<String>, known: Set<String>): List<String> {
    val ordered = order.filter { it in known }
    val unnamed = known.filterNot { it in order }
    return ordered + unnamed
}
