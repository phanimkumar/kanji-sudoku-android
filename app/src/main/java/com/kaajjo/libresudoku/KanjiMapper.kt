package com.kaajjo.libresudoku


object KanjiMapper {
    // 1..9 → Kanji symbols
    private val KANJI = listOf("水","木","火","山","空","月","花","風","日")
    fun toSymbol(n: Int): String = if (n in 1..9) KANJI[n - 1] else ""
}
