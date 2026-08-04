package com.kaajjo.libresudoku.ui.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaajjo.libresudoku.ui.theme.KanjiThemeColors

@Composable
fun KanjiInputBar(
    symbols: List<String>,
    selected: String?,
    remainingUses: List<Int>? = null,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    theme: KanjiThemeColors? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        symbols.forEachIndexed { index, symbol ->
            val remaining = remainingUses?.getOrNull(index)
            val isDepleted = remaining != null && remaining <= 0
            val isSelected = selected == symbol
            val shape = RoundedCornerShape(12.dp)

            val themedBrush: Brush? = when {
                theme == null -> null

                isSelected -> theme.primaryButton

                isDepleted -> Brush.linearGradient(
                    colors = listOf(
                        theme.boardBackground.copy(alpha = 0.45f),
                        theme.boardBackground.copy(alpha = 0.45f)
                    )
                )

                else -> theme.secondaryButton
            }

            val fallbackColor = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isDepleted -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }

            val symbolColor = when {
                theme != null && isSelected -> Color.White

                theme != null && isDepleted ->
                    theme.secondaryText.copy(alpha = 0.4f)

                theme != null -> theme.primaryText

                isSelected -> MaterialTheme.colorScheme.onPrimary

                isDepleted ->
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

                else -> MaterialTheme.colorScheme.onSurface
            }

            val borderColor = when {
                theme != null && isSelected ->
                    theme.accent.copy(alpha = 0.9f)

                theme != null ->
                    theme.boardBorder.copy(alpha = 0.14f)

                isSelected ->
                    MaterialTheme.colorScheme.primary

                else ->
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .clip(shape)
                    .then(
                        if (themedBrush != null) {
                            Modifier.background(
                                brush = themedBrush,
                                shape = shape
                            )
                        } else {
                            Modifier.background(
                                color = fallbackColor,
                                shape = shape
                            )
                        }
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.75.dp,
                        color = borderColor,
                        shape = shape
                    )
                    .clickable(enabled = !isDepleted) {
                        onSelect(symbol)
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = symbol,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Medium,
                        color = symbolColor
                    )

                    if (remaining != null) {
                        Text(
                            text = remaining.toString(),
                            fontSize = 11.sp,
                            color = symbolColor.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}