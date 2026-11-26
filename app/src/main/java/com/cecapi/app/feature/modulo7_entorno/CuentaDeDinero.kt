package com.cecapi.app.feature.modulo7_entorno

import com.cecapi.app.core.voice.VoiceText

/** Lo que se puede pedir sobre billetes y monedas. */
enum class ComandoDeDinero {
    VER_BILLETE, SUMAR_BILLETE, VER_MONEDA, SUMAR_MONEDA, TOTAL, BORRAR_BILLETES, BORRAR_MONEDAS;

    companion object {
        /** El comando de dinero que pide [texto], o null. Del más específico al más general. */
        fun de(texto: String): ComandoDeDinero? {
            val t = VoiceText.normalize(texto)
            fun has(vararg frases: String) = VoiceText.hasAny(t, *frases)
            return when {
                has("cuanto llevo", "cuanto dinero llevo", "que llevo", "cuanto suman", "total de billetes", "dime el total") -> TOTAL
                has("borra los billetes", "borrar billetes", "limpia los billetes", "reinicia los billetes", "borrar total") -> BORRAR_BILLETES
                has(
                    "agrega esta moneda", "agregar moneda", "agrega moneda", "suma esta moneda", "sumar moneda",
                    "suma la moneda", "sumar la moneda", "anota esta moneda", "anotar moneda", "anota la moneda",
                    "anotar la moneda", "guarda la moneda", "guardar la moneda", "guarda moneda", "guardar moneda",
                    "cuenta la moneda", "contar la moneda",
                ) -> SUMAR_MONEDA
                has("borra las monedas", "borrar monedas", "limpia las monedas", "reinicia las monedas") -> BORRAR_MONEDAS
                has(
                    "que moneda es", "que moneda", "cuanto vale la moneda", "cuanto es la moneda",
                    "reconoce la moneda", "reconocer la moneda", "moneda", "monedas",
                ) -> VER_MONEDA
                has("agrega este billete", "agregar billete", "agrega billete", "suma este billete", "sumar billete", "anota este billete", "anotar billete") -> SUMAR_BILLETE
                has("billete", "billetes", "dinero", "cuanto vale", "cuanto dinero") -> VER_BILLETE
                else -> null
            }
        }
    }
}

/** La cuenta de los billetes y monedas que se muestran uno por uno. */
class CuentaDeDinero {
    private val billetes = mutableMapOf<DenominacionBillete, Int>()
    private val monedas = mutableMapOf<DenominacionMoneda, Int>()

    /** Suma el billete solo si es uno y se leyó con claridad; devuelve lo que hay que decir. */
    fun sumarBillete(resultado: ResultadoBillete?): String {
        val d = resultado?.denominacion
        if (resultado == null || d == null || resultado.hayVarios) {
            return "No sumé nada. Para llevar la cuenta, muéstrame un solo billete a la vez, plano y con buena luz."
        }
        billetes[d] = (billetes[d] ?: 0) + 1
        return "Anotado, un billete de ${d.nombreHablado}. " + total()
    }

    /** Suma la moneda solo si se reconoció con claridad; devuelve lo que hay que decir. */
    fun sumarMoneda(resultado: ResultadoMoneda?): String {
        val d = resultado?.denominacion
        if (resultado == null || d == null || !resultado.esSegura) {
            return "No sumé nada. Para llevar la cuenta, muéstrame una sola moneda a la vez, sola y con buena luz."
        }
        monedas[d] = (monedas[d] ?: 0) + 1
        return "Anotada, una moneda de ${d.nombreHablado}. " + total()
    }

    fun borrarBilletes(): String {
        billetes.clear()
        return "Listo. Empecé la cuenta de billetes desde cero."
    }

    fun borrarMonedas(): String {
        monedas.clear()
        return "Listo. Empecé la cuenta de monedas desde cero."
    }

    /** Lo que se lleva sumado. Si no hay monedas, queda igual que la cuenta de solo billetes. */
    fun total(): String {
        if (monedas.isEmpty()) return totalDeBilletes()
        val partes = partesDeBilletes() + partesDeMonedas()
        val suma = billetes.entries.sumOf { it.key.valor * it.value } + monedas.entries.sumOf { it.key.valor * it.value }
        return "Llevas ${unir(partes)}. Son $suma pesos en total."
    }

    private fun totalDeBilletes(): String {
        if (billetes.isEmpty()) {
            return "Todavía no has sumado ningún billete ni moneda. Di agrega este billete o agrega esta moneda."
        }
        val suma = billetes.entries.sumOf { it.key.valor * it.value }
        val cuantos = billetes.values.sum()
        return "Llevas ${unir(partesDeBilletes())}. Son $cuantos ${if (cuantos == 1) "billete" else "billetes"}, $suma pesos en total."
    }

    private fun partesDeBilletes() = billetes.entries.sortedByDescending { it.key.valor }.map { (d, n) ->
        if (n == 1) "un billete de ${d.nombreHablado}" else "$n billetes de ${d.nombreHablado}"
    }

    private fun partesDeMonedas() = monedas.entries.sortedByDescending { it.key.valor }.map { (d, n) ->
        if (n == 1) "una moneda de ${d.nombreHablado}" else "$n monedas de ${d.nombreHablado}"
    }

    private fun unir(partes: List<String>) =
        if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " y " + partes.last()
}
