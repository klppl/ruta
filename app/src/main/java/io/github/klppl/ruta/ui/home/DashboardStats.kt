package io.github.klppl.ruta.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.klppl.ruta.blocking.BlockStats

/**
 * "What ruta has done for you" panel. Shown once there's been blocking activity, it turns the
 * launcher from a static grid of shortcuts into a home screen that reflects real usage: a headline
 * count of trackers and ads blocked, recent activity, how many sites you keep, and the size of the
 * loaded filter list.
 */
@Composable
fun DashboardStats(
    stats: BlockStats,
    sites: Int,
    rulesLoaded: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = formatCount(stats.total),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "trackers & ads blocked",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCell("Today", formatCount(stats.today), Modifier.weight(1f))
                StatCell("This week", formatCount(stats.week), Modifier.weight(1f))
                StatCell("Sites", sites.toString(), Modifier.weight(1f))
            }
            Text(
                text = "$rulesLoaded block rules loaded",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 1_234 -> "1,234"; 2_500_000 -> "2.5M". Keeps the headline compact without a locale dep. */
private fun formatCount(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> {
        val s = n.toString()
        val head = s.dropLast(3)
        val tail = s.takeLast(3)
        "$head,$tail"
    }
    else -> {
        val millions = n / 100_000L / 10.0
        if (millions % 1.0 == 0.0) "${millions.toLong()}M" else "${millions}M"
    }
}
