package com.lunify.ui.navigation

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
import com.lunify.ui.duo.DuoScreen
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
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = NavRoute.HOME,
        modifier = modifier,
        enterTransition = { fadeIn() + slideInHorizontally { it / 3 } },
        exitTransition = { fadeOut() + slideOutHorizontally { -it / 3 } },
        popEnterTransition = { fadeIn() + slideInHorizontally { -it / 3 } },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 3 } },
    ) {
        composable(NavRoute.HOME) {
            HomeScreen(
                playerState = playerState,
                onTrackSelect = onTrackSelect,
                onAlbumSelect = onAlbumSelect,
                onOpenSettings = { navController.navigate(NavRoute.SETTINGS) },
                onOpenDuo = onOpenDuo,
                onOpenTrackOptions = onOpenTrackOptions,
            )
        }

        composable(NavRoute.SEARCH) {
            SearchScreen(
                playerState = playerState,
                onTrackSelect = onTrackSelect,
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
            )
        }

        composable(NavRoute.DUO) {
            DuoScreen(
                playerState = playerState,
                onTrackSelect = onTrackSelect,
            )
        }

        composable(NavRoute.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
