package com.kaajjo.libresudoku.ui.game

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.derivedStateOf
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
import com.kaajjo.libresudoku.ui.components.board.BoxRef
import com.kaajjo.libresudoku.ui.game.components.ToolBarItem
import com.kaajjo.libresudoku.daily.DailyChallengeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

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
    @ApplicationContext appContext: Context,
) : ViewModel() {

    // ───────────── Daily challenge ─────────────
    private val dailyStore = DailyChallengeStore(appContext)
    private val boardUid: Long = savedStateHandle["gameUid"] ?: 0L

    /** True when this game is a daily challenge (today's or an earlier one). */
    val isDailyChallenge: Boolean = dailyStore.isDaily(boardUid)

    /**
     * Solved time waiting to go to the Daily Challenge leaderboard.
     * Set only when TODAY's daily is solved; GameScreen submits it and clears it.
     */
    var dailyTimeToSubmit by mutableStateOf<Long?>(null)
        private set

    fun onDailySubmitted() {
        dailyStore.markSubmitted()
        dailyTimeToSubmit = null
    }

    companion object {
        const val HARD_MISTAKE_LIMIT = 3
        const val HARD_HINT_LIMIT = 3

        private const val TIMER_TICK_MS = 250L
        private const val SAVE_DEBOUNCE_MS = 400L
        private const val HINT_TIME_PENALTY_S = 30
    }

    // ---- HARD LIMITS & AD EXTRAS ----
    // Snapshot state, so the "x / limit" labels recompose when an ad grants an extra.
    private var extraLivesFromAds by mutableIntStateOf(0)
    private var extraHintsFromAds by mutableIntStateOf(0)
    val kanjiTheme = appSettingsManager.kanjiTheme
    val effectiveMistakeLimit: Int
        get() = HARD_MISTAKE_LIMIT + extraLivesFromAds

    val effectiveHintLimit: Int
        get() = HARD_HINT_LIMIT + extraHintsFromAds

    private val _extraLifeDialog = MutableStateFlow(false)
    val extraLifeDialog = _extraLifeDialog.asStateFlow()

    private val _extraHintDialog = MutableStateFlow(false)
    val extraHintDialog = _extraHintDialog.asStateFlow()

    // One-shot feedback events (haptics, sounds, celebrations). GameScreen collects them.
    private val _events = Channel<GameEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Trackers for one-time celebration per section
    private val completedRows = mutableSetOf<Int>()
    private val completedCols = mutableSetOf<Int>()
    private val completedBoxes = mutableSetOf<Pair<Int, Int>>() // (boxRowIndex, boxColIndex)
    private val celebratedSymbols = mutableSetOf<Int>()

    /**
     * Kanji (1..9) whose nine cells are all currently correct. Drives the keypad seal.
     * Derived from the board, so it stays right after undo, redo and restore.
     */
    val completedSymbols: Set<Int> by derivedStateOf {
        val solved = solvedBoard
        val board = gameBoard
        if (solved.isEmpty() || solved.size != board.size) emptySet()
        else (1..board.size).filterTo(HashSet()) { isSymbolComplete(board, it) }
    }

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

    /** Called after the rewarded ad closes with the reward earned. Applies the hint right away. */
    fun onExtraHintAdWatched() {
        _extraHintDialog.value = false
        extraHintsFromAds += 1
        startTimer()
        useHint()
    }

    fun onExtraHintAdRejected() {
        _extraHintDialog.value = false
        startTimer()
    }

    fun onExtraLifeAdWatched() {
        _extraLifeDialog.value = false
        extraLivesFromAds += 1
        mistakesCount = effectiveMistakeLimit - 1
        saveNow()
        startTimer()
    }

    fun onExtraLifeAdRejected() {
        _extraLifeDialog.value = false
        giveUp()
    }

    // ---- async-loaded data ----
    private var boardEntity: SudokuBoard? = null
    private var initialBoard: List<List<Cell>>? = null

    /** True once the board (and any saved game) has been loaded. */
    var isLoaded by mutableStateOf(false)
        private set

    /** True if the puzzle could not be loaded. GameScreen shows a message. */
    var loadFailed by mutableStateOf(false)
        private set

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

    // Selected (armed) Kanji for keypad-first input
    var digitFirstNumber by mutableIntStateOf(0)
    private val inputMethod = appSettingsManager.inputMethod.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PreferencesConstants.DEFAULT_INPUT_METHOD
    )

    private var overrideInputMethodDF by mutableStateOf(false)

    var showSolution by mutableStateOf(false)
    var eraseButtonToggled by mutableStateOf(false)

    var hintsUsed by mutableIntStateOf(0)
    var mistakesMade = 0
    var notesTaken = 0

    val allRecords by lazy { getAllRecordsUseCase(gameDifficulty, gameType) }

    val advancedHintEnabled = appSettingsManager.advancedHintEnabled
    private var _advancedHintMode = MutableStateFlow(false)
    val advancedHintMode = _advancedHintMode.asStateFlow()

    private var _advancedHintData = MutableStateFlow<AdvancedHintData?>(null)
    val advancedHintData = _advancedHintData.asStateFlow()

    private var _advancedHintText = MutableStateFlow("")
    val advancedHintText = _advancedHintText.asStateFlow()

    // ---- timer ----
    var timeText by mutableStateOf("00:00")
    var duration = Duration.ZERO
        private set
    private var timerJob: Job? = null
    private var saveJob: Job? = null
    var gamePlaying by mutableStateOf(false)

    init {
        val navUid: Long = savedStateHandle["gameUid"] ?: 0L
        val continueSaved: Boolean = savedStateHandle["playedBefore"] ?: false
        val sudokuParser = SudokuParser()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = getBoardUseCase(navUid)
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
                        // Extra lives aren't stored in the database. If the saved game already
                        // used one (mistakes >= base limit), grant it again so the board isn't
                        // locked on reopen.
                        extraLivesFromAds =
                            (mistakesCount - HARD_MISTAKE_LIMIT + 1).coerceAtLeast(0)
                    } else {
                        gameBoard = initBoard
                    }

                    size = gameBoard.size
                    undoRedoManager = UndoRedoManager(GameState(gameBoard, notes))
                    remainingUsesList = countRemainingUses(gameBoard)

                    rebuildCompletedSections()

                    isLoaded = true
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    isLoaded = false
                    loadFailed = true
                    endGame = true
                    gamePlaying = false
                }
            }
        }
    }

    // ---- helpers ----
    private fun ready(): Boolean = boardEntity != null && initialBoard != null

    private fun inBounds(row: Int, col: Int): Boolean =
        row in gameBoard.indices && col in gameBoard.indices

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
        saveNow()
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

    // Correct-only checks using solvedBoard
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

        celebratedSymbols.clear()
        for (v in 1..localSize) {
            if (isSymbolComplete(gameBoard, v)) celebratedSymbols.add(v)
        }
    }

    /** True when every cell whose answer is [value] currently holds [value]. */
    private fun isSymbolComplete(board: List<List<Cell>>, value: Int): Boolean {
        val solved = solvedBoard
        if (solved.isEmpty() || value <= 0) return false
        var found = 0
        for (r in solved.indices) {
            for (c in solved[r].indices) {
                if (solved[r][c].value == value) {
                    if (board[r][c].value != value) return false
                    found++
                }
            }
        }
        return found > 0
    }

    // ---- core: set value ----
    private fun setValueCell(
        value: Int,
        row: Int = currCell.row,
        col: Int = currCell.col
    ): List<List<Cell>> {
        if (!ready()) return gameBoard
        if (!inBounds(row, col)) return gameBoard

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

        // The armed Kanji has just been used up: release it so taps don't silently do nothing.
        if (digitFirstNumber == value && remainingUsesList.getOrNull(value - 1) == 0) {
            digitFirstNumber = 0
        }

        val isMistake = new[row][col].error
        if (isMistake) {
            mistakesMade++
            mistakesCount++
            _events.trySend(GameEvent.Mistake)
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

        // Solving the puzzle gets its own finale; skip the smaller celebrations.
        if (gameCompleted) {
            _events.trySend(GameEvent.Solved)
            return new
        }
        if (isMistake) return new

        // One feedback event per move, covering everything the move completed.
        val completedCells = mutableListOf<Cell>()
        val boxes = mutableListOf<BoxRef>()
        var units = 0

        if (row !in completedRows && isRowCorrect(new, row)) {
            completedRows.add(row)
            completedCells.addAll(new[row])
            units++
        }

        if (col !in completedCols && isColCorrect(new, col)) {
            completedCols.add(col)
            for (r in new.indices) completedCells.add(new[r][col])
            units++
        }

        val boxH = getSectionHeightForSize(new.size)
        val boxW = getSectionWidthForSize(new.size)
        val boxKey = Pair(row / boxH, col / boxW)

        if (boxKey !in completedBoxes && isBoxCorrect(new, row, col)) {
            completedBoxes.add(boxKey)

            val startRow = (row / boxH) * boxH
            val startCol = (col / boxW) * boxW
            boxes.add(BoxRef(startRow, startCol, boxH, boxW))

            for (r in startRow until startRow + boxH) {
                for (c in startCol until startCol + boxW) {
                    completedCells.add(new[r][c])
                }
            }
            units++
        }

        var symbol = 0
        if (value !in celebratedSymbols && isSymbolComplete(new, value)) {
            celebratedSymbols.add(value)
            symbol = value
            for (r in new.indices) {
                for (c in new[r].indices) {
                    if (new[r][c].value == value) completedCells.add(new[r][c])
                }
            }
        }

        if (units > 0 || symbol > 0) {
            _events.trySend(
                GameEvent.Completed(
                    cells = completedCells,
                    originRow = row,
                    originCol = col,
                    boxes = boxes,
                    units = units,
                    symbol = symbol
                )
            )
        } else {
            _events.trySend(GameEvent.Placed)
        }

        return new
    }

    // ---- input handling ----
    fun processInput(cell: Cell, remainingUse: Boolean, longTap: Boolean = false): Boolean {
        if (!ready()) return false
        if (mistakesCount >= effectiveMistakeLimit) return false
        if (!gamePlaying) return false
        if (!inBounds(cell.row, cell.col)) return false

        currCell = cell
        val boardCell = gameBoard[cell.row][cell.col]

        // Tapping a given Kanji arms it, so every matching symbol is highlighted.
        if (boardCell.locked) {
            if (boardCell.value > 0 && !longTap) {
                digitFirstNumber = boardCell.value
                overrideInputMethodDF = true
                eraseButtonToggled = false
            }
            return false
        }

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
        saveNow()
        return true
    }

    /**
     * Keypad press. Arms the Kanji for keypad-first play (tap Kanji, then cells).
     * If nothing was armed and an empty cell is already selected, the Kanji is also
     * placed there right away, so cell-first play works too.
     */
    fun processInputKeyboard(number: Int, longTap: Boolean = false, remainingUse: Boolean = true) {
        if (!ready()) return
        if (!gamePlaying) return
        if (mistakesCount >= effectiveMistakeLimit) return

        val wasArmed = digitFirstNumber > 0

        digitFirstNumber = number
        overrideInputMethodDF = true
        eraseButtonToggled = false

        if (!wasArmed && inBounds(currCell.row, currCell.col)) {
            val target = gameBoard[currCell.row][currCell.col]
            if (!target.locked && target.value == 0) {
                processInput(target, remainingUse)
            }
        }
    }

    fun processNumberInput(number: Int) {
        if (!ready()) return
        if (!inBounds(currCell.row, currCell.col)) return
        val existing = gameBoard[currCell.row][currCell.col].value
        if (existing == number) return

        if (gamePlaying && !currCell.locked) {
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

    /** Starts (or resumes) the clock. Safe to call repeatedly. */
    fun startTimer() {
        if (!ready()) return
        if (endGame || gameCompleted) return
        gamePlaying = true
        if (timerJob?.isActive == true) return

        timerJob = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TIMER_TICK_MS)
                val now = SystemClock.elapsedRealtime()
                duration += (now - last).milliseconds
                last = now
                val text = duration.toFormattedString()
                if (text != timeText) timeText = text
            }
        }
    }

    fun pauseTimer() {
        gamePlaying = false
        timerJob?.cancel()
        timerJob = null
        saveNow()
    }

    /**
     * Saves the game shortly after the latest change (debounced), so several quick
     * moves produce one database write.
     */
    private fun saveNow() {
        if (!ready() || gameCompleted || endGame) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val board = gameBoard
            val notesSnapshot = notes
            val seconds = duration.inWholeSeconds
            val mistakes = mistakesCount
            withContext(Dispatchers.IO) {
                saveGameSafe(board, notesSnapshot, seconds, mistakes)
            }
        }
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
                if (inBounds(currCell.row, currCell.col) && !currCell.locked) {
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
        saveNow()
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

        // Prefer the selected cell if it's empty OR holds a wrong Kanji.
        val selected = if (inBounds(currCell.row, currCell.col)) {
            gameBoard[currCell.row][currCell.col]
        } else null

        val targetCell =
            if (selected != null && !selected.locked && needsHint(selected)) selected
            else findFirstHintableCell(gameBoard)
                ?: return

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

        duration = duration.plus(HINT_TIME_PENALTY_S.toDuration(DurationUnit.SECONDS))
        timeText = duration.toFormattedString()
        undoRedoManager.addState(GameState(gameBoard, notes))
        hintsUsed++
        saveNow()
    }

    private fun needsHint(cell: Cell): Boolean =
        cell.value == 0 || cell.value != solvedBoard[cell.row][cell.col].value

    private fun findFirstHintableCell(board: List<List<Cell>>): Cell? {
        // Empty cells first, then cells holding a wrong Kanji.
        for (r in board.indices) {
            for (c in board[r].indices) {
                val cell = board[r][c]
                if (!cell.locked && cell.value == 0) return cell
            }
        }
        for (r in board.indices) {
            for (c in board[r].indices) {
                val cell = board[r][c]
                if (!cell.locked && needsHint(cell)) return cell
            }
        }
        return null
    }

    // ---- reset ----
    /** Resets the board. The caller resumes play with startTimer(). */
    fun resetGame(resetTimer: Boolean) {
        if (!ready()) return

        completedRows.clear()
        completedCols.clear()
        completedBoxes.clear()
        celebratedSymbols.clear()

        notes = emptyNotes()
        currCell = Cell(-1, -1, 0)

        // A daily challenge never resets its clock, so restarting can't improve a time.
        if (resetTimer && !isDailyChallenge) {
            duration = Duration.ZERO
            timeText = duration.toFormattedString()
        }

        digitFirstNumber = 0
        notesToggled = false
        undoRedoManager.clear()

        val init = initialBoard ?: return
        gameBoard = init.map { row -> row.map { it.copy() } }
        remainingUsesList = countRemainingUses(gameBoard)

        mistakesCount = 0
        mistakesMade = 0
        notesTaken = 0

        endGame = false
        showSolution = false
        gameCompleted = false
        saveNow()
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
        saveNow()
    }

    private fun autoEraseNotesSafe(board: List<List<Cell>> = getBoardNoRef(), cell: Cell): List<Note> {
        val entity = boardEntity ?: return notes
        if (currCell.row < 0 || currCell.col < 0) return notes
        return sudokuUtils.autoEraseNotes(board, notes, cell, entity.type)
    }

    private suspend fun saveGameSafe(
        board: List<List<Cell>>,
        notesToSave: List<Note>,
        timerSeconds: Long,
        mistakes: Int
    ) {
        if (gameCompleted || endGame) return
        val entity = boardEntity ?: return
        val sudokuParser = SudokuParser()
        val savedGame = savedGameRepository.get(entity.uid)

        if (savedGame != null) {
            savedGameRepository.update(
                savedGame.copy(
                    timer = java.time.Duration.ofSeconds(timerSeconds),
                    currentBoard = sudokuParser.boardToString(board),
                    notes = sudokuParser.notesToString(notesToSave),
                    mistakes = mistakes,
                    lastPlayed = ZonedDateTime.now()
                )
            )
        } else {
            savedGameRepository.insert(
                SavedGame(
                    uid = entity.uid,
                    currentBoard = sudokuParser.boardToString(board),
                    notes = sudokuParser.notesToString(notesToSave),
                    timer = java.time.Duration.ofSeconds(timerSeconds),
                    mistakes = mistakes,
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
        pauseTimer()
        if (isDailyChallenge) dailyStore.markGaveUp(entity.uid)

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

        if (isDailyChallenge) {
            val ms = duration.inWholeMilliseconds
            val countsToday = dailyStore.isTodaysDaily(entity.uid)
            dailyStore.markSolved(entity.uid, ms)
            if (countsToday) dailyTimeToSubmit = ms
        }
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

        currCell = Cell(-1, -1, 0)
        _advancedHintMode.value = true

        val boardSnapshot = gameBoard
        val solvedSnapshot = solvedBoard
        val notesSnapshot = notes

        viewModelScope.launch {
            val hintSettings = appSettingsManager.advancedHintSettings.first()
            val hint = withContext(Dispatchers.Default) {
                AdvancedHint(
                    type = entity.type,
                    board = boardSnapshot,
                    solvedBoard = solvedSnapshot,
                    notes = notesSnapshot,
                    settings = hintSettings
                ).getEasiestHint()
            }
            _advancedHintData.value = hint
        }
    }

    fun cancelAdvancedHint() {
        _advancedHintData.value = null
        _advancedHintMode.value = false
    }

    fun applyAdvancedHint() {
        if (!ready()) return
        val cell = _advancedHintData.value?.targetCell
        if (cell == null || !inBounds(cell.row, cell.col)) {
            cancelAdvancedHint()
            return
        }
        digitFirstNumber = cell.value
        overrideInputMethodDF = true
        processInput(gameBoard[cell.row][cell.col], remainingUse = true)
        cancelAdvancedHint()
    }
}
