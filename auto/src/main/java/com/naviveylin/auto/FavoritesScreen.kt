package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.core.orderedGroupNames
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto screen for browsing favorite locations using [ListTemplate]. Backed by
 * [AutoFavoritesProvider] via [AutoEntryPoint].
 *
 * Three modes, all of them read-only browsers whose rows start the destination-picker flow:
 *
 * - **The all-favorites level** (`starredOnly = false`, `groupName = null`): one row per non-empty
 *   group, in the stored group order, showing the group name and its favorite count. A group row
 *   opens that group's level (spec: auto-favorites — The all-favorites mode lists groups first).
 * - **A group's level** (`groupName` set): that group's favorites in the stored favorite order.
 * - **The starred level** (`starredOnly = true`): the stored starred order, one list spanning all
 *   groups, so it has no group rows (spec `auto-favorites`; spec `starred-ordering`).
 *
 * Every level presents at most the row capacity the connected host displays and keeps the remainder
 * reachable through its final page row (spec: `car-list-row-limit`; design D4) — the host silently
 * ignores rows over that capacity, which is how the flat sectioned list this replaces used to lose
 * groups and favorites without a signal.
 *
 * Uses [ListTemplate] (not [PlaceListNavigationTemplate]): the place-list template requires every
 * non-browsable row to carry a distance span and every browsable row to carry a click listener,
 * neither of which applies to favorites browsing — building it would fail with an
 * IllegalArgumentException.
 */
class FavoritesScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    private val starredOnly: Boolean = false,
    /** The group whose own level this screen shows, or null for the group level (design D3). */
    private val groupName: String? = null,
    /** The page of this level to show; 0 is the level's first page (design D4). */
    private val page: Int = 0,
    /**
     * Dispatcher of the hop that touches the native client (design D1; spec: auto-map-renderer —
     * "A screen observation resolves its provider off the host thread"). Defaults to
     * [Dispatchers.Default], the production value; injectable so a case can own the hop it asserts.
     */
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default
) : Screen(carContext) {

    private val scope = carScreenScope("FavoritesScreen")

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val favoritesProvider = entryPoint.autoFavoritesProvider()

    /**
     * Shared-state observations for this screen (spec: auto/screen-observation; design D6):
     * [CarScreenObservations] owns how long the observation lives,
     * [FavoritesScreenObservations] owns what is observed. Started in `onStart`, stopped in
     * `onStop`, so a stopped screen issues no template invalidation and a reorder received while
     * stopped is applied once on the next start (`auto-favorites` — the clarity clause added by this
     * change).
     *
     * Declared before the `init` block on purpose: everything the observation touches is initialized
     * here, never by an `init` body that runs during construction (TODO.md §80).
     */
    private val observations = CarScreenObservations()
    private val screenObservations = FavoritesScreenObservations(
        observations = observations,
        favoritesProvider = favoritesProvider,
        backgroundDispatcher = backgroundDispatcher,
        onFavorites = ::onFavorites
    )

    private var favoritesData: Map<String, List<FavoriteLocation>> = emptyMap()

    /**
     * Stored group order (`AutoFavoritesProvider.groupOrder`): the group level's row sequence.
     * Read from its own flow, not from the map's iteration order — a group reorder
     * leaves the map contents equal, so the map flow may not re-emit (spec
     * `auto-favorites` — Group rows follow the stored group order).
     */
    private var groupOrder: List<String> = emptyList()

    /**
     * The stored starred order (`AutoFavoritesProvider.starredOrder`): one sequence
     * spanning all groups, what the starred mode renders. Read from its own flow for
     * the same reason as the group order — the order is not a property the group map
     * can express.
     */
    private var starredOrder: List<StarredFavoriteLocation> = emptyList()

    /**
     * The row capacity this screen presents (spec: `car-list-row-limit`; design D2). Starts at the
     * library default so the first template — which the host may ask for before the query returns —
     * is already bounded, and is replaced by the host's answer on the screen's own scope.
     */
    private var rowCapacity: Int = CarListCapacityReader.LIBRARY_DEFAULT

    /**
     * Whether the favourites store's initialisation attempt has finished (spec: `auto-favorites` —
     * The favourites list distinguishes a loading store from an empty one; design D3). The loading row
     * is shown only while this is false: the store publishes its contents from an empty map, so without
     * it an uninitialised store would be indistinguishable from a store that genuinely holds none.
     */
    private var storeReady = false
    private var loaded = false

    init {
        enableBackNavigation()
        // The favorites, group order and starred order are NOT collected here: they are this
        // screen's shared-state observation and live on the started period (`screenObservations`,
        // started in onStart / stopped in onStop), so a stopped screen never invalidates the host
        // and can never accumulate a second collector per start (spec: auto/screen-observation;
        // design D6).
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                screenObservations.start()
                // The capacity is asked once per started period and cached in a field; the template
                // builder never queries the host (design D2 — the read belongs off the host thread,
                // and every screen asks for itself: spec `car-list-row-limit` — Capacity is re-read
                // per screen).
                readRowCapacity()
            }
            override fun onStop(owner: LifecycleOwner) {
                // Every observation ends with the started period (spec: auto/screen-observation —
                // One instance of each observation per started period).
                observations.stop()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                // A destroy without a stop must not leave an observation running.
                observations.stop()
                // Cancel the screen's own scope (row actions, pushes, the capacity read) so its work
                // does not outlive the screen either.
                scope.cancel()
            }
        })
    }

    /**
     * Ask the host for this screen's row capacity, off the host thread, and re-render with the answer
     * (spec: `car-list-row-limit` — A car list never presents more rows than the host displays; design
     * D2). A host below car API 2, a host whose query fails and an unusable answer all take the
     * library's default instead ([CarListCapacityReader]), which the reader records as a
     * coordinate-free diagnostics line.
     */
    private fun readRowCapacity() {
        scope.launch {
            val capacity = withContext(backgroundDispatcher) { CarListCapacityReader.read(carContext) }
            if (capacity != rowCapacity) {
                rowCapacity = capacity
                invalidate()
            }
        }
    }

    /**
     * One emitted list state of the started period (spec: auto/screen-observation; design D6):
     * favourites, the stored group order, the stored starred order and the store's readiness, applied
     * together and rendered in place (spec: auto-favorites — favorites appear without re-entering the
     * screen, and a reorder updates the rows/headers in place).
     */
    private fun onFavorites(
        favorites: Map<String, List<FavoriteLocation>>,
        order: List<String>,
        starred: List<StarredFavoriteLocation>,
        ready: Boolean
    ) {
        favoritesData = favorites
        groupOrder = order
        starredOrder = starred
        storeReady = ready
        loaded = true
        invalidate()
    }

    override fun onGetTemplate(): ListTemplate = carListTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carListTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): ListTemplate {
        val builder = ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(title())
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )

        // Three states, in this order (spec: auto-favorites — The favourites list distinguishes a
        // loading store from an empty one; design D3): the loading row while nothing has been published
        // or the store is not initialised yet; then either the list or, only for a store that is ready
        // and genuinely empty, the empty state.
        if (!loaded || !storeReady) {
            builder.setSingleList(loadingList())
            return builder.build()
        }
        if (favoritesData.isEmpty() && starredOrder.isEmpty()) {
            builder.setSingleList(emptyFavoritesList())
            return builder.build()
        }
        if (starredOnly && starredOrder.isEmpty()) {
            builder.setSingleList(noStarredList())
            return builder.build()
        }

        // The level's rows, capped at the host's capacity, with the remainder behind the final page
        // row (spec: car-list-row-limit — A car list never presents more rows than the host displays;
        // Items beyond the capacity stay reachable). The arithmetic is the mapper's, so the pages
        // partition the level: every item is reachable exactly once.
        val rows = FavoritesScreenMapper.page(levelRows(), capacity = rowCapacity, page = page)
        if (rows.rows.isEmpty()) {
            builder.setSingleList(emptyFavoritesList())
            return builder.build()
        }

        val itemList = ItemList.Builder()
        for (row in rows.rows) {
            itemList.addItem(rowFor(row))
        }
        if (rows.hasFurtherPage) {
            itemList.addItem(pageRow(rows.remaining))
        }
        builder.setSingleList(itemList.build())
        return builder.build()
    }

    /** The header title: the starred wording, the group's own name, or the all-favorites wording. */
    private fun title(): String {
        val group = groupName
        return when {
            starredOnly -> carContext.getString(R.string.starred_favorites)
            group != null -> group
            else -> carContext.getString(R.string.favorites)
        }
    }

    /**
     * The rows of the level this screen shows, in its stored order (spec: auto-favorites — Group rows
     * follow the stored group order / A group row opens that group's favorites; `starred-ordering` for
     * the starred level).
     */
    private fun levelRows(): List<LevelRow> {
        val group = groupName
        return when {
            starredOnly -> starredOrder.map { LevelRow.Favorite(it.favorite) }
            group != null -> favoritesData[group].orEmpty().map { LevelRow.Favorite(it) }
            else -> orderedGroupNames(groupOrder, favoritesData.keys).mapNotNull { name ->
                val favorites = favoritesData[name].orEmpty()
                if (favorites.isEmpty()) {
                    // A group holding no favorites is not listed on the car, because no car
                    // affordance can move a favorite into one (spec: auto-favorites — A group
                    // holding no favorites is not listed).
                    null
                } else {
                    LevelRow.Group(name = name, count = favorites.size)
                }
            }
        }
    }

    /** One row of a level: a group row on the group level, a favorite row on every level. */
    private sealed interface LevelRow {
        data class Group(val name: String, val count: Int) : LevelRow
        data class Favorite(val favorite: FavoriteLocation) : LevelRow
    }

    /** Render one level row: a group opens its own level, a favorite starts the destination picker. */
    private fun rowFor(row: LevelRow): Row = when (row) {
        is LevelRow.Group -> groupRow(row)
        is LevelRow.Favorite -> favoriteRow(row.favorite)
    }

    /**
     * A group row: the group name and the favorite count under the wording the phone's group card
     * uses (`plurals/favorite_count`), so the two surfaces stay at parity by construction. No color
     * indicator — a car list row carries no tinted background (spec: auto-favorites — Group headers
     * shown).
     *
     * The tap pushes the group's own level through [armScreenPush] (design D3/D6), so the push leaves
     * the host's click callback and a failing push is confined to a logged no-op.
     */
    private fun groupRow(row: LevelRow.Group): Row = Row.Builder()
        .setTitle(row.name)
        .addText(
            carContext.resources.getQuantityString(
                R.plurals.favorite_count,
                row.count,
                row.count
            )
        )
        .setOnClickListener {
            Log.d(TAG, "Group selected: ${row.name}")
            armScreenPush(carContext, scope, "FavoritesScreen (group)") {
                FavoritesScreen(
                    carContext = carContext,
                    navigationViewModel = navigationViewModel,
                    groupName = row.name,
                    backgroundDispatcher = backgroundDispatcher
                )
            }
        }
        .build()

    /**
     * A favorite row. Row tap selects the favorite (rows with a click listener must not also carry
     * row actions — ROW_CONSTRAINTS_SIMPLE); the group the favorite belongs to is the level it is
     * listed on.
     */
    private fun favoriteRow(favorite: FavoriteLocation): Row = Row.Builder()
        .setTitle(favorite.name ?: carContext.getString(R.string.unnamed_favorite))
        .addText(FavoritesScreenMapper.buildDescription(favorite))
        .setOnClickListener {
            Log.d(TAG, "Favorite selected: ${favorite.name}")
            navigationViewModel.navigateTo(
                favorite.lat,
                favorite.lon,
                origin = SurfaceOrigin.CAR
            )
        }
        .build()

    /**
     * The final page row of a level that holds more rows than the host displays: it names how many
     * items are left and opens the next slice of the same ordered sequence (spec:
     * `car-list-row-limit` — The final row opens the next page).
     *
     * The next page is a pushed screen of the same level, so the host's back navigation returns to the
     * page that opened it (spec: "Back navigation returns to the previous page").
     */
    private fun pageRow(remaining: Int): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.more_items, remaining))
        .setOnClickListener {
            Log.d(TAG, "Opening the next favorites page: $remaining more")
            armScreenPush(carContext, scope, "FavoritesScreen (page)") {
                FavoritesScreen(
                    carContext = carContext,
                    navigationViewModel = navigationViewModel,
                    starredOnly = starredOnly,
                    groupName = groupName,
                    page = page + 1,
                    backgroundDispatcher = backgroundDispatcher
                )
            }
        }
        .build()

    /** The loading row of an uninitialised store (spec: auto-favorites). */
    private fun loadingList(): ItemList = ItemList.Builder()
        .addItem(Row.Builder().setTitle(carContext.getString(R.string.loading)).build())
        .build()

    /** A ready store that genuinely holds nothing (spec: auto-favorites — Empty favorites state). */
    private fun emptyFavoritesList(): ItemList = ItemList.Builder()
        .addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.no_favorites_saved))
                .addText(carContext.getString(R.string.favorites_save_hint))
                .build()
        )
        .build()

    /** The starred level with nothing starred: its own hint, not the empty-store wording. */
    private fun noStarredList(): ItemList = ItemList.Builder()
        .addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.no_starred_favorites))
                .addText(carContext.getString(R.string.starred_hint))
                .build()
        )
        .build()

    companion object {
        private const val TAG = "FavoritesScreen"
    }
}
