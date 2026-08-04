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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaajjo.libresudoku.R
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

    LaunchedEffect(Unit) {
        viewModel.isStartingGame = false
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
                    playedBefore = false
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