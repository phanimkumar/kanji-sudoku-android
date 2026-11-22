package com.kaajjo.libresudoku.ui.game.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaajjo.libresudoku.KanjiMapper
import com.kaajjo.libresudoku.ui.components.board.KanjiFonts
import com.kaajjo.libresudoku.ui.theme.KanjiThemeColors

/** Traditional seal-ink red, shared with the board's 完 seal. */
val HankoRed = Color(0xFFB7282E)

@Composable
fun KanjiInputBar(
    symbols: List<String>,
    selected: String?,
    remainingUses: List<Int>? = null,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    theme: KanjiThemeColors? = null,
    completedSymbols: Set<Int> = emptySet(),
    onKeyFeedback: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Same typeface and Japanese glyph forms as the board, so key and cell match exactly.
    val kanjiStyle = remember {
        TextStyle(
            fontFamily = FontFamily(KanjiFonts.regular(context)),
            localeList = LocaleList("ja-JP")
        )
    }

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

            // e.g. "Water, 水, 4 remaining" for TalkBack
            val meaning = KanjiMapper.meaning(index + 1)
            val description = buildString {
                append(meaning.ifEmpty { symbol })
                if (meaning.isNotEmpty()) append(", ").append(symbol)
                if (remaining != null) {
                    append(", ")
                    append(if (isDepleted) "complete" else "$remaining remaining")
                }
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
                        if (onKeyFeedback != null) onKeyFeedback()
                        else view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onSelect(symbol)
                    }
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                        this.selected = isSelected
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
                        color = symbolColor,
                        style = kanjiStyle
                    )

                    if (remaining != null) {
                        Text(
                            text = remaining.toString(),
                            fontSize = 11.sp,
                            color = symbolColor.copy(alpha = 0.65f)
                        )
                    }
                }

                // Red hanko seal when all nine of this Kanji are placed correctly.
                // It "stamps" in with a small overshoot; on screen load it simply appears.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = (index + 1) in completedSymbols,
                        enter = scaleIn(
                            initialScale = 1.9f,
                            animationSpec = spring(
                                dampingRatio = 0.45f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(tween(110)),
                        exit = fadeOut(tween(150))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .rotate(-8f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(HankoRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "完",
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                                color = Color.White,
                                style = kanjiStyle
                            )
                        }
                    }
                }
            }
        }
    }
}
