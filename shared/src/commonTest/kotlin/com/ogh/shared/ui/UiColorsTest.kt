package com.ogh.shared.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class UiColorsTest {
    @Test
    fun parsesRgbAndArgbValues() {
        assertEquals(Color(0xFFFF0000.toInt()), colorFromHex("#FF0000", Color.Black))
        assertEquals(Color(0x809146FF.toInt()), colorFromHex("809146FF", Color.Black))
    }

    @Test
    fun malformedValuesUseFallback() {
        val fallback = Color(0xFFD946EF.toInt())

        assertEquals(fallback, colorFromHex("not-a-color", fallback))
        assertEquals(fallback, colorFromHex("#FFF", fallback))
    }
}
