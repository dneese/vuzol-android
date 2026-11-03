package com.roman.zemzeme.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Selectable colour themes. Each pack defines the full palette used by the app
 * (chat bubbles, accents, surfaces) for dark, light and AMOLED modes.
 */
enum class ThemePack(val id: String, val label: String) {
    Bitchat("bitchat", "Bitchat"),
    Matrix("matrix", "Matrix Green"),
    Amber("amber", "Amber Terminal"),
    Cyber("cyber", "Cyberpunk"),
    Hacker("hacker", "Hacker Blue"),
    Ghost("ghost", "Ghost");

    companion object {
        fun fromId(id: String?): ThemePack =
            entries.firstOrNull { it.id == id } ?: Bitchat
    }
}

/** Colours that make up one theme pack. */
data class PackColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val sentBubble: Color,
    val receivedBubble: Color,
    val contactGreen: Color
)

object ThemePacks {

    private val bitchatDark = PackColors(
        primary = Color(0xFF2AABEE),
        onPrimary = Color(0xFF0F1620),
        primaryContainer = Color(0xFF003D42),
        onPrimaryContainer = Color(0xFF7FCBF0),
        secondary = Color(0xFF6E7BE8),
        onSecondary = Color(0xFF0F1620),
        secondaryContainer = Color(0xFF3D2952),
        onSecondaryContainer = Color(0xFFAFB8F5),
        tertiary = Color(0xFF4CAF50),
        onTertiary = Color(0xFF0F1620),
        tertiaryContainer = Color(0xFF1A3D24),
        onTertiaryContainer = Color(0xFFA5D6A7),
        error = Color(0xFFE53935),
        onError = Color(0xFF0F1620),
        errorContainer = Color(0xFF4D1F1A),
        onErrorContainer = Color(0xFFFFB4AB),
        background = Color(0xFF0F1620),
        onBackground = Color(0xFFF2F5F7),
        surface = Color(0xFF17212B),
        onSurface = Color(0xFFF2F5F7),
        surfaceVariant = Color(0xFF1F2C38),
        onSurfaceVariant = Color(0xFF8A9AA8),
        outline = Color(0xFF2B3B47),
        outlineVariant = Color(0xFF3A3A4C),
        textSecondary = Color(0xFF8A9AA8),
        textTertiary = Color(0xFF6C7A88),
        sentBubble = Color(0xFF2B5278),
        receivedBubble = Color(0xFF1F2C38),
        contactGreen = Color(0xFF30D158)
    )

    private val bitchatLight = PackColors(
        primary = Color(0xFF1E88C7),
        onPrimary = Color(0xFFF2F5F7),
        primaryContainer = Color(0xFFB8F5FF),
        onPrimaryContainer = Color(0xFF001F24),
        secondary = Color(0xFF4F5BC7),
        onSecondary = Color(0xFFF2F5F7),
        secondaryContainer = Color(0xFFF2DAFF),
        onSecondaryContainer = Color(0xFF2D0A42),
        tertiary = Color(0xFF2E7D32),
        onTertiary = Color(0xFFF2F5F7),
        tertiaryContainer = Color(0xFFC8FFCE),
        onTertiaryContainer = Color(0xFF002108),
        error = Color(0xFFB71C1C),
        onError = Color(0xFFF2F5F7),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF10161C),
        surface = Color(0xFFF2F5F7),
        onSurface = Color(0xFF10161C),
        surfaceVariant = Color(0xFFF4F6F8),
        onSurfaceVariant = Color(0xFF5A6570),
        outline = Color(0xFFE7EBEF),
        outlineVariant = Color(0xFFD1D1DC),
        textSecondary = Color(0xFF5A6570),
        textTertiary = Color(0xFF7C8894),
        sentBubble = Color(0xFFE3F6FF),
        receivedBubble = Color(0xFFF0F0F5),
        contactGreen = Color(0xFF248A3D)
    )

    private val matrixDark = PackColors(
        primary = Color(0xFF00E676),
        onPrimary = Color(0xFF04120A),
        primaryContainer = Color(0xFF003D1F),
        onPrimaryContainer = Color(0xFF9AF7C0),
        secondary = Color(0xFF00E5FF),
        onSecondary = Color(0xFF04120A),
        secondaryContainer = Color(0xFF00404A),
        onSecondaryContainer = Color(0xFF9DEBFF),
        tertiary = Color(0xFFB2FF59),
        onTertiary = Color(0xFF04120A),
        tertiaryContainer = Color(0xFF244000),
        onTertiaryContainer = Color(0xFFD4FFA0),
        error = Color(0xFFFF5252),
        onError = Color(0xFF0A0A0A),
        errorContainer = Color(0xFF4A1111),
        onErrorContainer = Color(0xFFFFD6D6),
        background = Color(0xFF05080A),
        onBackground = Color(0xFFB9FFC9),
        surface = Color(0xFF0A120D),
        onSurface = Color(0xFFB9FFC9),
        surfaceVariant = Color(0xFF101A13),
        onSurfaceVariant = Color(0xFF7FA98C),
        outline = Color(0xFF1C3323),
        outlineVariant = Color(0xFF152218),
        textSecondary = Color(0xFF7FA98C),
        textTertiary = Color(0xFF4F7059),
        sentBubble = Color(0xFF0C3A1E),
        receivedBubble = Color(0xFF0E1A12),
        contactGreen = Color(0xFF00E676)
    )

    private val matrixLight = PackColors(
        primary = Color(0xFF00893B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC7F5D8),
        onPrimaryContainer = Color(0xFF00210D),
        secondary = Color(0xFF00778A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFBFF0F7),
        onSecondaryContainer = Color(0xFF001F24),
        tertiary = Color(0xFF4F7A00),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD6FFAA),
        onTertiaryContainer = Color(0xFF132600),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF4FBF6),
        onBackground = Color(0xFF0B1A10),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0B1A10),
        surfaceVariant = Color(0xFFEFF7F0),
        onSurfaceVariant = Color(0xFF4B6353),
        outline = Color(0xFFD8E8DC),
        outlineVariant = Color(0xFFC2D4C6),
        textSecondary = Color(0xFF4B6353),
        textTertiary = Color(0xFF6B7F70),
        sentBubble = Color(0xFFD7F5E2),
        receivedBubble = Color(0xFFEFF7F0),
        contactGreen = Color(0xFF00893B)
    )

    private val amberDark = PackColors(
        primary = Color(0xFFFFB000),
        onPrimary = Color(0xFF1A1200),
        primaryContainer = Color(0xFF3D2A00),
        onPrimaryContainer = Color(0xFFFFD98A),
        secondary = Color(0xFFFFCA28),
        onSecondary = Color(0xFF1A1200),
        secondaryContainer = Color(0xFF3A2800),
        onSecondaryContainer = Color(0xFFFFE3A3),
        tertiary = Color(0xFFFFD54F),
        onTertiary = Color(0xFF1A1200),
        tertiaryContainer = Color(0xFF403000),
        onTertiaryContainer = Color(0xFFFFE9A8),
        error = Color(0xFFFF7043),
        onError = Color(0xFF1A1200),
        errorContainer = Color(0xFF4A1E00),
        onErrorContainer = Color(0xFFFFDBCB),
        background = Color(0xFF120C00),
        onBackground = Color(0xFFFFD98A),
        surface = Color(0xFF1B1200),
        onSurface = Color(0xFFFFD98A),
        surfaceVariant = Color(0xFF261A00),
        onSurfaceVariant = Color(0xFFB08A4A),
        outline = Color(0xFF3A2906),
        outlineVariant = Color(0xFF2A1D05),
        textSecondary = Color(0xFFB08A4A),
        textTertiary = Color(0xFF7A5D30),
        sentBubble = Color(0xFF4A3300),
        receivedBubble = Color(0xFF231803),
        contactGreen = Color(0xFFB2FF59)
    )

    private val amberLight = PackColors(
        primary = Color(0xFFB36B00),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE3B0),
        onPrimaryContainer = Color(0xFF2B1800),
        secondary = Color(0xFF8A6100),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFE0A8),
        onSecondaryContainer = Color(0xFF2B1800),
        tertiary = Color(0xFF8A6D00),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE9A8),
        onTertiaryContainer = Color(0xFF2B2200),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFF8ED),
        onBackground = Color(0xFF221600),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF221600),
        surfaceVariant = Color(0xFFFFF2DA),
        onSurfaceVariant = Color(0xFF6B5733),
        outline = Color(0xFFF0DFBE),
        outlineVariant = Color(0xFFDCC79B),
        textSecondary = Color(0xFF6B5733),
        textTertiary = Color(0xFF8A7550),
        sentBubble = Color(0xFFFFE9C2),
        receivedBubble = Color(0xFFFFF6E6),
        contactGreen = Color(0xFF557700)
    )

    private val cyberDark = PackColors(
        primary = Color(0xFFFF2E97),
        onPrimary = Color(0xFF14000D),
        primaryContainer = Color(0xFF4A0029),
        onPrimaryContainer = Color(0xFFFFA8D2),
        secondary = Color(0xFF00E5FF),
        onSecondary = Color(0xFF001418),
        secondaryContainer = Color(0xFF00404A),
        onSecondaryContainer = Color(0xFF9DEBFF),
        tertiary = Color(0xFF7C4DFF),
        onTertiary = Color(0xFF0D001F),
        tertiaryContainer = Color(0xFF2B0A5A),
        onTertiaryContainer = Color(0xFFD9C2FF),
        error = Color(0xFFFF5252),
        onError = Color(0xFF14000D),
        errorContainer = Color(0xFF4A1111),
        onErrorContainer = Color(0xFFFFD6D6),
        background = Color(0xFF0D0014),
        onBackground = Color(0xFFF5D9EA),
        surface = Color(0xFF170320),
        onSurface = Color(0xFFF5D9EA),
        surfaceVariant = Color(0xFF220A2F),
        onSurfaceVariant = Color(0xFFB084A4),
        outline = Color(0xFF3A1547),
        outlineVariant = Color(0xFF2A0D35),
        textSecondary = Color(0xFFB084A4),
        textTertiary = Color(0xFF7A5A72),
        sentBubble = Color(0xFF4A0A3D),
        receivedBubble = Color(0xFF1D0626),
        contactGreen = Color(0xFF00E5FF)
    )

    private val cyberLight = PackColors(
        primary = Color(0xFFB80062),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD6E9),
        onPrimaryContainer = Color(0xFF3E0020),
        secondary = Color(0xFF006875),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFBFF0F7),
        onSecondaryContainer = Color(0xFF001F24),
        tertiary = Color(0xFF5B36C7),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE7DDFF),
        onTertiaryContainer = Color(0xFF1B0063),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFF4FA),
        onBackground = Color(0xFF1C0A14),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1C0A14),
        surfaceVariant = Color(0xFFFFE7F3),
        onSurfaceVariant = Color(0xFF6B4059),
        outline = Color(0xFFF3CFE2),
        outlineVariant = Color(0xFFDDB3C9),
        textSecondary = Color(0xFF6B4059),
        textTertiary = Color(0xFF8A6076),
        sentBubble = Color(0xFFFFD6E9),
        receivedBubble = Color(0xFFFBEBF4),
        contactGreen = Color(0xFF006875)
    )

    private val hackerDark = PackColors(
        primary = Color(0xFF3D9BFF),
        onPrimary = Color(0xFF00122B),
        primaryContainer = Color(0xFF00325C),
        onPrimaryContainer = Color(0xFFB3D6FF),
        secondary = Color(0xFF7FDBFF),
        onSecondary = Color(0xFF00122B),
        secondaryContainer = Color(0xFF004C63),
        onSecondaryContainer = Color(0xFFB3ECF9),
        tertiary = Color(0xFF64FFDA),
        onTertiary = Color(0xFF001F1A),
        tertiaryContainer = Color(0xFF005045),
        onTertiaryContainer = Color(0xFFA6FFEA),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF00122B),
        errorContainer = Color(0xFF4A1111),
        onErrorContainer = Color(0xFFFFD6D6),
        background = Color(0xFF03080F),
        onBackground = Color(0xFFD6E9FF),
        surface = Color(0xFF07121F),
        onSurface = Color(0xFFD6E9FF),
        surfaceVariant = Color(0xFF0C1B2B),
        onSurfaceVariant = Color(0xFF7C9BBE),
        outline = Color(0xFF17324D),
        outlineVariant = Color(0xFF102438),
        textSecondary = Color(0xFF7C9BBE),
        textTertiary = Color(0xFF54718E),
        sentBubble = Color(0xFF0C3560),
        receivedBubble = Color(0xFF0A1826),
        contactGreen = Color(0xFF64FFDA)
    )

    private val hackerLight = PackColors(
        primary = Color(0xFF0067B8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD2E7FF),
        onPrimaryContainer = Color(0xFF001D36),
        secondary = Color(0xFF00677F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFBCEBFA),
        onSecondaryContainer = Color(0xFF001F27),
        tertiary = Color(0xFF00796B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFB6F5E9),
        onTertiaryContainer = Color(0xFF00201C),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF3F8FF),
        onBackground = Color(0xFF0A1622),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0A1622),
        surfaceVariant = Color(0xFFE8F1FC),
        onSurfaceVariant = Color(0xFF43566B),
        outline = Color(0xFFCFE0F5),
        outlineVariant = Color(0xFFB4CAE5),
        textSecondary = Color(0xFF43566B),
        textTertiary = Color(0xFF647B93),
        sentBubble = Color(0xFFD3E8FF),
        receivedBubble = Color(0xFFEFF5FC),
        contactGreen = Color(0xFF00796B)
    )

    private val ghostDark = PackColors(
        primary = Color(0xFFE6E6E6),
        onPrimary = Color(0xFF101010),
        primaryContainer = Color(0xFF2A2A2A),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFFBDBDBD),
        onSecondary = Color(0xFF101010),
        secondaryContainer = Color(0xFF242424),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFF9E9E9E),
        onTertiary = Color(0xFF101010),
        tertiaryContainer = Color(0xFF2E2E2E),
        onTertiaryContainer = Color(0xFFFFFFFF),
        error = Color(0xFFCF6679),
        onError = Color(0xFF101010),
        errorContainer = Color(0xFF3A1F25),
        onErrorContainer = Color(0xFFFFD9DF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFEDEDED),
        surface = Color(0xFF0C0C0C),
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = Color(0xFF161616),
        onSurfaceVariant = Color(0xFFA6A6A6),
        outline = Color(0xFF2E2E2E),
        outlineVariant = Color(0xFF1C1C1C),
        textSecondary = Color(0xFFA6A6A6),
        textTertiary = Color(0xFF757575),
        sentBubble = Color(0xFF232323),
        receivedBubble = Color(0xFF141414),
        contactGreen = Color(0xFFBDBDBD)
    )

    private val ghostLight = PackColors(
        primary = Color(0xFF2B2B2B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE4E4E4),
        onPrimaryContainer = Color(0xFF1A1A1A),
        secondary = Color(0xFF555555),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEDEDED),
        onSecondaryContainer = Color(0xFF1A1A1A),
        tertiary = Color(0xFF6B6B6B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFEAEAEA),
        onTertiaryContainer = Color(0xFF1A1A1A),
        error = Color(0xFFA33A45),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF161616),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF161616),
        surfaceVariant = Color(0xFFF1F1F1),
        onSurfaceVariant = Color(0xFF5A5A5A),
        outline = Color(0xFFE1E1E1),
        outlineVariant = Color(0xFFCDCDCD),
        textSecondary = Color(0xFF5A5A5A),
        textTertiary = Color(0xFF7D7D7D),
        sentBubble = Color(0xFFEDEDED),
        receivedBubble = Color(0xFFF6F6F6),
        contactGreen = Color(0xFF555555)
    )

    fun colors(pack: ThemePack, dark: Boolean): PackColors = when (pack) {
        ThemePack.Bitchat -> if (dark) bitchatDark else bitchatLight
        ThemePack.Matrix -> if (dark) matrixDark else matrixLight
        ThemePack.Amber -> if (dark) amberDark else amberLight
        ThemePack.Cyber -> if (dark) cyberDark else cyberLight
        ThemePack.Hacker -> if (dark) hackerDark else hackerLight
        ThemePack.Ghost -> if (dark) ghostDark else ghostLight
    }

    fun darkScheme(pack: ThemePack): androidx.compose.material3.ColorScheme {
        val c = colors(pack, dark = true)
        return darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onBackground,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariant,
            outline = c.outline,
            outlineVariant = c.outlineVariant,
            error = c.error,
            onError = c.onError,
            errorContainer = c.errorContainer,
            onErrorContainer = c.onErrorContainer
        )
    }

    fun lightScheme(pack: ThemePack): androidx.compose.material3.ColorScheme {
        val c = colors(pack, dark = false)
        return lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onBackground,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariant,
            outline = c.outline,
            outlineVariant = c.outlineVariant,
            error = c.error,
            onError = c.onError,
            errorContainer = c.errorContainer,
            onErrorContainer = c.onErrorContainer
        )
    }

    /** AMOLED variant: pure black surfaces with the pack's accents. */
    fun amoledScheme(pack: ThemePack): androidx.compose.material3.ColorScheme {
        val c = colors(pack, dark = true)
        return darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = Color(0xFF000000),
            onBackground = c.onBackground,
            surface = Color(0xFF0A0A0A),
            onSurface = c.onSurface,
            surfaceVariant = Color(0xFF141414),
            onSurfaceVariant = c.onSurfaceVariant,
            outline = Color(0xFF1E1E1E),
            outlineVariant = Color(0xFF101010),
            error = c.error,
            onError = c.onError,
            errorContainer = c.errorContainer,
            onErrorContainer = c.onErrorContainer
        )
    }

    fun extended(pack: ThemePack, dark: Boolean): ExtendedColors {
        val c = colors(pack, dark)
        return ExtendedColors(
            electricCyan = c.primary,
            electricCyanVariant = c.primaryContainer,
            neonPurple = c.secondary,
            matrixGreen = c.tertiary,
            solarOrange = c.secondary,
            signalRed = c.error,
            sentBubble = c.sentBubble,
            receivedBubble = c.receivedBubble,
            surfaceElevated = c.surfaceVariant,
            borderSubtle = c.outline,
            textSecondary = c.textSecondary,
            textTertiary = c.textTertiary,
            glowColor = c.primary.copy(alpha = if (dark) 0.35f else 0.18f),
            success = c.tertiary,
            link = c.secondary,
            contactGreen = c.contactGreen
        )
    }
}

/**
 * Stores the selected theme pack.
 */
object ThemePackManager {
    private const val PREFS_NAME = "bitchat_settings"
    private const val KEY_PACK = "theme_pack"

    private val _packFlow = MutableStateFlow(ThemePack.Bitchat)
    val packFlow: StateFlow<ThemePack> = _packFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _packFlow.value = ThemePack.fromId(prefs.getString(KEY_PACK, null))
    }

    fun set(context: Context, pack: ThemePack) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PACK, pack.id).apply()
        _packFlow.value = pack
    }
}
