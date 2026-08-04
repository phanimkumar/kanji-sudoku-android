package com.kaajjo.libresudoku.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class KanjiThemeType {
    JapanesePaper,
    Sakura,
    Bamboo,
    DarkSamurai
}

data class KanjiThemeColors(
    val background: Brush,
    val panel: Brush,
    val primaryButton: Brush,
    val secondaryButton: Brush,

    val primaryText: Color,
    val secondaryText: Color,

    val boardBackground: Color,
    val boardBorder: Color,

    val accent: Color,
    val completionOverlay: Color,
    val completionCard: Brush
)

object KanjiThemes {

    fun get(type: KanjiThemeType): KanjiThemeColors {
        return when (type) {

            KanjiThemeType.JapanesePaper -> KanjiThemeColors(
                background = Brush.verticalGradient(
                    listOf(
                        Color(0xFFF7F0DD),
                        Color(0xFFE8DCC2)
                    )
                ),
                panel = Brush.linearGradient(
                    listOf(
                        Color(0xFFFFFBF1),
                        Color(0xFFEDE1C7)
                    )
                ),
                primaryButton = Brush.linearGradient(
                    listOf(
                        Color(0xFF8B5E3C),
                        Color(0xFF654321)
                    )
                ),
                secondaryButton = Brush.linearGradient(
                    listOf(
                        Color(0xFFF8EFD8),
                        Color(0xFFE7D7B8)
                    )
                ),
                primaryText = Color(0xFF2B2118),
                secondaryText = Color(0xFF5C4A3A),
                boardBackground = Color(0xFFFFFCF4),
                boardBorder = Color(0xFF6B4D33),
                accent = Color(0xFFA66A3F),
                completionOverlay = Color(0xAA2B2118),
                completionCard = Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF8E8),
                        Color(0xFFEAD9B7)
                    )
                )
            )

            KanjiThemeType.Sakura -> KanjiThemeColors(
                background = Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF1F5),
                        Color(0xFFF8CAD8)
                    )
                ),
                panel = Brush.linearGradient(
                    listOf(
                        Color(0xFFFFF8FA),
                        Color(0xFFF6D7E1)
                    )
                ),
                primaryButton = Brush.linearGradient(
                    listOf(
                        Color(0xFFD96C8A),
                        Color(0xFFB94768)
                    )
                ),
                secondaryButton = Brush.linearGradient(
                    listOf(
                        Color(0xFFFFEEF3),
                        Color(0xFFF5CDD9)
                    )
                ),
                primaryText = Color(0xFF4A2430),
                secondaryText = Color(0xFF7A4A58),
                boardBackground = Color(0xFFFFFAFC),
                boardBorder = Color(0xFFB95776),
                accent = Color(0xFFE58CA5),
                completionOverlay = Color(0xAA4A2430),
                completionCard = Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF9FB),
                        Color(0xFFF6D2DE)
                    )
                )
            )

            KanjiThemeType.Bamboo -> KanjiThemeColors(
                background = Brush.verticalGradient(
                    listOf(
                        Color(0xFFF1F7E8),
                        Color(0xFFD6E6BF)
                    )
                ),
                panel = Brush.linearGradient(
                    listOf(
                        Color(0xFFF8FBEF),
                        Color(0xFFDDE9C7)
                    )
                ),
                primaryButton = Brush.linearGradient(
                    listOf(
                        Color(0xFF5F7F47),
                        Color(0xFF3F5F31)
                    )
                ),
                secondaryButton = Brush.linearGradient(
                    listOf(
                        Color(0xFFF0F6E5),
                        Color(0xFFD5E3BF)
                    )
                ),
                primaryText = Color(0xFF25351D),
                secondaryText = Color(0xFF526344),
                boardBackground = Color(0xFFFAFDF5),
                boardBorder = Color(0xFF607A4A),
                accent = Color(0xFF86A766),
                completionOverlay = Color(0xAA25351D),
                completionCard = Brush.verticalGradient(
                    listOf(
                        Color(0xFFFBFDF4),
                        Color(0xFFDCE9C5)
                    )
                )
            )

            KanjiThemeType.DarkSamurai -> KanjiThemeColors(
                background = Brush.verticalGradient(
                    listOf(
                        Color(0xFF121212),
                        Color(0xFF1F252A)
                    )
                ),
                panel = Brush.linearGradient(
                    listOf(
                        Color(0xFF252A2E),
                        Color(0xFF171A1D)
                    )
                ),
                primaryButton = Brush.linearGradient(
                    listOf(
                        Color(0xFFC59B4B),
                        Color(0xFF8A6325)
                    )
                ),
                secondaryButton = Brush.linearGradient(
                    listOf(
                        Color(0xFF303438),
                        Color(0xFF222629)
                    )
                ),
                primaryText = Color(0xFFF4E7C2),
                secondaryText = Color(0xFFC9B98A),
                boardBackground = Color(0xFF202326),
                boardBorder = Color(0xFFC59B4B),
                accent = Color(0xFFD4AF61),
                completionOverlay = Color(0xDD000000),
                completionCard = Brush.verticalGradient(
                    listOf(
                        Color(0xFF2D3033),
                        Color(0xFF17191B)
                    )
                )
            )
        }
    }
}