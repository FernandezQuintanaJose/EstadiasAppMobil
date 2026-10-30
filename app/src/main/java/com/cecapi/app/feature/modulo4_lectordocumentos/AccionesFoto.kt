package com.cecapi.app.feature.modulo4_lectordocumentos

import android.content.Context
import android.net.Uri
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado de traducir: el texto en español, o por qué no se pudo. */
sealed interface Traduccion {
    data class Lista(val idioma: String, val texto: String) : Traduccion
    data object YaEnEspanol : Traduccion
    data object IdiomaDesconocido : Traduccion
    data class Fallo(val motivo: String) : Traduccion
}

/**
 * Las acciones extra sobre la misma foto del panel de la cámara: leer códigos QR y traducir el texto al
 * español. Todo corre en el teléfono con ML Kit; el traductor solo descarga el idioma la primera vez.
 */
@Singleton
class AccionesFoto @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Los códigos QR o de barras de la foto, ya en palabras. Vacía si no hay ninguno. */
    suspend fun leerCodigos(imagen: Uri): List<String> {
        val escaner = BarcodeScanning.getClient()
        return try {
            escaner.process(InputImage.fromFilePath(context, imagen)).await().mapNotNull(::describir)
        } finally {
            escaner.close()
        }
    }

    private fun describir(codigo: Barcode): String? {
        val valor = codigo.displayValue?.takeIf { it.isNotBlank() } ?: return null
        return when (codigo.valueType) {
            Barcode.TYPE_URL -> "Un enlace a internet: $valor"
            Barcode.TYPE_PHONE -> "Un número de teléfono: $valor"
            Barcode.TYPE_EMAIL -> "Un correo: $valor"
            Barcode.TYPE_WIFI -> "Datos de una red wifi llamada ${codigo.wifi?.ssid ?: "sin nombre"}"
            Barcode.TYPE_PRODUCT, Barcode.TYPE_ISBN -> "Un código de producto: $valor"
            else -> valor
        }
    }

    /** Traduce [texto] al español. Detecta el idioma; si ya es español, no hace nada. */
    suspend fun traducirAlEspanol(texto: String): Traduccion {
        val identificador = LanguageIdentification.getClient()
        val codigo = try {
            identificador.identifyLanguage(texto).await()
        } catch (e: Exception) {
            return Traduccion.Fallo("No pude saber en qué idioma está.")
        } finally {
            identificador.close()
        }
        if (codigo == "und") return Traduccion.IdiomaDesconocido
        if (codigo == "es") return Traduccion.YaEnEspanol
        val origen = TranslateLanguage.fromLanguageTag(codigo) ?: return Traduccion.IdiomaDesconocido
        val traductor = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(origen).setTargetLanguage(TranslateLanguage.SPANISH).build(),
        )
        return try {
            // La primera vez se descarga el idioma (unos 30 MB); después funciona sin internet.
            traductor.downloadModelIfNeeded().await()
            Traduccion.Lista(nombreDelIdioma(codigo), traductor.translate(texto).await())
        } catch (e: Exception) {
            Traduccion.Fallo("No pude descargar el traductor. La primera vez necesita internet.")
        } finally {
            traductor.close()
        }
    }

    private fun nombreDelIdioma(codigo: String): String =
        java.util.Locale(codigo).getDisplayLanguage(java.util.Locale("es", "MX")).ifBlank { codigo }
}
