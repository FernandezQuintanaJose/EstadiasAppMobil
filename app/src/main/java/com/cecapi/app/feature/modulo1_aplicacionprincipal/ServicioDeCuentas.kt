package com.cecapi.app.feature.modulo1_aplicacionprincipal

import kotlinx.coroutines.flow.StateFlow

/**
 * Lo que el resto de la app necesita de las cuentas, sin saber dónde viven. Hoy las guarda el teléfono
 * ([CuentasLocales]); cuando el servidor esté publicado habrá otra implementación que llame a sus rutas
 * (docs/cuentas-servicio.md) y las pantallas no cambian.
 */
interface ServicioDeCuentas {
    sealed interface EditarPerfilResult {
        data object Exito : EditarPerfilResult
        data object ContrasenaActualIncorrecta : EditarPerfilResult
        data object SinSesion : EditarPerfilResult
    }

    /** Quien tiene la sesión abierta, o null. */
    val currentUser: StateFlow<UsuarioEntity?>

    suspend fun login(nombreUsuario: String, contrasena: String): LoginResult

    suspend fun register(
        nombreUsuario: String,
        contrasena: String,
        nombreCompleto: String,
        origen: String = "",
        email: String? = null,
    ): RegisterResult

    fun logout()

    /** Borra la cuenta de quien tiene sesión y todos sus datos; false si no hay sesión. */
    suspend fun deleteCurrentAccount(): Boolean

    suspend fun actualizarPerfil(
        nombreCompleto: String,
        contrasenaActual: String,
        nuevaContrasena: String?,
        apodo: String? = null,
        usarApodoRanking: Boolean = false,
        email: String? = null,
    ): EditarPerfilResult

    /** ¿Es [entered] la contraseña de la cuenta con ese [storedHash]? */
    suspend fun passwordMatches(storedHash: String, entered: String): Boolean

    suspend fun pedirInstitucion(institucion: String)

    suspend fun borrarCuentaDe(usuarioId: Long)

    suspend fun pedirCambioEducador()

    suspend fun consumirAvisoPendiente(): String?

    fun consumeLogoutNotice(): Boolean
}
