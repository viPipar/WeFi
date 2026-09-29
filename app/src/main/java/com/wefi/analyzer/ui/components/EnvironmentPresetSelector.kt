package com.wefi.analyzer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wefi.analyzer.domain.model.EnvironmentPreset

/**
 * Kontrol selektor preset redaman lingkungan untuk kalibrasi jarak Log-Distance Path Loss.
 */
@Composable
fun EnvironmentPresetSelector(
    selectedPreset: EnvironmentPreset,
    onPresetSelected: (EnvironmentPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = EnvironmentPreset.entries
    val labels = presets.map { "${it.label} (n=${it.exponent})" }
    val selectedIndex = presets.indexOf(selectedPreset).coerceAtLeast(0)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "KALIBRASI REDAMAN LINGKUNGAN (PATH LOSS n)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        BlynkSegmentedControl(
            items = labels,
            selectedIndex = selectedIndex,
            onItemSelected = { onPresetSelected(presets[it]) }
        )
    }
}
