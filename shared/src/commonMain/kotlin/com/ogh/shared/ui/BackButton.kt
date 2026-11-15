package com.ogh.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp

/** Top-bar back action announced as a "Back" button with a 48dp touch target. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = "←",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = BACK_LABEL
                role = Role.Button
                onClick(label = BACK_LABEL) {
                    onClick()
                    true
                }
            }
            .wrapContentSize(Alignment.Center),
    )
}

private const val BACK_LABEL = "Back"
