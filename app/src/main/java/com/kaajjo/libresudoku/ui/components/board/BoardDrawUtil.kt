package com.kaajjo.libresudoku.ui.components.board

import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.kaajjo.libresudoku.KanjiMapper
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.Note
import com.kaajjo.libresudoku.core.qqwing.Cage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toArgb
import com.kaajjo.libresudoku.ui.theme.KanjiErrorColor
import com.kaajjo.libresudoku.ui.theme.KanjiLockedColor
import com.kaajjo.libresudoku.ui.theme.KanjiSymbolColor

/* -------------------- CELL HIGHLIGHT -------------------- */

fun DrawScope.drawRoundCell(
    row: Int,
    col: Int,
    gameSize: Int,
    rect: Rect,
    color: Color,
    cornerRadius: CornerRadius = CornerRadius.Zero
) {
    val path = Path().apply {
        addRoundRect(
            roundRectForCell(
                row = row,
                col = col,
                gameSize = gameSize,
                rect = rect,
                cornerRadius = cornerRadius
            )
        )
    }
    drawPath(path = path, color = color)
}

/* -------------------- NOTES -------------------- */

fun DrawScope.drawNotes(
    size: Int,
    paint: Paint,
    highlightPaint: Paint,
    notes: List<Note>,
    notesToHighlight: List<Note>,
    cellSize: Float,
    cellSizeDivWidth: Float,
    killerSumBounds: AndroidRect,
) {
    val noteBounds = AndroidRect()
    paint.getTextBounds("1", 0, 1, noteBounds)

    val perRow = floor(sqrt(size.toFloat()))
    val cellDivHeight = (cellSize - killerSumBounds.height() * 1.5f) / perRow

    drawIntoCanvas { canvas ->
        notes.forEach { note ->
            val textToDraw = if (note.value in 1..9) {
                KanjiMapper.toSymbol(note.value)   // Kanji for normal 9x9 notes
            } else {
                note.value.toString(16).uppercase() // fallback for 10+ (if ever used)
            }
            val noteTextMeasure = paint.measureText(textToDraw)

            val noteCol = getNoteColumnNumber(note.value, size)
            val noteRow = getNoteRowNumber(note.value, size)

            val horizontalPadding =
                if (noteRow == 0) noteTextMeasure / 3f
                else if (noteRow == 1) 0f
                else if (noteRow == 2 && note.value > 9) 0f
                else -(noteTextMeasure / 3f)

            if (notesToHighlight.contains(note)) {
                canvas.nativeCanvas.drawCircle(
                    note.col * cellSize + cellSizeDivWidth / 2f +
                            (cellSizeDivWidth * noteRow) + horizontalPadding,
                    note.row * cellSize + noteBounds.height() * 1.5f +
                            killerSumBounds.height() + (cellDivHeight * noteCol) -
                            (noteBounds.height() * 0.5f),
                    noteTextMeasure * 1.1f,
                    highlightPaint
                )
            }

            canvas.nativeCanvas.drawText(
                textToDraw,
                note.col * cellSize + cellSizeDivWidth / 2f +
                        (cellSizeDivWidth * noteRow) - noteTextMeasure / 2f + horizontalPadding,
                note.row * cellSize + noteBounds.height() * 1.5f +
                        killerSumBounds.height() + (cellDivHeight * noteCol),
                paint
            )
        }
    }
}

/* -------------------- NUMBERS (KANJI + COLORS) -------------------- */

fun DrawScope.drawNumbers(
    size: Int,
    board: List<List<Cell>>,
    highlightErrors: Boolean,
    errorTextPaint: Paint,
    lockedTextPaint: Paint,
    textPaint: Paint,
    questions: Boolean,
    cellSize: Float
) {
    // Fix default colors (Japanese style)
    errorTextPaint.color = KanjiErrorColor.toArgb()
    lockedTextPaint.color = KanjiLockedColor.toArgb()
    textPaint.color = KanjiSymbolColor.toArgb()

    val textBounds = AndroidRect()
    textPaint.getTextBounds("1", 0, 1, textBounds)
    val width = textPaint.measureText("1")

    drawIntoCanvas { canvas ->
        for (i in 0 until size) {
            for (j in 0 until size) {
                val cell = board[i][j]
                if (cell.value == 0) continue

                val paint = when {
                    highlightErrors && cell.error -> errorTextPaint
                    cell.locked -> lockedTextPaint
                    else -> textPaint
                }

                val v = cell.value
                val textToDraw = when {
                    questions -> "?"
                    v in 1..9 -> KanjiMapper.toSymbol(v)
                    else -> v.toString(16).uppercase() // A/B/C… for 10+
                }

                val localBounds = AndroidRect()
                paint.getTextBounds(textToDraw, 0, textToDraw.length, localBounds)
                val textWidth = paint.measureText(textToDraw)

                canvas.nativeCanvas.drawText(
                    textToDraw,
                    cell.col * cellSize + (cellSize - textWidth) / 2f,
                    cell.row * cellSize + (cellSize + localBounds.height()) / 2f,
                    paint
                )
            }
        }
    }
}

/* -------------------- BOARD FRAME -------------------- */

fun DrawScope.drawBoardFrame(
    thickLineColor: Color,
    thickLineWidth: Float,
    maxWidth: Float,
    cornerRadius: CornerRadius
) {
    drawRoundRect(
        color = thickLineColor,
        topLeft = Offset.Zero,
        size = Size(maxWidth, maxWidth),
        cornerRadius = cornerRadius,
        style = Stroke(width = thickLineWidth)
    )
}

/* -------------------- POSITION LINES (ROW & COLUMN) -------------------- */

fun DrawScope.drawPositionLines(
    row: Int,
    col: Int,
    gameSize: Int,
    color: Color,
    cellSize: Float,
    lineLength: Float,
    cornerRadius: CornerRadius
) {
    // Highlight entire row
    drawRoundCell(
        row = row,
        col = 0,
        gameSize = gameSize,
        rect = Rect(
            offset = Offset(0f, row * cellSize),
            size = Size(lineLength, cellSize)
        ),
        color = color,
        cornerRadius = cornerRadius
    )

    // Highlight entire column
    drawRoundCell(
        row = 0,
        col = col,
        gameSize = gameSize,
        rect = Rect(
            offset = Offset(col * cellSize, 0f),
            size = Size(cellSize, lineLength)
        ),
        color = color,
        cornerRadius = cornerRadius
    )
}

/* -------------------- ROUND RECT HELPER -------------------- */

fun roundRectForCell(
    row: Int,
    col: Int,
    gameSize: Int,
    rect: Rect,
    cornerRadius: CornerRadius
): RoundRect {
    val topLeft = if (row == 0 && col == 0) cornerRadius else CornerRadius.Zero
    val topRight = if (row == 0 && col == gameSize - 1) cornerRadius else CornerRadius.Zero
    val bottomLeft = if (row == gameSize - 1 && col == 0) cornerRadius else CornerRadius.Zero
    val bottomRight =
        if (row == gameSize - 1 && col == gameSize - 1) cornerRadius else CornerRadius.Zero

    return RoundRect(
        rect = rect,
        topLeft = topLeft,
        topRight = topRight,
        bottomLeft = bottomLeft,
        bottomRight = bottomRight
    )
}

/* -------------------- CROSS HIGHLIGHT -------------------- */

fun DrawScope.drawCrossSelection(
    gameSize: Int,
    sectionHeight: Int,
    sectionWidth: Int,
    color: Color,
    cellSize: Float
) {
    for (i in 0 until gameSize / sectionWidth) {
        for (j in 0 until gameSize / sectionHeight) {
            if ((i % 2 == 0 && j % 2 != 0) || (i % 2 != 0 && j % 2 == 0)) {
                drawRect(
                    color = color,
                    topLeft = Offset(
                        x = i * sectionWidth * cellSize,
                        y = j * sectionHeight * cellSize
                    ),
                    size = Size(cellSize * sectionWidth, cellSize * sectionHeight)
                )
            }
        }
    }
}

/* -------------------- KILLER CAGES -------------------- */


/* -------------------- HELPERS -------------------- */

private fun getNoteColumnNumber(value: Int, size: Int): Int {
    val perRow = floor(sqrt(size.toFloat())).toInt().coerceAtLeast(1)
    return (value - 1) / perRow
}

private fun getNoteRowNumber(value: Int, size: Int): Int {
    val perRow = floor(sqrt(size.toFloat())).toInt().coerceAtLeast(1)
    return (value - 1) % perRow
}

fun getSectionWidthForSize(size: Int): Int =
    when (size) {
        6 -> 3
        9 -> 3
        12 -> 4
        else -> ceil(sqrt(size.toFloat())).toInt().coerceAtLeast(1)
    }

fun getSectionHeightForSize(size: Int): Int =
    when (size) {
        6 -> 2
        9 -> 3
        12 -> 3
        else -> floor(sqrt(size.toFloat())).toInt().coerceAtLeast(1)
    }
