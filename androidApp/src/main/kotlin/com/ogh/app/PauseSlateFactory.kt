package com.ogh.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import kotlin.math.max

/** Renders a bounded, encoder-sized pause slate from the logo or a persisted document URI. */
class PauseSlateFactory(private val context: Context) {

    fun create(uri: Uri?, width: Int, height: Int): Bitmap {
        require(width > 0 && height > 0)
        val output = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(BACKGROUND_COLOR)

        val customImage = uri?.let { decodeSampled(it, width, height) }
        if (customImage != null) {
            drawCentered(canvas, customImage, width, height)
            customImage.recycle()
        } else {
            drawLogo(canvas, width, height)
        }
        return output
    }

    private fun decodeSampled(uri: Uri, width: Int, height: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > width * 2 ||
            bounds.outHeight / sampleSize > height * 2
        ) {
            sampleSize *= 2
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = max(1, sampleSize) },
            )
        }
    }.getOrNull()

    private fun drawCentered(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int) {
        val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val targetWidth = (bitmap.width * scale).toInt()
        val targetHeight = (bitmap.height * scale).toInt()
        val left = (width - targetWidth) / 2
        val top = (height - targetHeight) / 2
        canvas.drawBitmap(
            bitmap,
            null,
            Rect(left, top, left + targetWidth, top + targetHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }

    private fun drawLogo(canvas: Canvas, width: Int, height: Int) {
        val logo = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground) ?: return
        val size = (minOf(width, height) * 0.42f).toInt()
        val left = (width - size) / 2
        val top = (height - size) / 2
        logo.setBounds(left, top, left + size, top + size)
        logo.draw(canvas)
    }

    private companion object {
        val BACKGROUND_COLOR: Int = Color.rgb(10, 8, 18)
    }
}
