package com.lunify.ui.home

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
import com.lunify.domain.model.MoodFilter
import com.lunify.ui.theme.AccentCyan
import com.lunify.ui.theme.GlassPill
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Mood & Genre Chips Row (MoodChipsRow.kt)
 * ==============================================================================
 *
 * Hey friend! This component renders the horizontal mood selector seen at the
 * top of YouTube Music and SimpMusic.
 *
 * Users can tap chips like "Relax", "Workout", or "Focus" to immediately filter
 * recommendations into contextual vibes with zero loading friction!
 * ==============================================================================
 */

val DefaultMoodFilters = listOf(
    MoodFilter("all", "All", isSelected = true),
    MoodFilter("relax", "Relax"),
    MoodFilter("workout", "Workout"),
    MoodFilter("energize", "Energize"),
    MoodFilter("focus", "Focus"),
    MoodFilter("commute", "Commute"),
    MoodFilter("party", "Party"),
    MoodFilter("sleep", "Sleep"),
)

@Composable
fun MoodChipsRow(
    moods: List<MoodFilter>,
    selectedMoodId: String,
    onMoodSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(moods) { mood ->
            val isSelected = mood.id == selectedMoodId
            val animatedBgColor by animateColorAsState(
                targetValue = if (isSelected) AccentCyan else GlassPill,
                animationSpec = spring(),
                label = "moodChipBg",
            )
            val animatedTextColor by animateColorAsState(
                targetValue = if (isSelected) Color.Black else TextSecondary,
                animationSpec = spring(),
                label = "moodChipText",
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
                        onClick = { onMoodSelected(mood.id) },
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mood.title,
                    color = animatedTextColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
