package com.lunify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunify.domain.model.TrackSortOption
import com.lunify.ui.theme.GlassPill
import com.lunify.ui.theme.GlassSheetBackground
import com.lunify.ui.theme.TextMuted
import com.lunify.ui.theme.TextPrimary
import com.lunify.ui.theme.TextSecondary
import com.lunify.ui.theme.liquidGlass

/**
 * ==============================================================================
 * Lunify - Sort Options Sheet (SortOptionsSheet.kt)
 * ==============================================================================
 *
 * Hey friend! This pop-up sheet allows sorting the downloaded tracks by:
 * 1. Name: Alphabetical A to Z.
 * 2. Time created: Newest added files first.
 * 3. Times played: Most frequently listened to songs first.
 * 4. Duration: Longest tracks first.
 * ==============================================================================
 */

@Composable
fun SortOptionsSheet(
    activeSortOption: TrackSortOption,
    onSelectSortOption: (TrackSortOption) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                backgroundColor = GlassSheetBackground,
                interactive = false,
                borderWidth = 1.dp,
            )
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(TextMuted.copy(alpha = 0.4f), CircleShape),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sort Tracks By",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )

                LucideIconButton(
                    iconRes = LucideIcons.Close,
                    contentDescription = "Close sort options",
                    onClick = onClose,
                    iconSize = 20.dp,
                    buttonSize = 36.dp,
                    tint = TextMuted,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TrackSortOption.entries.forEach { option ->
                val isSelected = option == activeSortOption

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) GlassPill else Color.Transparent)
                        .clickable {
                            onSelectSortOption(option)
                            onClose()
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.displayName,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )

                    if (isSelected) {
                        LucideIcon(
                            iconRes = LucideIcons.Check,
                            contentDescription = "Selected",
                            size = 18.dp,
                            tint = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
