package com.kaajjo.libresudoku.ui.game

import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.ui.components.board.BoxRef

/**
 * One-shot feedback events from GameViewModel to GameScreen.
 * Exactly one event is sent per placed Kanji, so haptics and sounds never stack.
 */
sealed interface GameEvent {

    /** A Kanji was placed (correct, or mistakes are not being highlighted). */
    data object Placed : GameEvent

    /** A wrong Kanji was placed. */
    data object Mistake : GameEvent

    /**
     * The move completed one or more rows / columns / boxes and/or all nine of a Kanji.
     * @param units number of rows + columns + boxes completed by this move
     * @param symbol the completed Kanji value (1..9), or 0 if none
     */
    data class Completed(
        val cells: List<Cell>,
        val originRow: Int,
        val originCol: Int,
        val boxes: List<BoxRef>,
        val units: Int,
        val symbol: Int
    ) : GameEvent

    /** The puzzle is solved. */
    data object Solved : GameEvent
}
