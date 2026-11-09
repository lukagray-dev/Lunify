package com.lunify.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lunify.ui.navigation.NavRoute
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.GlassPill
import com.lunify.ui.theme.GlassSurfaceElevated
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Floating Liquid Glass Navigation Bar (AppBottomNavigationBar.kt)
 * ==============================================================================
 *
 * Hey friend! This component recreates SimpMusic's iconic floating navigation:
 *
 * 1. Liquid Glass Capsule:
 *    - Houses our primary destinations: Home, Library, Duo, Settings.
 *    - Active tabs are highlighted with a glowing neon cyan backing pill.
 * 2. Floating Glass Search FAB:
 *    - Positioned adjacent to the main capsule as a dedicated circular button.
 * 3. Elevated Floating Geometry:
 *    - Floats cleanly above the system navigation bar (`navigationBarsPadding`),
 *      leaving room for the floating MiniPlayer directly above it!
 * ==============================================================================
 */

/**
 * Navigation item representation for bottom bar destinations.
 */
data class BottomNavItem(
    val route: String,
    val iconRes: Int,
    val label: String,
)

val BottomNavItems = listOf(
    BottomNavItem(NavRoute.HOME, LucideIcons.Home, "Home"),
    BottomNavItem(NavRoute.LIBRARY, LucideIcons.Download, "Library"),
    BottomNavItem(NavRoute.SETTINGS, LucideIcons.Settings, "Settings"),
)

@Composable
fun AppBottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Primary Liquid Glass Capsule Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .liquidGlass(
                    shape = CircleShape,
                    backgroundColor = GlassSurfaceElevated,
                    interactive = false,
                )
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomNavItems.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val animatedIconColor by animateColorAsState(
                        targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f),
                        animationSpec = spring(),
                        label = "bottomNavIconTint",
                    )
                    val animatedPillColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color.Transparent,
                        animationSpec = spring(),
                        label = "bottomNavPillColor",
                    )

                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(CircleShape)
                            .background(animatedPillColor)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(bounded = true, color = Color.White),
                                onClick = { onNavigate(item.route) },
                            )
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LucideIcon(
                            iconRes = item.iconRes,
                            contentDescription = item.label,
                            size = 22.dp,
                            tint = animatedIconColor,
                        )
                    }
                }
            }
        }

        // 2. Floating Circular Search FAB (Matching SimpMusic's standalone search button)
        val isSearchSelected = currentRoute == NavRoute.SEARCH
        val searchFabColor by animateColorAsState(
            targetValue = if (isSearchSelected) Color.White else GlassSurfaceElevated,
            animationSpec = spring(),
            label = "searchFabBg",
        )
        val searchIconColor by animateColorAsState(
            targetValue = if (isSearchSelected) Color.Black else Color.White,
            animationSpec = spring(),
            label = "searchFabIcon",
        )

        Box(
            modifier = Modifier
                .size(60.dp)
                .liquidGlass(
                    shape = CircleShape,
                    backgroundColor = searchFabColor,
                    interactive = false,
                )
                .clip(CircleShape)
                .clickable {
                    onNavigate(NavRoute.SEARCH)
                },
            contentAlignment = Alignment.Center,
        ) {
            LucideIcon(
                iconRes = LucideIcons.Search,
                contentDescription = "Search YouTube Music",
                size = 22.dp,
                tint = searchIconColor,
            )
        }
    }
}
