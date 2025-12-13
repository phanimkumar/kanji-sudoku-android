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
import kotlin.time.Duration
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
) : ViewModel() {

    companion object {
        const val HARD_MISTAKE_LIMIT = 3  // base lives
        const val HARD_HINT_LIMIT = 3     // base hints
    }

    // ---- HARD LIMITS & AD EXTRAS ----

    // Extra lives / hints earned via ads
    private var extraLivesFromAds = 0
    private var extraHintsFromAds = 0

    // Effective limits (base + ad extras)
    val effectiveMistakeLimit: Int
        get() = HARD_MISTAKE_LIMIT + extraLivesFromAds

    val effectiveHintLimit: Int
        get() = HARD_HINT_LIMIT + extraHintsFromAds

    // Dialog state for ads
    private val _extraLifeDialog = MutableStateFlow(false)
    val extraLifeDialog = _extraLifeDialog.asStateFlow()

    private val _extraHintDialog = MutableStateFlow(false)
    val extraHintDialog = _extraHintDialog.asStateFlow()

    // ---- Ad dialog helpers ----

    fun showExtraLifeDialog() {
        _extraLifeDialog.value = true
    }
/*
    fun onExtraLifeAdWatched() {
        // user watched ad → give one more life
        extraLivesFromAds += 1
        _extraLifeDialog.value = false
        // game continues; player can press Play to resume timer
    }*/

    /*fun onExtraLifeAdRejected() {
        // user refused → treat as give up / game over
        _extraLifeDialog.value = false
        giveUp()
    }
*/
    fun showExtraHintDialog() {
        _extraHintDialog.value = true
    }

    // --- Show Solution Ad Gate ---
    private val _solutionAdDialog = MutableStateFlow(false)
    val solutionAdDialog = _solutionAdDialog.asStateFlow()

    var solutionAdWatched by mutableStateOf(false)

    fun showSolutionAdDialog() {
        _solutionAdDialog.value = true
    }

    fun onSolutionAdWatched() {
        // user watched ad → unlock solution for this game
        solutionAdWatched = true
        _solutionAdDialog.value = false
        showSolution = true    // actually reveal solution
    }

    fun onSolutionAdRejected() {
        _solutionAdDialog.value = false
    }


    fun onExtraHintAdWatched() {
        // Player watched ad: give +1 extra hint
        extraHintsFromAds += 1
        _extraHintDialog.value = false
        // Hint will be consumed on next Hint tap
    }

    fun onExtraHintAdRejected() {
        _extraHintDialog.value = false
    }

    // ⬇️ your existing init { ... } and the rest of the class continues from here
    init {
        val gameUid: Long = savedStateHandle["gameUid"] ?: 0L
        val continueSaved: Boolean = savedStateHandle["playedBefore"] ?: false

        val sudokuParser = SudokuParser()


        viewModelScope.launch(Dispatchers.IO) {
            boardEntity = getBoardUseCase(gameUid)
            val savedGame = savedGameRepository.get(boardEntity.uid)

            withContext(Dispatchers.Main) {
                gameType = boardEntity.type
                gameDifficulty = boardEntity.difficulty
            }

            // ... keep the rest of your init as it is ...


            withContext(Dispatchers.Default) {
                initialBoard = sudokuParser.parseBoard(
                    boardEntity.initialBoard,
                    boardEntity.type
                ).toList()
                initialBoard.forEach { cells ->
                    cells.forEach { cell ->
                        cell.locked = cell.value != 0
                    }
                }

                if (boardEntity.solvedBoard.isNotBlank() && !boardEntity.solvedBoard.contains("0")) {
                    solvedBoard = sudokuParser.parseBoard(
                        boardEntity.solvedBoard,
                        boardEntity.type
                    )
                    boardEntity.killerCages?.let { cagesString ->
                        cages = sudokuParser.parseKillerSudokuCages(cagesString)
                    }
                    for (i in solvedBoard.indices) {
                        for (j in solvedBoard.indices) {
                            solvedBoard[i][j].locked = initialBoard[i][j].locked
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        solveBoard()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (savedGame != null && continueSaved) {
                    restoreSavedGame(savedGame)
                } else {
                    gameBoard = initialBoard
                }
                size = gameBoard.size
                undoRedoManager = UndoRedoManager(GameState(gameBoard, notes))
                remainingUsesList = countRemainingUses(gameBoard)
            }
            saveGame()
        }
    }
    fun onExtraLifeAdWatched() {
        // user watched ad → give one more life
        extraLivesFromAds += 1
        _extraLifeDialog.value = false
        // timer resume will be handled from GameScreen after ad if you want
    }

    fun onExtraLifeAdRejected() {
        // user refused → normal game over
        giveUp()
        _extraLifeDialog.value = false
    }

    var giveUp by mutableStateOf(false)

    val fontSize = appSettingsManager.fontSize
    val keepScreenOn = appSettingsManager.keepScreenOn

    var remainingUsesList by mutableStateOf<List<Int>>(emptyList())

    val firstGame = appSettingsManager.firstGame
    private lateinit var boardEntity: SudokuBoard
    var size by mutableIntStateOf(9)
    var gameType by mutableStateOf(GameType.Unspecified)
    var gameDifficulty by mutableStateOf(GameDifficulty.Unspecified)

    // dialogs, menus
    var restartDialog by mutableStateOf(false)
    var showMenu by mutableStateOf(false)
    var showNotesMenu by mutableStateOf(false)
    var showUndoRedoMenu by mutableStateOf(false)

    // count remaining uses
    var remainingUse = appSettingsManager.remainingUse

    // timer
    var timerEnabled = appSettingsManager.timerEnabled

    // identical numbers highlight
    val identicalHighlight = appSettingsManager.highlightIdentical

    // mistakes checking method
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

    // mistakes
    // used for mistakes limit
    var mistakesCount by mutableIntStateOf(0)

    // notes
    var notesToggled by mutableStateOf(false)
    var notes by mutableStateOf(emptyList<Note>())

    private lateinit var initialBoard: List<List<Cell>>
    var gameBoard by mutableStateOf(List(9) { row -> List(9) { col -> Cell(row, col, 0) } })
    var solvedBoard = emptyList<List<Cell>>()
    var cages by mutableStateOf(emptyList<Cage>())

    var currCell by mutableStateOf(Cell(-1, -1, 0))
    private var undoRedoManager = UndoRedoManager(GameState(gameBoard, notes))
    private var sudokuUtils = SudokuUtils()
    var gameCompleted by mutableStateOf(false)

    // Selected number for digit first method
    var digitFirstNumber by mutableIntStateOf(0)
    private val inputMethod = appSettingsManager.inputMethod
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PreferencesConstants.DEFAULT_INPUT_METHOD
        )

    // temporarily use digit first method when true
    private var overrideInputMethodDF by mutableStateOf(false)

    // show/hide solution (when give up)
    var showSolution by mutableStateOf(false)

    // when true, tapping on any cell will clear it
    var eraseButtonToggled by mutableStateOf(false)

    // used only in the game-completed section. Not saved anywhere
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

    private fun clearNotesAtCell(
        notes: List<Note>,
        row: Int = currCell.row,
        col: Int = currCell.col
    ): List<Note> {
        return notes.minus(
            notes.filter { note ->
                note.row == row
                        && note.col == col
            }.toSet()
        )
    }

    private fun emptyNotes(): List<Note> = emptyList()

    fun clearNotes() {
        notes = emptyNotes()
        undoRedoManager.addState(
            GameState(gameBoard, notes)
        )
    }

    private fun addNote(note: Int, row: Int, col: Int): List<Note> {
        return notes.plus(Note(row, col, note))
    }

    private fun removeNote(note: Int, row: Int, col: Int): List<Note> =
        notes.minus(Note(row, col, note))

    private fun getBoardNoRef(): List<List<Cell>> =
        gameBoard.map { items -> items.map { item -> item.copy() } }

    private fun setValueCell(
        value: Int,
        row: Int = currCell.row,
        col: Int = currCell.col
    ): List<List<Cell>> {
        var new = getBoardNoRef()

        new[row][col].value = value
        remainingUsesList = countRemainingUses(new)

        if (currCell.row == row && currCell.col == col) {
            currCell = currCell.copy(value = new[row][col].value)
        }

        // If cell cleared → no error, just return
        if (value == 0) {
            new[row][col].error = false
            currCell = currCell.copy(error = false)
            return new
        }

        // ─── STRICT MISTAKE CHECK: ONLY AGAINST SOLUTION ───
        when (mistakesMethod.value) {
            0 -> {
                // mistake checking off (no red, no error)
                new[row][col].error = false
            }
            else -> {
                // treat any non-matching symbol as mistake
                new = isValidCell(new, new[row][col])
            }
        }

        // keep currCell error in sync with board
        currCell = currCell.copy(error = new[row][col].error)

        // updating mistakes limit
        if (new[row][col].error) {
            mistakesMade++

                mistakesCount++
                if (mistakesCount >= effectiveMistakeLimit && !endGame) {
                    pauseTimer()
                    _extraLifeDialog.value = true   // extra life dialog (ad)
                }

        }

        gameCompleted = isCompleted(new)
        if (gameCompleted) {
            onGameComplete()
        }

        if (autoEraseNotes.value) {
            notes = autoEraseNotes(new, currCell)
        }

        return new
    }


    private fun countRemainingUses(board: List<List<Cell>>): MutableList<Int> {
        val uses = mutableListOf<Int>()
        for (i in 0 until size) {
            uses.add(size - sudokuUtils.countNumberInBoard(board, i + 1))
        }
        return uses
    }

    fun processInput(cell: Cell, remainingUse: Boolean, longTap: Boolean = false): Boolean {
        // 🔒 Don't allow any more moves once we've hit the effective mistake limit
        if (mistakesCount >= effectiveMistakeLimit) return false

        if (gamePlaying) {
            currCell =
                if (currCell.row == cell.row && currCell.col == cell.col && digitFirstNumber == 0) {
                    Cell(-1, -1)
                } else {
                    cell
                }

            if (currCell.row >= 0 && currCell.col >= 0 && !gameBoard[currCell.row][currCell.col].locked) {
                if ((inputMethod.value == 1 || overrideInputMethodDF) && digitFirstNumber > 0) {
                    if (!longTap) {
                        if ((remainingUsesList.size >= digitFirstNumber && remainingUsesList[digitFirstNumber - 1] > 0) || !remainingUse) {
                            processNumberInput(digitFirstNumber)
                            undoRedoManager.addState(GameState(gameBoard, notes))
                            if (notesToggled) currCell =
                                Cell(currCell.row, currCell.col, digitFirstNumber)
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
            } else {
                return false
            }
        } else {
            return false
        }
    }

    fun processInputKeyboard(number: Int, longTap: Boolean = false) {
        // 🔒 Stop keyboard input once limit is reached
        if (mistakesCount >= effectiveMistakeLimit) return
// 🚫 Prevent deleting symbol when user taps the same number already placed
        val current = currCell.value
        if (!longTap && inputMethod.value == 0 && current == number) {
            return // <-- fixed behaviour
        }
        if (gamePlaying) {
            if (!longTap) {
                if (inputMethod.value == 0 && !currCell.locked && currCell.col >= 0 && currCell.row >= 0) {
                    overrideInputMethodDF = false
                    digitFirstNumber = 0
                    processNumberInput(number)
                    undoRedoManager.addState(GameState(gameBoard, notes))
                } else if (inputMethod.value == 1) {
                    digitFirstNumber = if (digitFirstNumber == number) 0 else number
                    currCell = Cell(-1, -1, digitFirstNumber)
                }
            } else {
                if (inputMethod.value == 0) {
                    overrideInputMethodDF = true
                    digitFirstNumber = if (digitFirstNumber == number) 0 else number
                    currCell = Cell(-1, -1, digitFirstNumber)
                }
            }
            eraseButtonToggled = false
        }
    }


    fun processNumberInput(number: Int) {
        val existing = gameBoard[currCell.row][currCell.col].value
        if (existing == number) {
            return
        }
        if (currCell.row >= 0 && currCell.col >= 0 && gamePlaying && !currCell.locked) {
            if (!notesToggled) {
                // Clear all note to set a number
                notes = clearNotesAtCell(notes, currCell.row, currCell.col)

                val currentValue = gameBoard[currCell.row][currCell.col].value

                // NEW: if same number is tapped again, do nothing
                if (currentValue == number) {
                    return
                }

                gameBoard = setValueCell(number)
            } else {
                gameBoard = setValueCell(0)
                setNote(number)
                remainingUsesList = countRemainingUses(gameBoard)
            }
        }
    }

    private fun setNote(number: Int) {
        val note = Note(currCell.row, currCell.col, number)
        notes = if (notes.contains(note)) {
            removeNote(note.value, note.row, note.col)
        } else {
            notesTaken++
            addNote(note.value, note.row, note.col)
        }
    }

    var timeText by mutableStateOf("00:00")
    private var duration = Duration.ZERO
    private lateinit var timer: Timer
    var gamePlaying by mutableStateOf(false)

    fun startTimer() {
        if (!gamePlaying) {
            gamePlaying = true
            val updateRate = 50L

            timer = fixedRateTimer(initialDelay = updateRate, period = updateRate) {
                val prevTime = duration

                duration = duration.plus((updateRate * 1e6).toDuration(DurationUnit.NANOSECONDS))
                // update text every second
                if (prevTime.toInt(DurationUnit.SECONDS) != duration.toInt(DurationUnit.SECONDS)) {
                    timeText = duration.toFormattedString()
                    // save game
                    if (gameBoard.any { it.any { cell -> cell.value != 0 } }) {
                        viewModelScope.launch(Dispatchers.IO) {
                            saveGame()
                        }
                    }
                }
            }
        }
    }

    fun pauseTimer() {
        gamePlaying = false
        timer.cancel()
    }

    fun toolbarClick(item: ToolBarItem) {
        if (gamePlaying) {
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

                ToolBarItem.Hint -> {
                    useHint()
                }

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
                        val notesInCell =
                            notes.count { note -> note.row == currCell.row && note.col == currCell.col }
                        notes = clearNotesAtCell(notes)
                        gameBoard = setValueCell(0)
                        if (prevValue != 0 || notesInCell != 0) {
                            undoRedoManager.addState(GameState(gameBoard, notes))
                        }
                    }
                }
            }
        }
    }

    private fun useHint() {
        // block if hint limit reached
        if (hintsUsed >= effectiveHintLimit) {
            _extraHintDialog.value = true   // ask to watch ad
            return
        }

        if (solvedBoard.isEmpty()) solveBoard()
        if (currCell.row >= 0 && currCell.col >= 0 && !currCell.locked) {
            notes = clearNotesAtCell(notes, currCell.row, currCell.col)
            gameBoard = setValueCell(solvedBoard[currCell.row][currCell.col].value)

            val new = getBoardNoRef()
            new[currCell.row][currCell.col].error = false
            gameBoard = new

            duration = duration.plus(30.toDuration(DurationUnit.SECONDS))
            timeText = duration.toFormattedString()
            undoRedoManager.addState(GameState(gameBoard, notes))
            hintsUsed++
        }
    }

    fun resetGame(resetTimer: Boolean) {
        // stop and reset game
        notes = emptyNotes()
        currCell = Cell(-1, -1, 0)
        if (resetTimer) {
            duration = Duration.ZERO
            timeText = duration.toFormattedString()
        }
        digitFirstNumber = 0
        notesToggled = false
        undoRedoManager.clear()

        // init a new game with initial board
        gameBoard = initialBoard.map { items -> items.map { item -> item.copy() } }

        remainingUsesList = countRemainingUses(gameBoard)


        mistakesMade = 0
        notesTaken = 0
    }

    private fun isValidCell(
        board: List<List<Cell>> = getBoardNoRef(),
        cell: Cell
    ): List<List<Cell>> {
        if (solvedBoard.isNotEmpty()) {
            board[cell.row][cell.col].error =
                solvedBoard[cell.row][cell.col].value != board[cell.row][cell.col].value
        } else {
            solveBoard()
        }
        return board
    }

    private fun isCompleted(board: List<List<Cell>> = getBoardNoRef()): Boolean {
        if (solvedBoard.isEmpty()) solveBoard()
        for (i in solvedBoard.indices) {
            for (j in solvedBoard.indices) {
                if (solvedBoard[i][j].value != board[i][j].value) {
                    return false
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val savedGame = savedGameRepository.get(boardEntity.uid)
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
        notes = sudokuUtils.computeNotes(gameBoard, boardEntity.type)
        undoRedoManager.addState(GameState(gameBoard, notes))
    }

    private fun autoEraseNotes(board: List<List<Cell>> = getBoardNoRef(), cell: Cell): List<Note> {
        if (currCell.row < 0 || currCell.col < 0) {
            return notes
        }
        return sudokuUtils.autoEraseNotes(board, notes, cell, boardEntity.type)
    }

    private suspend fun saveGame() {
        val savedGame = savedGameRepository.get(boardEntity.uid)
        val sudokuParser = SudokuParser()
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
                    uid = boardEntity.uid,
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

    private fun restoreSavedGame(savedGame: SavedGame?) {
        if (savedGame != null) {
            // restore timer and text
            duration = savedGame.timer.toKotlinDuration()
            timeText = duration.toFormattedString()

            mistakesCount = savedGame.mistakes
            val sudokuParser = SudokuParser()
            gameBoard = sudokuParser.parseBoard(
                savedGame.currentBoard,
                boardEntity.type
            )
            notes = sudokuParser.parseNotes(savedGame.notes)

            for (i in gameBoard.indices) {
                for (j in gameBoard.indices) {
                    gameBoard[i][j].locked = initialBoard[i][j].locked

                    if (gameBoard[i][j].value != 0 && !gameBoard[i][j].locked) {
                        if (mistakesMethod.value == 1) {
                            gameBoard[i][j].error =
                                !sudokuUtils.isValidCellDynamic(
                                    board = gameBoard,
                                    cell = gameBoard[i][j],
                                    type = boardEntity.type
                                )
                        } else {
                            gameBoard[i][j].error =
                                isValidCell(gameBoard, gameBoard[i][j])[i][j].error
                        }
                    }
                }
            }
        }
    }

    fun giveUp() {
        giveUp = true
        endGame = true
        currCell = Cell(-1, -1, 0)
        viewModelScope.launch(Dispatchers.IO) {
            val savedGame = savedGameRepository.get(boardEntity.uid)
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
        if (endGame) return

        pauseTimer()
        currCell = Cell(-1, -1, 0)

        viewModelScope.launch(Dispatchers.IO) {
            saveGame()
            recordRepository.insert(
                Record(
                    board_uid = boardEntity.uid,
                    type = boardEntity.type,
                    difficulty = boardEntity.difficulty,
                    date = ZonedDateTime.now(),
                    time = duration.toJavaDuration()
                )
            )
        }

        endGame = true
        gameCompleted = true   // ⭐ THIS IS THE MISSING PART
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

    // to make sure that solvedBoard really contains a solved board
    private fun solveBoard() {
        val qqWing = QQWingController()
        val boardToSolve = boardEntity.initialBoard.map { it.digitToInt(13) }.toIntArray()
        val solved = qqWing.solve(boardToSolve, boardEntity.type)

        val newSolvedBoard = List(boardEntity.type.size) { row ->
            List(boardEntity.type.size) { col ->
                Cell(
                    row,
                    col,
                    0
                )
            }
        }
        for (i in 0 until size) {
            for (j in 0 until size) {
                newSolvedBoard[i][j].value = solved[i * size + j]
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val sudokuParser = SudokuParser()
            updateBoardUseCase(
                boardEntity.copy(solvedBoard = sudokuParser.boardToString(newSolvedBoard))
            )
        }
        solvedBoard = newSolvedBoard

        for (i in solvedBoard.indices) {
            for (j in solvedBoard.indices) {
                solvedBoard[i][j].locked = initialBoard[i][j].locked
            }
        }
    }

    fun checkMistakesAll() {
        var new = getBoardNoRef()

        if (!this::initialBoard.isInitialized) return

        for (i in new.indices) {
            for (j in new.indices) {
                if (new[i][j].value != 0 && !new[i][j].locked) {
                    when (mistakesMethod.value) {
                        0 -> {
                            // mistake checking is off
                            new[i][j].error = false
                        }
                        else -> {
                            // ALWAYS check against final solution
                            new = isValidCell(new, new[i][j])
                        }
                    }
                }
            }
        }

        gameBoard = new
    }


    fun getAdvancedHint() {
        viewModelScope.launch(Dispatchers.Default) {
            currCell = Cell(-1, -1, 0)
            _advancedHintMode.emit(true)
            val hintSettings = runBlocking { appSettingsManager.advancedHintSettings.first() }
            val advancedHint = AdvancedHint(
                type = boardEntity.type,
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
