package com.ogh.shared.ui

import androidx.compose.ui.graphics.Color

internal fun colorFromHex(value: String, fallback: Color): Color = runCatching {
    val digits = value.removePrefix("#")
    require(digits.length == 6 || digits.length == 8)
    val packed = digits.toLong(16).toInt()
    Color(if (digits.length == 6) packed or 0xFF000000.toInt() else packed)
}.getOrDefault(fallback)
