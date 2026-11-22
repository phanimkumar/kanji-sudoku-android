package com.kaajjo.libresudoku.ui.game


import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility as NormalAnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.kaajjo.libresudoku.KanjiMapper
import com.kaajjo.libresudoku.R
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.PreferencesConstants
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintData
import com.kaajjo.libresudoku.core.utils.SudokuParser
import com.kaajjo.libresudoku.daily.PlayGamesHelper
import com.kaajjo.libresudoku.destinations.SettingsCategoriesScreenDestination
import com.kaajjo.libresudoku.ui.AdManager
import com.kaajjo.libresudoku.ui.BannerAd
import com.kaajjo.libresudoku.ui.components.AdvancedHintContainer
import com.kaajjo.libresudoku.ui.components.AnimatedNavigation
import com.kaajjo.libresudoku.ui.components.board.Board
import com.kaajjo.libresudoku.ui.components.board.Celebration
import com.kaajjo.libresudoku.ui.components.board.CelebrationKind
import com.kaajjo.libresudoku.ui.components.board.KanjiFonts
import com.kaajjo.libresudoku.ui.game.components.GameMenu
import com.kaajjo.libresudoku.ui.game.components.HankoRed
import com.kaajjo.libresudoku.ui.game.components.KanjiInputBar
import com.kaajjo.libresudoku.ui.game.components.NotesMenu
import com.kaajjo.libresudoku.ui.game.components.ToolBarItem
import com.kaajjo.libresudoku.ui.game.components.ToolbarItem
import com.kaajjo.libresudoku.ui.game.components.UndoRedoMenu
import com.kaajjo.libresudoku.ui.onboarding.FirstGameDialog
import com.kaajjo.libresudoku.ui.theme.KanjiScreenPadding
import com.kaajjo.libresudoku.ui.theme.KanjiThemeColors
import com.kaajjo.libresudoku.ui.theme.KanjiThemeType
import com.kaajjo.libresudoku.ui.theme.KanjiThemes
import com.kaajjo.libresudoku.ui.util.ReverseArrangement
import com.kaajjo.libresudoku.ui.util.findActivity
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.25f),
                shape = shape
            )
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false
            )
            .padding(10.dp)
    ) {
        content()
    }
}

@Destination(style = AnimatedNavigation::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    gameUid: Long,
    playedBefore: Boolean,
    viewModel: GameViewModel = hiltViewModel(),
    navigator: DestinationsNavigator
) {
    val localView = LocalView.current
    val kanjiTheme by viewModel.kanjiTheme.collectAsStateWithLifecycle(
        initialValue = KanjiThemeType.JapanesePaper
    )

    val theme = KanjiThemes.get(kanjiTheme)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    // findActivity() walks ContextWrappers safely; a plain cast can crash.
    val activity = remember(context) { context.findActivity() }

    var showKanjiInfo by remember { mutableStateOf(false) }

    // ── Feedback & celebrations ──
    val feedback = remember(context, localView) { GameFeedback(context, localView) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }

    var celebration by remember { mutableStateOf<Celebration?>(null) }
    var celebrationSeq by remember { mutableLongStateOf(0L) }
    var caption by remember { mutableStateOf<String?>(null) }
    var lastCaption by remember { mutableStateOf("") }

    // Finale state survives rotation, so nothing replays.
    var sealVisible by rememberSaveable { mutableStateOf(false) }
    var resultVisible by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    GameEvent.Placed -> feedback.placed()
                    GameEvent.Mistake -> feedback.mistake()

                    is GameEvent.Completed -> {
                        celebrationSeq++
                        celebration = Celebration(
                            id = celebrationSeq,
                            kind = if (event.symbol > 0) CelebrationKind.Symbol else CelebrationKind.Unit,
                            cells = event.cells,
                            originRow = event.originRow,
                            originCol = event.originCol,
                            boxes = event.boxes
                        )
                        if (event.symbol > 0) {
                            feedback.symbolComplete()
                            caption = "${KanjiMapper.toSymbol(event.symbol)} · " +
                                    "${KanjiMapper.meaning(event.symbol)} — complete"
                        } else {
                            feedback.unitComplete(event.units)
                        }
                    }

                    GameEvent.Solved -> {
                        celebrationSeq++
                        celebration = Celebration(
                            id = celebrationSeq,
                            kind = CelebrationKind.Solved,
                            cells = viewModel.gameBoard.flatten(),
                            originRow = 0,
                            originCol = 0
                        )
                        feedback.solvedStart()
                    }
                }
            }
        }
    }

    // Symbol-complete caption: fade in, hold, fade out.
    LaunchedEffect(caption) {
        caption?.let {
            lastCaption = it
            delay(2200)
            caption = null
        }
    }

    // Daily challenge: send today's solved time to the Play Games leaderboard.
    // If it fails (offline / not signed in) it is retried from the Home screen.
    LaunchedEffect(viewModel.dailyTimeToSubmit) {
        val ms = viewModel.dailyTimeToSubmit ?: return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        PlayGamesHelper.submitDailyTime(act, ms) { ok ->
            if (ok) viewModel.onDailySubmitted()
        }
    }

    // Finale: board wave (from the Solved event) → 完 seal lands → result card rises.
    LaunchedEffect(viewModel.gameCompleted) {
        if (viewModel.gameCompleted && !resultVisible) {
            if (!sealVisible) {
                delay(1100)
                sealVisible = true
                feedback.seal()
            }
            delay(900)
            resultVisible = true
        }
    }

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

    var renderNotes by remember { mutableStateOf(true) }

    if (keepScreenOn) KeepScreenOn()

    if (firstGame) {
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
        containerColor = Color.Transparent,
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
                    NormalAnimatedVisibility(
                        visible = viewModel.endGame &&
                                (viewModel.mistakesCount >= viewModel.effectiveMistakeLimit || viewModel.giveUp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = {
                                    if (!viewModel.showSolution) {
                                        if (!viewModel.solutionAdWatched) {
                                            viewModel.showSolutionAdDialog()
                                        } else {
                                            viewModel.showSolution = true
                                        }
                                    } else {
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
                    NormalAnimatedVisibility(visible = !viewModel.endGame) {
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
                                    if (viewModel.gamePlaying) R.drawable.ic_round_pause_24
                                    else R.drawable.ic_round_play_24
                                ),
                                contentDescription = null
                            )
                        }
                    }

                    // Restart
                    NormalAnimatedVisibility(visible = !viewModel.endGame) {
                        IconButton(onClick = {
                            viewModel.pauseTimer()
                            viewModel.restartDialog = true
                        }) {
                            Icon(
                                modifier = Modifier.rotate(restartButtonAnimation),
                                painter = painterResource(R.drawable.ic_round_replay_24),
                                contentDescription = null
                            )
                        }
                    }

                    // Menu
                    NormalAnimatedVisibility(visible = !viewModel.endGame) {
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
                                soundEnabled = feedback.soundEnabled,
                                onSoundToggle = { feedback.toggleSound() },
                                hapticsEnabled = feedback.hapticsEnabled,
                                onHapticsToggle = { feedback.toggleHaptics() },
                                onGiveUpClick = {
                                    viewModel.pauseTimer()
                                    viewModel.giveUpDialog = true
                                },
                                onSettingsClick = {
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
                    containerColor = Color.Transparent,
                    titleContentColor = theme.primaryText,
                    navigationIconContentColor = theme.primaryText,
                    actionIconContentColor = theme.primaryText
                )
            )
        }
    ) { scaffoldPaddings ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
                .padding(scaffoldPaddings)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(KanjiScreenPadding),
                verticalArrangement = Arrangement.Top
            ) {

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { // Top info row
                    NormalAnimatedVisibility(visible = !viewModel.endGame) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TopBoardSection(
                                text = stringResource(viewModel.gameDifficulty.resName),
                                theme = theme
                            )

                            // Shows the real limit, including any extra life earned from an ad.
                            TopBoardSection(
                                text = stringResource(
                                    R.string.mistakes_number_out_of,
                                    viewModel.mistakesCount,
                                    viewModel.effectiveMistakeLimit
                                ),
                                theme = theme
                            )

                            TopBoardSection(
                                text = "Hints ${viewModel.hintsUsed}/${viewModel.effectiveHintLimit}",
                                theme = theme
                            )

                            val timerEnabled by viewModel.timerEnabled.collectAsStateWithLifecycle(
                                initialValue = PreferencesConstants.DEFAULT_SHOW_TIMER
                            )

                            NormalAnimatedVisibility(visible = timerEnabled || viewModel.endGame) {
                                TopBoardSection(
                                    text = viewModel.timeText,
                                    theme = theme
                                )
                            }
                        }
                    }

                    // Board area locked to square so it never covers keyboard
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Play icon overlay
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !viewModel.gamePlaying && !viewModel.endGame,
                            enter = expandVertically(clip = false) + fadeIn(),
                            exit = shrinkVertically(clip = false) + fadeOut()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(12.dp),
                                tint = theme.accent
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // Unbounded: don't clip the board's soft glass shadow.
                                .blur(boardBlur, BlurredEdgeTreatment.Unbounded)
                                .scale(boardScale)
                        ) {
                            Board(
                                // The board draws its own glass shadow now.
                                modifier = Modifier.fillMaxSize(),
                                board = if (!viewModel.showSolution) viewModel.gameBoard else viewModel.solvedBoard,
                                size = viewModel.size,
                                mainTextSize = fontSizeValue,
                                autoFontSize = fontSizeFactor == 0,
                                notes = viewModel.notes,
                                selectedCell = viewModel.currCell,
                                selectedNumberForHighlight = viewModel.digitFirstNumber,
                                onClick = { cell ->
                                    viewModel.processInput(cell = cell, remainingUse = remainingUse)
                                },
                                onLongClick = { cell ->
                                    if (viewModel.processInput(
                                            cell,
                                            remainingUse,
                                            longTap = true
                                        )
                                    ) {
                                        localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    }
                                },
                                identicalNumbersHighlight = highlightIdentical,
                                errorsHighlight = errorHighlight != 0,
                                positionLines = positionLines,
                                notesToHighlight = if (viewModel.digitFirstNumber > 0) {
                                    viewModel.notes.filter { it.value == viewModel.digitFirstNumber }
                                } else emptyList(),
                                enabled = viewModel.gamePlaying && !viewModel.endGame,
                                questions = !(viewModel.gamePlaying || viewModel.endGame) &&
                                        SDK_INT < Build.VERSION_CODES.R,
                                renderNotes = renderNotes && !viewModel.showSolution,
                                zoomable = viewModel.gameType == GameType.Default12x12 ||
                                        viewModel.gameType == GameType.Killer12x12,
                                crossHighlight = crossHighlight,
                                cages = viewModel.cages,
                                cellsToHighlight = if (advancedHintMode && advancedHintData != null) {
                                    advancedHintData!!.helpCells + advancedHintData!!.targetCell
                                } else null,
                                kanjiTheme = theme,
                                celebration = celebration
                            )

                            // 完 seal stamped on the board when the puzzle is solved.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = sealVisible && viewModel.gameCompleted,
                                    enter = scaleIn(
                                        initialScale = 2.2f,
                                        animationSpec = spring(
                                            dampingRatio = 0.5f,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    ) + fadeIn(tween(90))
                                ) {
                                    SolvedSeal()
                                }
                            }
                        }
                    }

                    // Kanji meanings button (OUTSIDE the board box), with the
                    // "水 · Water — complete" caption fading in on the left.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = caption != null,
                                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
                                exit = fadeOut(tween(400))
                            ) {
                                Text(
                                    text = lastCaption,
                                    color = theme.accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        TextButton(onClick = { showKanjiInfo = true }) {
                            Text("Kanji symbols & meanings", color = theme.primaryText)
                        }
                    }

                    // Below-board content
                    if (!viewModel.endGame) {
                        AnimatedContent(
                            targetState = advancedHintMode,
                            label = "AdvancedHint / Keyboard switch"
                        ) { isAdvanced ->
                            if (isAdvanced) {
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
                                val kanjiSymbols =
                                    remember { (1..9).map { KanjiMapper.toSymbol(it) } }
                                val remainingUsesList = viewModel.remainingUsesList

                                Column(
                                    verticalArrangement = if (funKeyboardOverNum) ReverseArrangement
                                    else Arrangement.Top
                                ) {
                                    KanjiInputBar(
                                        theme = theme,
                                        symbols = kanjiSymbols,
                                        selected = if (viewModel.digitFirstNumber > 0)
                                            KanjiMapper.toSymbol(viewModel.digitFirstNumber)
                                        else null,
                                        remainingUses = if (remainingUse) remainingUsesList else null,
                                        completedSymbols = viewModel.completedSymbols,
                                        onKeyFeedback = { feedback.keyTap() },
                                        onSelect = { symbol ->
                                            val index = kanjiSymbols.indexOf(symbol)
                                            if (index >= 0) {
                                                if (remainingUse &&
                                                    index < remainingUsesList.size &&
                                                    remainingUsesList[index] <= 0
                                                ) return@KanjiInputBar

                                                viewModel.processInputKeyboard(
                                                    number = index + 1,
                                                    remainingUse = remainingUse
                                                )
                                            }
                                        }
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    ) {
                                        // Undo / Redo
                                        Box(modifier = Modifier.weight(1f)) {
                                            UndoRedoMenu(
                                                expanded = viewModel.showUndoRedoMenu,
                                                onDismiss = { viewModel.showUndoRedoMenu = false },
                                                onRedoClick = {
                                                    viewModel.toolbarClick(ToolBarItem.Redo)
                                                }
                                            )
                                            ToolbarItem(
                                                theme = theme,
                                                painter = painterResource(R.drawable.ic_round_undo_24),
                                                onClick = { viewModel.toolbarClick(ToolBarItem.Undo) },
                                                onLongClick = { viewModel.showUndoRedoMenu = true }
                                            )
                                        }

                                        // Hint
                                        val hintsDisabled by viewModel.disableHints.collectAsStateWithLifecycle(
                                            initialValue = PreferencesConstants.DEFAULT_HINTS_DISABLED
                                        )
                                        if (!hintsDisabled) {
                                            ToolbarItem(
                                                theme = theme,
                                                modifier = Modifier.weight(1f),
                                                painter = painterResource(R.drawable.ic_lightbulb_stars_24),
                                                onClick = { viewModel.toolbarClick(ToolBarItem.Hint) }
                                            )
                                        }

                                        // Notes
                                        Box(modifier = Modifier.weight(1f)) {
                                            NotesMenu(
                                                expanded = viewModel.showNotesMenu,
                                                onDismiss = { viewModel.showNotesMenu = false },
                                                onComputeNotesClick = { viewModel.computeNotes() },
                                                onClearNotesClick = { viewModel.clearNotes() },
                                                renderNotes = renderNotes,
                                                onRenderNotesClick = { renderNotes = !renderNotes }
                                            )
                                            ToolbarItem(
                                                theme = theme,
                                                painter = painterResource(R.drawable.ic_round_edit_24),
                                                toggled = viewModel.notesToggled,
                                                onClick = { viewModel.toolbarClick(ToolBarItem.Note) },
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
                                            theme = theme,
                                            modifier = Modifier.weight(1f),
                                            painter = painterResource(R.drawable.ic_eraser_24),
                                            toggled = viewModel.eraseButtonToggled,
                                            onClick = { viewModel.toolbarClick(ToolBarItem.Remove) },
                                            onLongClick = {
                                                if (viewModel.gamePlaying) {
                                                    localView.performHapticFeedback(
                                                        HapticFeedbackConstants.VIRTUAL_KEY
                                                    )
                                                    viewModel.toggleEraseButton()
                                                }
                                            }
                                        )

                                        // Advanced hint
                                        if (advancedHintEnabled) {
                                            ToolbarItem(
                                                theme = theme,
                                                modifier = Modifier.weight(1f),
                                                painter = rememberVectorPainter(Icons.Rounded.AutoAwesome),
                                                onClick = {
                                                    val act = activity
                                                    if (viewModel.gamePlaying && act != null) {
                                                        viewModel.pauseTimer()
                                                        AdManager.showRewarded(act) { earned ->
                                                            if (earned) viewModel.getAdvancedHint()
                                                            // Always resume, even if the ad was skipped or unavailable.
                                                            viewModel.startTimer()
                                                        }
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
                BannerAd()
            }
        }

        // ───────────────────── dialogs ─────────────────────
        // Note: dialogs no longer call pauseTimer() during composition.
        // The buttons that open them pause the game instead.

        if (viewModel.restartDialog) {
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
                    }) { Text(stringResource(R.string.dialog_no)) }
                },
                confirmButton = {
                    TextButton(onClick = {
                        restartButtonAngleState -= 360
                        viewModel.resetGame(resetTimer)
                        viewModel.restartDialog = false
                        viewModel.startTimer()
                    }) { Text(stringResource(R.string.dialog_yes)) }
                }
            )
        }

        if (viewModel.giveUpDialog) {
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
                    }) { Text(stringResource(R.string.dialog_no)) }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.giveUp()
                        viewModel.giveUpDialog = false
                    }) { Text(stringResource(R.string.dialog_yes)) }
                }
            )
        }

        // Extra life dialog. Not dismissable by tapping outside or Back:
        // an accidental tap used to forfeit the puzzle.
        val extraLifeDialog by viewModel.extraLifeDialog.collectAsStateWithLifecycle()
        if (extraLifeDialog) {
            AlertDialog(
                onDismissRequest = { /* explicit choice required */ },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                ),
                title = { Text("Get extra life") },
                text = { Text("Watch an ad to continue with 1 extra life.") },
                confirmButton = {
                    TextButton(onClick = {
                        val act = activity ?: return@TextButton
                        AdManager.showRewarded(act) { earned ->
                            // If not earned, the dialog stays open so the player can retry or give up.
                            if (earned) viewModel.onExtraLifeAdWatched()
                        }
                    }) {
                        Text("Watch Ad")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onExtraLifeAdRejected() }) {
                        Text("Give up")
                    }
                }
            )
        }

        // Extra hint dialog
        val extraHintDialog by viewModel.extraHintDialog.collectAsStateWithLifecycle()

        if (extraHintDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onExtraHintAdRejected() },
                title = { Text("Get extra hint") },
                text = { Text("Watch an ad to get 1 extra hint.") },
                confirmButton = {
                    TextButton(onClick = {
                        val act = activity ?: return@TextButton
                        AdManager.showRewarded(act) { earned ->
                            if (earned) viewModel.onExtraHintAdWatched()   // applies the hint
                            else viewModel.onExtraHintAdRejected()         // closes and resumes
                        }
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
                    TextButton(onClick = { showKanjiInfo = false }) { Text("Close") }
                }
            )
        }

        // Show Solution dialog
        val solutionAdDialog by viewModel.solutionAdDialog.collectAsStateWithLifecycle()
        if (solutionAdDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onSolutionAdRejected() },
                title = { Text("Watch Solution") },
                text = { Text("Watch an ad to see the full solution grid.") },
                confirmButton = {
                    TextButton(onClick = {
                        val act = activity ?: return@TextButton
                        AdManager.showRewarded(act) { earned ->
                            if (earned) viewModel.onSolutionAdWatched()
                        }
                    }) {
                        Text("Watch Ad")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onSolutionAdRejected() }) { Text("Cancel") }
                }
            )
        }

        // Puzzle could not be loaded
        if (viewModel.loadFailed) {
            AlertDialog(
                onDismissRequest = { navigator.popBackStack() },
                title = { Text("Couldn't open this puzzle") },
                text = { Text("Something went wrong while loading the game. Please try another puzzle.") },
                confirmButton = {
                    TextButton(onClick = { navigator.popBackStack() }) { Text("Back") }
                }
            )
        }

        // Completion card. The interstitial (if any) is shown only when the player
        // taps Continue, never over the moment of solving.
        if (viewModel.gameCompleted) {
            var leaving by remember { mutableStateOf(false) }
            val scrimAlpha by animateFloatAsState(
                targetValue = if (resultVisible) 0.55f else 0f,
                animationSpec = tween(350),
                label = "Result scrim"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = resultVisible,
                    enter = fadeIn(tween(220)) + slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessLow
                        )
                    ) { it / 3 }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .shadow(16.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(theme.boardBackground)
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (viewModel.isDailyChallenge) "DAILY CHALLENGE"
                            else stringResource(viewModel.gameDifficulty.resName).uppercase(),
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            color = theme.secondaryText
                        )
                        Text(
                            text = "Puzzle solved",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.primaryText,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Row(
                            modifier = Modifier.padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            ResultStat(label = "Time", value = viewModel.timeText, theme = theme)
                            ResultStat(label = "Mistakes", value = "${viewModel.mistakesCount}", theme = theme)
                            ResultStat(label = "Hints", value = "${viewModel.hintsUsed}", theme = theme)
                        }
                        if (viewModel.isDailyChallenge) {
                            TextButton(
                                modifier = Modifier.padding(top = 16.dp),
                                onClick = { activity?.let { PlayGamesHelper.showDailyLeaderboard(it) } }
                            ) {
                                Text("View leaderboard", color = theme.accent)
                            }
                        }
                        FilledTonalButton(
                            modifier = Modifier.padding(top = if (viewModel.isDailyChallenge) 4.dp else 24.dp),
                            enabled = !leaving,
                            onClick = {
                                leaving = true
                                val act = activity
                                if (act != null) {
                                    AdManager.maybeShowInterstitial(act) { navigator.popBackStack() }
                                } else {
                                    navigator.popBackStack()
                                }
                            }
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }
        }

        // ───────────────────── side effects ─────────────────────

        LaunchedEffect(viewModel.mistakesMethod) {
            viewModel.checkMistakesAll()
        }

        // Preload ads (no-op until consent allows ads).
        LaunchedEffect(Unit) {
            AdManager.preload(context)
        }

        // Start the clock once the puzzle has actually loaded (the old LaunchedEffect(Unit)
        // could run before loading finished and silently do nothing).
        LaunchedEffect(viewModel.isLoaded, firstGame) {
            if (firstGame) {
                viewModel.pauseTimer()
            } else if (viewModel.isLoaded && !viewModel.endGame && !viewModel.gameCompleted) {
                viewModel.startTimer()
            }
        }

        OnLifecycleEvent { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.pauseTimer()   // also saves the game
                    viewModel.currCell = Cell(-1, -1, 0)
                }

                else -> {}
            }
        }
    }
}

/** Red hanko-style seal with 完 ("complete"), stamped on the solved board. */
@Composable
private fun SolvedSeal() {
    val context = LocalContext.current
    val sealFont = remember { FontFamily(KanjiFonts.bold(context)) }
    Box(
        modifier = Modifier
            .size(64.dp)
            .rotate(-10f)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(HankoRed)
            .border(2.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Puzzle complete" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "完",
            fontSize = 36.sp,
            lineHeight = 36.sp,
            color = Color.White,
            fontFamily = sealFont,
            style = TextStyle(localeList = LocaleList("ja-JP"))
        )
    }
}

@Composable
private fun ResultStat(label: String, value: String, theme: KanjiThemeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = theme.primaryText
        )
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            color = theme.secondaryText
        )
    }
}

@Composable
fun TopBoardSection(
    text: String,
    theme: KanjiThemeColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 4.dp),
            color = theme.primaryText
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
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
fun KeepScreenOn() = AndroidView({ View(it).apply { keepScreenOn = true } })
