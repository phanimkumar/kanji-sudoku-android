package com.kaajjo.libresudoku.ui.game

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.pow

/**
 * Haptics and sound for the game screen.
 *
 * Haptics:
 *  - Android 12+: rich composed effects (tick / click / thud / rise) where the device supports them.
 *  - Android 10–11: system predefined effects.
 *  - Older, or if vibration is unavailable: the View's standard haptic feedback.
 *  Always respects the system "Touch feedback" setting and the in-game Vibration toggle.
 *
 * Sound:
 *  - Short WAVs from res/raw (kanji_tap, kanji_error, kanji_unit, kanji_symbol, kanji_solved).
 *    Each is optional; a missing file is simply silent.
 *  - Silent when the phone is on silent/vibrate, or the in-game Sound toggle is off.
 */
class GameFeedback(context: Context, private val view: View) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    var soundEnabled by mutableStateOf(prefs.getBoolean(KEY_SOUND, true))
        private set
    var hapticsEnabled by mutableStateOf(prefs.getBoolean(KEY_HAPTICS, true))
        private set

    fun toggleSound() {
        soundEnabled = !soundEnabled
        prefs.edit().putBoolean(KEY_SOUND, soundEnabled).apply()
    }

    fun toggleHaptics() {
        hapticsEnabled = !hapticsEnabled
        prefs.edit().putBoolean(KEY_HAPTICS, hapticsEnabled).apply()
    }

    // ───────────────────── public events ─────────────────────

    /** Keypad key pressed. */
    fun keyTap() = haptic(
        primary = listOf(P(PrimTick, 0.45f)),
        predefined = PRE_TICK,
        legacyMs = 8, legacyAmp = 50,
        viewFallback = HapticFeedbackConstants.CLOCK_TICK
    )

    /** A Kanji was placed (correct, or correctness not shown). */
    fun placed() {
        haptic(
            primary = listOf(P(PrimTick, 0.75f)),
            predefined = PRE_TICK,
            legacyMs = 10, legacyAmp = 80,
            viewFallback = HapticFeedbackConstants.KEYBOARD_TAP
        )
        play(SOUND_TAP, volume = 0.55f)
    }

    /** A wrong Kanji was placed. */
    fun mistake() {
        haptic(
            primary = listOf(P(PrimClick, 0.55f), P(PrimClick, 0.55f, 70)),
            predefined = PRE_DOUBLE_CLICK,
            legacyMs = 30, legacyAmp = 120,
            viewFallback = HapticFeedbackConstants.LONG_PRESS
        )
        play(SOUND_ERROR, volume = 0.7f)
    }

    /** One or more rows / columns / boxes completed by a single move. */
    fun unitComplete(units: Int) {
        val extra = (units - 1).coerceIn(0, 2)
        val prims = buildList {
            add(P(PrimTick, 0.5f))
            add(P(PrimClick, 1.0f, 45))
            repeat(extra) { add(P(PrimTick, 0.8f, 70)) }
        }
        haptic(
            primary = prims,
            predefined = PRE_HEAVY_CLICK,
            legacyMs = 25, legacyAmp = 180,
            viewFallback = HapticFeedbackConstants.CONTEXT_CLICK
        )
        // Each extra unit raises the chime one step on a pentatonic scale.
        play(SOUND_UNIT, volume = 0.75f, semitones = PENTATONIC[extra])
    }

    /** All nine of one Kanji placed correctly. */
    fun symbolComplete() {
        haptic(
            primary = listOf(P(PrimThud, 1.0f), P(PrimTick, 0.5f, 90)),
            alternative = listOf(P(PrimClick, 1.0f), P(PrimTick, 0.6f, 90)),
            predefined = PRE_HEAVY_CLICK,
            legacyMs = 35, legacyAmp = 220,
            viewFallback = HapticFeedbackConstants.LONG_PRESS
        )
        play(SOUND_SYMBOL, volume = 0.85f)
    }

    /** Puzzle solved: start of the board wave. */
    fun solvedStart() {
        haptic(
            primary = listOf(P(PrimSlowRise, 0.55f)),
            alternative = listOf(P(PrimTick, 0.4f), P(PrimTick, 0.6f, 80), P(PrimTick, 0.8f, 80)),
            predefined = PRE_TICK,
            legacyMs = 15, legacyAmp = 70,
            viewFallback = HapticFeedbackConstants.CLOCK_TICK
        )
        play(SOUND_UNIT, volume = 0.6f, semitones = 0)
    }

    /** The 完 seal lands on the board. */
    fun seal() {
        haptic(
            primary = listOf(P(PrimThud, 1.0f)),
            alternative = listOf(P(PrimClick, 1.0f)),
            predefined = PRE_HEAVY_CLICK,
            legacyMs = 40, legacyAmp = 255,
            viewFallback = HapticFeedbackConstants.LONG_PRESS
        )
        play(SOUND_SOLVED, volume = 0.9f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }

    // ───────────────────── haptics ─────────────────────

    private data class P(val prim: Int, val scale: Float, val delayMs: Int = 0)

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }

    // Set to false if the VIBRATE permission is missing; we then use View haptics.
    private var vibratorUsable = vibrator != null

    private fun systemTouchFeedbackOn(): Boolean = runCatching {
        Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    }.getOrDefault(true)

    private fun haptic(
        primary: List<P>,
        alternative: List<P>? = null,
        predefined: Int,
        legacyMs: Long,
        legacyAmp: Int,
        viewFallback: Int
    ) {
        if (!hapticsEnabled || !systemTouchFeedbackOn()) return

        val v = vibrator
        if (v == null || !vibratorUsable) {
            view.performHapticFeedback(viewFallback)
            return
        }

        val effect: VibrationEffect = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                composed(v, primary)
                    ?: alternative?.let { composed(v, it) }
                    ?: VibrationEffect.createPredefined(predefined)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                VibrationEffect.createPredefined(predefined)

            else -> VibrationEffect.createOneShot(
                legacyMs,
                if (v.hasAmplitudeControl()) legacyAmp else VibrationEffect.DEFAULT_AMPLITUDE
            )
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        } catch (_: SecurityException) {
            // VIBRATE permission missing from the manifest: fall back for the rest of the session.
            vibratorUsable = false
            view.performHapticFeedback(viewFallback)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun composed(v: Vibrator, prims: List<P>): VibrationEffect? {
        val ids = prims.map { primitiveId(it.prim) }.distinct().toIntArray()
        if (!v.areAllPrimitivesSupported(*ids)) return null
        val c = VibrationEffect.startComposition()
        prims.forEach { c.addPrimitive(primitiveId(it.prim), it.scale, it.delayMs) }
        return c.compose()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun primitiveId(p: Int): Int = when (p) {
        PrimTick -> VibrationEffect.Composition.PRIMITIVE_TICK
        PrimClick -> VibrationEffect.Composition.PRIMITIVE_CLICK
        PrimThud -> VibrationEffect.Composition.PRIMITIVE_THUD
        PrimSlowRise -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
        else -> VibrationEffect.Composition.PRIMITIVE_CLICK
    }

    // ───────────────────── sound ─────────────────────

    private var soundPool: SoundPool? = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = IntArray(SOUND_NAMES.size)

    init {
        SOUND_NAMES.forEachIndexed { i, name -> soundIds[i] = loadRaw(name) }
    }

    @SuppressLint("DiscouragedApi")
    private fun loadRaw(name: String): Int {
        val res = app.resources.getIdentifier(name, "raw", app.packageName)
        if (res == 0) return 0
        return runCatching { soundPool?.load(app, res, 1) ?: 0 }.getOrDefault(0)
    }

    private fun play(sound: Int, volume: Float, semitones: Int = 0) {
        if (!soundEnabled) return
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = soundIds.getOrNull(sound) ?: 0
        if (id == 0) return
        val rate = 2f.pow(semitones / 12f).coerceIn(0.5f, 2f)
        soundPool?.play(id, volume, volume, 1, 0, rate)
    }

    private companion object {
        const val PREFS = "game_feedback"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_HAPTICS = "haptics_enabled"

        const val PrimTick = 1
        const val PrimClick = 2
        const val PrimThud = 3
        const val PrimSlowRise = 4

        // VibrationEffect.EFFECT_* values (API 29+), as literals so older APIs compile cleanly.
        const val PRE_CLICK = 0
        const val PRE_DOUBLE_CLICK = 1
        const val PRE_TICK = 2
        const val PRE_HEAVY_CLICK = 5

        const val SOUND_TAP = 0
        const val SOUND_ERROR = 1
        const val SOUND_UNIT = 2
        const val SOUND_SYMBOL = 3
        const val SOUND_SOLVED = 4
        val SOUND_NAMES = listOf("kanji_tap", "kanji_error", "kanji_unit", "kanji_symbol", "kanji_solved")

        // Pentatonic steps in semitones (root, 2nd, 3rd).
        val PENTATONIC = intArrayOf(0, 2, 4)
    }
}
