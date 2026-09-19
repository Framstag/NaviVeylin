package com.naviveylin.core.search

/**
 * Where a result row's markings come from: the favorite heart and the
 * perfect-match marking are independent facts about one row, so a row can carry
 * both (spec: search-result-ranking — "Perfect-match marking and cross-surface
 * parity").
 */
enum class ResultMarking {
    /** Neither a favorite nor a perfect match. */
    NONE,

    /** Matches a favorite (heart only). */
    FAVORITE,

    /** Perfect match for the query (perfect-match marking only). */
    PERFECT,

    /** A favorite that is also a perfect match for the query. */
    FAVORITE_AND_PERFECT
}

/** True when the marking includes the favorite heart. */
val ResultMarking.isFavorite: Boolean
    get() = this == ResultMarking.FAVORITE || this == ResultMarking.FAVORITE_AND_PERFECT

/** True when the marking includes the perfect-match glyph. */
val ResultMarking.isPerfect: Boolean
    get() = this == ResultMarking.PERFECT || this == ResultMarking.FAVORITE_AND_PERFECT

/**
 * Combine the two independent row facts into the marking a row shows. Pure, so
 * the phone row and the car row cannot disagree about what a row is.
 */
fun resultMarkingOf(isFavorite: Boolean, isPerfect: Boolean): ResultMarking = when {
    isFavorite && isPerfect -> ResultMarking.FAVORITE_AND_PERFECT
    isFavorite -> ResultMarking.FAVORITE
    isPerfect -> ResultMarking.PERFECT
    else -> ResultMarking.NONE
}
