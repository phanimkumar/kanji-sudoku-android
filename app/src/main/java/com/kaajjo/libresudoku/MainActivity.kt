package com.kaajjo.libresudoku

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.kaajjo.libresudoku.core.PreferencesConstants
import com.kaajjo.libresudoku.core.utils.GlobalExceptionHandler
import com.kaajjo.libresudoku.data.datastore.AppSettingsManager
import com.kaajjo.libresudoku.data.datastore.ThemeSettingsManager
import com.kaajjo.libresudoku.destinations.HomeScreenDestination
import com.kaajjo.libresudoku.destinations.ImportFromFileScreenDestination
import com.kaajjo.libresudoku.destinations.MoreScreenDestination
import com.kaajjo.libresudoku.destinations.StatisticsScreenDestination
import com.kaajjo.libresudoku.destinations.WelcomeScreenDestination
import com.kaajjo.libresudoku.ui.AdManager
import com.kaajjo.libresudoku.ui.app_crash.CrashActivity
import com.kaajjo.libresudoku.ui.components.navigation_bar.NavigationBarComponent
import com.kaajjo.libresudoku.ui.theme.BoardColors
import com.kaajjo.libresudoku.ui.theme.LibreSudokuTheme
import com.kaajjo.libresudoku.ui.theme.SudokuBoardColorsImpl
import com.kaajjo.libresudoku.ui.util.findActivity
import com.materialkolor.PaletteStyle
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

val LocalBoardColors = staticCompositionLocalOf { SudokuBoardColorsImpl() }

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var settings: AppSettingsManager

    private lateinit var consentInformation: ConsentInformation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NOTE: the KDP book export that used to run here on every launch has been
        // removed. Run KdpSudokuExporter from a debug-only entry point when needed.

        requestAdConsent()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (!BuildConfig.DEBUG) {
            GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)
        }

        setContent {
            val mainViewModel: MainActivityViewModel = hiltViewModel()

            val dynamicColors by mainViewModel.dc.collectAsStateWithLifecycle(isSystemInDarkTheme())
            val darkTheme by mainViewModel.darkTheme.collectAsStateWithLifecycle(PreferencesConstants.DEFAULT_DARK_THEME)
            val amoledBlack by mainViewModel.amoledBlack.collectAsStateWithLifecycle(PreferencesConstants.DEFAULT_AMOLED_BLACK)
            val firstLaunch by mainViewModel.firstLaunch.collectAsStateWithLifecycle(false)
            val colorSeed by mainViewModel.colorSeed.collectAsStateWithLifecycle(initialValue = Color.Red)
            val paletteStyle by mainViewModel.paletteStyle.collectAsStateWithLifecycle(initialValue = PaletteStyle.TonalSpot)

            LibreSudokuTheme(
                darkTheme = when (darkTheme) {
                    1 -> false
                    2 -> true
                    else -> isSystemInDarkTheme()
                },
                dynamicColor = dynamicColors,
                amoled = amoledBlack,
                colorSeed = colorSeed,
                paletteStyle = paletteStyle
            ) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()

                var bottomBarState by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(navBackStackEntry) {
                    bottomBarState = when (navBackStackEntry?.destination?.route) {
                        StatisticsScreenDestination.route, HomeScreenDestination.route, MoreScreenDestination.route -> true
                        else -> false
                    }
                }
                LaunchedEffect(firstLaunch) {
                    if (firstLaunch) {
                        navController.navigate(
                            route = WelcomeScreenDestination.route,
                            navOptions = navOptions {
                                popUpTo(HomeScreenDestination.route) {
                                    inclusive = true
                                }
                            }
                        )
                    }
                }

                val boardColors = SudokuBoardColorsImpl(
                    foregroundColor = BoardColors.foregroundColor,
                    notesColor = BoardColors.notesColor,
                    altForegroundColor = BoardColors.altForegroundColor,
                    errorColor = BoardColors.errorColor,
                    highlightColor = BoardColors.highlightColor,
                    thickLineColor = BoardColors.thickLineColor,
                    thinLineColor = BoardColors.thinLineColor
                )

                // The upstream self-update check (GitHub releases) has been removed:
                // a Play Store build must update only through Google Play.

                CompositionLocalProvider(LocalBoardColors provides boardColors) {
                    Scaffold(
                        bottomBar = {
                            NavigationBarComponent(
                                navController = navController,
                                isVisible = bottomBarState,
                                updateAvailable = false
                            )
                        },
                        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                    ) { paddingValues ->
                        DestinationsNavHost(
                            navGraph = NavGraphs.root,
                            navController = navController,
                            startRoute = NavGraphs.root.startRoute,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }
            }
        }
    }

    /**
     * Google UMP consent flow.
     * - EEA/UK users see the consent form when Google requires it.
     * - Everyone else gets ads straight away.
     * - If the consent check itself fails (no consent message set up in AdMob,
     *   no network, etc.), ads still start instead of being blocked for good.
     */
    private fun requestAdConsent() {
        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        val params = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "Consent",
                        "Update OK, status=${consentInformation.consentStatus}, " +
                                "canRequestAds=${consentInformation.canRequestAds()}"
                    )
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    if (formError != null && BuildConfig.DEBUG) {
                        Log.w("Consent", "Form error: ${formError.message}")
                    }
                    startAdsIfAllowed()
                }
            },
            { requestError ->
                if (BuildConfig.DEBUG) Log.w("Consent", "Update error: ${requestError.message}")
                // The consent check itself failed. Don't block ads completely.
                AdManager.initOnce(this)
            }
        )

        // Consent already known from an earlier launch: start immediately.
        startAdsIfAllowed()

        // Safety net: if neither callback has started ads within 5 seconds,
        // start them unless this user still has to give consent.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!AdManager.adsAllowed &&
                consentInformation.consentStatus != ConsentInformation.ConsentStatus.REQUIRED
            ) {
                if (BuildConfig.DEBUG) Log.w("Consent", "Fallback: starting ads after timeout")
                AdManager.initOnce(this)
            }
        }, 5_000L)
    }

    private fun startAdsIfAllowed() {
        val status = consentInformation.consentStatus
        if (consentInformation.canRequestAds() ||
            status == ConsentInformation.ConsentStatus.NOT_REQUIRED ||
            status == ConsentInformation.ConsentStatus.OBTAINED
        ) {
            AdManager.initOnce(this)
        }
    }
}

@HiltViewModel
class MainActivityViewModel
@Inject constructor(
    themeSettingsManager: ThemeSettingsManager,
    appSettingsManager: AppSettingsManager
) : ViewModel() {
    val dc = themeSettingsManager.dynamicColors
    val darkTheme = themeSettingsManager.darkTheme
    val amoledBlack = themeSettingsManager.amoledBlack
    val firstLaunch = appSettingsManager.firstLaunch
    val monetSudokuBoard = themeSettingsManager.monetSudokuBoard
    val colorSeed = themeSettingsManager.themeColorSeed
    val paletteStyle = themeSettingsManager.themePaletteStyle
    val autoUpdateChannel = appSettingsManager.autoUpdateChannel
    val updateDismissedName = appSettingsManager.updateDismissedName
}

@Destination(
    deepLinks = [
        DeepLink(
            uriPattern = "content://",
            mimeType = "*/*",
            action = Intent.ACTION_VIEW
        )
    ]
)
@Composable
fun HandleImportFromFileDeepLink(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val activity = context.findActivity()
        if (activity != null) {
            val intentData = activity.intent.data
            if (intentData != null) {
                navigator.navigate(
                    ImportFromFileScreenDestination(
                        fileUri = intentData.toString(),
                        fromDeepLink = true
                    )
                )
            }
        }
    }
}
