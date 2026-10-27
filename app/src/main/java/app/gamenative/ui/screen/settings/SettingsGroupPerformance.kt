package app.gamenative.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.vapor.VaporAutoTune
import app.gamenative.vapor.VaporSettings
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch

private val VAPOR_FPS_CHOICES = intArrayOf(30, 40, 45, 60, 90, 120)

@Composable
fun SettingsGroupPerformance() {
    val context = LocalContext.current
    remember(context) { VaporSettings.init(context) }

    SettingsGroup {
        var phoneReport by rememberSaveable { mutableStateOf<String?>(null) }
        var fpsTarget by rememberSaveable { mutableIntStateOf(VaporSettings.fpsTarget) }

        phoneReport?.let { report ->
            AlertDialog(
                onDismissRequest = { phoneReport = null },
                title = { Text(stringResource(R.string.vapor_phone_check_title)) },
                text = {
                    Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(text = report, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { phoneReport = null }) { Text(stringResource(R.string.vapor_ok)) }
                },
            )
        }

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.vapor_phone_check_title)) },
            subtitle = { Text(stringResource(R.string.vapor_phone_check_subtitle)) },
            onClick = { phoneReport = VaporAutoTune.profile(context).toReport() },
        )

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.vapor_autotune_title)) },
            subtitle = { Text(stringResource(R.string.vapor_autotune_subtitle)) },
            onClick = {
                val profile = VaporAutoTune.apply(context)
                fpsTarget = VaporSettings.fpsTarget
                app.gamenative.ui.util.SnackbarManager.show(
                    context.getString(R.string.vapor_autotune_done, profile.tier.label, profile.plan.fpsTarget),
                )
            },
        )

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.vapor_fps_target_title)) },
            subtitle = { Text(stringResource(R.string.vapor_fps_target_subtitle, fpsTarget)) },
            onClick = {
                val i = VAPOR_FPS_CHOICES.indexOf(fpsTarget)
                val next = VAPOR_FPS_CHOICES[(i + 1) % VAPOR_FPS_CHOICES.size]
                VaporSettings.fpsTarget = next
                fpsTarget = next
            },
        )

        var sustained by rememberSaveable { mutableStateOf(VaporSettings.sustainedPerformance) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = sustained,
            title = { Text(stringResource(R.string.vapor_sustained_title)) },
            subtitle = { Text(stringResource(R.string.vapor_sustained_subtitle)) },
            onCheckedChange = {
                sustained = it
                VaporSettings.sustainedPerformance = it
            },
        )

        var lockRefresh by rememberSaveable { mutableStateOf(VaporSettings.lockMaxRefreshRate) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = lockRefresh,
            title = { Text(stringResource(R.string.vapor_refresh_title)) },
            subtitle = { Text(stringResource(R.string.vapor_refresh_subtitle)) },
            onCheckedChange = {
                lockRefresh = it
                VaporSettings.lockMaxRefreshRate = it
            },
        )

        var batterySaver by rememberSaveable { mutableStateOf(VaporSettings.respectBatterySaver) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = batterySaver,
            title = { Text(stringResource(R.string.vapor_battery_title)) },
            subtitle = { Text(stringResource(R.string.vapor_battery_subtitle)) },
            onCheckedChange = {
                batterySaver = it
                VaporSettings.respectBatterySaver = it
            },
        )

        var thermal by rememberSaveable { mutableStateOf(VaporSettings.thermalWarnings) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = thermal,
            title = { Text(stringResource(R.string.vapor_thermal_title)) },
            subtitle = { Text(stringResource(R.string.vapor_thermal_subtitle)) },
            onCheckedChange = {
                thermal = it
                VaporSettings.thermalWarnings = it
            },
        )

        var powerControlDefaultEnabled by rememberSaveable { mutableStateOf(PrefManager.powerControlDefaultEnabled) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = powerControlDefaultEnabled,
            title = { Text(stringResource(R.string.settings_performance_power_control_title)) },
            subtitle = { Text(stringResource(R.string.settings_performance_power_control_subtitle)) },
            onCheckedChange = {
                powerControlDefaultEnabled = it
                PrefManager.powerControlDefaultEnabled = it
            },
        )
    }
}
