package com.kaajjo.libresudoku.ui.components.board

import com.kaajjo.libresudoku.KanjiMapper

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaajjo.libresudoku.LocalBoardColors
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.ui.theme.BoardColors
import com.kaajjo.libresudoku.ui.theme.LibreSudokuTheme
import com.kaajjo.libresudoku.ui.theme.SudokuBoardColors
import com.kaajjo.libresudoku.ui.theme.SudokuBoardColorsImpl
import com.kaajjo.libresudoku.ui.util.LightDarkPreview
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

@Composable
fun BoardPreview(
    modifier: Modifier = Modifier,
    size: Int = 9,
    boardString: String? = null,
    board: List<List<Cell>>? = null,
    mainTextSize: TextUnit = when (size) {
        6 -> 16.sp
        9 -> 11.sp
        12 -> 9.sp
        else -> 22.sp
    },
    boardColors: SudokuBoardColors = LocalBoardColors.current
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
    ) {
        val density = LocalDensity.current
        val context = LocalContext.current

        val maxWidth = constraints.maxWidth.toFloat()
        val cellSize = maxWidth / size.toFloat()
        val foregroundColor = boardColors.altForegroundColor
        val thickLineColor = boardColors.thickLineColor
        val thinLineColor = boardColors.thinLineColor

        val vertThick = floor(sqrt(size.toFloat())).toInt()
        val horThick = ceil(sqrt(size.toFloat())).toInt()

        val fontSizePx = with(density) { mainTextSize.toPx() }
        val face = remember { KanjiFonts.regular(context) }

        // Keyed on size/colour so thumbnails stay correct after theme or size changes.
        val textPaint = remember(fontSizePx, foregroundColor, face) {
            kanjiPaint(foregroundColor, fontSizePx, face)
        }
        val glyphCenterY = remember(textPaint) { kanjiCenterY(textPaint) }

        val boardStrokeWidth = with(density) { 1.1.dp.toPx() }
        val thinLineWidth = with(density) { 0.6.dp.toPx() }
        val thickLineWidth = with(density) { 1.1.dp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Outer rounded border
            drawRoundRect(
                color = thickLineColor,
                topLeft = Offset.Zero,
                size = Size(maxWidth, maxWidth),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = boardStrokeWidth)
            )

            // Vertical grid lines
            for (i in 1 until size) {
                val isThickLine = i % horThick == 0
                drawLine(
                    color = if (isThickLine) thickLineColor else thinLineColor,
                    start = Offset(cellSize * i.toFloat(), 0f),
                    end = Offset(cellSize * i.toFloat(), maxWidth),
                    strokeWidth = if (isThickLine) thickLineWidth else thinLineWidth
                )
            }

            // Horizontal grid lines
            for (i in 1 until size) {
                val isThickLine = i % vertThick == 0
                drawLine(
                    color = if (isThickLine) thickLineColor else thinLineColor,
                    start = Offset(0f, cellSize * i.toFloat()),
                    end = Offset(maxWidth, cellSize * i.toFloat()),
                    strokeWidth = if (isThickLine) thickLineWidth else thinLineWidth
                )
            }

            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                if (board != null) {
                    // Preview from full Cell board (Kanji + colours)
                    for (i in 0 until size) {
                        for (j in 0 until size) {
                            val cell = board[i][j]
                            if (cell.value == 0) continue

                            textPaint.color = when {
                                cell.error -> AndroidColor.parseColor("#B71C1C")   // error red
                                cell.locked -> AndroidColor.parseColor("#001933")  // locked deep navy
                                else -> AndroidColor.parseColor("#5A0000")         // user dark maroon
                            }

                            val v = cell.value
                            val displayText =
                                if (v in 1..9) KanjiMapper.toSymbol(v) else v.toString(16).uppercase()

                            nc.drawText(
                                displayText,
                                (j + 0.5f) * cellSize,
                                (i + 0.5f) * cellSize - glyphCenterY,
                                textPaint
                            )
                        }
                    }
                    // Restore the shared paint's colour for the next draw.
                    textPaint.color = foregroundColor.toArgb()
                } else if (boardString != null && boardString.length == size * size) {
                    // Preview from string board (last played games)
                    for (j in 0 until size) {
                        for (i in 0 until size) {
                            val ch = boardString[size * j + i]
                            if (ch == '0') continue

                            val displayText =
                                if (ch in '1'..'9') KanjiMapper.toSymbol(ch.digitToInt())
                                else ch.uppercase()

                            nc.drawText(
                                displayText,
                                (i + 0.5f) * cellSize,
                                (j + 0.5f) * cellSize - glyphCenterY,
                                textPaint
                            )
                        }
                    }
                }
            }
        }
    }
}

@LightDarkPreview
@Composable
private fun BoardPreviewPreview() {
    LibreSudokuTheme {
        Surface {
            BoardPreview(
                boardString = "0000100000040000000000000700000000000900000000680000000000000005000000000000000",
                boardColors = SudokuBoardColorsImpl(
                    foregroundColor = BoardColors.foregroundColor,
                    notesColor = BoardColors.notesColor,
                    altForegroundColor = BoardColors.altForegroundColor,
                    errorColor = BoardColors.errorColor,
                    highlightColor = BoardColors.highlightColor,
                    thickLineColor = BoardColors.thickLineColor,
                    thinLineColor = BoardColors.thinLineColor
                )
            )
        }
    }
}
