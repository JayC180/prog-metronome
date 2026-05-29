package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType

@Composable
fun BuildErrorBanner(
    errors:    List<String>,
    onDismiss: () -> Unit,
    modifier:  Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RhythmColors.dangerBg)
            .border(
                width = 0.5.dp,
                color = RhythmColors.dangerBorder,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                "Can't play — fix these first:",
                style = RhythmType.label.copy(
                    fontSize = 11.sp,
                    color    = RhythmColors.danger,
                ),
            )
            Text(
                "✕",
                style = RhythmType.label.copy(
                    fontSize = 13.sp,
                    color    = RhythmColors.danger,
                ),
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
        errors.forEach { error ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.Top,
            ) {
                Text("•", style = RhythmType.label.copy(
                    fontSize = 11.sp, color = RhythmColors.danger.copy(alpha=0.7f)))
                Text(error, style = RhythmType.label.copy(
                    fontSize = 11.sp, color = RhythmColors.danger.copy(alpha=0.85f)))
            }
        }
    }
}
