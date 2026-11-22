package com.kaajjo.libresudoku.tools

import android.content.Context
import android.os.Environment
import com.kaajjo.libresudoku.core.qqwing.GameDifficulty
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.qqwing.QQWingController
import java.io.File

object KdpSudokuExporter {

    private const val EASY_COUNT = 30
    private const val MEDIUM_COUNT = 30
    private const val HARD_COUNT = 30

    private val kanjiSymbols = listOf(
        "水", "木", "火", "山", "空", "月", "花", "風", "日"
    )

    fun exportVolume2(context: Context): File {

        val baseDirectory =
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir

        val exportDirectory = File(
            baseDirectory,
            "SudokuMultiverse"
        )

        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs()
        }

        /*
         * If Volume 1 JSON still exists in this folder,
         * load its puzzles so Volume 2 cannot repeat them.
         */
        val volume1File = File(
            exportDirectory,
            "kanji_sudoku_easy_volume_1.json"
        )

        val usedPuzzles = mutableSetOf<String>()

        if (volume1File.exists()) {
            usedPuzzles.addAll(
                extractPuzzleStrings(volume1File)
            )
        }

        val puzzles = mutableListOf<PuzzlePair>()

        // 1–30 EASY
        generateSection(
            difficulty = GameDifficulty.Easy,
            displayDifficulty = "Easy",
            count = EASY_COUNT,
            puzzles = puzzles,
            usedPuzzles = usedPuzzles
        )

        // 31–60 MEDIUM
        generateSection(
            difficulty = GameDifficulty.Moderate,
            displayDifficulty = "Medium",
            count = MEDIUM_COUNT,
            puzzles = puzzles,
            usedPuzzles = usedPuzzles
        )

        // 61–90 HARD
        generateSection(
            difficulty = GameDifficulty.Hard,
            displayDifficulty = "Hard",
            count = HARD_COUNT,
            puzzles = puzzles,
            usedPuzzles = usedPuzzles
        )

        val json = buildJson(puzzles)

        val outputFile = File(
            exportDirectory,
            "kanji_sudoku_volume_2.json"
        )

        outputFile.writeText(json)

        return outputFile
    }

    private fun generateSection(
        difficulty: GameDifficulty,
        displayDifficulty: String,
        count: Int,
        puzzles: MutableList<PuzzlePair>,
        usedPuzzles: MutableSet<String>
    ) {

        var generatedForSection = 0

        while (generatedForSection < count) {

            val controller = QQWingController()

            val puzzle = controller.generate(
                GameType.Default9x9,
                difficulty
            )

            val solution = controller.solve(
                puzzle,
                GameType.Default9x9
            )

            // Safety check: must have exactly one valid solution
            if (
                controller.isImpossible ||
                controller.solutionCount != 1
            ) {
                continue
            }

            val puzzleString = puzzle.joinToString("")
            val solutionString = solution.joinToString("")

            // Reject duplicates from Volume 1 or Volume 2
            if (!usedPuzzles.add(puzzleString)) {
                continue
            }

            puzzles.add(
                PuzzlePair(
                    number = puzzles.size + 1,
                    difficulty = displayDifficulty,
                    puzzle = puzzleString,
                    solution = solutionString
                )
            )

            generatedForSection++
        }
    }

    private fun extractPuzzleStrings(
        file: File
    ): Set<String> {

        val regex =
            Regex(""""puzzle"\s*:\s*"([0-9]{81})"""")

        return regex
            .findAll(file.readText())
            .map { match ->
                match.groupValues[1]
            }
            .toSet()
    }

    private fun buildJson(
        puzzles: List<PuzzlePair>
    ): String {

        return buildString {

            appendLine("{")
            appendLine("""  "book": "Kanji Sudoku",""")
            appendLine("""  "volume": 2,""")
            appendLine("""  "type": "9x9",""")
            appendLine("""  "puzzleCount": ${puzzles.size},""")

            appendLine("""  "difficultyBreakdown": {""")
            appendLine("""    "Easy": $EASY_COUNT,""")
            appendLine("""    "Medium": $MEDIUM_COUNT,""")
            appendLine("""    "Hard": $HARD_COUNT""")
            appendLine("  },")

            appendLine("""  "symbols": [""")

            kanjiSymbols.forEachIndexed { index, symbol ->

                val comma =
                    if (index < kanjiSymbols.lastIndex) "," else ""

                appendLine(
                    """    {"value": ${index + 1}, "symbol": "$symbol"}$comma"""
                )
            }

            appendLine("  ],")
            appendLine("""  "puzzles": [""")

            puzzles.forEachIndexed { index, item ->

                appendLine("    {")
                appendLine("""      "number": ${item.number},""")
                appendLine("""      "difficulty": "${item.difficulty}",""")
                appendLine("""      "puzzle": "${item.puzzle}",""")
                appendLine("""      "solution": "${item.solution}"""")
                append("    }")

                if (index < puzzles.lastIndex) {
                    append(",")
                }

                appendLine()
            }

            appendLine("  ]")
            appendLine("}")
        }
    }

    private data class PuzzlePair(
        val number: Int,
        val difficulty: String,
        val puzzle: String,
        val solution: String
    )
}