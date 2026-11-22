package com.kaajjo.libresudoku.ui.components.board

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kaajjo.libresudoku.core.Cell
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class CelebrationKind { Unit, Symbol, Solved }

/** A completed 3×3 (or other) box, by its top-left cell and size in cells. */
data class BoxRef(val row: Int, val col: Int, val height: Int, val width: Int)

/**
 * One board celebration: a soft light wave across [cells], starting at the origin cell,
 * plus an outline trace around each completed box.
 *
 * Purely descriptive. Board animates a clock from 0 to [durationMs] and asks this
 * object how bright each cell is at that moment, so nothing recomposes per frame.
 */
@Immutable
class Celebration(
    val id: Long,
    val kind: CelebrationKind,
    cells: List<Cell>,
    originRow: Int,
    originCol: Int,
    val boxes: List<BoxRef> = emptyList(),
) {
    internal val rows: IntArray
    internal val cols: IntArray
    internal val delays: FloatArray

    /** How long each cell's glow lasts. */
    val pulseMs: Float = when (kind) {
        CelebrationKind.Unit -> 520f
        CelebrationKind.Symbol -> 640f
        CelebrationKind.Solved -> 560f
    }

    /** Peak glow opacity. Kept low so it reads as light, not paint. */
    val maxGlowAlpha: Float = when (kind) {
        CelebrationKind.Unit -> 0.30f
        CelebrationKind.Symbol -> 0.36f
        CelebrationKind.Solved -> 0.40f
    }

    /** How much each Kanji gently "lifts" (grows) at the peak of its glow. */
    val lift: Float = when (kind) {
        CelebrationKind.Unit -> 0.07f
        CelebrationKind.Symbol -> 0.09f
        CelebrationKind.Solved -> 0.10f
    }

    val durationMs: Int

    private val byKey = HashMap<Int, Float>()

    init {
        val unique = cells.distinctBy { key(it.row, it.col) }
        rows = IntArray(unique.size) { unique[it].row }
        cols = IntArray(unique.size) { unique[it].col }
        delays = FloatArray(unique.size) { i ->
            val dr = (rows[i] - originRow).toFloat()
            val dc = (cols[i] - originCol).toFloat()
            when (kind) {
                // Ripple outwards from the cell that was just placed.
                CelebrationKind.Unit -> sqrt(dr * dr + dc * dc) * 45f
                CelebrationKind.Symbol -> sqrt(dr * dr + dc * dc) * 55f
                // Diagonal wave from the top-left corner.
                CelebrationKind.Solved -> (rows[i] + cols[i]) * 32f
            }
        }
        for (i in rows.indices) byKey[key(rows[i], cols[i])] = delays[i]

        val wave = (delays.maxOrNull() ?: 0f) + pulseMs
        val trace = if (boxes.isNotEmpty()) TRACE_MS + FADE_MS else 0f
        durationMs = max(wave, trace).toInt() + 16
    }

    /** 0..1 brightness of a cell whose wave starts at [delay], at time [elapsed]. */
    internal fun intensityAt(delay: Float, elapsed: Float): Float {
        val local = (elapsed - delay) / pulseMs
        if (local <= 0f || local >= 1f) return 0f
        return sin(local * PI).toFloat()
    }

    /** Scale for the Kanji in (row, col) at time [elapsed]; 1 when not celebrating. */
    fun kanjiScale(row: Int, col: Int, elapsed: Float): Float {
        val delay = byKey[key(row, col)] ?: return 1f
        return 1f + lift * intensityAt(delay, elapsed)
    }

    private fun key(row: Int, col: Int) = row * 64 + col

    internal companion object {
        const val TRACE_MS = 520f
        const val FADE_MS = 320f
    }
}

/** Soft radial light behind each celebrating cell. Draw under the grid and Kanji. */
fun DrawScope.drawCelebrationGlow(
    celebration: Celebration,
    elapsed: Float,
    cellSize: Float,
    color: Color
) {
    val c = celebration
    for (i in c.rows.indices) {
        val intensity = c.intensityAt(c.delays[i], elapsed)
        if (intensity < 0.01f) continue
        val a = c.maxGlowAlpha * intensity
        val topLeft = Offset(c.cols[i] * cellSize, c.rows[i] * cellSize)
        val center = Offset(topLeft.x + cellSize / 2f, topLeft.y + cellSize / 2f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = a),
                    color.copy(alpha = a * 0.35f),
                    Color.Transparent
                ),
                center = center,
                radius = cellSize * 0.95f
            ),
            topLeft = topLeft,
            size = Size(cellSize, cellSize)
        )
    }
}

/** Accent outline that draws itself around each completed box, then fades. */
fun DrawScope.drawCelebrationTrace(
    celebration: Celebration,
    elapsed: Float,
    cellSize: Float,
    color: Color,
    strokeWidth: Float
) {
    if (celebration.boxes.isEmpty()) return

    val traceMs = Celebration.TRACE_MS
    val fadeMs = Celebration.FADE_MS
    val p = (elapsed / traceMs).coerceIn(0f, 1f)
    val eased = 1f - (1f - p) * (1f - p) * (1f - p)          // ease-out cubic
    val alpha = if (elapsed < traceMs) 0.85f
    else (0.85f * (1f - (elapsed - traceMs) / fadeMs)).coerceAtLeast(0f)
    if (alpha <= 0f || eased <= 0f) return

    val inset = strokeWidth
    val corner = CornerRadius(cellSize * 0.14f, cellSize * 0.14f)
    val measure = PathMeasure()

    celebration.boxes.forEach { box ->
        val outline = Path().apply {
            addRoundRect(
                RoundRect(
                    left = box.col * cellSize + inset,
                    top = box.row * cellSize + inset,
                    right = (box.col + box.width) * cellSize - inset,
                    bottom = (box.row + box.height) * cellSize - inset,
                    cornerRadius = corner
                )
            )
        }
        measure.setPath(outline, false)
        val segment = Path()
        measure.getSegment(0f, measure.length * eased, segment, true)
        drawPath(
            path = segment,
            color = color.copy(alpha = alpha),
            style = Stroke(width = strokeWidth * 1.2f, cap = StrokeCap.Round)
        )
    }
}
