package com.kaajjo.libresudoku.ui.components.board

import com.kaajjo.libresudoku.ui.theme.KanjiBoardCellBackground
import com.kaajjo.libresudoku.ui.theme.KanjiOuterBorder
import com.kaajjo.libresudoku.ui.theme.KanjiSelectedCell
import com.kaajjo.libresudoku.ui.theme.KanjiRowColHighlight
import com.kaajjo.libresudoku.ui.theme.KanjiSameSymbolHighlight
import com.kaajjo.libresudoku.ui.theme.KanjiSubgridBorder
import com.kaajjo.libresudoku.ui.theme.KanjiCellBorder
import com.kaajjo.libresudoku.ui.theme.KanjiThemeColors
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
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
import com.kaajjo.libresudoku.ui.theme.LibreSudokuTheme
import com.kaajjo.libresudoku.ui.theme.SudokuBoardColors
import com.kaajjo.libresudoku.ui.theme.SudokuBoardColorsImpl
import com.kaajjo.libresudoku.ui.util.LightDarkPreview
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

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
)
{
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
    ) {
        val maxWidth = constraints.maxWidth.toFloat()

        val cellSize by remember(size) { mutableFloatStateOf(maxWidth / size.toFloat()) }
        val cellSizeDivWidth by remember(size) { mutableFloatStateOf(cellSize / ceil(sqrt(size.toFloat()))) }

        val errorColor = boardColors.errorColor
        val highlightColor = boardColors.highlightColor
        val boardBackgroundColor = kanjiTheme?.boardBackground ?: Color(0xFFF1F5F9)
        val outerFrameColor = kanjiTheme?.boardBorder ?: KanjiOuterBorder
        val thinLineColor = kanjiTheme?.boardBorder?.copy(alpha = 0.30f)
            ?: Color(0xFF94A3B8).copy(alpha = 0.7f)
        val thickLineColor = kanjiTheme?.boardBorder?.copy(alpha = 0.78f)
            ?: Color(0xFF334155)
        val notesColor = kanjiTheme?.secondaryText ?: boardColors.notesColor

        val lockedTextColor = kanjiTheme?.primaryText ?: Color(0xFF8B0000)
        val enteredTextColor = kanjiTheme?.accent ?: Color(0xFF8B0000)

        val selectedCellColor = kanjiTheme?.accent?.copy(alpha = 0.34f)
            ?: KanjiSelectedCell
        val rowColHighlightColor = kanjiTheme?.accent?.copy(alpha = 0.11f)
            ?: KanjiRowColHighlight
        val sameSymbolHighlightColor = kanjiTheme?.accent?.copy(alpha = 0.20f)
            ?: KanjiSameSymbolHighlight

        val vertThick by remember(size) { mutableIntStateOf(floor(sqrt(size.toFloat())).toInt()) }
        val horThick by remember(size) { mutableIntStateOf(ceil(sqrt(size.toFloat())).toInt()) }

        var fontSizePx by remember { mutableFloatStateOf(1f) }
        with(LocalDensity.current) {
            LaunchedEffect(autoFontSize, size, mainTextSize) {
                fontSizePx = if (autoFontSize) {
                    (cellSize * 0.70f).toSp().toPx()
                } else {
                    mainTextSize.toPx()
                }
            }
        }

        val noteSizePx = with(LocalDensity.current) { (cellSizeDivWidth * 0.8f).toSp().toPx() }
        val killerSumSizePx = with(LocalDensity.current) { noteSizePx * 1.1f }

        val thinLineWidth = with(LocalDensity.current) { 1.6.dp.toPx() }
        val thickLineWidth = with(LocalDensity.current) { 3.dp.toPx() }

        val killerSumBounds by remember { mutableStateOf(android.graphics.Rect()) }

        var textPaint by remember(fontSizePx) {
            mutableStateOf(
                Paint().apply {
                    color = enteredTextColor.toArgb()
                    isAntiAlias = true
                    textSize = fontSizePx
                    isFakeBoldText = true
                    style = Paint.Style.FILL_AND_STROKE
                    strokeWidth = 0.8f
                }
            )
        }

        var errorTextPaint by remember {
            mutableStateOf(
                Paint().apply {
                    color = errorColor.toArgb()
                    isAntiAlias = true
                    textSize = fontSizePx
                    isFakeBoldText = true
                }
            )
        }

        var lockedTextPaint by remember {
            mutableStateOf(
                Paint().apply {
                    color = lockedTextColor.toArgb()
                    isAntiAlias = true
                    textSize = fontSizePx
                    isFakeBoldText = true
                }
            )
        }

        var notePaint by remember {
            mutableStateOf(
                Paint().apply {
                    color = notesColor.toArgb()
                    isAntiAlias = true
                    textSize = noteSizePx
                }
            )
        }

        val noteHighlightPaint by remember {
            mutableStateOf(
                Paint().apply {
                    color = highlightColor.copy(alpha = 0.3f).toArgb()
                    isAntiAlias = true
                }
            )
        }

        var killerSumPaint by remember {
            mutableStateOf(
                Paint().apply {
                    color = notesColor.toArgb()
                    isAntiAlias = true
                    textSize = killerSumSizePx
                }
            )
        }

        LaunchedEffect(mainTextSize, noteTextSize, boardColors, kanjiTheme) {
            textPaint = Paint().apply {
                color = enteredTextColor.toArgb()
                isAntiAlias = true
                textSize = fontSizePx
                isFakeBoldText = true
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = 0.8f
            }
            notePaint = Paint().apply {
                color = notesColor.toArgb()
                isAntiAlias = true
                textSize = noteSizePx
            }
            errorTextPaint = Paint().apply {
                color = Color(230, 67, 83).toArgb()
                isAntiAlias = true
                textSize = fontSizePx
                isFakeBoldText = true
            }
            lockedTextPaint = Paint().apply {
                color = lockedTextColor.toArgb()
                isAntiAlias = true
                textSize = fontSizePx
                isFakeBoldText = true
            }
            killerSumPaint = Paint().apply {
                color = notesColor.toArgb()
                isAntiAlias = true
                textSize = killerSumSizePx
            }
        }

        var zoom by remember(enabled) { mutableFloatStateOf(1f) }
        var offset by remember(enabled) { mutableStateOf(Offset.Zero) }

        val boardModifier = Modifier
            .fillMaxSize()
            .pointerInput(key1 = enabled, key2 = board) {
                detectTapGestures(
                    onTap = {
                        val totalOffset = it / zoom + offset
                        val row =
                            floor((totalOffset.y) / cellSize)
                                .toInt()
                                .coerceIn(board.indices)
                        val column =
                            floor((totalOffset.x) / cellSize)
                                .toInt()
                                .coerceIn(board.indices)
                        onClick(board[row][column])
                    },
                    onLongPress = {
                        if (enabled) {
                            val totalOffset = it / zoom + offset
                            val row = floor((totalOffset.y) / cellSize).toInt()
                            val column = floor((totalOffset.x) / cellSize).toInt()
                            onLongClick(board[row][column])
                        }
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
            drawRect(
                color = boardBackgroundColor,
                size = this.size
            )

            val cornerRadius = CornerRadius(15f, 15f)
            val hasSelection = selectedCell.row >= 0 && selectedCell.col >= 0

            // 1. Row and column highlight: weakest layer.
            if (hasSelection && positionLines) {
                drawPositionLines(
                    row = selectedCell.row,
                    col = selectedCell.col,
                    gameSize = size,
                    color = rowColHighlightColor,
                    cellSize = cellSize,
                    lineLength = maxWidth,
                    cornerRadius = cornerRadius
                )
            }

            // 2. Same-symbol highlight.
            if (identicalNumbersHighlight && selectedNumberForHighlight > 0) {
                for (i in 0 until size) {
                    for (j in 0 until size) {
                        if (board[i][j].value == selectedNumberForHighlight) {
                            drawRoundCell(
                                row = i,
                                col = j,
                                gameSize = size,
                                rect = Rect(
                                    offset = Offset(
                                        x = j * cellSize,
                                        y = i * cellSize
                                    ),
                                    size = Size(cellSize, cellSize)
                                ),
                                color = sameSymbolHighlightColor,
                                cornerRadius = cornerRadius
                            )
                        }
                    }
                }
            }

            // 3. Selected cell: strongest layer.
            if (hasSelection) {
                drawRoundCell(
                    row = selectedCell.row,
                    col = selectedCell.col,
                    gameSize = size,
                    rect = Rect(
                        offset = Offset(
                            x = selectedCell.col * cellSize,
                            y = selectedCell.row * cellSize
                        ),
                        size = Size(cellSize, cellSize)
                    ),
                    color = selectedCellColor,
                    cornerRadius = cornerRadius
                )
            }

            if (errorsHighlight) {
                for (i in 0 until size) {
                    for (j in 0 until size) {
                        val cell = board[i][j]
                        if (cell.error) {
                            drawRoundCell(
                                row = i,
                                col = j,
                                gameSize = size,
                                rect = Rect(
                                    offset = Offset(
                                        x = j * cellSize,
                                        y = i * cellSize
                                    ),
                                    size = Size(cellSize, cellSize)
                                ),
                                color = errorColor.copy(alpha = 0.35f),
                                cornerRadius = cornerRadius
                            )
                        }
                    }
                }
            }

            cellsToHighlight?.forEach {
                drawRoundCell(
                    row = it.row,
                    col = it.col,
                    gameSize = size,
                    color = highlightColor.copy(alpha = 0.3f),
                    rect = Rect(
                        Offset(
                            x = it.col * cellSize,
                            y = it.row * cellSize
                        ),
                        size = Size(cellSize, cellSize)
                    ),
                    cornerRadius = cornerRadius
                )
            }

            drawBoardFrame(
                thickLineColor = outerFrameColor,
                thickLineWidth = thickLineWidth,
                maxWidth = maxWidth,
                cornerRadius = CornerRadius(15f, 15f)
            )

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
                if (maxWidth >= cellSize * i) {
                    drawLine(
                        color = if (isThickLine) thickLineColor else thinLineColor,
                        start = Offset(0f, cellSize * i.toFloat()),
                        end = Offset(maxWidth, cellSize * i.toFloat()),
                        strokeWidth = if (isThickLine) thickLineWidth else thinLineWidth
                    )
                }
            }

            drawNumbers(
                size = size,
                board = board,
                highlightErrors = errorsHighlight,
                errorTextPaint = errorTextPaint,
                lockedTextPaint = lockedTextPaint,
                textPaint = textPaint,
                questions = questions,
                cellSize = cellSize
            )

            if (!notes.isNullOrEmpty() && !questions && renderNotes) {
                drawNotes(
                    size = size,
                    paint = notePaint,
                    highlightPaint = noteHighlightPaint,
                    notes = notes,
                    notesToHighlight = notesToHighlight,
                    cellSize = cellSize,
                    cellSizeDivWidth = cellSizeDivWidth,
                    killerSumBounds = killerSumBounds
                )
            }

            if (crossHighlight && size != 6) {
                drawCrossSelection(
                    gameSize = size,
                    sectionWidth = getSectionWidthForSize(size),
                    sectionHeight = getSectionHeightForSize(size),
                    color = highlightColor.copy(alpha = 0.1f),
                    cellSize = cellSize
                )
            }

            if (cages.isNotEmpty()) {
                cages.forEach { cage ->
                    val textToDraw = if (questions) "?" else cage.sum.toString()
                    killerSumPaint.getTextBounds(textToDraw, 0, textToDraw.length, killerSumBounds)
                    val cellWithSum = cage.cells.first()
                    killerSumPaint.color = Color(0xFF000000).toArgb()

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