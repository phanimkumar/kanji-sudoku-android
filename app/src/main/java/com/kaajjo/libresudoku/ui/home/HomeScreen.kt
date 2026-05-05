package com.kaajjo.libresudoku.ui.home

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaajjo.libresudoku.R
import com.kaajjo.libresudoku.destinations.GameScreenDestination
import com.kaajjo.libresudoku.ui.components.AnimatedNavigation
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

/* -------------------- GLASS COLORS -------------------- */
private val GlassMint = Color(0xFF2EC4B6)
private val GlassSky = Color(0xFFBDF7F1)
private val GlassDark = Color(0xFF062524)

// Better contrast on mint/glass
private val InkDark = Color(0xFF063B39)

@OptIn(ExperimentalMaterial3Api::class)
@Destination(style = AnimatedNavigation::class)
@RootNavGraph(start = true)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navigator: DestinationsNavigator
) {
    var continueGameDialog by rememberSaveable { mutableStateOf(false) }

    val lastGame by viewModel.lastSavedGame.collectAsStateWithLifecycle()
    val canContinue = lastGame != null && !lastGame!!.completed

    // ensure no stuck "starting" state blocks clicks
    LaunchedEffect(Unit) {
        viewModel.isStartingGame = false
    }

    // Navigate when new game is inserted
    LaunchedEffect(viewModel.readyToPlay, viewModel.insertedBoardUid) {
        val uid = viewModel.insertedBoardUid
        if (viewModel.readyToPlay && uid > 0L) {
            viewModel.readyToPlay = false

            navigator.navigate(
                GameScreenDestination(
                    gameUid = uid,
                    playedBefore = false
                )
            )

            // ✅ CRITICAL: clear stale navigation state
            // Prevents reopening the same (possibly completed) board on future recompositions
            viewModel.insertedBoardUid = 0L
            viewModel.isStartingGame = false
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF0F9FF),   // very light
                            Color(0xFFBAE6FD)    // soft teal
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
            ) {

                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 32.sp,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.SemiBold,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    HorizontalPicker(
                        text = stringResource(viewModel.selectedDifficulty.resName),
                        onLeftClick = { viewModel.changeDifficulty(-1) },
                        onRightClick = { viewModel.changeDifficulty(1) }
                    )

                    HorizontalPicker(
                        text = stringResource(viewModel.selectedType.resName),
                        onLeftClick = { viewModel.changeType(-1) },
                        onRightClick = { viewModel.changeType(1) }
                    )
                }

                if (canContinue) {
                    // ✅ Continue (clean outline glass, NO patch)
                    GlassButton(
                        text = stringResource(R.string.action_continue),
                        filled = false,
                        enabled = true
                    ) {
                        navigator.navigate(
                            GameScreenDestination(
                                gameUid = lastGame!!.uid, // ✅ keep your working uid
                                playedBefore = true
                            )
                        )
                    }

                    // ✅ Play New (primary filled)
                    GlassButton(
                        text = stringResource(R.string.action_play),
                        filled = true,
                        enabled = true
                    ) {
                        continueGameDialog = true
                    }
                } else {
                    // ✅ Play new directly
                    GlassButton(
                        text = stringResource(R.string.action_play),
                        filled = true,
                        enabled = true
                    ) {
                        viewModel.giveUpLastGame()
                        viewModel.startGame()
                    }
                }
            }
        }
    }

    // ✅ Keep your dialog as-is (no functionality change)
    if (continueGameDialog) {
        AlertDialog(
            title = { Text("New Game") },
            text = { Text("Start a new game? Current progress will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    continueGameDialog = false
                    viewModel.giveUpLastGame()
                    viewModel.startGame()
                }) {
                    Text(stringResource(R.string.dialog_new_game_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { continueGameDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            onDismissRequest = { continueGameDialog = false }
        )
    }
}

/* -------------------- GLASS COMPONENTS -------------------- */

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        GlassSky.copy(alpha = 0.35f),
                        GlassMint.copy(alpha = 0.25f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.5f), GlassMint.copy(alpha = 0.3f))
                ),
                RoundedCornerShape(26.dp)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * ✅ FIXED:
 * - filled=true  -> shadow + gradient (primary)
 * - filled=false -> NO shadow + FLAT glass (secondary) => no dirty patch
 */
@Composable
private fun GlassButton(
    text: String,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)

    val bgBrush: Brush = if (filled) {
        Brush.linearGradient(
            listOf(
                Color(0xFF0284C7),
                Color(0xFF0369A1)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.32f),
                Color.White.copy(alpha = 0.22f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(52.dp)
            // ✅ shadow ONLY for filled
            .then(
                if (filled) Modifier.shadow(10.dp, shape)
                else Modifier
            )
            .clip(shape)
            // ✅ background call is now EXPLICIT and valid
            .background(
                brush = bgBrush,
                shape = shape
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.35f),
                shape = shape
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            color = if (filled) Color.White else InkDark
        )
    }
}

/* -------------------- PICKER -------------------- */

@Composable
fun HorizontalPicker(
    modifier: Modifier = Modifier,
    text: String,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit
) {
    // ✅ On mint glass, dark text/icons read better than white
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onLeftClick) {
            Icon(
                painter = painterResource(R.drawable.ic_round_keyboard_arrow_left_24),
                contentDescription = null,
                tint = InkDark
            )
        }

        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "picker"
        ) { value ->
            Text(value, color = InkDark, fontSize = 18.sp)
        }

        IconButton(onClick = onRightClick) {
            Icon(
                painter = painterResource(R.drawable.ic_round_keyboard_arrow_right_24),
                contentDescription = null,
                tint = InkDark
            )
        }
    }
}
