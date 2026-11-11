package com.example.anonymouspager.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QrCodeImage(payload: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(payload) { createQrBitmap(payload) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = modifier
    )
}

private fun createQrBitmap(payload: String): Bitmap {
    val size = 512
    val bits = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) {
            for (y in 0 until size) {
                setPixel(x, y, if (bits[x, y]) 0xFF1E2A12.toInt() else 0xFFB6E36B.toInt())
            }
        }
    }
}
