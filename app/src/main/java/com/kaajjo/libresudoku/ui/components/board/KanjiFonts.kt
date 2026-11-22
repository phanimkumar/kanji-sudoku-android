package com.kaajjo.libresudoku.ui.components.board

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

/**
 * Typefaces used to draw the nine Kanji on the board and keypad.
 *
 * Optional bundled fonts are looked up by name, so the app compiles and runs
 * whether or not they are present:
 *   res/font/kanji9_regular.otf  (player entries, notes, keypad)
 *   res/font/kanji9_bold.otf     (given cells)
 *
 * When they are missing, the system font is used (same as before this change).
 */
object KanjiFonts {

    private const val REGULAR_NAME = "kanji9_regular"
    private const val BOLD_NAME = "kanji9_bold"

    private var loaded = false
    private var regularFace: Typeface? = null
    private var boldFace: Typeface? = null

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        regularFace = find(context, REGULAR_NAME)
        boldFace = find(context, BOLD_NAME)
        loaded = true
    }

    @SuppressLint("DiscouragedApi")
    private fun find(context: Context, name: String): Typeface? {
        val id = context.resources.getIdentifier(name, "font", context.packageName)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
    }

    fun regular(context: Context): Typeface {
        load(context)
        return regularFace ?: Typeface.DEFAULT
    }

    fun bold(context: Context): Typeface {
        load(context)
        return boldFace
            ?: regularFace?.let { Typeface.create(it, Typeface.BOLD) }
            ?: Typeface.DEFAULT_BOLD
    }
}
