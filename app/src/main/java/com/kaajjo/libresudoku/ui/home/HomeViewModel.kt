package com.kaajjo.libresudoku.ui.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaajjo.libresudoku.BuildConfig
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.qqwing.Cage
import com.kaajjo.libresudoku.core.qqwing.CageGenerator
import com.kaajjo.libresudoku.core.qqwing.GameDifficulty
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.qqwing.QQWingController
import com.kaajjo.libresudoku.core.utils.SudokuParser
import com.kaajjo.libresudoku.data.database.model.SudokuBoard
import com.kaajjo.libresudoku.data.datastore.AppSettingsManager
import com.kaajjo.libresudoku.domain.repository.BoardRepository
import com.kaajjo.libresudoku.domain.repository.SavedGameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.kaajjo.libresudoku.core.qqwing.DailyPuzzleGenerator

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appSettingsManager: AppSettingsManager,
    private val boardRepository: BoardRepository,
    private val savedGameRepository: SavedGameRepository
) : ViewModel() {

    val lastSavedGame = savedGameRepository.getLast()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val lastGamesLimit = 5
    val lastGames = savedGameRepository.getLastPlayable(limit = lastGamesLimit)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    var insertedBoardUid by mutableStateOf(-1L)

    private val difficulties = listOf(
        GameDifficulty.Easy,
        GameDifficulty.Moderate,
        GameDifficulty.Hard,
        GameDifficulty.Challenge,
    )

    private val types = listOf(
        GameType.Default9x9,
        GameType.Default6x6,
        GameType.Default12x12,
        GameType.Killer9x9,
        GameType.Killer12x12,
        GameType.Killer6x6
    )

    val lastSelectedGameDifficultyType = appSettingsManager.lastSelectedGameDifficultyType
    val saveSelectedGameDifficultyType = appSettingsManager.saveSelectedGameDifficultyType
    val kanjiTheme = appSettingsManager.kanjiTheme
    var selectedDifficulty by mutableStateOf(difficulties.first())
    var selectedType by mutableStateOf(types.first())

    var isGenerating by mutableStateOf(false)
    var isSolving by mutableStateOf(false)
    var readyToPlay by mutableStateOf(false)

    // ✅ New: single loading flag for HomeScreen overlay + Play button lock
    var isStartingGame by mutableStateOf(false)

    private var puzzle =
        List(selectedType.size) { row -> List(selectedType.size) { col -> Cell(row, col, 0) } }
    private var solvedPuzzle =
        List(selectedType.size) { row -> List(selectedType.size) { col -> Cell(row, col, 0) } }

    private inline fun d(msg: () -> String) {
        if (BuildConfig.DEBUG) Log.d("HomeVM", msg())
    }

    fun startGame() {
        // ✅ prevent double tap
        if (isStartingGame) return

        val gameTypeToGenerate = selectedType
        val gameDifficultyToGenerate = selectedDifficulty
        val size = gameTypeToGenerate.size

        // ✅ reset state on main thread
        readyToPlay = false
        insertedBoardUid = -1L
        isStartingGame = true
        isGenerating = false
        isSolving = false

        // reset local boards
        puzzle = List(size) { row -> List(size) { col -> Cell(row, col, 0) } }
        solvedPuzzle = List(size) { row -> List(size) { col -> Cell(row, col, 0) } }

        viewModelScope.launch {
            try {
                // Save last selected difficulty/type if setting enabled (IO)
                withContext(Dispatchers.IO) {
                    val shouldSave = appSettingsManager.saveSelectedGameDifficultyType.first()
                    if (shouldSave) {
                        appSettingsManager.setLastSelectedGameDifficultyType(
                            difficulty = selectedDifficulty,
                            type = selectedType
                        )
                    }
                }

                val qqWingController = QQWingController()

                // ✅ Generate (Default)
                isGenerating = true
                val generated = withContext(Dispatchers.Default) {
                    qqWingController.generate(gameTypeToGenerate, gameDifficultyToGenerate)
                }
                isGenerating = false

                // ✅ Solve (Default)
                isSolving = true
                val solved = withContext(Dispatchers.Default) {
                    qqWingController.solve(generated, gameTypeToGenerate)
                }
                isSolving = false

                d {
                    "Generated size=$size type=$gameTypeToGenerate diff=$gameDifficultyToGenerate " +
                            "impossible=${qqWingController.isImpossible} solCount=${qqWingController.solutionCount}"
                }

                if (qqWingController.isImpossible || qqWingController.solutionCount != 1) {
                    // ❌ fail safe (don’t navigate, don’t hang loader)
                    d { "Abort: puzzle impossible or not uniquely solvable" }
                    isStartingGame = false
                    return@launch
                }

                // Fill puzzles (main thread is fine)
                var givens = 0
                for (i in 0 until size) {
                    for (j in 0 until size) {
                        val g = generated[i * size + j]
                        val s = solved[i * size + j]
                        puzzle[i][j].value = g
                        solvedPuzzle[i][j].value = s
                        if (g != 0) givens++
                    }
                }
                d { "Sanity: givens=$givens / ${size * size}" }

                // Killer cages (Default)
                val cages: List<Cage>? = if (
                    gameTypeToGenerate in setOf(
                        GameType.Killer9x9,
                        GameType.Killer12x12,
                        GameType.Killer6x6
                    )
                ) {
                    withContext(Dispatchers.Default) {
                        CageGenerator(solvedPuzzle, gameTypeToGenerate).generate(2, 5)
                    }
                } else null

                // Insert board (IO)
                val uid = withContext(Dispatchers.IO) {
                    val sudokuParser = SudokuParser()
                    boardRepository.insert(
                        SudokuBoard(
                            uid = 0,
                            initialBoard = sudokuParser.boardToString(puzzle),
                            solvedBoard = sudokuParser.boardToString(solvedPuzzle),
                            difficulty = selectedDifficulty,
                            type = selectedType,
                            killerCages = cages?.let { sudokuParser.killerSudokuCagesToString(it) }
                        )
                    )
                }

                insertedBoardUid = uid
                readyToPlay = uid > 0L

                d { "Inserted board uid=$uid readyToPlay=$readyToPlay" }

                // ✅ If insert failed, unlock loader here.
                // If insert succeeded, HomeScreen will navigate immediately.
                if (!readyToPlay) isStartingGame = false
            } catch (t: Throwable) {
                d { "startGame FAILED: ${t.message}" }
                isGenerating = false
                isSolving = false
                readyToPlay = false
                insertedBoardUid = -1L
                isStartingGame = false
            }
        }
    }

    fun startDailyGame() {
        if (isStartingGame) return

        readyToPlay = false
        insertedBoardUid = -1L
        isStartingGame = true
        isGenerating = true
        isSolving = false

        viewModelScope.launch {
            try {
                val daily = withContext(Dispatchers.Default) {
                    DailyPuzzleGenerator.generateTodayPuzzle(
                        difficulty = GameDifficulty.Easy,
                        type = GameType.Default9x9
                    )
                }

                isGenerating = false

                val uid = withContext(Dispatchers.IO) {
                    boardRepository.insert(
                        SudokuBoard(
                            uid = 0,
                            initialBoard = daily.initialBoard,
                            solvedBoard = daily.solvedBoard,
                            difficulty = daily.difficulty,
                            type = daily.type,
                            killerCages = null
                        )
                    )
                }

                insertedBoardUid = uid
                readyToPlay = uid > 0L

                if (!readyToPlay) isStartingGame = false
            } catch (t: Throwable) {
                d { "startDailyGame FAILED: ${t.message}" }
                isGenerating = false
                isSolving = false
                readyToPlay = false
                insertedBoardUid = -1L
                isStartingGame = false
            }
        }
    }

    fun changeDifficulty(diff: Int) {
        val indexToSet = difficulties.indexOf(selectedDifficulty) + diff
        if (indexToSet in difficulties.indices) {
            selectedDifficulty = difficulties[indexToSet]
        }
    }

    fun changeType(diff: Int) {
        val indexToSet = types.indexOf(selectedType) + diff
        if (indexToSet in types.indices) {
            selectedType = types[indexToSet]
        }
    }

    fun giveUpLastGame() {
        viewModelScope.launch(Dispatchers.IO) {
            lastSavedGame.value?.let {
                if (!it.completed) {
                    savedGameRepository.update(
                        it.copy(
                            completed = true,
                            canContinue = true
                        )
                    )
                }
            }
        }
    }
}
