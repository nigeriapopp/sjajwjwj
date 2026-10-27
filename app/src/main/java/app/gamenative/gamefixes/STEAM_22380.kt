package app.gamenative.gamefixes

import app.gamenative.data.GameSource

/**
 * Fallout: New Vegas (Steam)
 * Registry install path, plus Vapor's many-core startup crash fix.
 */
val STEAM_Fix_22380: KeyedGameFix = KeyedCompositeGameFix(
    gameSource = GameSource.STEAM,
    gameId = "22380",
    fixes = listOf(
        RegistryKeyFix(
            registryKey = "Software\\Wow6432Node\\Bethesda Softworks\\FalloutNV",
            defaultValues = mapOf(
                "Installed Path" to INSTALL_PATH_PLACEHOLDER,
            ),
        ),
        BethesdaThreadingFix(
            myGamesFolder = "FalloutNV",
            defaultIni = "Fallout_default.ini",
            userIni = "Fallout.ini",
        ),
    ),
)
