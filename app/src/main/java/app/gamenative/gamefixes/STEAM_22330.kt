package app.gamenative.gamefixes

import app.gamenative.data.GameSource

/**
 * The Elder Scrolls IV: Oblivion (Steam)
 * Registry install path, plus Vapor's launch setup (game exe, ini files, window size, CPU cores).
 */
val STEAM_Fix_22330: KeyedGameFix = KeyedCompositeGameFix(
    gameSource = GameSource.STEAM,
    gameId = "22330",
    fixes = listOf(
        RegistryKeyFix(
            registryKey = "Software\\Wow6432Node\\Bethesda Softworks\\Oblivion",
            defaultValues = mapOf(
                "Installed Path" to INSTALL_PATH_PLACEHOLDER,
            ),
        ),
        BethesdaSetupFix(app.gamenative.vapor.BethesdaSetup.OBLIVION),
    ),
)
