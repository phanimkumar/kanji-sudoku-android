package com.kaajjo.libresudoku.ui.components.board

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaajjo.libresudoku.LocalBoardColors
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.Note
import com.kaajjo.libresudoku.core.qqwing.Cage
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.utils.SudokuParser
import com.kaajjo.libresudoku.ui.theme.BoardColors
import com.kaajjo.libresudoku.ui.theme.KanjiErrorColor
import com.kaajjo.libresudoku.ui.theme.KanjiLockedColor
import com.kaajjo.libresudoku.ui.theme.KanjiOuterBorder
import com.kaajjo.libresudoku.ui.theme.KanjiRowColHighlight
import com.kaajjo.libresudoku.ui.theme.KanjiSameSymbolHighlight
import com.kaajjo.libresudoku.ui.theme.KanjiSelectedCell
import com.kaajjo.libresudoku.ui.theme.KanjiSymbolColor
import com.kaajjo.libresudoku.ui.theme.KanjiThemeColors
import com.kaajjo.libresudoku.ui.theme.LibreSudokuTheme
import com.kaajjo.libresudoku.ui.theme.SudokuBoardColors
import com.kaajjo.libresudoku.ui.theme.SudokuBoardColorsImpl
import com.kaajjo.libresudoku.ui.util.LightDarkPreview
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

// Board look. Previous values: corner 15px (density-dependent), thin 1.6dp, thick 3dp.
private val BOARD_CORNER = 12.dp
private const val THIN_LINE_DP = 1.0f
private const val THICK_LINE_DP = 2.2f

// Kanji size relative to the cell when "auto font size" is on (was 0.70).
private const val AUTO_KANJI_SCALE = 0.65f

// Note Kanji size relative to its 1/3-cell slot (was 0.80).
private const val NOTE_KANJI_SCALE = 0.72f

// Glass look. Lower GLASS_ALPHA = more see-through; 1f = solid (previous look).
private const val GLASS_ALPHA = 0.84f
private const val GLASS_SHEEN_ALPHA = 0.10f   // soft light from the top-left
private const val GLASS_RIM_ALPHA = 0.22f     // thin bright edge along the top
private val GLASS_SHADOW_BLUR = 14.dp
private val GLASS_SHADOW_OFFSET = 4.dp
private const val GLASS_SHADOW_ALPHA = 0.28f

@Composable
fun Board(
    modifier: Modifier = Modifier,
    board: List<List<Cell>>,
    size: Int = board.size,
    notes: List<Note>? = null,
    mainTextSize: TextUnit = when (size) {
        6 -> 32.sp
        9 -> 26.sp
        12 -> 24.sp
        else -> 14.sp
    },
    autoFontSize: Boolean = false,
    noteTextSize: TextUnit = when (size) {
        6 -> 18.sp
        9 -> 12.sp
        12 -> 7.sp
        else -> 14.sp
    },
    selectedCell: Cell,
    onClick: (Cell) -> Unit,
    onLongClick: (Cell) -> Unit = { },
    identicalNumbersHighlight: Boolean = true,
    errorsHighlight: Boolean = true,
    positionLines: Boolean = true,
    enabled: Boolean = true,
    questions: Boolean = false,
    renderNotes: Boolean = true,
    cellsToHighlight: List<Cell>? = null,
    notesToHighlight: List<Note> = emptyList(),
    zoomable: Boolean = false,
    boardColors: SudokuBoardColors = LocalBoardColors.current,
    crossHighlight: Boolean = false,
    cages: List<Cage> = emptyList(),
    selectedNumberForHighlight: Int = 0,
    kanjiTheme: KanjiThemeColors? = null,
    celebration: Celebration? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
    ) {
        val density = LocalDensity.current
        val context = LocalContext.current

        // Computed directly (not remembered) so they follow resizes, folds and rotation.
        val maxWidth = constraints.maxWidth.toFloat()
        val cellSize = maxWidth / size.toFloat()
        val cellSizeDivWidth = cellSize / ceil(sqrt(size.toFloat()))
        val vertThick = floor(sqrt(size.toFloat())).toInt()
        val horThick = ceil(sqrt(size.toFloat())).toInt()

        // ---- colours (theme first, previous look as fallback) ----
        val highlightColor = boardColors.highlightColor
        val boardBackgroundColor = kanjiTheme?.boardBackground ?: Color(0xFFF1F5F9)
        val outerFrameColor = kanjiTheme?.boardBorder ?: KanjiOuterBorder
        val thinLineColor = kanjiTheme?.boardBorder?.copy(alpha = 0.30f)
            ?: Color(0xFF94A3B8).copy(alpha = 0.7f)
        val thickLineColor = kanjiTheme?.boardBorder?.copy(alpha = 0.78f)
            ?: Color(0xFF334155)
        val notesColor = kanjiTheme?.secondaryText ?: boardColors.notesColor

        val lockedTextColor = kanjiTheme?.primaryText ?: KanjiLockedColor
        val enteredTextColor = kanjiTheme?.accent ?: KanjiSymbolColor
        val errorTextColor = KanjiErrorColor
        val errorCellColor = boardColors.errorColor.copy(alpha = 0.35f)

        val selectedCellColor = kanjiTheme?.accent?.copy(alpha = 0.34f) ?: KanjiSelectedCell
        val rowColHighlightColor = kanjiTheme?.accent?.copy(alpha = 0.11f) ?: KanjiRowColHighlight
        val sameSymbolHighlightColor = kanjiTheme?.accent?.copy(alpha = 0.20f) ?: KanjiSameSymbolHighlight
        val flashColor = (kanjiTheme?.accent ?: highlightColor).copy(alpha = 0.30f)
        val noteHighlightColor = (kanjiTheme?.accent ?: highlightColor).copy(alpha = 0.30f)
        val crossColor = highlightColor.copy(alpha = 0.1f)
        val killerSumColor = kanjiTheme?.primaryText ?: Color.Black
        val celebrationColor = kanjiTheme?.accent ?: highlightColor

        // ---- celebration clock (read only while drawing: no recomposition per frame) ----
        val celebrationClock = remember { Animatable(0f) }
        LaunchedEffect(celebration?.id) {
            val c = celebration ?: return@LaunchedEffect
            celebrationClock.snapTo(0f)
            celebrationClock.animateTo(
                targetValue = c.durationMs.toFloat(),
                animationSpec = tween(durationMillis = c.durationMs, easing = LinearEasing)
            )
        }

        // ---- sizes ----
        val fontSizePx = if (autoFontSize) cellSize * AUTO_KANJI_SCALE
        else with(density) { mainTextSize.toPx() }
        val noteSizePx = cellSizeDivWidth * NOTE_KANJI_SCALE
        val killerSumSizePx = cellSizeDivWidth * 0.8f * 1.1f

        val thinLineWidth = with(density) { THIN_LINE_DP.dp.toPx() }
        val thickLineWidth = with(density) { THICK_LINE_DP.dp.toPx() }
        val boardCornerPx = with(density) { BOARD_CORNER.toPx() }

        // ---- typefaces & paints (rebuilt only when their inputs change) ----
        val regularFace = remember { KanjiFonts.regular(context) }
        val boldFace = remember { KanjiFonts.bold(context) }

        val lockedTextPaint = remember(fontSizePx, lockedTextColor, boldFace) {
            kanjiPaint(lockedTextColor, fontSizePx, boldFace)
        }
        val textPaint = remember(fontSizePx, enteredTextColor, regularFace) {
            kanjiPaint(enteredTextColor, fontSizePx, regularFace)
        }
        val errorTextPaint = remember(fontSizePx, errorTextColor, regularFace) {
            kanjiPaint(errorTextColor, fontSizePx, regularFace)
        }
        val notePaint = remember(noteSizePx, notesColor, regularFace) {
            kanjiPaint(notesColor, noteSizePx, regularFace)
        }
        val noteHighlightPaint = remember(noteHighlightColor) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = noteHighlightColor.toArgb() }
        }
        val killerSumPaint = remember(killerSumSizePx, killerSumColor) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = killerSumColor.toArgb()
                textSize = killerSumSizePx
            }
        }
        val killerSumBounds = remember { android.graphics.Rect() }

        // Vertical centre of the nine Kanji glyphs, measured once per paint.
        val glyphCenterY = remember(lockedTextPaint) { kanjiCenterY(lockedTextPaint) }
        val noteGlyphCenterY = remember(notePaint) { kanjiCenterY(notePaint) }

        // ---- gestures ----
        var zoom by remember(enabled) { mutableFloatStateOf(1f) }
        var offset by remember(enabled) { mutableStateOf(Offset.Zero) }

        // Read the latest values inside long-lived gesture detectors.
        val currentBoard by rememberUpdatedState(board)
        val currentCellSize by rememberUpdatedState(cellSize)
        val currentOnClick by rememberUpdatedState(onClick)
        val currentOnLongClick by rememberUpdatedState(onLongClick)
        val currentEnabled by rememberUpdatedState(enabled)

        fun cellAt(position: Offset): Cell? {
            val b = currentBoard
            if (b.isEmpty() || currentCellSize <= 0f) return null
            val p = position / zoom + offset
            val row = floor(p.y / currentCellSize).toInt().coerceIn(b.indices)
            val col = floor(p.x / currentCellSize).toInt().coerceIn(b[row].indices)
            return b[row][col]
        }

        val boardShape = RoundedCornerShape(BOARD_CORNER)
        val shadowBlurPx = with(density) { GLASS_SHADOW_BLUR.toPx() }
        val shadowOffsetPx = with(density) { GLASS_SHADOW_OFFSET.toPx() }
        val shadowPaint = remember(shadowBlurPx) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb((GLASS_SHADOW_ALPHA * 255).toInt(), 0, 0, 0)
                maskFilter = BlurMaskFilter(shadowBlurPx, BlurMaskFilter.Blur.NORMAL)
            }
        }
        val shadowClip = remember { android.graphics.Path() }

        val boardModifier = Modifier
            .fillMaxSize()
            // Soft shadow drawn only OUTSIDE the board, so it never darkens the glass.
            // Blurred shadows need hardware support from Android 9; older phones skip it.
            .drawBehind {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val w = this.size.width
                    val h = this.size.height
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        shadowClip.reset()
                        shadowClip.addRoundRect(
                            0f, 0f, w, h,
                            boardCornerPx, boardCornerPx,
                            android.graphics.Path.Direction.CW
                        )
                        nc.save()
                        nc.clipOutPath(shadowClip)
                        nc.drawRoundRect(
                            0f, shadowOffsetPx, w, h + shadowOffsetPx,
                            boardCornerPx, boardCornerPx, shadowPaint
                        )
                        nc.restore()
                    }
                }
            }
            .clip(boardShape)
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = { pos -> cellAt(pos)?.let { currentOnClick(it) } },
                    onLongPress = { pos ->
                        if (currentEnabled) cellAt(pos)?.let { currentOnLongClick(it) }
                    }
                )
            }

        val zoomModifier = Modifier
            .pointerInput(enabled) {
                detectTransformGestures(
                    onGesture = { gestureCentroid, gesturePan, gestureZoom, _ ->
                        if (enabled) {
                            val oldScale = zoom
                            val newScale = (zoom * gestureZoom).coerceIn(1f..3f)

                            offset = (offset + gestureCentroid / oldScale) -
                                    (gestureCentroid / newScale + gesturePan / oldScale)

                            zoom = newScale
                            if (offset.x < 0) {
                                offset = Offset(0f, offset.y)
                            } else if (maxWidth - offset.x < maxWidth / zoom) {
                                offset = offset.copy(x = maxWidth - maxWidth / zoom)
                            }
                            if (offset.y < 0) {
                                offset = Offset(offset.x, 0f)
                            } else if (maxWidth - offset.y < maxWidth / zoom) {
                                offset = offset.copy(y = maxWidth - maxWidth / zoom)
                            }
                        }
                    }
                )
            }
            .graphicsLayer {
                translationX = -offset.x * zoom
                translationY = -offset.y * zoom
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0f, 0f)
            }

        Canvas(
            modifier = if (zoomable) boardModifier.then(zoomModifier) else boardModifier
        ) {
            // The canvas is clipped to the rounded board shape, so every layer below
            // can use plain rectangles: corners are always correct.
            // Glass: slightly see-through surface + soft light from the top-left.
            drawRect(color = boardBackgroundColor.copy(alpha = GLASS_ALPHA), size = this.size)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = GLASS_SHEEN_ALPHA), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(this.size.width * 0.65f, this.size.height * 0.65f)
                ),
                size = this.size
            )

            val elapsed = celebrationClock.value

            fun fillCell(row: Int, col: Int, color: Color) {
                drawRect(
                    color = color,
                    topLeft = Offset(col * cellSize, row * cellSize),
                    size = Size(cellSize, cellSize)
                )
            }

            // 0. Alternating-box tint (background layer, under the Kanji).
            if (crossHighlight && size != 6) {
                drawCrossSelection(
                    gameSize = size,
                    sectionWidth = getSectionWidthForSize(size),
                    sectionHeight = getSectionHeightForSize(size),
                    color = crossColor,
                    cellSize = cellSize
                )
            }

            val hasSelection = selectedCell.row in 0 until size && selectedCell.col in 0 until size

            // 1. Row and column highlight: weakest layer.
            if (hasSelection && positionLines) {
                drawPositionLines(
                    row = selectedCell.row,
                    col = selectedCell.col,
                    gameSize = size,
                    color = rowColHighlightColor,
                    cellSize = cellSize,
                    lineLength = maxWidth,
                    cornerRadius = CornerRadius.Zero
                )
            }

            // 2. Same-symbol highlight.
            if (identicalNumbersHighlight && selectedNumberForHighlight > 0) {
                for (i in 0 until size) {
                    for (j in 0 until size) {
                        if (board[i][j].value == selectedNumberForHighlight) {
                            fillCell(i, j, sameSymbolHighlightColor)
                        }
                    }
                }
            }

            // 3. Selected cell: strongest layer.
            if (hasSelection) {
                fillCell(selectedCell.row, selectedCell.col, selectedCellColor)
            }

            // 4. Errors.
            if (errorsHighlight) {
                for (i in 0 until size) {
                    for (j in 0 until size) {
                        if (board[i][j].error) fillCell(i, j, errorCellColor)
                    }
                }
            }

            // 5. Completion flash / advanced-hint cells.
            cellsToHighlight?.forEach {
                if (it.row in 0 until size && it.col in 0 until size) {
                    fillCell(it.row, it.col, flashColor)
                }
            }

            // 5b. Celebration light (under the grid and Kanji).
            if (celebration != null) {
                drawCelebrationGlow(celebration, elapsed, cellSize, celebrationColor)
            }

            // 6. Grid lines.
            for (i in 1 until size) {
                val isThickLine = i % horThick == 0
                drawLine(
                    color = if (isThickLine) thickLineColor else thinLineColor,
                    start = Offset(cellSize * i.toFloat(), 0f),
                    end = Offset(cellSize * i.toFloat(), maxWidth),
                    strokeWidth = if (isThickLine) thickLineWidth else thinLineWidth
                )
            }

            for (i in 1 until size) {
                val isThickLine = i % vertThick == 0
                drawLine(
                    color = if (isThickLine) thickLineColor else thinLineColor,
                    start = Offset(0f, cellSize * i.toFloat()),
                    end = Offset(maxWidth, cellSize * i.toFloat()),
                    strokeWidth = if (isThickLine) thickLineWidth else thinLineWidth
                )
            }

            // 7. Outer frame, inset by half its width so it isn't cut by the clip.
            val half = thickLineWidth / 2f
            drawRoundRect(
                color = outerFrameColor,
                topLeft = Offset(half, half),
                size = Size(maxWidth - thickLineWidth, maxWidth - thickLineWidth),
                cornerRadius = CornerRadius(
                    (boardCornerPx - half).coerceAtLeast(0f),
                    (boardCornerPx - half).coerceAtLeast(0f)
                ),
                style = Stroke(width = thickLineWidth)
            )

            // 7b. Glass rim: a thin bright edge along the top, fading downwards.
            val rimInset = thickLineWidth + 0.5f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = GLASS_RIM_ALPHA), Color.Transparent),
                    startY = 0f,
                    endY = maxWidth * 0.35f
                ),
                topLeft = Offset(rimInset, rimInset),
                size = Size(maxWidth - rimInset * 2, maxWidth - rimInset * 2),
                cornerRadius = CornerRadius(
                    (boardCornerPx - rimInset).coerceAtLeast(0f),
                    (boardCornerPx - rimInset).coerceAtLeast(0f)
                ),
                style = Stroke(width = with(density) { 1.dp.toPx() })
            )

            // 7c. Celebration outline around completed boxes.
            if (celebration != null) {
                drawCelebrationTrace(celebration, elapsed, cellSize, celebrationColor, thickLineWidth)
            }

            // 8. Kanji.
            drawNumbers(
                size = size,
                board = board,
                highlightErrors = errorsHighlight,
                errorTextPaint = errorTextPaint,
                lockedTextPaint = lockedTextPaint,
                textPaint = textPaint,
                questions = questions,
                cellSize = cellSize,
                glyphCenterY = glyphCenterY,
                scaleFor = if (celebration != null) {
                    { r: Int, col: Int -> celebration.kanjiScale(r, col, elapsed) }
                } else null
            )

            // 9. Notes.
            if (!notes.isNullOrEmpty() && !questions && renderNotes) {
                drawNotes(
                    size = size,
                    paint = notePaint,
                    highlightPaint = noteHighlightPaint,
                    notes = notes,
                    notesToHighlight = notesToHighlight,
                    cellSize = cellSize,
                    cellSizeDivWidth = cellSizeDivWidth,
                    killerSumBounds = killerSumBounds,
                    noteGlyphCenterY = noteGlyphCenterY
                )
            }

            // 10. Killer cages (unchanged behaviour).
            if (cages.isNotEmpty()) {
                cages.forEach { cage ->
                    val textToDraw = if (questions) "?" else cage.sum.toString()
                    killerSumPaint.getTextBounds(textToDraw, 0, textToDraw.length, killerSumBounds)
                    val cellWithSum = cage.cells.first()

                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            textToDraw,
                            cellWithSum.col * cellSize + (cellSize * 0.05f),
                            cellWithSum.row * cellSize + killerSumBounds.height() + (cellSize * 0.05f),
                            killerSumPaint
                        )
                    }

                    drawKillerCage(
                        cage = cage,
                        cellWithSum = cellWithSum,
                        cellSize = cellSize,
                        strokeWidth = thickLineWidth,
                        color = thinLineColor,
                        cornerTextPadding = Offset(
                            killerSumBounds.width().toFloat(),
                            killerSumBounds.height().toFloat()
                        )
                    )
                }
            }
        }
    }
}

/** Paint for Kanji: centred horizontally, Japanese glyph forms, given typeface. */
internal fun kanjiPaint(color: Color, sizePx: Float, face: Typeface): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.color = color.toArgb()
        textSize = sizePx
        textAlign = Paint.Align.CENTER
        textLocale = Locale.JAPAN
        typeface = face
    }

@LightDarkPreview
@Composable
private fun BoardPreviewLight() {
    LibreSudokuTheme {
        Surface {
            val sudokuParser = SudokuParser()
            val board by remember {
                mutableStateOf(
                    sudokuParser.parseBoard(
                        board = "....1........4.............7...........9........68...............5...............",
                        gameType = GameType.Default9x9,
                        emptySeparator = '.'
                    ).toList()
                )
            }
            val notes = listOf(Note(2, 3, 1), Note(2, 3, 5))
            Board(
                board = board,
                notes = notes,
                selectedCell = Cell(-1, -1),
                onClick = { },
                selectedNumberForHighlight = 1,
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
