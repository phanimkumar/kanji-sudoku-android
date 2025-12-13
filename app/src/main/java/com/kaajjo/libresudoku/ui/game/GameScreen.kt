package com.kaajjo.libresudoku.ui.game

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.sp
import com.kaajjo.libresudoku.destinations.SettingsCategoriesScreenDestination

import androidx.compose.foundation.background
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaajjo.libresudoku.KanjiMapper
import com.kaajjo.libresudoku.R
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.PreferencesConstants
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintData
import com.kaajjo.libresudoku.core.utils.SudokuParser
import com.kaajjo.libresudoku.ui.components.AdvancedHintContainer
import com.kaajjo.libresudoku.ui.components.AnimatedNavigation
import com.kaajjo.libresudoku.ui.components.board.Board
import com.kaajjo.libresudoku.ui.game.components.GameMenu
import com.kaajjo.libresudoku.ui.game.components.KanjiInputBar
import com.kaajjo.libresudoku.ui.game.components.NotesMenu
import com.kaajjo.libresudoku.ui.game.components.ToolBarItem
import com.kaajjo.libresudoku.ui.game.components.ToolbarItem
import com.kaajjo.libresudoku.ui.game.components.UndoRedoMenu
import com.kaajjo.libresudoku.ui.onboarding.FirstGameDialog
import com.kaajjo.libresudoku.ui.theme.KanjiScreenPadding
import com.kaajjo.libresudoku.ui.util.ReverseArrangement
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

// We show "x / 3" in the top bar; 3 is the hard-coded base limit
private const val UI_MISTAKE_LIMIT = 3

@Destination(
    style = AnimatedNavigation::class
    // navArgsDelegate removed here to keep this file simpler.
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(

    gameUid: Long,
    playedBefore: Boolean,
    viewModel: GameViewModel = hiltViewModel(),
    navigator: DestinationsNavigator
) {
    val localView = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    // Kanji info dialog visibility
    var showKanjiInfo by remember { mutableStateOf(false) }

    val firstGame by viewModel.firstGame.collectAsStateWithLifecycle(initialValue = false)
    val resetTimer by viewModel.resetTimerOnRestart.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_GAME_RESET_TIMER
    )
    val mistakesLimit by viewModel.mistakesLimit.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_MISTAKES_LIMIT
    )
    val errorHighlight by viewModel.mistakesMethod.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_HIGHLIGHT_MISTAKES
    )
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_KEEP_SCREEN_ON
    )
    val remainingUse by viewModel.remainingUse.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_REMAINING_USES
    )
    val highlightIdentical by viewModel.identicalHighlight.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_HIGHLIGHT_IDENTICAL
    )
    val positionLines by viewModel.positionLines.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_POSITION_LINES
    )
    val crossHighlight by viewModel.crossHighlight.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_BOARD_CROSS_HIGHLIGHT
    )
    val funKeyboardOverNum by viewModel.funKeyboardOverNum.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_FUN_KEYBOARD_OVER_NUM
    )

    val fontSizeFactor by viewModel.fontSize.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_FONT_SIZE_FACTOR
    )
    val fontSizeValue by remember(fontSizeFactor, viewModel.gameType) {
        mutableStateOf(viewModel.getFontSize(factor = fontSizeFactor))
    }

    val advancedHintEnabled by viewModel.advancedHintEnabled.collectAsStateWithLifecycle(
        initialValue = PreferencesConstants.DEFAULT_ADVANCED_HINT
    )
    val advancedHintMode by viewModel.advancedHintMode.collectAsStateWithLifecycle(false)
    val advancedHintData by viewModel.advancedHintData.collectAsStateWithLifecycle(null)

    if (keepScreenOn) {
        KeepScreenOn()
    }

    if (firstGame) {
        viewModel.pauseTimer()
        FirstGameDialog(
            onFinished = {
                viewModel.setFirstGameFalse()
                viewModel.startTimer()
            }
        )
    }

    var restartButtonAngleState by remember { mutableFloatStateOf(0f) }
    val restartButtonAnimation: Float by animateFloatAsState(
        targetValue = restartButtonAngleState,
        animationSpec = tween(durationMillis = 250),
        label = "restartButtonAnimation"
    )

    val boardBlur by animateDpAsState(
        targetValue = if (viewModel.gamePlaying || viewModel.endGame) 0.dp else 10.dp,
        label = "Game board blur"
    )
    val boardScale by animateFloatAsState(
        targetValue = if (viewModel.gamePlaying || viewModel.endGame) 1f else 0.90f,
        label = "Game board scale"
    )

    Scaffold(
        containerColor = Color(0xFFFDE8EC),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_round_arrow_back_24),
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    // After game: show "show solution / show mine" button
                    AnimatedVisibility(
                        visible = viewModel.endGame &&
                                (viewModel.mistakesCount >= viewModel.effectiveMistakeLimit || viewModel.giveUp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (!viewModel.showSolution) {
                                        // First time (or after hiding) → need ad once
                                        if (!viewModel.solutionAdWatched) {
                                            viewModel.showSolutionAdDialog()
                                        } else {
                                            viewModel.showSolution = true
                                        }
                                    } else {
                                        // Already showing solution → toggle back to user's grid
                                        viewModel.showSolution = false
                                    }
                                }
                            ) {
                                AnimatedContent(
                                    targetState =
                                        if (viewModel.showSolution)
                                            stringResource(R.string.action_show_mine_sudoku)
                                        else
                                            stringResource(R.string.action_show_solution),
                                    label = "Show solution/mine button"
                                ) { labelText ->
                                    Text(labelText)
                                }
                            }

                        }
                    }

                    // Play / Pause
                    AnimatedVisibility(visible = !viewModel.endGame) {
                        val rotationAngle by animateFloatAsState(
                            targetValue = if (viewModel.gamePlaying) 0f else 360f,
                            label = "Play/Pause game icon rotation"
                        )
                        IconButton(onClick = {
                            if (!viewModel.gamePlaying) viewModel.startTimer()
                            else viewModel.pauseTimer()
                            viewModel.currCell = Cell(-1, -1, 0)
                        }) {
                            Icon(
                                modifier = Modifier.rotate(rotationAngle),
                                painter = painterResource(
                                    if (viewModel.gamePlaying) {
                                        R.drawable.ic_round_pause_24
                                    } else {
                                        R.drawable.ic_round_play_24
                                    }
                                ),
                                contentDescription = null
                            )
                        }
                    }

                    // Restart
                    AnimatedVisibility(visible = !viewModel.endGame) {
                        IconButton(onClick = { viewModel.restartDialog = true }) {
                            Icon(
                                modifier = Modifier.rotate(restartButtonAnimation),
                                painter = painterResource(R.drawable.ic_round_replay_24),
                                contentDescription = null
                            )
                        }
                    }

                    // Menu
                    AnimatedVisibility(visible = !viewModel.endGame) {
                        Box {
                            IconButton(onClick = { viewModel.showMenu = !viewModel.showMenu }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = null
                                )
                            }
                            GameMenu(
                                expanded = viewModel.showMenu,
                                onDismiss = { viewModel.showMenu = false },
                                onGiveUpClick = {
                                    viewModel.pauseTimer()
                                    viewModel.giveUpDialog = true
                                },
                                onSettingsClick = {
                                    // Settings navigation from game temporarily disabled
                                    viewModel.showMenu = false
                                    navigator.navigate(
                                        SettingsCategoriesScreenDestination(
                                            launchedFromGame = true
                                        )
                                    )
                                                  },
                                onExportClick = {
                                    val stringBoard = SudokuParser().boardToString(
                                        viewModel.gameBoard,
                                        emptySeparator = '.'
                                    )
                                    clipboardManager.setText(
                                        AnnotatedString(stringBoard.uppercase())
                                    )

                                    if (SDK_INT < 33) {
                                        Toast.makeText(
                                            context,
                                            R.string.export_string_state_copied,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFDE8EC),
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                    actionIconContentColor = Color.Black
                )
            )
        }
    ) { scaffoldPaddings ->
        Column(
            modifier = Modifier
                .padding(scaffoldPaddings)
                .padding(KanjiScreenPadding),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Top info row
            AnimatedVisibility(visible = !viewModel.endGame) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TopBoardSection(stringResource(viewModel.gameDifficulty.resName))


                        TopBoardSection(
                            stringResource(
                                R.string.mistakes_number_out_of,
                                viewModel.mistakesCount,
                                UI_MISTAKE_LIMIT
                            )
                        )


                    val timerEnabled by viewModel.timerEnabled.collectAsStateWithLifecycle(
                        initialValue = PreferencesConstants.DEFAULT_SHOW_TIMER
                    )
                    AnimatedVisibility(visible = timerEnabled || viewModel.endGame) {
                        TopBoardSection(viewModel.timeText)
                    }
                }
            }

            var renderNotes by remember { mutableStateOf(true) }

            // Board
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    AnimatedVisibility(
                        visible = !viewModel.gamePlaying && !viewModel.endGame,
                        enter = expandVertically(clip = false) + fadeIn(),
                        exit = shrinkVertically(clip = false) + fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(12.dp)
                        )
                    }
                }

                Board(
                    modifier = Modifier
                        .blur(boardBlur)
                        .scale(boardScale, boardScale),
                    board = if (!viewModel.showSolution) viewModel.gameBoard
                    else viewModel.solvedBoard,
                    size = viewModel.size,
                    mainTextSize = fontSizeValue,
                    autoFontSize = fontSizeFactor == 0,
                    notes = viewModel.notes,
                    selectedCell = viewModel.currCell,
                    onClick = { cell ->
                        viewModel.processInput(
                            cell = cell,
                            remainingUse = remainingUse,
                        )
                        if (!viewModel.gamePlaying) {
                            localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            viewModel.startTimer()
                        }
                    },
                    onLongClick = { cell ->
                        if (viewModel.processInput(cell, remainingUse, longTap = true)) {
                            localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    },
                    identicalNumbersHighlight = highlightIdentical,
                    errorsHighlight = errorHighlight != 0,
                    positionLines = positionLines,
                    notesToHighlight = if (viewModel.digitFirstNumber > 0) {
                        viewModel.notes.filter { it.value == viewModel.digitFirstNumber }
                    } else {
                        emptyList()
                    },
                    enabled = viewModel.gamePlaying && !viewModel.endGame,
                    questions = !(viewModel.gamePlaying || viewModel.endGame) &&
                            SDK_INT < Build.VERSION_CODES.R,
                    renderNotes = renderNotes && !viewModel.showSolution,
                    zoomable = viewModel.gameType == GameType.Default12x12 ||
                            viewModel.gameType == GameType.Killer12x12,
                    crossHighlight = crossHighlight,
                    cages = viewModel.cages,
                    cellsToHighlight = if (advancedHintMode && advancedHintData != null)
                        advancedHintData!!.helpCells + advancedHintData!!.targetCell
                    else null
                )
            }
            // Kanji meanings button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showKanjiInfo = true }) {
                    Text("Kanji symbols & meanings")
                }
            }

            // Below-board content:
            //  - If game still running → either Advanced Hint UI or Keyboard + Kanji bar
            if (!viewModel.endGame) {
                AnimatedContent(
                    targetState = advancedHintMode,
                    label = "AdvancedHint / Keyboard switch"
                ) { isAdvanced ->
                    if (isAdvanced) {
                        // ADVANCED HINT MODE
                        advancedHintData?.let { hintData ->
                            AdvancedHintContainer(
                                advancedHintData = hintData,
                                onApplyClick = { viewModel.applyAdvancedHint() },
                                onBackClick = { viewModel.cancelAdvancedHint() },
                                onSettingsClick = { /* advanced hint settings */ }
                            )
                        } ?: run {
                            AdvancedHintContainer(
                                advancedHintData = AdvancedHintData(
                                    titleRes = R.string.advanced_hint_no_hint_title,
                                    textResWithArg = Pair(
                                        R.string.advanced_hint_no_hint,
                                        emptyList()
                                    ),
                                    targetCell = Cell(-1, -1, 0),
                                    helpCells = emptyList()
                                ),
                                onApplyClick = null,
                                onBackClick = { viewModel.cancelAdvancedHint() },
                                onSettingsClick = { /* advanced hint settings */ }
                            )
                        }
                    } else {
                        // NORMAL MODE: Kanji bar + toolbar
                        val kanjiSymbols = remember {
                            (1..9).map { KanjiMapper.toSymbol(it) }
                        }
                        val remainingUsesList = viewModel.remainingUsesList

                        Column(
                            verticalArrangement = if (funKeyboardOverNum)
                                ReverseArrangement
                            else
                                Arrangement.Top
                        ) {
                            // Kanji bar under the board
                            KanjiInputBar(
                                symbols = kanjiSymbols,
                                selected = if (viewModel.digitFirstNumber > 0)
                                    KanjiMapper.toSymbol(viewModel.digitFirstNumber)
                                else null,
                                remainingUses = if (remainingUse) remainingUsesList else null,
                                onSelect = { symbol ->
                                    val index = kanjiSymbols.indexOf(symbol)
                                    if (index >= 0) {
                                        if (remainingUse &&
                                            index < remainingUsesList.size &&
                                            remainingUsesList[index] <= 0
                                        ) {
                                            // Symbol exhausted; ignore tap
                                            return@KanjiInputBar
                                        }

                                        val num = index + 1 // 1..9
                                        viewModel.processInputKeyboard(number = num)
                                    }
                                }
                            )

                            // Toolbar row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                // Undo / Redo
                                Box(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    UndoRedoMenu(
                                        expanded = viewModel.showUndoRedoMenu,
                                        onDismiss = { viewModel.showUndoRedoMenu = false },
                                        onRedoClick = {
                                            viewModel.toolbarClick(ToolBarItem.Redo)
                                        }
                                    )
                                    ToolbarItem(
                                        painter = painterResource(
                                            R.drawable.ic_round_undo_24
                                        ),
                                        onClick = {
                                            viewModel.toolbarClick(ToolBarItem.Undo)
                                        },
                                        onLongClick = {
                                            viewModel.showUndoRedoMenu = true
                                        }
                                    )
                                }

                                // Hint button (hard-limited in ViewModel)
                                val hintsDisabled by viewModel.disableHints
                                    .collectAsStateWithLifecycle(
                                        initialValue = PreferencesConstants
                                            .DEFAULT_HINTS_DISABLED
                                    )
                                if (!hintsDisabled) {
                                    ToolbarItem(
                                        modifier = Modifier.weight(1f),
                                        painter = painterResource(
                                            R.drawable.ic_lightbulb_stars_24
                                        ),
                                        onClick = {
                                            // ViewModel enforces HARD_HINT_LIMIT and shows ad dialog
                                            viewModel.toolbarClick(ToolBarItem.Hint)
                                        }
                                    )
                                }

                                // Notes
                                Box(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    NotesMenu(
                                        expanded = viewModel.showNotesMenu,
                                        onDismiss = {
                                            viewModel.showNotesMenu = false
                                        },
                                        onComputeNotesClick = {
                                            viewModel.computeNotes()
                                        },
                                        onClearNotesClick = {
                                            viewModel.clearNotes()
                                        },
                                        renderNotes = renderNotes,
                                        onRenderNotesClick = {
                                            renderNotes = !renderNotes
                                        }
                                    )
                                    ToolbarItem(
                                        painter = painterResource(
                                            R.drawable.ic_round_edit_24
                                        ),
                                        toggled = viewModel.notesToggled,
                                        onClick = {
                                            viewModel.toolbarClick(ToolBarItem.Note)
                                        },
                                        onLongClick = {
                                            if (viewModel.gamePlaying) {
                                                localView.performHapticFeedback(
                                                    HapticFeedbackConstants.VIRTUAL_KEY
                                                )
                                                viewModel.showNotesMenu = true
                                            }
                                        }
                                    )
                                }

                                // Eraser
                                ToolbarItem(
                                    modifier = Modifier.weight(1f),
                                    painter = painterResource(
                                        R.drawable.ic_eraser_24
                                    ),
                                    toggled = viewModel.eraseButtonToggled,
                                    onClick = {
                                        viewModel.toolbarClick(ToolBarItem.Remove)
                                    },
                                    onLongClick = {
                                        if (viewModel.gamePlaying) {
                                            localView.performHapticFeedback(
                                                HapticFeedbackConstants.VIRTUAL_KEY
                                            )
                                            viewModel.toggleEraseButton()
                                        }
                                    }
                                )

                                // Advanced hint button
                                if (advancedHintEnabled) {
                                    ToolbarItem(
                                        modifier = Modifier.weight(1f),
                                        painter = rememberVectorPainter(
                                            Icons.Rounded.AutoAwesome
                                        ),
                                        onClick = {
                                            if (viewModel.gamePlaying) {
                                                viewModel.getAdvancedHint()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

            }

        }

    }

    // ───────────────────── dialogs ─────────────────────

    if (viewModel.restartDialog) {
        viewModel.pauseTimer()
        AlertDialog(
            onDismissRequest = {
                viewModel.restartDialog = false
                viewModel.startTimer()
            },
            title = { Text(stringResource(R.string.action_reset_game)) },
            text = { Text(stringResource(R.string.reset_game_text)) },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.restartDialog = false
                    viewModel.startTimer()
                }) {
                    Text(stringResource(R.string.dialog_no))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    restartButtonAngleState -= 360
                    viewModel.resetGame(resetTimer)
                    viewModel.restartDialog = false
                    viewModel.startTimer()
                }) {
                    Text(stringResource(R.string.dialog_yes))
                }
            }
        )
    }

    if (viewModel.giveUpDialog) {
        viewModel.pauseTimer()
        AlertDialog(
            onDismissRequest = {
                viewModel.giveUpDialog = false
                viewModel.startTimer()
            },
            title = { Text(stringResource(R.string.action_give_up)) },
            text = { Text(stringResource(R.string.give_up_text)) },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.giveUpDialog = false
                    viewModel.startTimer()
                }) {
                    Text(stringResource(R.string.dialog_no))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.giveUp()
                    viewModel.giveUpDialog = false
                    viewModel.pauseTimer()
                }) {
                    Text(stringResource(R.string.dialog_yes))
                }
            }
        )
    }

    // Extra life dialog (triggered when mistakes reach effectiveMistakeLimit)
    val extraLifeDialog by viewModel.extraLifeDialog.collectAsStateWithLifecycle()
    if (extraLifeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onExtraLifeAdRejected() },
            title = { Text("Out of Lives") },
            text = { Text("Watch an ad to get 1 extra life and continue playing.") },
            confirmButton = {
                TextButton(onClick = {
                    // TODO: integrate ad; call this after ad success
                    viewModel.onExtraLifeAdWatched()
                }) {
                    Text("Watch Ad")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onExtraLifeAdRejected() }) {
                    Text("Quit")
                }
            }
        )
    }

    // Extra hint dialog (triggered when hints reach effectiveHintLimit)
    val extraHintDialog by viewModel.extraHintDialog.collectAsStateWithLifecycle()
    if (extraHintDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onExtraHintAdRejected() },
            title = { Text("Out of Hints") },
            text = { Text("Watch an ad to unlock 1 extra hint.") },
            confirmButton = {
                TextButton(onClick = {
                    // TODO: integrate ad; call this after ad success
                    viewModel.onExtraHintAdWatched()
                }) {
                    Text("Watch Ad")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onExtraHintAdRejected() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Kanji symbols information dialog
    if (showKanjiInfo) {
        AlertDialog(
            onDismissRequest = { showKanjiInfo = false },
            title = { Text("Kanji Symbols Guide") },
            text = {
                Column {
                    Text("These are the 9 Kanji used in the game and what they represent:\n")

                    Text("水  – Water: calm, flow, adaptability.")
                    Text("木  – Tree: growth, stability, roots.")
                    Text("火  – Fire: energy, passion, power.")
                    Text("山  – Mountain: strength, patience, challenges.")
                    Text("空  – Sky: freedom, openness, possibilities.")
                    Text("月  – Moon: coolness, reflection, calm nights.")
                    Text("花  – Flower: beauty, creativity, life in bloom.")
                    Text("風  – Wind: change, movement, freshness.")
                    Text("日  – Sun: light, clarity, new beginnings.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showKanjiInfo = false }) {
                    Text("Close")
                }
            }
        )
    }


// Show Solution dialog (gates solution behind ad once)
    val solutionAdDialog by viewModel.solutionAdDialog.collectAsStateWithLifecycle()
    if (solutionAdDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onSolutionAdRejected() },
            title = { Text("Watch Solution") },
            text = { Text("Watch an ad to see the full solution grid.") },
            confirmButton = {
                TextButton(onClick = {
                    // TODO: integrate real ad; after success call:
                    viewModel.onSolutionAdWatched()
                }) {
                    Text("Watch Ad")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onSolutionAdRejected() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (viewModel.gameCompleted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xAA000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Text(
                    text = "🎉 Congratulations 🎉",
                    fontSize = 32.sp,
                    color = Color.White
                )

                Text(
                    text = "You solved the Kanji Sudoku!",
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.padding(top = 12.dp)
                )

                TextButton(
                    modifier = Modifier.padding(top = 24.dp),
                    onClick = {

                        viewModel.resetGame(true)

                        viewModel.gameCompleted = false
                        viewModel.endGame = false
                        viewModel.giveUp = false
                        viewModel.mistakesCount = 0

                        // Start timer again (like restart dialog)
                        viewModel.startTimer()

                    }
                ) {
                    Text("Play Again", color = Color.Yellow, fontSize = 20.sp)
                }
            }
        }
    }

    // ───────────────────── side effects ─────────────────────

    LaunchedEffect(viewModel.mistakesMethod) {
        viewModel.checkMistakesAll()
    }

    LaunchedEffect(Unit) {
        if (!viewModel.endGame && !viewModel.gameCompleted) {
            viewModel.startTimer()
        }
    }

    LaunchedEffect(viewModel.gameCompleted) {
        if (viewModel.gameCompleted) {
            viewModel.onGameComplete()
        }
    }

    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                if (viewModel.gamePlaying) viewModel.startTimer()
            }
            Lifecycle.Event.ON_PAUSE -> {
                viewModel.pauseTimer()
                viewModel.currCell = Cell(-1, -1, 0)
            }
            Lifecycle.Event.ON_DESTROY -> viewModel.pauseTimer()
            else -> {}
        }
    }
}

@Composable
fun TopBoardSection(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun OnLifecycleEvent(onEvent: (owner: LifecycleOwner, event: Lifecycle.Event) -> Unit) {
    val eventHandler = rememberUpdatedState(onEvent)
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    DisposableEffect(lifecycleOwner.value) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { owner, event ->
            eventHandler.value(owner, event)
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun KeepScreenOn() = AndroidView({ View(it).apply { keepScreenOn = true } })
