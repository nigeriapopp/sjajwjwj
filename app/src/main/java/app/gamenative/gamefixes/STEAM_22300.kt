package app.gamenative.gamefixes

import app.gamenative.data.GameSource

/**
 * Fallout 3 (Steam)
 * Registry install path, plus Vapor's many-core startup crash fix.
 */
val STEAM_Fix_22300: KeyedGameFix = KeyedCompositeGameFix(
    gameSource = GameSource.STEAM,
    gameId = "22300",
    fixes = listOf(
        RegistryKeyFix(
            registryKey = "Software\\Wow6432Node\\Bethesda Softworks\\Fallout3",
            defaultValues = mapOf(
                "Installed Path" to INSTALL_PATH_PLACEHOLDER,
            ),
        ),
        BethesdaThreadingFix(
            myGamesFolder = "Fallout3",
            defaultIni = "Fallout_default.ini",
            userIni = "Fallout.ini",
        ),
    ),
)
