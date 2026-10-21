package de.stiller.gt7.ui

import androidx.compose.ui.graphics.ImageBitmap

/** [image] as PNG file content; each platform encodes with its own image library. */
expect fun encodePng(image: ImageBitmap): ByteArray
