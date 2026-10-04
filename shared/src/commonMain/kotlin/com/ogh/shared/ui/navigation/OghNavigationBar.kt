package com.ogh.shared.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ogh.shared.ui.theme.OghColors

/**
 * Primary navigation tabs for the Ogh application.
 */
enum class NavTab(val title: String, val screen: Screen) {
    STREAM("Stream", Screen.PREVIEW),
    SETTINGS("Settings", Screen.SETTINGS),
    ABOUT("About", Screen.ABOUT),
}

/**
 * Clear bottom navigation bar providing direct access to Stream, Settings, and About.
 */
@Composable
fun OghNavigationBar(
    currentScreen: Screen,
    onTabSelected: (NavTab) -> Unit,
    canNavigateToStream: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeTab = when (currentScreen) {
        Screen.PREVIEW -> NavTab.STREAM
        Screen.SETTINGS, Screen.DESTINATIONS, Screen.ACCOUNTS,
        Screen.ADD_DESTINATION, Screen.EDIT_DESTINATION -> NavTab.SETTINGS
        Screen.ABOUT -> NavTab.ABOUT
    }

    NavigationBar(
        modifier = modifier,
        containerColor = OghColors.SurfaceDark,
        contentColor = OghColors.OnSurface,
        tonalElevation = 0.dp,
    ) {
        NavTab.entries.forEach { tab ->
            val isSelected = activeTab == tab
            val isEnabled = if (tab == NavTab.STREAM) canNavigateToStream else true

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                enabled = isEnabled,
                label = { Text(tab.title) },
                icon = {
                    NavTabIcon(
                        tab = tab,
                        isSelected = isSelected,
                        color = if (isSelected) Color.White else OghColors.OnSurfaceMuted,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = OghColors.VioletLight,
                    indicatorColor = OghColors.VioletDark,
                    unselectedIconColor = OghColors.OnSurfaceMuted,
                    unselectedTextColor = OghColors.OnSurfaceMuted,
                    disabledIconColor = OghColors.OnSurfaceMuted.copy(alpha = 0.38f),
                    disabledTextColor = OghColors.OnSurfaceMuted.copy(alpha = 0.38f),
                ),
            )
        }
    }
}

@Composable
private fun NavTabIcon(
    tab: NavTab,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(24.dp)
            .semantics { contentDescription = tab.title },
    ) {
        when (tab) {
            NavTab.STREAM -> drawStreamIcon(color, isSelected)
            NavTab.SETTINGS -> drawSettingsIcon(color)
            NavTab.ABOUT -> drawAboutIcon(color)
        }
    }
}

private fun DrawScope.drawStreamIcon(color: Color, isSelected: Boolean) {
    val scale = size.width / 24f
    val strokeWidth = 2f * scale

    drawRoundRect(
        color = color,
        topLeft = Offset(2f * scale, 5.5f * scale),
        size = Size(12f * scale, 13f * scale),
        cornerRadius = CornerRadius(2.5f * scale, 2.5f * scale),
        style = if (isSelected) Fill else Stroke(width = strokeWidth),
    )

    val lensPath = Path().apply {
        moveTo(15f * scale, 9f * scale)
        lineTo(21f * scale, 5.5f * scale)
        lineTo(21f * scale, 18.5f * scale)
        lineTo(15f * scale, 15f * scale)
        close()
    }
    drawPath(
        path = lensPath,
        color = color,
        style = if (isSelected) Fill else Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawSettingsIcon(color: Color) {
    val scale = size.width / 24f
    val strokeWidth = 2f * scale
    val knobRadius = 2.5f * scale

    drawLine(
        color = color,
        start = Offset(3f * scale, 6f * scale),
        end = Offset(21f * scale, 6f * scale),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawCircle(color, radius = knobRadius, center = Offset(8f * scale, 6f * scale))

    drawLine(
        color = color,
        start = Offset(3f * scale, 12f * scale),
        end = Offset(21f * scale, 12f * scale),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawCircle(color, radius = knobRadius, center = Offset(16f * scale, 12f * scale))

    drawLine(
        color = color,
        start = Offset(3f * scale, 18f * scale),
        end = Offset(21f * scale, 18f * scale),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawCircle(color, radius = knobRadius, center = Offset(11f * scale, 18f * scale))
}

private fun DrawScope.drawAboutIcon(color: Color) {
    val scale = size.width / 24f
    val strokeWidth = 2f * scale
    val centerOffset = Offset(12f * scale, 12f * scale)
    val radius = 9.5f * scale

    drawCircle(
        color = color,
        radius = radius,
        center = centerOffset,
        style = Stroke(width = strokeWidth),
    )

    drawCircle(
        color = color,
        radius = 1.3f * scale,
        center = Offset(12f * scale, 7.5f * scale),
    )

    drawLine(
        color = color,
        start = Offset(12f * scale, 10.5f * scale),
        end = Offset(12f * scale, 16.5f * scale),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}
