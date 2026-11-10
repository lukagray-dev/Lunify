package com.lunify.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lunify.data.local.LibraryStorage
import com.lunify.data.repository.LocalAudioRepository
import com.lunify.domain.model.Album
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.Track
import com.lunify.domain.model.TrackSortOption
import com.lunify.player.AudioPlayerManager
import com.lunify.player.duo.DuoManager
import com.lunify.ui.home.HomeScreen
import com.lunify.ui.library.LibraryScreen
import com.lunify.ui.search.SearchScreen
import com.lunify.ui.settings.SettingsScreen

/**
 * ==============================================================================
 * Lunify - Navigation Graph (AppNavigationGraph.kt)
 * ==============================================================================
 *
 * Hey friend! This composable manages transitions between all primary stages:
 * - Home discovery
 * - Search catalog
 * - Offline library (with real device storage & playback)
 * - Duo synchronization room
 * - Settings
 *
 * It uses smooth horizontal slide and crossfade animations.
 * ==============================================================================
 */

@Composable
fun AppNavigationGraph(
    navController: NavHostController,
    playerState: PlayerState,
    onTrackSelect: (Track) -> Unit,
    onPlayQueue: (List<Track>, Track) -> Unit,
    onAlbumSelect: (Album) -> Unit,
    onOpenDuo: () -> Unit,
    onOpenTrackOptions: (Track) -> Unit,
    currentSortOption: TrackSortOption,
    onOpenSortSheet: () -> Unit,
    libraryRefreshTrigger: Int,
    audioPlayerManager: AudioPlayerManager,
    libraryStorage: LibraryStorage,
    localAudioRepository: LocalAudioRepository,
    duoManager: DuoManager,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = NavRoute.HOME,
        modifier = modifier,
        enterTransition = {
            val fromIndex = getRouteIndex(initialState.destination.route)
            val toIndex = getRouteIndex(targetState.destination.route)
            val direction = if (toIndex >= fromIndex) 1 else -1
            slideInHorizontally(
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> fullWidth * direction },
            ) + fadeIn(animationSpec = tween(340))
        },
        exitTransition = {
            val fromIndex = getRouteIndex(initialState.destination.route)
            val toIndex = getRouteIndex(targetState.destination.route)
            val direction = if (toIndex >= fromIndex) -1 else 1
            slideOutHorizontally(
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> fullWidth * direction },
            ) + fadeOut(animationSpec = tween(340))
        },
        popEnterTransition = {
            val fromIndex = getRouteIndex(initialState.destination.route)
            val toIndex = getRouteIndex(targetState.destination.route)
            val direction = if (toIndex >= fromIndex) 1 else -1
            slideInHorizontally(
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> fullWidth * direction },
            ) + fadeIn(animationSpec = tween(340))
        },
        popExitTransition = {
            val fromIndex = getRouteIndex(initialState.destination.route)
            val toIndex = getRouteIndex(targetState.destination.route)
            val direction = if (toIndex >= fromIndex) -1 else 1
            slideOutHorizontally(
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> fullWidth * direction },
            ) + fadeOut(animationSpec = tween(340))
        },
    ) {
        composable(NavRoute.HOME) {
            HomeScreen(
                playerState = playerState,
                onTrackSelect = onTrackSelect,
                onPlayQueue = onPlayQueue,
                onAlbumSelect = onAlbumSelect,
                onOpenSettings = { navController.navigate(NavRoute.SETTINGS) },
                onOpenDuo = onOpenDuo,
                onOpenTrackOptions = onOpenTrackOptions,
                duoManager = duoManager,
            )
        }

        composable(NavRoute.SEARCH) {
            SearchScreen(
                playerState = playerState,
                onTrackSelect = onTrackSelect,
                onPlayQueue = onPlayQueue,
                onOpenTrackOptions = onOpenTrackOptions,
            )
        }

        composable(NavRoute.LIBRARY) {
            LibraryScreen(
                playerState = playerState,
                onTrackSelect = onTrackSelect,
                onPlayQueue = onPlayQueue,
                onOpenTrackOptions = onOpenTrackOptions,
                currentSortOption = currentSortOption,
                onOpenSortSheet = onOpenSortSheet,
                refreshTrigger = libraryRefreshTrigger,
                audioPlayerManager = audioPlayerManager,
                libraryStorage = libraryStorage,
                localAudioRepository = localAudioRepository,
                duoManager = duoManager,
            )
        }

        composable(NavRoute.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Returns a linear index for primary navigation routes to determine directional swipe animations.
 * When navigating to a higher index, screens slide from right to left; when navigating to a lower
 * index, screens slide from left to right.
 */
private fun getRouteIndex(route: String?): Int {
    return when (route) {
        NavRoute.HOME -> 0
        NavRoute.LIBRARY -> 1
        NavRoute.SEARCH -> 2
        NavRoute.SETTINGS -> 3
        else -> 0
    }
}
