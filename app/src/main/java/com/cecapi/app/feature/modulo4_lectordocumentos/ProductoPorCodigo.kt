package com.cecapi.app.feature.modulo4_lectordocumentos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Busca un producto por su código de barras en Open Food Facts (base abierta, gratuita, licencia ODbL).
 * Solo se envía el número del código: nada de la foto ni de la persona. Si no hay internet o el producto no
 * está en la base, devuelve null y la app dice solo el número.
 */
object ProductoPorCodigo {
    private const val USER_AGENT = "VozAcceso/1.0 (app de accesibilidad; dragonsteam.3141@gmail.com)"

    suspend fun buscar(codigo: String): String? = withContext(Dispatchers.IO) {
        if (!codigo.all { it.isDigit() } || codigo.length !in 8..14) return@withContext null
        val conexion = (URL("https://world.openfoodfacts.org/api/v2/product/$codigo.json?fields=product_name,brands,quantity")
            .openConnection() as HttpURLConnection)
        try {
            conexion.connectTimeout = 5000
            conexion.readTimeout = 5000
            conexion.setRequestProperty("User-Agent", USER_AGENT)
            if (conexion.responseCode != 200) return@withContext null
            val json = JSONObject(conexion.inputStream.bufferedReader().use { it.readText() })
            if (json.optInt("status") != 1) return@withContext null
            val producto = json.optJSONObject("product") ?: return@withContext null
            frase(producto.optString("product_name"), producto.optString("brands"), producto.optString("quantity"))
        } catch (e: Exception) {
            null
        } finally {
            conexion.disconnect()
        }
    }

    /** "Chocolate, de la marca Abuelita, 90 gramos." o null si la base no trae ni nombre ni marca. */
    internal fun frase(nombre: String, marcas: String, cantidad: String): String? {
        val producto = nombre.trim()
        val marca = marcas.split(',').firstOrNull()?.trim().orEmpty()
        if (producto.isEmpty() && marca.isEmpty()) return null
        return buildString {
            append(if (producto.isNotEmpty()) producto else "Producto")
            if (marca.isNotEmpty() && !producto.contains(marca, ignoreCase = true)) append(", de la marca ").append(marca)
            if (cantidad.isNotBlank()) append(", ").append(cantidad.trim())
            append(".")
        }
    }
}
