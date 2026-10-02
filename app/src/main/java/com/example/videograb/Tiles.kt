package com.example.videograb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** App-icon style shortcut: a badge on top, label underneath. */
@Composable
fun SiteTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        badge()
        Text(
            label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ActionBadge(icon: ImageVector, dim: Dp = 52.dp) {
    Box(
        Modifier.size(dim).clip(RoundedCornerShape(dim * 0.3f)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(dim * 0.5f))
    }
}

/** Fixed-column grid that can sit inside a LazyColumn item. */
@Composable
fun SiteGrid(columns: Int, count: Int, cell: @Composable (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until count).chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { i ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { cell(i) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
