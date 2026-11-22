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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/* -------------------- CELL HIGHLIGHT -------------------- */

/** Kept for compatibility. The board now clips to its rounded shape, so plain rects suffice. */
fun DrawScope.drawRoundCell(
    row: Int,
    col: Int,
    gameSize: Int,
    rect: Rect,
    color: Color,
    cornerRadius: CornerRadius = CornerRadius.Zero
) {
    if (cornerRadius == CornerRadius.Zero) {
        drawRect(color = color, topLeft = rect.topLeft, size = rect.size)
        return
    }
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

/* -------------------- GLYPH METRICS -------------------- */

/**
 * Vertical centre (relative to the baseline) of the union of the nine Kanji glyphs.
 * Drawing at `cellCentreY - kanjiCenterY(paint)` centres every Kanji optically and
 * keeps them all on one shared baseline.
 */
fun kanjiCenterY(paint: Paint): Float {
    val r = AndroidRect()
    val union = AndroidRect()
    for (n in 1..9) {
        val s = KanjiMapper.toSymbol(n)
        paint.getTextBounds(s, 0, s.length, r)
        if (n == 1) union.set(r) else union.union(r)
    }
    return union.exactCenterY()
}

private fun symbolFor(value: Int): String =
    if (value in 1..9) KanjiMapper.toSymbol(value)
    else value.toString(16).uppercase() // A/B/C… for 10+

/* -------------------- NOTES -------------------- */

/**
 * Each note is centred in its own sub-cell of a 3×3 grid inside the cell.
 * [paint] must use Paint.Align.CENTER.
 */
fun DrawScope.drawNotes(
    size: Int,
    paint: Paint,
    highlightPaint: Paint,
    notes: List<Note>,
    notesToHighlight: List<Note>,
    cellSize: Float,
    cellSizeDivWidth: Float,
    killerSumBounds: AndroidRect,
    noteGlyphCenterY: Float,
) {
    val perRow = floor(sqrt(size.toFloat())).toInt().coerceAtLeast(1)
    val topInset = if (killerSumBounds.height() > 0) killerSumBounds.height() * 1.2f else 0f
    val subW = cellSizeDivWidth
    val subH = (cellSize - topInset) / perRow
    val radius = minOf(subW, subH) * 0.46f
    val highlighted = if (notesToHighlight.isEmpty()) emptySet() else notesToHighlight.toHashSet()

    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        notes.forEach { note ->
            val k = note.value - 1
            val cx = note.col * cellSize + (k % perRow + 0.5f) * subW
            val cy = note.row * cellSize + topInset + (k / perRow + 0.5f) * subH

            if (note in highlighted) {
                nc.drawCircle(cx, cy, radius, highlightPaint)
            }
            nc.drawText(symbolFor(note.value), cx, cy - noteGlyphCenterY, paint)
        }
    }
}

/* -------------------- NUMBERS (KANJI) -------------------- */

/**
 * Draws the Kanji in each filled cell, centred on the cell.
 * Paints carry their own colours (from the Kanji theme); they must use Paint.Align.CENTER.
 */
fun DrawScope.drawNumbers(
    size: Int,
    board: List<List<Cell>>,
    highlightErrors: Boolean,
    errorTextPaint: Paint,
    lockedTextPaint: Paint,
    textPaint: Paint,
    questions: Boolean,
    cellSize: Float,
    glyphCenterY: Float,
    scaleFor: ((row: Int, col: Int) -> Float)? = null,
) {
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        for (i in 0 until size) {
            for (j in 0 until size) {
                val cell = board[i][j]
                if (cell.value == 0) continue

                val paint = when {
                    highlightErrors && cell.error -> errorTextPaint
                    cell.locked -> lockedTextPaint
                    else -> textPaint
                }

                val text = if (questions) "?" else symbolFor(cell.value)
                val cx = (j + 0.5f) * cellSize
                val cy = (i + 0.5f) * cellSize
                val scale = scaleFor?.invoke(i, j) ?: 1f

                if (scale != 1f) {
                    // Celebration "lift": grow the glyph around the cell centre.
                    nc.save()
                    nc.scale(scale, scale, cx, cy)
                    nc.drawText(text, cx, cy - glyphCenterY, paint)
                    nc.restore()
                } else {
                    nc.drawText(text, cx, cy - glyphCenterY, paint)
                }
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

/**
 * Highlights the selected row and column. Drawn as plain rectangles; the board's
 * clip gives them correct rounded ends at the frame. [cornerRadius] is ignored.
 */
@Suppress("UNUSED_PARAMETER")
fun DrawScope.drawPositionLines(
    row: Int,
    col: Int,
    gameSize: Int,
    color: Color,
    cellSize: Float,
    lineLength: Float,
    cornerRadius: CornerRadius
) {
    drawRect(
        color = color,
        topLeft = Offset(0f, row * cellSize),
        size = Size(lineLength, cellSize)
    )
    drawRect(
        color = color,
        topLeft = Offset(col * cellSize, 0f),
        size = Size(cellSize, lineLength)
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

/* -------------------- HELPERS -------------------- */

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
