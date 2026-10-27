package app.gamenative.ui.component.quickMenus

import android.content.Context
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.utils.ContainerUtils
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Vapor menu header: the game's name in a gradient, with clock and battery chips under it. */
@Composable
fun VaporMenuHeader(
    containerId: String?,
    fallbackTitle: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var title by remember(containerId) { mutableStateOf(fallbackTitle) }
    LaunchedEffect(containerId) {
        if (containerId != null) {
            val name = withContext(Dispatchers.IO) {
                runCatching { ContainerUtils.resolveGameName(containerId) }.getOrNull()
            }
            if (!name.isNullOrBlank()) title = name
        }
    }
    val clock = remember(visible) { DateFormat.getTimeFormat(context).format(Date()) }
    val battery = remember(visible) {
        runCatching {
            (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(-1)
    }
    val accent = PluviaTheme.colors.accentCyan
    val accent2 = PluviaTheme.colors.accentPurple

    Column(modifier = modifier) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                brush = Brush.horizontalGradient(listOf(accent, accent2)),
            ),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            HeaderChip(clock, accent)
            if (battery in 0..100) {
                HeaderChip("$battery%", if (battery <= 15) PluviaTheme.colors.accentDanger else accent2)
            }
        }
    }
}

@Composable
private fun HeaderChip(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
