package com.kaajjo.libresudoku.ui.game.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kaajjo.libresudoku.R
import com.kaajjo.libresudoku.ui.theme.KanjiThemeColors
import com.kaajjo.libresudoku.ui.theme.KanjiThemeType
import com.kaajjo.libresudoku.ui.theme.KanjiThemes
import com.kaajjo.libresudoku.ui.theme.LibreSudokuTheme
import com.kaajjo.libresudoku.ui.util.LightDarkPreview

enum class ToolBarItem {
    Undo,
    Hint,
    Note,
    Remove,
    Redo
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarItem(
    modifier: Modifier = Modifier,
    painter: Painter,
    toggled: Boolean = false,
    theme: KanjiThemeColors? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(18.dp)

    val themedBackground = when {
        theme != null && toggled -> theme.primaryButton
        theme != null -> theme.secondaryButton
        else -> null
    }

    val fallbackBackground = if (toggled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    }

    val borderColor = when {
        theme != null && toggled -> theme.accent.copy(alpha = 0.85f)
        theme != null -> theme.boardBorder.copy(alpha = 0.18f)
        toggled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    }

    val iconTint = when {
        theme != null && toggled -> Color.White
        theme != null -> theme.primaryText
        toggled -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .then(
                if (themedBackground != null) {
                    Modifier.background(
                        brush = themedBackground,
                        shape = shape
                    )
                } else {
                    Modifier.background(
                        color = fallbackBackground,
                        shape = shape
                    )
                }
            )
            .border(
                width = if (toggled) 1.5.dp else 0.75.dp,
                color = borderColor,
                shape = shape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = iconTint
        )
    }
}

@LightDarkPreview
@Composable
private fun ToolbarItemPreview() {
    val theme = KanjiThemes.get(KanjiThemeType.Sakura)

    LibreSudokuTheme {
        Surface {
            Row {
                ToolbarItem(
                    modifier = Modifier.weight(1f),
                    painter = painterResource(R.drawable.ic_round_edit_24),
                    theme = theme
                )

                ToolbarItem(
                    modifier = Modifier.weight(1f),
                    painter = painterResource(R.drawable.ic_round_edit_24),
                    toggled = true,
                    theme = theme
                )
            }
        }
    }
}