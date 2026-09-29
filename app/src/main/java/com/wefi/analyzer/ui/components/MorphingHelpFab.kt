package com.wefi.analyzer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wefi.analyzer.ui.theme.BlynkBlue
import kotlinx.coroutines.delay

/**
 * Juicy Morphing Floating Help Assistant:
 * - Muncul dan membesar (56dp) saat berpindah tab atau saat ada scroll.
 * - Menggunakan kurva animasi juicy ease-in-ease-out (FastOutSlowInEasing).
 * - Auto-docking mengecil (28dp) ke pinggir layar saat pengguna diam (idle 3 detik).
 * - Mengetuk bulatan membesarkannya kembali dan membuka Contextual Help Drawer.
 */
@Composable
fun MorphingHelpFab(
    tabId: Int,
    isUserScrolling: Boolean = false,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    // Re-expand saat berpindah tab atau ada aktivitas scroll
    LaunchedEffect(tabId, isUserScrolling) {
        isExpanded = true
        delay(3200)
        isExpanded = false
    }

    val fabSize by animateDpAsState(
        targetValue = if (isExpanded) 56.dp else 28.dp,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "fab_size"
    )

    val edgeOffset by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "fab_edge_offset"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 86.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            modifier = Modifier
                .offset(x = edgeOffset)
                .size(fabSize)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .clickable {
                    if (!isExpanded) {
                        isExpanded = true
                    }
                    onHelpClick()
                },
            color = Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isExpanded) {
                    Icon(
                        imageVector = Icons.Rounded.QuestionMark,
                        contentDescription = "Bantuan Kontekstual & Troubleshooting",
                        tint = BlynkBlue,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(BlynkBlue, CircleShape)
                    )
                }
            }
        }
    }
}
