package com.wefi.analyzer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tile ringkas untuk menampilkan metrik telemetry (misal: RSSI, Jarak, PHY Rate, Ping).
 */
@Composable
fun BlynkMetricTile(
    label: String,
    value: String,
    unit: String = "",
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
