package com.kaajjo.libresudoku


object KanjiMapper {
    // 1..9 → Kanji symbols
    private val KANJI = listOf("水","木","火","山","空","月","花","風","日")

    // 1..9 → English meaning (same order as KANJI). Used for accessibility labels.
    private val MEANINGS = listOf(
        "Water", "Tree", "Fire", "Mountain", "Sky", "Moon", "Flower", "Wind", "Sun"
    )

    fun toSymbol(n: Int): String = if (n in 1..9) KANJI[n - 1] else ""

    fun meaning(n: Int): String = if (n in 1..9) MEANINGS[n - 1] else ""
}
