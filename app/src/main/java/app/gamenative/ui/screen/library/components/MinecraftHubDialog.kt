package app.gamenative.ui.screen.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.vapor.MinecraftGuide
import app.gamenative.vapor.MinecraftHub
import app.gamenative.vapor.VaporAutoTune

/** Vapor's Minecraft: Java Edition hub. */
@Composable
fun MinecraftHubDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var refresh by remember { mutableIntStateOf(0) }
    val installed = remember(refresh) { MinecraftHub.installed(context) }
    val tuning = remember {
        val profile = VaporAutoTune.profile(context)
        MinecraftGuide.tuningFor(profile.snapshot.totalRamMb, profile.tier)
    }
    val steps = remember(tuning) { MinecraftGuide.guide(tuning) }
    val grass = Color(0xFF5DBB63)
    val dirt = Color(0xFF8B5A2B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                // Hero
                val shimmer = rememberInfiniteTransition(label = "mcHero")
                val shift by shimmer.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
                    label = "mcHeroShift",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(grass, Color(0xFF2E7D32), dirt),
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(600f + 600f * shift, 600f),
                            ),
                        ),
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text(
                            "Minecraft",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                        )
                        Text(
                            "Java Edition on your phone · mods included",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (installed.isNotEmpty()) {
                        installed.forEach { launcher ->
                            BigButton(
                                text = "Play with ${launcher.name}",
                                icon = { Icon(Icons.Filled.PlayArrow, null, tint = Color.White) },
                                brush = Brush.horizontalGradient(listOf(grass, Color(0xFF2E7D32))),
                                onClick = {
                                    if (!MinecraftHub.launch(context, launcher)) {
                                        SnackbarManager.show("Couldn't open ${launcher.name}")
                                    }
                                },
                            )
                        }
                    } else {
                        Text(
                            "You need a free launcher app to run Java Edition. Pick one:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    listOf(MinecraftHub.ZALITH, MinecraftHub.AMETHYST)
                        .filter { l -> installed.none { it.packageName == l.packageName } }
                        .forEach { launcher ->
                            LauncherCard(
                                launcher = launcher,
                                accent = if (launcher == MinecraftHub.ZALITH) grass else PluviaTheme.colors.accentPurple,
                                onGet = {
                                    uri.openUri(launcher.downloadUrl)
                                    SnackbarManager.show("Download the .apk under Assets, install it, then come back here")
                                },
                            )
                        }

                    if (installed.isEmpty()) {
                        Text(
                            "Installed it already? Tap here to check again.",
                            color = PluviaTheme.colors.accentCyan,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { refresh++ }.padding(vertical = 4.dp),
                        )
                    }

                    Text(
                        "Set up in a few minutes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    steps.forEachIndexed { i, step -> StepCard(step, startExpanded = i == 0, accent = grass) }

                    Text(
                        "You need to own Minecraft: Java Edition on a Microsoft account. Vapor and these launchers " +
                            "are not made by Mojang or Microsoft.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BigButton(text: String, icon: @Composable () -> Unit, brush: Brush, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun LauncherCard(launcher: MinecraftHub.Launcher, accent: Color, onGet: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(launcher.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(launcher.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(accent)
                .clickable(onClick = onGet)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Download, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Get", color = Color.Black, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun StepCard(step: MinecraftGuide.Step, startExpanded: Boolean, accent: Color) {
    var expanded by remember { mutableStateOf(startExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .border(1.dp, accent.copy(alpha = if (expanded) 0.5f else 0.12f), RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp),
    ) {
        Text(step.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                step.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
