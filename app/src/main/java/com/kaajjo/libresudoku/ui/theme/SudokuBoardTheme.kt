package com.kaajjo.libresudoku.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kaajjo.libresudoku.ui.theme.ColorUtils.blend
import com.kaajjo.libresudoku.ui.theme.ColorUtils.harmonizeWithPrimary

object BoardColors {

    // User-entered numbers (your moves) - dark maroon, ink-like
    inline val foregroundColor: Color
        @Composable
        get() = Color(0xFF00FF00)

    // Given / locked numbers (original puzzle) - deep navy, stable
    inline val altForegroundColor: Color
        @Composable
        get() = Color(0xFFFF00FF)

    // Notes / candidates - soft grey
    inline val notesColor: Color
        @Composable
        get() = Color(0xFF0000FF)

    // Error text / outlines - strong red, distinct from maroon
    inline val errorColor: Color
        @Composable
        get() = Color(0xFFFF0000)

    // Cell highlight (selected / same number) - light blue wash
    inline val highlightColor: Color
        @Composable
        get() = Color(0xFFFFFF00)

    // Thick 3×3 grid lines - rich navy
    inline val thickLineColor: Color
        @Composable
        get() = Color(0xFF00FFFF)

    // Thin cell lines - black
    inline val thinLineColor: Color
        @Composable
        get() = Color(0xFF000000)
}

interface SudokuBoardColors {
    val foregroundColor: Color
    val notesColor: Color
    val altForegroundColor: Color
    val errorColor: Color
    val highlightColor: Color
    val thickLineColor: Color
    val thinLineColor: Color
}

class SudokuBoardColorsImpl(
    override val foregroundColor: Color = Color.White,
    override val notesColor: Color = Color.White,
    override val altForegroundColor: Color = Color.White,
    override val errorColor: Color = Color.White,
    override val highlightColor: Color = Color.White,
    override val thickLineColor: Color = Color.White,
    override val thinLineColor: Color = Color.White,
) : SudokuBoardColors
