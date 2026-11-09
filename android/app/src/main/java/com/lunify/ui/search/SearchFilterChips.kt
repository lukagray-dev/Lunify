package com.lunify.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.GlassPill
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Search Category Filter Chips (SearchFilterChips.kt)
 * ==============================================================================
 *
 * Hey friend! This component allows users to narrow down YouTube Music search
 * results to specific media types: All, Songs, Videos, Albums, and Artists.
 * ==============================================================================
 */

val SearchCategories = listOf("All", "Songs", "Videos", "Albums", "Artists")

@Composable
fun SearchFilterChips(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SearchCategories) { category ->
            val isSelected = category.equals(selectedCategory, ignoreCase = true)
            val animatedBgColor by animateColorAsState(
                targetValue = if (isSelected) AccentCyan else GlassPill,
                animationSpec = spring(),
                label = "searchFilterBg",
            )
            val animatedTextColor by animateColorAsState(
                targetValue = if (isSelected) Color.Black else TextSecondary,
                animationSpec = spring(),
                label = "searchFilterText",
            )

            val interactionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .height(34.dp)
                    .liquidGlass(
                        shape = CircleShape,
                        backgroundColor = animatedBgColor,
                        interactive = true,
                        borderWidth = if (isSelected) 0.dp else 1.dp,
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = Color.White),
                        onClick = { onCategorySelected(category) },
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category,
                    color = animatedTextColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
