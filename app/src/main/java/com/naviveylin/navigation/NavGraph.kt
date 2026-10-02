package com.naviveylin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.naviveylin.data.StartMapResolver
import com.naviveylin.ui.MainScreen
import com.naviveylin.ui.map.MapCanvasScreen
import com.naviveylin.ui.mapmanager.MapManagerScreen
import java.util.Base64

object Routes {
    const val MAIN = "main"
    const val MAP_MANAGER = "map_manager"
    const val MAP_CANVAS = "map_canvas/{mapPath}"

    fun mapCanvas(mapPath: String) =
        "map_canvas/${Base64.getUrlEncoder().encodeToString(mapPath.toByteArray())}"
}

@Composable
fun NavGraph(startMapResolver: StartMapResolver) {
    val navController = rememberNavController()

    // The start destination is the map resolved by the shared rule — the last opened one, else the
    // deterministic fallback, else the entry point when nothing is installed (spec: start-map-selection).
    // Resolution is off-main, so the composition shows nothing until the answer arrives.
    var startDest by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDest = startMapResolver.resolve()
            ?.let { Routes.mapCanvas(it) }
            ?: Routes.MAIN
    }

    val destination = startDest
    if (destination == null) return // still loading

    NavHost(navController = navController, startDestination = destination) {
        composable(Routes.MAIN) {
            // Re-initialise the map view every time MAIN becomes the current
            // destination, so maps downloaded in the Map Manager appear
            // immediately after navigating back (fix-download).
            val backStackEntry by navController.currentBackStackEntryAsState()
            val isCurrent = backStackEntry?.destination?.route == Routes.MAIN
            var visitCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(isCurrent) {
                if (isCurrent) visitCount++
            }

            // The map shown here comes from the same resolver as the start destination, so a map
            // downloaded in the Map Manager appears immediately after navigating back
            // (fix-download) and the discovery rule has one source (spec:
            // map-download-infrastructure — callers agree on the installed set).
            var startMap by remember(visitCount) { mutableStateOf<String?>(null) }
            var resolved by remember(visitCount) { mutableStateOf(false) }
            LaunchedEffect(visitCount) {
                startMap = startMapResolver.resolve()
                resolved = true
            }

            val mapPath = startMap
            if (!resolved) {
                // Still resolving: show neither screen, as the start destination does.
            } else if (mapPath == null) {
                MainScreen(
                    onNavigateToMapManager = {
                        navController.navigate(Routes.MAP_MANAGER)
                    }
                )
            } else {
                key(visitCount) {
                    MapCanvasScreen(
                        mapPath = mapPath,
                        onNavigateToMapManager = {
                            navController.navigate(Routes.MAP_MANAGER)
                        }
                    )
                }
            }
        }
        composable(Routes.MAP_MANAGER) {
            MapManagerScreen(
                onBack = { navController.popBackStack() },
                onMapSelected = { mapPath ->
                    navController.navigate(Routes.mapCanvas(mapPath))
                }
            )
        }
        composable(
            route = Routes.MAP_CANVAS,
            arguments = listOf(navArgument("mapPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("mapPath") ?: return@composable
            val mapPath = String(Base64.getUrlDecoder().decode(raw))
            MapCanvasScreen(
                mapPath = mapPath,
                onNavigateToMapManager = {
                    navController.navigate(Routes.MAP_MANAGER)
                }
            )
        }
    }
}
