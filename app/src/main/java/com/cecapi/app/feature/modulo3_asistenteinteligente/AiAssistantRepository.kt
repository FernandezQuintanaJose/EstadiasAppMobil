package com.cecapi.app.feature.modulo3_asistenteinteligente

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiAssistantRepository @Inject constructor(
    private val api: AiAssistantApi,
    private val consultaIaDao: ConsultaIaDao,
    private val respuestaIaDao: RespuestaIaDao,
    private val contextoDao: ContextoConversacionDao,
) {
    fun observeRecent(usuarioId: Long): Flow<List<ConsultaIaEntity>> = consultaIaDao.observeRecent(usuarioId)

    /** Empieza un chat nuevo: lo siguiente que se pregunte no arrastra lo de la conversación anterior. */
    suspend fun nuevaConversacion(usuarioId: Long) = contextoDao.borrarDeUsuario(usuarioId)

    /** [guardarComo] es lo que queda en el historial; por omisión, la propia pregunta (puede llevar instrucciones extra que no se guardan). */
    suspend fun ask(usuarioId: Long, pregunta: String, guardarComo: String = pregunta): Result<String> {
        val contexto = contextoDao.findByUser(usuarioId)
        val historial = contexto?.resumenContexto?.let { listOf(it) } ?: emptyList()

        val resultado = api.ask(pregunta, historial)

        val consultaId = consultaIaDao.insert(ConsultaIaEntity(usuarioId = usuarioId, textoPregunta = guardarComo))
        resultado.onSuccess { respuesta ->
            respuestaIaDao.insert(RespuestaIaEntity(consultaId = consultaId, textoRespuesta = respuesta))
            val nuevoResumen = "P: $guardarComo / R: $respuesta"
            if (contexto == null) {
                contextoDao.insert(ContextoConversacionEntity(usuarioId = usuarioId, resumenContexto = nuevoResumen))
            } else {
                contextoDao.updateResumen(contexto.id, nuevoResumen, System.currentTimeMillis())
            }
        }
        return resultado
    }

    /**
     * Pide un documento (pantalla Consultas). Se guarda en las consultas anteriores con lo que pidió la persona,
     * pero no entra al contexto de la conversación: un documento largo no es parte de la plática con el asistente.
     */
    suspend fun documento(usuarioId: Long, pedido: String): Result<String> {
        val resultado = api.document(pedido)
        val consultaId = consultaIaDao.insert(ConsultaIaEntity(usuarioId = usuarioId, textoPregunta = pedido))
        resultado.onSuccess { texto ->
            respuestaIaDao.insert(RespuestaIaEntity(consultaId = consultaId, textoRespuesta = texto))
        }
        return resultado
    }
}
