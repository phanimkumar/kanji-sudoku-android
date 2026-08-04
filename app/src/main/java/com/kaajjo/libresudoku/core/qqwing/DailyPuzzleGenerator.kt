    package com.kaajjo.libresudoku.core.qqwing



    import com.kaajjo.libresudoku.core.Cell
    import com.kaajjo.libresudoku.core.utils.SudokuParser
    import java.time.LocalDate
    import java.time.format.DateTimeFormatter

    object DailyPuzzleGenerator {

        fun todaySeed(): Int {
            return LocalDate.now()
                .format(DateTimeFormatter.BASIC_ISO_DATE)
                .toInt()
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