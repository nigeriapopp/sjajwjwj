package app.gamenative.ui.component.quickMenus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.vapor.SaveSnapshots
import app.gamenative.vapor.VaporTime
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vapor "Saves" tab: snapshot the game's save folders and load an earlier one.
 * Loading closes the game; the snapshot is put back right before the next launch.
 */
@Composable
fun SaveSnapshotsQuickMenuTab(
    containerRoot: File,
    onLoadAndExit: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val scope = rememberCoroutineScope()
    var snapshots by remember(containerRoot) { mutableStateOf<List<SaveSnapshots.Snapshot>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var confirmLoad by remember { mutableStateOf<SaveSnapshots.Snapshot?>(null) }
    val accent = PluviaTheme.colors.accentCyan
    val accent2 = PluviaTheme.colors.accentPurple

    LaunchedEffect(containerRoot, refreshKey) {
        snapshots = withContext(Dispatchers.IO) { SaveSnapshots.list(containerRoot) }
    }

    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        SaveNowButton(
            busy = busy,
            brush = Brush.horizontalGradient(listOf(accent, accent2)),
            focusRequester = focusRequester,
            onClick = {
                if (busy) return@SaveNowButton
                busy = true
                scope.launch {
                    val snap = withContext(Dispatchers.IO) {
                        val n = SaveSnapshots.list(containerRoot).count { it.label.startsWith("Save ") } + 1
                        SaveSnapshots.create(containerRoot, "Save $n")
                    }
                    busy = false
                    refreshKey++
                    SnackbarManager.show(
                        if (snap != null) "Saved · ${SaveSnapshots.describeSize(snap.bytes)}" else "Nothing to save yet",
                    )
                }
            },
        )

        Text(
            text = "Backs up the game's Documents, AppData and Saved Games folders.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
        )

        AnimatedVisibility(
            visible = snapshots.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = "No snapshots yet. Tap Save now to make your first one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(snapshots, key = { it.id }) { snap ->
                SnapshotRow(
                    snap = snap,
                    accent = accent,
                    modifier = Modifier.animateItem(),
                    onLoad = { confirmLoad = snap },
                    onDelete = {
                        scope.launch {
                            withContext(Dispatchers.IO) { SaveSnapshots.delete(snap) }
                            refreshKey++
                        }
                    },
                )
            }
        }
    }

    confirmLoad?.let { snap ->
        AlertDialog(
            onDismissRequest = { confirmLoad = null },
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            title = { Text("Load \"${snap.label}\"?") },
            text = {
                Text(
                    "The game will close now. Press Play again and you'll continue from this save. " +
                        "Your current saves are backed up first, so nothing is lost.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLoad = null
                    SaveSnapshots.requestRestoreOnNextLaunch(containerRoot, snap)
                    SnackbarManager.show("Loading \"${snap.label}\" — press Play to continue")
                    onLoadAndExit()
                }) { Text("Load and close game") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLoad = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SaveNowButton(
    busy: Boolean,
    brush: Brush,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "saveNowScale",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Save, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (busy) "Saving…" else "Save now",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun SnapshotRow(
    snap: SaveSnapshots.Snapshot,
    accent: Color,
    modifier: Modifier,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember(snap.id) { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, accent.copy(alpha = if (expanded) 0.6f else 0.15f), RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (snap.label == "Before loading") Icons.Filled.History else Icons.Filled.Save,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snap.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${VaporTime.ago(now, snap.createdAt)} · ${SaveSnapshots.describeSize(snap.bytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
                    .clickable(onClick = onLoad),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Load this save",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
