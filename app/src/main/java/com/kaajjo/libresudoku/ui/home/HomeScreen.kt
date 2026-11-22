package com.kaajjo.libresudoku.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaajjo.libresudoku.R
import com.kaajjo.libresudoku.daily.PlayGamesHelper
import com.kaajjo.libresudoku.ui.util.findActivity
import com.kaajjo.libresudoku.destinations.GameScreenDestination
import com.kaajjo.libresudoku.ui.components.AnimatedNavigation
import com.kaajjo.libresudoku.ui.theme.KanjiThemeColors
import com.kaajjo.libresudoku.ui.theme.KanjiThemeType
import com.kaajjo.libresudoku.ui.theme.KanjiThemes
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Destination(style = AnimatedNavigation::class)
@RootNavGraph(start = true)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navigator: DestinationsNavigator
) {
    var continueGameDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val lastGame by viewModel.lastSavedGame.collectAsStateWithLifecycle()
    val canContinue = lastGame != null && !lastGame!!.completed

    val selectedKanjiTheme by viewModel.kanjiTheme
        .collectAsStateWithLifecycle(
            initialValue = KanjiThemeType.JapanesePaper
        )

    val theme = KanjiThemes.get(selectedKanjiTheme)

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(Unit) {
        viewModel.isStartingGame = false
        viewModel.refreshDaily()

        // A daily time solved offline (or before sign-in) is sent now.
        val pending = viewModel.dailyStore.pendingSubmission()
        if (pending != null && activity != null) {
            PlayGamesHelper.submitDailyTime(activity, pending.timeMs) { ok ->
                if (ok) viewModel.onDailySubmitted()
            }
        }
    }

    LaunchedEffect(
        viewModel.readyToPlay,
        viewModel.insertedBoardUid
    ) {
        val uid = viewModel.insertedBoardUid

        if (viewModel.readyToPlay && uid > 0L) {
            viewModel.readyToPlay = false

            navigator.navigate(
                GameScreenDestination(
                    gameUid = uid,
                    playedBefore = viewModel.navigatePlayedBefore
                )
            )

            viewModel.insertedBoardUid = 0L
            viewModel.isStartingGame = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    space = 18.dp,
                    alignment = Alignment.CenterVertically
                )
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 32.sp,
                    color = theme.primaryText,
                    fontWeight = FontWeight.SemiBold
                )

                DailyChallengeCard(
                    theme = theme,
                    ui = viewModel.dailyUi,
                    onPlay = { viewModel.startDailyGame() },
                    onLeaderboard = {
                        activity?.let { PlayGamesHelper.showDailyLeaderboard(it) }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalPicker(
                        theme = theme,
                        text = stringResource(
                            viewModel.selectedDifficulty.resName
                        ),
                        onLeftClick = {
                            viewModel.changeDifficulty(-1)
                        },
                        onRightClick = {
                            viewModel.changeDifficulty(1)
                        }
                    )

                    HorizontalPicker(
                        theme = theme,
                        text = stringResource(
                            viewModel.selectedType.resName
                        ),
                        onLeftClick = {
                            viewModel.changeType(-1)
                        },
                        onRightClick = {
                            viewModel.changeType(1)
                        }
                    )
                }

                if (canContinue) {
                    GlassButton(
                        theme = theme,
                        text = stringResource(R.string.action_continue),
                        filled = false
                    ) {
                        navigator.navigate(
                            GameScreenDestination(
                                gameUid = lastGame!!.uid,
                                playedBefore = true
                            )
                        )
                    }

                    GlassButton(
                        theme = theme,
                        text = stringResource(R.string.action_play),
                        filled = true
                    ) {
                        continueGameDialog = true
                    }
                } else {
                    GlassButton(
                        theme = theme,
                        text = stringResource(R.string.action_play),
                        filled = true
                    ) {
                        viewModel.giveUpLastGame()
                        viewModel.startGame()
                    }
                }
            }
        }
    }

    if (continueGameDialog) {
        AlertDialog(
            title = {
                Text(
                    text = "New Game",
                    color = theme.primaryText
                )
            },
            text = {
                Text(
                    text = "Start a new game? Current progress will be lost.",
                    color = theme.secondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        continueGameDialog = false
                        viewModel.giveUpLastGame()
                        viewModel.startGame()
                    }
                ) {
                    Text(
                        text = stringResource(
                            R.string.dialog_new_game_positive
                        ),
                        color = theme.accent
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        continueGameDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = theme.secondaryText
                    )
                }
            },
            onDismissRequest = {
                continueGameDialog = false
            }
        )
    }
}

@Composable
private fun DailyChallengeCard(
    theme: KanjiThemeColors,
    ui: HomeViewModel.DailyUi,
    onPlay: () -> Unit,
    onLeaderboard: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val done = ui.status == HomeViewModel.DailyStatus.Solved ||
            ui.status == HomeViewModel.DailyStatus.GaveUp

    Column(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .clip(shape)
            .background(brush = theme.secondaryButton, shape = shape)
            .border(1.dp, theme.accent.copy(alpha = 0.35f), shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daily Challenge",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.primaryText
            )
            Text(
                text = ui.dateLabel,
                fontSize = 13.sp,
                color = theme.secondaryText
            )
        }

        Text(
            text = when (ui.status) {
                HomeViewModel.DailyStatus.NotStarted -> "One puzzle. Everyone. Fastest time wins."
                HomeViewModel.DailyStatus.InProgress -> "Your daily puzzle is waiting."
                HomeViewModel.DailyStatus.Solved -> "完  Solved in ${formatDailyTime(ui.timeMs)}"
                HomeViewModel.DailyStatus.GaveUp -> "Better luck tomorrow."
            },
            fontSize = 14.sp,
            color = if (ui.status == HomeViewModel.DailyStatus.Solved) theme.accent
            else theme.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (done) {
            Text(
                text = "Next puzzle in ${formatCountdown(ui.nextInMinutes)}",
                fontSize = 12.sp,
                color = theme.secondaryText.copy(alpha = 0.8f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            if (!done) {
                SmallPill(
                    theme = theme,
                    text = if (ui.status == HomeViewModel.DailyStatus.InProgress) "Continue" else "Play",
                    filled = true,
                    onClick = onPlay
                )
            }
            SmallPill(
                theme = theme,
                text = "Leaderboard",
                filled = done,
                onClick = onLeaderboard
            )
        }
    }
}

@Composable
private fun SmallPill(
    theme: KanjiThemeColors,
    text: String,
    filled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(
                brush = if (filled) theme.primaryButton else theme.secondaryButton,
                shape = shape
            )
            .border(
                1.dp,
                if (filled) theme.accent.copy(alpha = 0.45f) else theme.boardBorder.copy(alpha = 0.4f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = if (filled) Color.White else theme.primaryText
        )
    }
}

private fun formatDailyTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatCountdown(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun GlassButton(
    theme: KanjiThemeColors,
    text: String,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)

    val backgroundBrush: Brush = if (filled) {
        theme.primaryButton
    } else {
        theme.secondaryButton
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(52.dp)
            .then(
                if (filled) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(
                brush = backgroundBrush,
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (filled) {
                    theme.accent.copy(alpha = 0.45f)
                } else {
                    theme.boardBorder.copy(alpha = 0.4f)
                },
                shape = shape
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            color = if (filled) {
                Color.White
            } else {
                theme.primaryText
            }
        )
    }
}

@Composable
fun HorizontalPicker(
    theme: KanjiThemeColors,
    modifier: Modifier = Modifier,
    text: String,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onLeftClick
        ) {
            Icon(
                painter = painterResource(
                    R.drawable.ic_round_keyboard_arrow_left_24
                ),
                contentDescription = null,
                tint = theme.primaryText
            )
        }

        AnimatedContent(
            targetState = text,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "picker"
        ) { value ->
            Text(
                text = value,
                color = theme.primaryText,
                fontSize = 18.sp
            )
        }

        IconButton(
            onClick = onRightClick
        ) {
            Icon(
                painter = painterResource(
                    R.drawable.ic_round_keyboard_arrow_right_24
                ),
                contentDescription = null,
                tint = theme.primaryText
            )
        }
    }
}