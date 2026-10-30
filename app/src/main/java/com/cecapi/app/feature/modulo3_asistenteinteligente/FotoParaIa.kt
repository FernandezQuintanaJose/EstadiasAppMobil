package com.cecapi.app.feature.modulo3_asistenteinteligente

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Prepara una foto del teléfono para mandarla a `/ver`: la endereza, la reduce y la pasa a JPEG.
 *
 * El servidor también la endereza y la reduce, pero mandar la foto completa de la cámara (varios MB en
 * base64) gasta datos y tarda. Al volver a comprimirla se pierde el EXIF, así que el giro se aplica aquí,
 * y de paso no salen del teléfono el GPS ni el modelo.
 */
object FotoParaIa {
    /** Igual que IMAGEN_LADO_MAX del servidor: más grande no mejora lo que la IA reconoce. */
    private const val LADO_MAXIMO = 1280
    private const val CALIDAD_JPEG = 85

    /** El JPEG listo, o null si el archivo no es una imagen que se pueda abrir. */
    fun desdeArchivo(ruta: String): ByteArray? {
        val bordes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, bordes)
        if (bordes.outWidth <= 0 || bordes.outHeight <= 0) return null

        // Primero se reduce al cargar (potencias de 2, barato en memoria) y luego al tamaño exacto.
        var muestreo = 1
        while (maxOf(bordes.outWidth, bordes.outHeight) / (muestreo * 2) >= LADO_MAXIMO) muestreo *= 2
        val cargada = BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = muestreo })
            ?: return null

        val escala = minOf(1f, LADO_MAXIMO.toFloat() / maxOf(cargada.width, cargada.height))
        val matriz = Matrix().apply {
            postScale(escala, escala)
            postRotate(gradosExif(ruta))
        }
        val lista = Bitmap.createBitmap(cargada, 0, 0, cargada.width, cargada.height, matriz, true)
        if (lista !== cargada) cargada.recycle()

        return ByteArrayOutputStream().use { salida ->
            lista.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)
            lista.recycle()
            salida.toByteArray()
        }
    }

    private fun gradosExif(ruta: String): Float = try {
        when (ExifInterface(ruta).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
}
