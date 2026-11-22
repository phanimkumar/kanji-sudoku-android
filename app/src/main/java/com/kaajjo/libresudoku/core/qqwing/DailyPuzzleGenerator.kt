package com.kaajjo.libresudoku.core.qqwing

import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.utils.SudokuParser
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DailyPuzzleGenerator {

    /**
     * "Today" for the daily challenge is the UTC date, so every player in the world
     * gets the same puzzle at the same moment (new puzzle at 05:30 IST).
     */
    fun todayUtc(): LocalDate = LocalDate.now(ZoneOffset.UTC)

    /** Seed / key for today's puzzle, e.g. 20261003. */
    fun todaySeed(): Int = todayUtc().format(DateTimeFormatter.BASIC_ISO_DATE).toInt()

    /** Label for the Home screen, e.g. "Sat, 3 Oct". */
    fun todayLabel(): String =
        todayUtc().format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))

    /** Time left until the next daily puzzle. */
    fun timeUntilNext(): Duration {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val next = todayUtc().plusDays(1).atStartOfDay(ZoneOffset.UTC)
        return Duration.between(now, next)
    }

    fun generateTodayPuzzle(
        difficulty: GameDifficulty = GameDifficulty.Easy,
        type: GameType = GameType.Default9x9
    ): DailyPuzzleResult {
        val seed = todaySeed()

        val qqWing = QQWing(type, difficulty)
        qqWing.setRandom(seed)
        qqWing.generatePuzzle()
        qqWing.solve()

        val puzzle = qqWing.getPuzzle()
        val solution = qqWing.getSolution()

        val puzzleBoard = toCellBoard(puzzle, type.size)
        val solvedBoard = toCellBoard(solution, type.size)

        val parser = SudokuParser()

        return DailyPuzzleResult(
            seed = seed,
            initialBoard = parser.boardToString(puzzleBoard),
            solvedBoard = parser.boardToString(solvedBoard),
            difficulty = difficulty,
            type = type
        )
    }

    private fun toCellBoard(values: IntArray, size: Int): List<List<Cell>> {
        return List(size) { row ->
            List(size) { col ->
                Cell(
                    row = row,
                    col = col,
                    value = values[row * size + col]
                )
            }
        }
    }
}

data class DailyPuzzleResult(
    val seed: Int,
    val initialBoard: String,
    val solvedBoard: String,
    val difficulty: GameDifficulty,
    val type: GameType
)
