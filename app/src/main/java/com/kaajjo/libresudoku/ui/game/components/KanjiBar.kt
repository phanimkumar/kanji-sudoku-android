package com.kaajjo.libresudoku.ui.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KanjiInputBar(
    symbols: List<String>,
    selected: String?,
    remainingUses: List<Int>? = null,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        symbols.forEachIndexed { index, symbol ->

            val remaining = remainingUses?.getOrNull(index)
            val isDepleted = remaining != null && remaining <= 0
            val isSelected = selected == symbol

            val tileColor =
                when {
                    isSelected -> Color(0xFFB30059)        // Dark pink for selection
                    isDepleted -> Color(0xFFE0E0E0)        // Gray tile when used up
                    else -> Color.White
                }

            val textColor =
                when {
                    isDepleted -> Color.Gray
                    isSelected -> Color.White
                    else -> Color.Black
                }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .background(tileColor, RoundedCornerShape(12.dp))
                    .clickable(enabled = !isDepleted) {
                        onSelect(symbol)
                    },
                contentAlignment = Alignment.Center 
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // Kanji symbol
                    Text(
                        text = symbol,
                        fontSize = 22.sp,
                        color = textColor
                    )

                    // Remaining count (optional)
                    if (remaining != null) {
                        Text(
                            text = remaining.toString(),
                            fontSize = 12.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
