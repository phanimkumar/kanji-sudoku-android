package com.kaajjo.libresudoku.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.Note
import com.kaajjo.libresudoku.core.PreferencesConstants
import com.kaajjo.libresudoku.core.qqwing.Cage
import com.kaajjo.libresudoku.core.qqwing.GameDifficulty
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.qqwing.QQWingController
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHint
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintData
import com.kaajjo.libresudoku.core.utils.GameState
import com.kaajjo.libresudoku.core.utils.SudokuParser
import com.kaajjo.libresudoku.core.utils.SudokuUtils
import com.kaajjo.libresudoku.core.utils.UndoRedoManager
import com.kaajjo.libresudoku.core.utils.toFormattedString
import com.kaajjo.libresudoku.data.database.model.Record
import com.kaajjo.libresudoku.data.database.model.SavedGame
import com.kaajjo.libresudoku.data.database.model.SudokuBoard
import com.kaajjo.libresudoku.data.datastore.AppSettingsManager
import com.kaajjo.libresudoku.data.datastore.ThemeSettingsManager
import com.kaajjo.libresudoku.domain.repository.RecordRepository
import com.kaajjo.libresudoku.domain.repository.SavedGameRepository
import com.kaajjo.libresudoku.domain.usecase.board.GetBoardUseCase
import com.kaajjo.libresudoku.domain.usecase.board.UpdateBoardUseCase
import com.kaajjo.libresudoku.domain.usecase.record.GetAllRecordsUseCase
import com.kaajjo.libresudoku.ui.game.components.ToolBarItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.Timer
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.delay

@HiltViewModel
class GameViewModel @Inject constructor(
    private val savedGameRepository: SavedGameRepository,
    private val appSettingsManager: AppSettingsManager,
    private val recordRepository: RecordRepository,
    private val updateBoardUseCase: UpdateBoardUseCase,
    private val getBoardUseCase: GetBoardUseCase,
    themeSettingsManager: ThemeSettingsManager,
    private val savedStateHandle: SavedStateHandle,
    private val getAllRecordsUseCase: GetAllRecordsUseCase,
) : ViewModel() {

    companion object {
        const val HARD_MISTAKE_LIMIT = 3
        const val HARD_HINT_LIMIT = 3
    }

    // ---- HARD LIMITS & AD EXTRAS ----
    private var extraLivesFromAds = 0
    private var extraHintsFromAds = 0
    val kanjiTheme = appSettingsManager.kanjiTheme
    val effectiveMistakeLimit: Int
        get() = HARD_MISTAKE_LIMIT + extraLivesFromAds

    val effectiveHintLimit: Int
        get() = HARD_HINT_LIMIT + extraHintsFromAds

    // Dialog state (ads coming soon)
    private val _extraLifeDialog = MutableStateFlow(false)
    val extraLifeDialog = _extraLifeDialog.asStateFlow()

    private val _extraHintDialog = MutableStateFlow(false)
    val extraHintDialog = _extraHintDialog.asStateFlow()

    // ✅ HAPTIC EVENT (GameScreen listens and vibrates)
    private val _sectionCompleteEvent = MutableStateFlow(0)
    val sectionCompleteEvent = _sectionCompleteEvent.asStateFlow()

    private fun notifySectionComplete(cells: List<Cell>) {
        _cellsToHighlight.value = cells.distinctBy { "${it.row}-${it.col}" }
        _sectionCompleteEvent.value = _sectionCompleteEvent.value + 1

        viewModelScope.launch {
            delay(650)
            _cellsToHighlight.value = emptyList()
        }
    }

    // ✅ Trackers for one-time haptic per section
    private val completedRows = mutableSetOf<Int>()
    private val completedCols = mutableSetOf<Int>()
    private val completedBoxes = mutableSetOf<Pair<Int, Int>>() // (boxRowIndex, boxColIndex)

    // --- Show Solution Ad Gate ---
    private val _solutionAdDialog = MutableStateFlow(false)
    val solutionAdDialog = _solutionAdDialog.asStateFlow()

    var solutionAdWatched by mutableStateOf(false)

    fun showSolutionAdDialog() {
        _solutionAdDialog.value = true
    }

    fun onSolutionAdWatched() {
        solutionAdWatched = true
        _solutionAdDialog.value = false
        showSolution = true
    }

    fun onSolutionAdRejected() {
        _solutionAdDialog.value = false
    }

    fun showExtraLifeDialog() {
        _extraLifeDialog.value = true
    }

    fun showExtraHintDialog() {
        _extraHintDialog.value = true
    }

    fun onExtraHintAdWatched() {
        _extraHintDialog.value = false

        extraHintsFromAds += 1

        endGame = false
        gamePlaying = true

        startTimer()
    }

    fun onExtraHintAdRejected() {
        _extraHintDialog.value = false
    }

    fun onExtraLifeAdWatched() {
        _extraLifeDialog.value = false

        extraLivesFromAds += 1
        mistakesCount = effectiveMistakeLimit - 1
        endGame = false
        gamePlaying = true



        startTimer()
    }

    fun onExtraLifeAdRejected() {
        _extraLifeDialog.value = false
        giveUp()
    }

    // ---- IMPORTANT: crash-proof async data ----
    private var boardEntity: SudokuBoard? = null
    private var initialBoard: List<List<Cell>>? = null
    private var isLoaded by mutableStateOf(false)

    // ---- state ----
    var giveUp by mutableStateOf(false)

    val fontSize = appSettingsManager.fontSize
    val keepScreenOn = appSettingsManager.keepScreenOn

    var remainingUsesList by mutableStateOf<List<Int>>(emptyList())

    val firstGame = appSettingsManager.firstGame

    var size by mutableIntStateOf(9)
    var gameType by mutableStateOf(GameType.Unspecified)
    var gameDifficulty by mutableStateOf(GameDifficulty.Unspecified)

    var restartDialog by mutableStateOf(false)
    var showMenu by mutableStateOf(false)
    var showNotesMenu by mutableStateOf(false)
    var showUndoRedoMenu by mutableStateOf(false)

    var remainingUse = appSettingsManager.remainingUse
    var timerEnabled = appSettingsManager.timerEnabled

    val identicalHighlight = appSettingsManager.highlightIdentical

    var mistakesMethod = appSettingsManager.highlightMistakes.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesConstants.DEFAULT_HIGHLIGHT_MISTAKES
    )

    var positionLines = appSettingsManager.positionLines
    val crossHighlight = themeSettingsManager.boardCrossHighlight
    val funKeyboardOverNum = appSettingsManager.funKeyboardOverNumbers

    var mistakesLimit = appSettingsManager.mistakesLimit.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesConstants.DEFAULT_MISTAKES_LIMIT
    )

    private var autoEraseNotes = appSettingsManager.autoEraseNotes.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesConstants.DEFAULT_AUTO_ERASE_NOTES
    )

    var resetTimerOnRestart = appSettingsManager.resetTimerEnabled
    var disableHints = appSettingsManager.hintsDisabled

    var endGame by mutableStateOf(false)
    var giveUpDialog by mutableStateOf(false)

    var mistakesCount by mutableIntStateOf(0)

    var notesToggled by mutableStateOf(false)
    var notes by mutableStateOf(emptyList<Note>())

    // default board (safe)
    var gameBoard by mutableStateOf(List(9) { r -> List(9) { c -> Cell(r, c, 0) } })
    var solvedBoard by mutableStateOf(emptyList<List<Cell>>())
    var cages by mutableStateOf(emptyList<Cage>())

    var currCell by mutableStateOf(Cell(-1, -1, 0))
    private var undoRedoManager = UndoRedoManager(GameState(gameBoard, notes))
    private var sudokuUtils = SudokuUtils()
    var gameCompleted by mutableStateOf(false)

    // Selected number for digit-first method (kept, but you force cell-first in processInputKeyboard)
    var digitFirstNumber by mutableIntStateOf(0)
    private val inputMethod = appSettingsManager.inputMethod.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PreferencesConstants.DEFAULT_INPUT_METHOD
    )

    private var overrideInputMethodDF by mutableStateOf(false)

    var showSolution by mutableStateOf(false)
    var eraseButtonToggled by mutableStateOf(false)

    var hintsUsed = 0
    var mistakesMade = 0
    var notesTaken = 0



    val allRecords by lazy { getAllRecordsUseCase(gameDifficulty, gameType) }

    val advancedHintEnabled = appSettingsManager.advancedHintEnabled
    private var _advancedHintMode = MutableStateFlow(false)
    val advancedHintMode = _advancedHintMode.asStateFlow()

    private var _advancedHintData = MutableStateFlow<AdvancedHintData?>(null)
    val advancedHintData = _advancedHintData.asStateFlow()

    private var _cellsToHighlight = MutableStateFlow<List<Cell>>(emptyList())
    val cellsToHighlight = _cellsToHighlight.asStateFlow()

    private var _advancedHintText = MutableStateFlow("")
    val advancedHintText = _advancedHintText.asStateFlow()

    // ---- timer ----
    var timeText by mutableStateOf("00:00")
    var duration = Duration.ZERO
        private set
    private var timer: Timer? = null
    var gamePlaying by mutableStateOf(false)

    init {
        val navUid: Long = savedStateHandle["gameUid"] ?: 0L
        val continueSaved: Boolean = savedStateHandle["playedBefore"] ?: false
        val sudokuParser = SudokuParser()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val boardUidToLoad = navUid

                val entity = getBoardUseCase(boardUidToLoad)
                boardEntity = entity

                val savedGame: SavedGame? = savedGameRepository.get(entity.uid)

                withContext(Dispatchers.Main) {
                    gameType = entity.type
                    gameDifficulty = entity.difficulty
                }

                val initBoard = withContext(Dispatchers.Default) {
                    sudokuParser.parseBoard(entity.initialBoard, entity.type).toList().also { b ->
                        b.forEach { row -> row.forEach { cell -> cell.locked = cell.value != 0 } }
                    }
                }
                initialBoard = initBoard

                withContext(Dispatchers.Default) {
                    if (entity.solvedBoard.isNotBlank() && !entity.solvedBoard.contains("0")) {
                        val solved = sudokuParser.parseBoard(entity.solvedBoard, entity.type)
                        entity.killerCages?.let { cagesString ->
                            cages = sudokuParser.parseKillerSudokuCages(cagesString)
                        }
                        for (i in solved.indices) {
                            for (j in solved.indices) {
                                solved[i][j].locked = initBoard[i][j].locked
                            }
                        }
                        solvedBoard = solved
                    } else {
                        withContext(Dispatchers.Main) { solveBoardSafe() }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (
                        continueSaved &&
                        savedGame != null &&
                        savedGame.currentBoard.isNotBlank() &&
                        !savedGame.completed
                    ) {
                        restoreSavedGameSafe(savedGame)
                    } else {
                        gameBoard = initBoard
                    }

                    size = gameBoard.size
                    undoRedoManager = UndoRedoManager(GameState(gameBoard, notes))
                    remainingUsesList = countRemainingUses(gameBoard)

                    // ✅ Correction 3: initialize trackers AFTER restore/init
                    rebuildCompletedSections()

                    isLoaded = true
                }


            } catch (ce: CancellationException) {
                // ignore
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    isLoaded = false
                    endGame = true
                    gamePlaying = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timer?.cancel()
        timer = null
    }

    // ---- helpers ----
    private fun ready(): Boolean = boardEntity != null && initialBoard != null

    private fun clearNotesAtCell(
        notes: List<Note>,
        row: Int = currCell.row,
        col: Int = currCell.col
    ): List<Note> {
        return notes.minus(notes.filter { it.row == row && it.col == col }.toSet())
    }

    private fun emptyNotes(): List<Note> = emptyList()

    fun clearNotes() {
        if (!ready()) return
        notes = emptyNotes()
        undoRedoManager.addState(GameState(gameBoard, notes))
    }

    private fun addNote(note: Int, row: Int, col: Int): List<Note> =
        notes.plus(Note(row, col, note))

    private fun removeNote(note: Int, row: Int, col: Int): List<Note> =
        notes.minus(Note(row, col, note))

    private fun getBoardNoRef(): List<List<Cell>> =
        gameBoard.map { row -> row.map { it.copy() } }

    private fun countRemainingUses(board: List<List<Cell>>): MutableList<Int> {
        val uses = mutableListOf<Int>()
        val localSize = board.size
        for (i in 0 until localSize) {
            uses.add(localSize - sudokuUtils.countNumberInBoard(board, i + 1))
        }
        return uses
    }

    private fun getSectionHeightForSize(gameSize: Int): Int {
        return when (gameSize) {
            6 -> 2
            9 -> 3
            12 -> 3
            else -> ceil(sqrt(gameSize.toFloat())).toInt()
        }
    }

    private fun getSectionWidthForSize(gameSize: Int): Int {
        return when (gameSize) {
            6 -> 3
            9 -> 3
            12 -> 4
            else -> ceil(sqrt(gameSize.toFloat())).toInt()
        }
    }

    // ✅ Correct-only checks using solvedBoard
    private fun isRowCorrect(board: List<List<Cell>>, row: Int): Boolean {
        if (solvedBoard.isEmpty()) return false
        for (c in board[row].indices) {
            val v = board[row][c].value
            if (v == 0) return false
            if (v != solvedBoard[row][c].value) return false
        }
        return true
    }

    private fun isColCorrect(board: List<List<Cell>>, col: Int): Boolean {
        if (solvedBoard.isEmpty()) return false
        for (r in board.indices) {
            val v = board[r][col].value
            if (v == 0) return false
            if (v != solvedBoard[r][col].value) return false
        }
        return true
    }

    private fun isBoxCorrect(board: List<List<Cell>>, row: Int, col: Int): Boolean {
        if (solvedBoard.isEmpty()) return false

        val boxH = getSectionHeightForSize(board.size)
        val boxW = getSectionWidthForSize(board.size)

        val boxRow = (row / boxH) * boxH
        val boxCol = (col / boxW) * boxW

        for (r in boxRow until boxRow + boxH) {
            for (c in boxCol until boxCol + boxW) {
                val v = board[r][c].value
                if (v == 0) return false
                if (v != solvedBoard[r][c].value) return false
            }
        }
        return true
    }

    // ✅ Correct3: initialize trackers based on restored/init board
    private fun rebuildCompletedSections() {
        if (solvedBoard.isEmpty()) return

        completedRows.clear()
        completedCols.clear()
        completedBoxes.clear()

        val localSize = gameBoard.size
        val boxH = getSectionHeightForSize(localSize)
        val boxW = getSectionWidthForSize(localSize)

        for (r in 0 until localSize) {
            if (isRowCorrect(gameBoard, r)) completedRows.add(r)
        }
        for (c in 0 until localSize) {
            if (isColCorrect(gameBoard, c)) completedCols.add(c)
        }

        for (r in 0 until localSize step boxH) {
            for (c in 0 until localSize step boxW) {
                if (isBoxCorrect(gameBoard, r, c)) {
                    completedBoxes.add(Pair(r / boxH, c / boxW))
                }
            }
        }
    }

    // ---- core: set value ----
    private fun setValueCell(
        value: Int,
        row: Int = currCell.row,
        col: Int = currCell.col
    ): List<List<Cell>> {
        if (!ready()) return gameBoard

        var new = getBoardNoRef()

        new[row][col].value = value
        remainingUsesList = countRemainingUses(new)

        if (currCell.row == row && currCell.col == col) {
            currCell = currCell.copy(value = new[row][col].value)
        }

        if (value == 0) {
            new[row][col].error = false
            currCell = currCell.copy(error = false)
            return new
        }

        when (mistakesMethod.value) {
            0 -> new[row][col].error = false
            else -> new = isValidCell(new, new[row][col])
        }

        currCell = currCell.copy(error = new[row][col].error)

        if (new[row][col].error) {
            mistakesMade++
            mistakesCount++
            if (mistakesCount >= effectiveMistakeLimit && !endGame) {
                pauseTimer()
                _extraLifeDialog.value = true
            }
        }

        gameCompleted = isCompleted(new)
        if (gameCompleted) onGameComplete()

        if (autoEraseNotes.value) {
            notes = autoEraseNotesSafe(new, currCell)
        }

        // ✅ One-time haptic per completed section
        // ✅ One haptic event per move (avoid 2–3 rapid events being collapsed)
        if (value != 0) {
            val completedCells = mutableListOf<Cell>()

            if (row !in completedRows && isRowCorrect(new, row)) {
                completedRows.add(row)
                completedCells.addAll(new[row])
            }

            if (col !in completedCols && isColCorrect(new, col)) {
                completedCols.add(col)
                for (r in new.indices) completedCells.add(new[r][col])
            }

            val boxH = getSectionHeightForSize(new.size)
            val boxW = getSectionWidthForSize(new.size)
            val boxKey = Pair(row / boxH, col / boxW)

            if (boxKey !in completedBoxes && isBoxCorrect(new, row, col)) {
                completedBoxes.add(boxKey)

                val startRow = (row / boxH) * boxH
                val startCol = (col / boxW) * boxW

                for (r in startRow until startRow + boxH) {
                    for (c in startCol until startCol + boxW) {
                        completedCells.add(new[r][c])
                    }
                }
            }

            if (completedCells.isNotEmpty()) {
                notifySectionComplete(completedCells)
            }
        }


        return new
    }

    // ---- input handling ----
    fun processInput(cell: Cell, remainingUse: Boolean, longTap: Boolean = false): Boolean {
        if (!ready()) return false
        if (mistakesCount >= effectiveMistakeLimit) return false

        if (gamePlaying) {
            currCell = cell

            if (currCell.row >= 0 && currCell.col >= 0 && !gameBoard[currCell.row][currCell.col].locked) {
                if ((inputMethod.value == 1 || overrideInputMethodDF) && digitFirstNumber > 0) {
                    if (!longTap) {
                        if ((remainingUsesList.size >= digitFirstNumber && remainingUsesList[digitFirstNumber - 1] > 0) || !remainingUse) {
                            processNumberInput(digitFirstNumber)
                            undoRedoManager.addState(GameState(gameBoard, notes))
                            if (notesToggled) currCell = Cell(currCell.row, currCell.col, digitFirstNumber)
                        }
                    } else if (!currCell.locked) {
                        gameBoard = setValueCell(0)
                        setNote(digitFirstNumber)
                        undoRedoManager.addState(GameState(gameBoard, notes))
                    }
                } else if (eraseButtonToggled) {
                    val oldCell = currCell
                    processNumberInput(0)
                    if (oldCell.value != 0 && !oldCell.locked) {
                        undoRedoManager.addState(GameState(gameBoard, notes))
                    }
                }
                remainingUsesList = countRemainingUses(gameBoard)
                return true
            }
            return false
        }
        return false
    }

    fun processInputKeyboard(number: Int, longTap: Boolean = false) {
        if (!ready()) return
        if (!gamePlaying) return
        if (mistakesCount >= effectiveMistakeLimit) return

        // ✅ keypad-first selection must happen even without selected cell
        digitFirstNumber = number
        overrideInputMethodDF = true
        eraseButtonToggled = false

        // ✅ do not place number here
        // placement will happen when user taps a cell in processInput()
    }


    fun processNumberInput(number: Int) {
        if (!ready()) return
        val existing = gameBoard[currCell.row][currCell.col].value
        if (existing == number) return

        if (currCell.row >= 0 && currCell.col >= 0 && gamePlaying && !currCell.locked) {
            if (!notesToggled) {
                notes = clearNotesAtCell(notes, currCell.row, currCell.col)
                gameBoard = setValueCell(number)
            } else {
                gameBoard = setValueCell(0)
                setNote(number)
                remainingUsesList = countRemainingUses(gameBoard)
            }
        }
    }

    private fun setNote(number: Int) {
        if (!ready()) return
        val note = Note(currCell.row, currCell.col, number)
        notes = if (notes.contains(note)) {
            removeNote(note.value, note.row, note.col)
        } else {
            notesTaken++
            addNote(note.value, note.row, note.col)
        }
    }

    // ---- timer ----
    fun startTimer() {
        if (!ready()) return
        if (gamePlaying) return
        if (endGame || gameCompleted) return
        gamePlaying = true
        val updateRate = 50L

        timer?.cancel()
        timer = fixedRateTimer(initialDelay = updateRate, period = updateRate) {
            val prevTime = duration
            duration = duration.plus((updateRate * 1e6).toDuration(DurationUnit.NANOSECONDS))
            if (prevTime.toInt(DurationUnit.SECONDS) != duration.toInt(DurationUnit.SECONDS)) {
                timeText = duration.toFormattedString()
                if (gameBoard.any { it.any { cell -> cell.value != 0 } }) {
                    viewModelScope.launch(Dispatchers.IO) { saveGameSafe() }

                }
            }
        }
    }

    fun pauseTimer() {
        gamePlaying = false
        timer?.cancel()
        timer = null
    }

    // ---- toolbar ----
    fun toolbarClick(item: ToolBarItem) {
        if (!ready()) return
        if (!gamePlaying) return

        when (item) {
            ToolBarItem.Undo -> {
                if (undoRedoManager.canUndo()) {
                    undoRedoManager.undo().also {
                        gameBoard = it.board
                        notes = it.notes
                    }
                    checkMistakesAll()
                }
                remainingUsesList = countRemainingUses(gameBoard)
            }

            ToolBarItem.Redo -> {
                if (undoRedoManager.canRedo()) {
                    undoRedoManager.redo()?.let {
                        gameBoard = it.board
                        notes = it.notes
                    }
                    checkMistakesAll()
                }
                remainingUsesList = countRemainingUses(gameBoard)
            }

            ToolBarItem.Hint -> useHint()

            ToolBarItem.Note -> {
                notesToggled = !notesToggled
                eraseButtonToggled = false
            }

            ToolBarItem.Remove -> {
                if (inputMethod.value == 1 || eraseButtonToggled) {
                    toggleEraseButton()
                    return
                }
                if (currCell.row >= 0 && currCell.col >= 0 && !currCell.locked) {
                    val prevValue = gameBoard[currCell.row][currCell.col].value
                    val notesInCell = notes.count { it.row == currCell.row && it.col == currCell.col }
                    notes = clearNotesAtCell(notes)
                    gameBoard = setValueCell(0)
                    if (prevValue != 0 || notesInCell != 0) {
                        undoRedoManager.addState(GameState(gameBoard, notes))
                    }
                }
            }
        }
    }

    // ---- hint ----
    private fun useHint() {
        if (!ready()) return

        if (hintsUsed >= effectiveHintLimit) {
            _extraHintDialog.value = true
            return
        }

        if (solvedBoard.isEmpty()) solveBoardSafe()
        if (solvedBoard.isEmpty()) return

        val targetCell =
            if (currCell.row >= 0 && currCell.col >= 0 && !currCell.locked && currCell.value == 0) {
                currCell
            } else {
                findFirstHintableCell(gameBoard)
            } ?: return

        currCell = gameBoard[targetCell.row][targetCell.col]
        notes = clearNotesAtCell(notes, targetCell.row, targetCell.col)

        gameBoard = setValueCell(
            value = solvedBoard[targetCell.row][targetCell.col].value,
            row = targetCell.row,
            col = targetCell.col
        )

        val new = getBoardNoRef()
        new[targetCell.row][targetCell.col].error = false
        gameBoard = new

        duration = duration.plus(30.toDuration(DurationUnit.SECONDS))
        timeText = duration.toFormattedString()
        undoRedoManager.addState(GameState(gameBoard, notes))
        hintsUsed++
    }

    private fun findFirstHintableCell(board: List<List<Cell>>): Cell? {
        for (r in board.indices) {
            for (c in board[r].indices) {
                val cell = board[r][c]
                if (!cell.locked && cell.value == 0) return cell
            }
        }
        return null
    }

    // ---- reset ----
    fun resetGame(resetTimer: Boolean) {
        if (!ready()) return

        // ✅ clear trackers
        completedRows.clear()
        completedCols.clear()
        completedBoxes.clear()

        notes = emptyNotes()
        currCell = Cell(-1, -1, 0)

        if (resetTimer) {
            duration = Duration.ZERO
            timeText = duration.toFormattedString()
        }

        digitFirstNumber = 0
        notesToggled = false
        undoRedoManager.clear()

        val init = initialBoard ?: return
        gameBoard = init.map { row -> row.map { it.copy() } }
        remainingUsesList = countRemainingUses(gameBoard)

        // ✅ existing
        mistakesMade = 0
        notesTaken = 0

        // 🔥 ADD THESE (CRITICAL)
        endGame = false
        gamePlaying = true
        showSolution = false
        gameCompleted = false   // if you are using this flag
    }

    // ---- validation ----
    private fun isValidCell(board: List<List<Cell>> = getBoardNoRef(), cell: Cell): List<List<Cell>> {
        if (solvedBoard.isNotEmpty()) {
            board[cell.row][cell.col].error =
                solvedBoard[cell.row][cell.col].value != board[cell.row][cell.col].value
        } else {
            solveBoardSafe()
        }
        return board
    }

    private fun isCompleted(board: List<List<Cell>> = getBoardNoRef()): Boolean {
        if (!ready()) return false
        if (solvedBoard.isEmpty()) solveBoardSafe()
        if (solvedBoard.isEmpty()) return false

        for (i in solvedBoard.indices) {
            for (j in solvedBoard.indices) {
                if (solvedBoard[i][j].value != board[i][j].value) return false
            }
        }

        val entity = boardEntity ?: return true
        viewModelScope.launch(Dispatchers.IO) {
            val savedGame = savedGameRepository.get(entity.uid)
            if (savedGame != null) {
                savedGameRepository.update(
                    savedGame.copy(
                        completed = true,
                        giveUp = false,
                        canContinue = false,
                        finishedAt = ZonedDateTime.now()
                    )
                )
            }
        }
        return true
    }

    fun computeNotes() {
        if (!ready()) return
        val entity = boardEntity ?: return
        notes = sudokuUtils.computeNotes(gameBoard, entity.type)
        undoRedoManager.addState(GameState(gameBoard, notes))
    }

    private fun autoEraseNotesSafe(board: List<List<Cell>> = getBoardNoRef(), cell: Cell): List<Note> {
        val entity = boardEntity ?: return notes
        if (currCell.row < 0 || currCell.col < 0) return notes
        return sudokuUtils.autoEraseNotes(board, notes, cell, entity.type)
    }

    private suspend fun saveGameSafe() {
        if (gameCompleted || endGame) return
        val entity = boardEntity ?: return
        val sudokuParser = SudokuParser()
        val savedGame = savedGameRepository.get(entity.uid)

        if (savedGame != null) {
            savedGameRepository.update(
                savedGame.copy(
                    timer = java.time.Duration.ofSeconds(duration.inWholeSeconds),
                    currentBoard = sudokuParser.boardToString(gameBoard),
                    notes = sudokuParser.notesToString(notes),
                    mistakes = mistakesCount,
                    lastPlayed = ZonedDateTime.now()
                )
            )
        } else {
            savedGameRepository.insert(
                SavedGame(
                    uid = entity.uid,
                    currentBoard = sudokuParser.boardToString(gameBoard),
                    notes = sudokuParser.notesToString(notes),
                    timer = java.time.Duration.ofSeconds(duration.inWholeSeconds),
                    mistakes = mistakesCount,
                    lastPlayed = ZonedDateTime.now(),
                    startedAt = ZonedDateTime.now()
                )
            )
        }
    }

    private fun restoreSavedGameSafe(savedGame: SavedGame?) {
        val entity = boardEntity ?: return
        val init = initialBoard ?: return
        if (savedGame == null) return
        if (savedGame.currentBoard.isBlank()) return

        duration = savedGame.timer.toKotlinDuration()
        timeText = duration.toFormattedString()

        mistakesCount = savedGame.mistakes
        val sudokuParser = SudokuParser()

        gameBoard = sudokuParser.parseBoard(savedGame.currentBoard, entity.type)
        notes = sudokuParser.parseNotes(savedGame.notes)

        for (i in gameBoard.indices) {
            for (j in gameBoard.indices) {
                gameBoard[i][j].locked = init[i][j].locked
                if (gameBoard[i][j].value != 0 && !gameBoard[i][j].locked) {
                    if (mistakesMethod.value == 1) {
                        gameBoard[i][j].error = !sudokuUtils.isValidCellDynamic(
                            board = gameBoard,
                            cell = gameBoard[i][j],
                            type = entity.type
                        )
                    } else {
                        gameBoard[i][j].error = isValidCell(gameBoard, gameBoard[i][j])[i][j].error
                    }
                }
            }
        }
    }

    fun giveUp() {
        if (!ready()) return
        val entity = boardEntity ?: return

        giveUp = true
        endGame = true
        currCell = Cell(-1, -1, 0)

        viewModelScope.launch(Dispatchers.IO) {
            val savedGame = savedGameRepository.get(entity.uid)
            if (savedGame != null) {
                val sudokuParser = SudokuParser()
                savedGameRepository.update(
                    savedGame.copy(
                        timer = java.time.Duration.ofSeconds(duration.inWholeSeconds),
                        currentBoard = sudokuParser.boardToString(gameBoard),
                        completed = true,
                        giveUp = true,
                        mistakes = mistakesCount,
                        canContinue = false,
                        finishedAt = ZonedDateTime.now()
                    )
                )
            }
        }
    }

    fun onGameComplete() {
        if (!ready()) return
        if (endGame) return
        val entity = boardEntity ?: return

        pauseTimer()
        currCell = Cell(-1, -1, 0)

        viewModelScope.launch(Dispatchers.IO) {

            recordRepository.insert(
                Record(
                    board_uid = entity.uid,
                    type = entity.type,
                    difficulty = entity.difficulty,
                    date = ZonedDateTime.now(),
                    time = duration.toJavaDuration()
                )
            )
        }

        endGame = true
        gameCompleted = true
    }

    fun getFontSize(type: GameType = gameType, factor: Int): TextUnit {
        return sudokuUtils.getFontSize(type, factor)
    }

    fun setFirstGameFalse() {
        viewModelScope.launch(Dispatchers.IO) {
            appSettingsManager.setFirstGame(false)
        }
    }

    fun toggleEraseButton() {
        notesToggled = false
        currCell = Cell(-1, -1, 0)
        digitFirstNumber = -1
        eraseButtonToggled = !eraseButtonToggled
    }

    private fun solveBoardSafe() {
        val entity = boardEntity ?: return
        val init = initialBoard ?: return

        val qqWing = QQWingController()
        val boardToSolve = entity.initialBoard.map { it.digitToInt(13) }.toIntArray()
        val solved = qqWing.solve(boardToSolve, entity.type)

        val newSolvedBoard = List(entity.type.size) { row ->
            List(entity.type.size) { col -> Cell(row, col, 0) }
        }

        val localSize = entity.type.size
        for (i in 0 until localSize) {
            for (j in 0 until localSize) {
                newSolvedBoard[i][j].value = solved[i * localSize + j]
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val sudokuParser = SudokuParser()
            updateBoardUseCase(entity.copy(solvedBoard = sudokuParser.boardToString(newSolvedBoard)))
        }

        solvedBoard = newSolvedBoard

        for (i in solvedBoard.indices) {
            for (j in solvedBoard.indices) {
                solvedBoard[i][j].locked = init[i][j].locked
            }
        }
    }

    fun checkMistakesAll() {
        if (!ready()) return

        var new = getBoardNoRef()
        for (i in new.indices) {
            for (j in new.indices) {
                if (new[i][j].value != 0 && !new[i][j].locked) {
                    when (mistakesMethod.value) {
                        0 -> new[i][j].error = false
                        else -> new = isValidCell(new, new[i][j])
                    }
                }
            }
        }
        gameBoard = new
    }

    fun getAdvancedHint() {
        if (!ready()) return
        val entity = boardEntity ?: return

        viewModelScope.launch(Dispatchers.Default) {
            currCell = Cell(-1, -1, 0)
            _advancedHintMode.emit(true)
            val hintSettings = runBlocking { appSettingsManager.advancedHintSettings.first() }
            val advancedHint = AdvancedHint(
                type = entity.type,
                board = gameBoard,
                solvedBoard = solvedBoard,
                notes = notes,
                settings = hintSettings
            )
            _advancedHintData.emit(advancedHint.getEasiestHint())
        }
    }

    fun cancelAdvancedHint() {
        viewModelScope.launch(Dispatchers.IO) {
            _advancedHintData.emit(null)
            _advancedHintMode.emit(false)
        }
    }

    fun applyAdvancedHint() {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.Default) {
            val cell = _advancedHintData.value?.targetCell
            if (cell != null) {
                currCell = gameBoard[cell.row][cell.col]
                digitFirstNumber = cell.value
                processInput(cell, true)
                cancelAdvancedHint()
            }
        }
    }


}
