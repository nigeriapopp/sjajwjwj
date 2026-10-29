package app.gamenative.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.vapor.VaporRelease
import app.gamenative.vapor.VaporSettings
import app.gamenative.vapor.VaporUpdater
import app.gamenative.vapor.VaporUpdates
import kotlinx.coroutines.launch

/** Checks GitHub once per app start and offers a newer Vapor build. */
@Composable
fun VaporUpdatePrompt() {
    val context = LocalContext.current
    var release by remember { mutableStateOf<VaporRelease?>(null) }
    LaunchedEffect(Unit) {
        VaporSettings.init(context)
        if (app.gamenative.vapor.VaporPaths.isSecondaryUser() && !VaporSettings.secondaryUserNoticeShown) {
            VaporSettings.secondaryUserNoticeShown = true
            app.gamenative.ui.util.SnackbarManager.show(
                "Vapor is running in Secure Folder or a second profile. Vapor redirects its files there " +
                    "automatically; if a game misbehaves, installing Vapor in the normal profile is the most reliable.",
            )
        }
        if (!VaporSettings.autoUpdateCheck) return@LaunchedEffect
        val latest = VaporUpdater.checkLatest()
        if (VaporUpdates.isNewer(latest, VaporUpdater.installedVersionCode(context)) &&
            latest!!.versionCode != VaporSettings.skippedUpdate
        ) {
            release = latest
        }
    }
    release?.let { r ->
        VaporUpdateDialog(r, onDismiss = { release = null }, onSkip = { VaporSettings.skippedUpdate = r.versionCode })
    }
}

@Composable
fun VaporUpdateDialog(release: VaporRelease, onDismiss: () -> Unit, onSkip: (() -> Unit)? = null) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(-1f) }
    var message by remember { mutableStateOf<String?>(null) }
    var keyMismatch by remember { mutableStateOf(false) }
    val busy = progress >= 0f && progress < 1f

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                "Vapor update ready",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(PluviaTheme.colors.accentCyan, PluviaTheme.colors.accentPurple))),
                )
                Text(release.title, style = MaterialTheme.typography.titleSmall)
                if (release.apkSize > 0) {
                    Text("%.0f MB · installs over your current app, games and logins stay".format(release.apkSize / 1_048_576.0))
                }
                if (release.notes.isNotBlank()) {
                    Text(
                        release.notes.trim().take(600),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()),
                    )
                }
                if (progress >= 0f) {
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(if (progress < 1f) "Downloading… ${(progress * 100).toInt()}%" else "Opening installer…")
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (keyMismatch) {
                TextButton(onClick = { uri.openUri("https://github.com/${VaporUpdates.REPO}/releases/latest") }) {
                    Text("Open download page")
                }
            } else {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        message = null
                        progress = 0f
                        scope.launch {
                            val result = VaporUpdater.downloadAndInstall(
                                context,
                                release.apkUrl,
                                "Vapor-${release.versionCode}.apk",
                                selfUpdate = true,
                            ) { p -> progress = p }
                            when (result) {
                                VaporUpdater.Result.Started -> onDismiss()
                                VaporUpdater.Result.NeedsPermission -> {
                                    progress = -1f
                                    message = "Allow Vapor to install updates, then tap Update again."
                                    VaporUpdater.openInstallPermission(context)
                                }
                                VaporUpdater.Result.KeyMismatch -> {
                                    progress = -1f
                                    keyMismatch = true
                                    message = "Your installed Vapor was signed with an older key, so Android can't " +
                                        "update it in place. Uninstall it once and install this APK; every update after " +
                                        "that installs over the top."
                                }
                                is VaporUpdater.Result.Failed -> {
                                    progress = -1f
                                    message = result.reason
                                }
                            }
                        }
                    },
                ) { Text("Update") }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    onSkip?.invoke()
                    onDismiss()
                },
            ) { Text(if (onSkip != null) "Skip this one" else "Later") }
        },
    )
}
