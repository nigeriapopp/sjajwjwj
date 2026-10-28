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
import kotlinx.coroutines.launch
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

    SettingsGroup(title = { Text(stringResource(R.string.vapor_group_phone)) }) {
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
                dismissButton = {
                    TextButton(onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, report)
                        runCatching {
                            context.startActivity(
                                android.content.Intent.createChooser(send, null)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }) { Text(stringResource(R.string.vapor_share)) }
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

    }

    SettingsGroup(title = { Text(stringResource(R.string.vapor_group_speed)) }) {
        var fastLaunch by rememberSaveable { mutableStateOf(VaporSettings.fastLaunch) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = fastLaunch,
            title = { Text(stringResource(R.string.vapor_fast_launch_title)) },
            subtitle = { Text(stringResource(R.string.vapor_fast_launch_subtitle)) },
            onCheckedChange = {
                fastLaunch = it
                VaporSettings.fastLaunch = it
            },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.vapor_repair_title)) },
            subtitle = { Text(stringResource(R.string.vapor_repair_subtitle)) },
            onClick = {
                VaporSettings.repairPending = true
                app.gamenative.ui.util.SnackbarManager.show(context.getString(R.string.vapor_repair_done))
            },
        )
    }

    SettingsGroup(title = { Text(stringResource(R.string.vapor_group_saves_graphics)) }) {
        var presets by rememberSaveable { mutableStateOf(VaporSettings.phonePresets) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = presets,
            title = { Text(stringResource(R.string.vapor_presets_title)) },
            subtitle = { Text(stringResource(R.string.vapor_presets_subtitle)) },
            onCheckedChange = {
                presets = it
                VaporSettings.phonePresets = it
            },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.vapor_presets_reset_title)) },
            subtitle = { Text(stringResource(R.string.vapor_presets_reset_subtitle)) },
            onClick = {
                VaporSettings.resetPresets()
                app.gamenative.ui.util.SnackbarManager.show(context.getString(R.string.vapor_presets_reset_done))
            },
        )
        var autoSave by rememberSaveable { mutableStateOf(VaporSettings.autoSaveBackup) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = autoSave,
            title = { Text(stringResource(R.string.vapor_autosave_title)) },
            subtitle = { Text(stringResource(R.string.vapor_autosave_subtitle)) },
            onCheckedChange = {
                autoSave = it
                VaporSettings.autoSaveBackup = it
            },
        )
    }

    SettingsGroup(title = { Text(stringResource(R.string.vapor_group_updates)) }) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var release by remember { mutableStateOf<app.gamenative.vapor.VaporRelease?>(null) }
        var checking by remember { mutableStateOf(false) }
        val installed = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }
        release?.let { r -> app.gamenative.ui.component.VaporUpdateDialog(r, onDismiss = { release = null }) }
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.vapor_update_check_title)) },
            subtitle = { Text(stringResource(R.string.vapor_update_check_subtitle, installed)) },
            onClick = {
                if (checking) return@SettingsMenuLink
                checking = true
                scope.launch {
                    val latest = app.gamenative.vapor.VaporUpdater.checkLatest()
                    checking = false
                    when {
                        latest == null -> app.gamenative.ui.util.SnackbarManager.show(context.getString(R.string.vapor_update_offline))
                        app.gamenative.vapor.VaporUpdates.isNewer(latest, app.gamenative.vapor.VaporUpdater.installedVersionCode(context)) ->
                            release = latest
                        else -> app.gamenative.ui.util.SnackbarManager.show(context.getString(R.string.vapor_update_none))
                    }
                }
            },
        )
        var autoUpdate by rememberSaveable { mutableStateOf(VaporSettings.autoUpdateCheck) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = autoUpdate,
            title = { Text(stringResource(R.string.vapor_update_auto_title)) },
            subtitle = { Text(stringResource(R.string.vapor_update_auto_subtitle)) },
            onCheckedChange = {
                autoUpdate = it
                VaporSettings.autoUpdateCheck = it
            },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.vapor_crash_reset_title)) },
            subtitle = { Text(stringResource(R.string.vapor_crash_reset_subtitle)) },
            onClick = {
                VaporSettings.resetCrashes()
                app.gamenative.ui.util.SnackbarManager.show(context.getString(R.string.vapor_crash_reset_done))
            },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.vapor_crash_log_title)) },
            subtitle = { Text(stringResource(R.string.vapor_crash_log_subtitle)) },
            onClick = {
                if (!app.gamenative.vapor.GameCrashLog.share(context)) {
                    app.gamenative.ui.util.SnackbarManager.show(context.getString(R.string.vapor_crash_log_none))
                }
            },
        )
    }

    SettingsGroup(title = { Text(stringResource(R.string.vapor_group_controls)) }) {
        var smartControls by rememberSaveable { mutableStateOf(VaporSettings.smartControls) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = smartControls,
            title = { Text(stringResource(R.string.vapor_smart_controls_title)) },
            subtitle = { Text(stringResource(R.string.vapor_smart_controls_subtitle)) },
            onCheckedChange = {
                smartControls = it
                VaporSettings.smartControls = it
            },
        )

        var ghostControls by rememberSaveable { mutableStateOf(VaporSettings.ghostControls) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = ghostControls,
            title = { Text(stringResource(R.string.vapor_ghost_controls_title)) },
            subtitle = { Text(stringResource(R.string.vapor_ghost_controls_subtitle)) },
            onCheckedChange = {
                ghostControls = it
                VaporSettings.ghostControls = it
            },
        )

    }

    SettingsGroup(title = { Text(stringResource(R.string.vapor_group_in_game)) }) {
        var heatStepDown by rememberSaveable { mutableStateOf(VaporSettings.heatStepDown) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = heatStepDown,
            title = { Text(stringResource(R.string.vapor_heat_step_title)) },
            subtitle = { Text(stringResource(R.string.vapor_heat_step_subtitle)) },
            onCheckedChange = {
                heatStepDown = it
                VaporSettings.heatStepDown = it
            },
        )

        var dnd by rememberSaveable { mutableStateOf(VaporSettings.doNotDisturb) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = dnd,
            title = { Text(stringResource(R.string.vapor_dnd_title)) },
            subtitle = { Text(stringResource(R.string.vapor_dnd_subtitle)) },
            onCheckedChange = {
                dnd = it
                VaporSettings.doNotDisturb = it
                if (it) {
                    val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                        as? android.app.NotificationManager
                    if (nm != null && !nm.isNotificationPolicyAccessGranted) {
                        app.gamenative.ui.util.SnackbarManager.show(context.getString(R.string.vapor_dnd_grant))
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            },
        )

        var playtime by rememberSaveable { mutableStateOf(VaporSettings.playtimeReminders) }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = playtime,
            title = { Text(stringResource(R.string.vapor_playtime_title)) },
            subtitle = { Text(stringResource(R.string.vapor_playtime_subtitle)) },
            onCheckedChange = {
                playtime = it
                VaporSettings.playtimeReminders = it
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
