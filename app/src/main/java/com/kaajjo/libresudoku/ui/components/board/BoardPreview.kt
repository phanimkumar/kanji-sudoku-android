package com.kaajjo.libresudoku.ui.components.board

import com.kaajjo.libresudoku.KanjiMapper

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
    )

    {
        val maxWidth = constraints.maxWidth.toFloat()

        val cellSize by remember(size) { mutableFloatStateOf(maxWidth / size.toFloat()) }
        val foregroundColor = boardColors.altForegroundColor
        val thickLineColor = boardColors.thickLineColor
        val thinLineColor = boardColors.thinLineColor

        val vertThick by remember(size) { mutableIntStateOf(floor(sqrt(size.toFloat())).toInt()) }
        val horThick by remember(size) { mutableIntStateOf(ceil(sqrt(size.toFloat())).toInt()) }

        val fontSizePx = with(LocalDensity.current) { mainTextSize.toPx() }

        val textPaint by remember {
            mutableStateOf(
                Paint().apply {
                    color = foregroundColor.toArgb()
                    isAntiAlias = true
                    textSize = fontSizePx
                }
            )
        }
        val width by remember { mutableFloatStateOf(textPaint.measureText("1")) }
        val boardStrokeWidth = with(LocalDensity.current) { 1.1.dp.toPx() }
        val thinLineWidth = with(LocalDensity.current) { 0.6.dp.toPx() }
        val thickLineWidth = with(LocalDensity.current) { 1.1.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
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

            val textBounds = Rect()
            textPaint.getTextBounds("1", 0, 1, textBounds)

            drawIntoCanvas { canvas ->
                if (board != null) {
                    // Preview from full Cell board (use Kanji + colors)
                    for (i in 0 until size) {
                        for (j in 0 until size) {
                            val cell = board[i][j]
                            if (cell.value != 0) {

                                textPaint.color = when {
                                    cell.error -> AndroidColor.parseColor("#B71C1C")   // error red
                                    cell.locked -> AndroidColor.parseColor("#001933")  // locked deep navy
                                    else -> AndroidColor.parseColor("#5A0000")         // user dark maroon
                                }

                                val v = cell.value
                                val displayText = if (v in 1..9) {
                                    KanjiMapper.toSymbol(v)
                                } else {
                                    v.toString(16).uppercase()
                                }

                                val textWidth = textPaint.measureText(displayText)

                                canvas.nativeCanvas.drawText(
                                    displayText,
                                    cell.col * cellSize + (cellSize - textWidth) / 2f,
                                    (cell.row * cellSize + cellSize) -
                                            (cellSize - textBounds.height()) / 2f,
                                    textPaint
                                )
                            }
                        }
                    }
                } else if (boardString != null && boardString.length == size * size) {
                    // Preview from string board (last played games)
                    textPaint.color = foregroundColor.toArgb()

                    for (j in 0 until size) {
                        for (i in 0 until size) {
                            val ch = boardString[size * j + i]
                            if (ch != '0') {

                                val displayText = if (ch in '1'..'9') {
                                    KanjiMapper.toSymbol(ch.digitToInt())   // 1..9 → Kanji
                                } else {
                                    ch.uppercase()                          // fallback
                                }

                                val textWidth = textPaint.measureText(displayText)

                                canvas.nativeCanvas.drawText(
                                    displayText,
                                    i * cellSize + (cellSize - textWidth) / 2f,
                                    j * cellSize + cellSize -
                                            (cellSize - textBounds.height()) / 2f,
                                    textPaint
                                )
                            }
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
