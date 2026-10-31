package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cecapi.app.core.data.AccountEraser
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.util.PasswordHasher
import com.cecapi.app.core.util.PasswordRules
import com.cecapi.app.core.voice.VoiceEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoginResult {
    data class Success(val usuario: UsuarioEntity) : LoginResult
    data object InvalidCredentials : LoginResult
}

sealed interface RegisterResult {
    data class Success(val usuario: UsuarioEntity) : RegisterResult
    data object UsernameTaken : RegisterResult
}

private val Context.sessionStore by preferencesDataStore(name = "remembered_session")

@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usuarioDao: UsuarioDao,
    private val permisosModuloDao: PermisosModuloDao,
    private val configuracionUsuarioDao: ConfiguracionUsuarioDao,
    private val accountEraser: AccountEraser,
    private val voiceEngine: VoiceEngine,
) {
    private val _currentUser = MutableStateFlow<UsuarioEntity?>(null)
    val currentUser: StateFlow<UsuarioEntity?> = _currentUser.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // Abrir la aplicación desde el mismo dispositivo donde se usó antes inicia sesión directamente,
        // sin volver a pedir la contraseña — la pantalla de bloqueo del propio teléfono es el límite de seguridad,
        // el mismo compromiso que hace cualquier aplicación que "mantiene la sesión iniciada". Se borra en el momento en que cierran sesión.
        scope.launch { restoreRememberedSession() }
    }

    private suspend fun restoreRememberedSession() {
        val rememberedId = context.sessionStore.data.first()[KEY_REMEMBERED_USER_ID] ?: return
        val usuario = usuarioDao.findById(rememberedId) ?: return
        _currentUser.value = usuario
    }

    suspend fun login(nombreUsuario: String, contrasena: String): LoginResult {
        val usuario = usuarioDao.findByUsername(nombreUsuario.trim())
            ?: return LoginResult.InvalidCredentials
        if (!passwordMatches(usuario.contrasenaHash, contrasena)) {
            return LoginResult.InvalidCredentials
        }
        completeLogin(usuario)
        return LoginResult.Success(usuario)
    }

    /**
     * Compara la contraseña normalizada (ver [PasswordRules.normalize]). Las cuentas creadas antes de la regla
     * de 15 letras guardaron el hash de lo que se escribió tal cual, así que también se acepta ese hash para
     * que sigan entrando; al cambiar su contraseña quedan con la forma nueva.
     */
    fun passwordMatches(storedHash: String, entered: String): Boolean =
        storedHash == PasswordHasher.hash(PasswordRules.normalize(entered)) ||
            storedHash == PasswordHasher.hash(entered.trim())

    /**
     * Crea una cuenta nueva de CECAPI. Cualquiera puede autorregistrarse e iniciar sesión de inmediato — "por default se
     * les dejará ingresar" — pero [UsuarioEntity.validado] empieza en false, así que aparece como pendiente para
     * quien esté por encima en Gestión (administrador > directivo > educador > alumno, o
     * administrador > usuario) para confirmarlo después. Nada aquí bloquea por eso.
     */
    suspend fun register(
        nombreUsuario: String,
        contrasena: String,
        nombreCompleto: String,
        origen: String = "",
        email: String? = null,
    ): RegisterResult {
        val nombreNormalizado = nombreUsuario.trim().uppercase()
        // La tabla no tiene un índice único en el nombre de usuario, así que la verificación de duplicados debe ser explícita.
        if (usuarioDao.findByUsername(nombreNormalizado) != null) return RegisterResult.UsernameTaken
        val institucion = origen.trim()
        // Todos empiezan como usuarios. Si eligieron una institución, queda la solicitud pendiente; la institución
        // la valida después, y solo entonces pasa a ser su institución.
        val usuario = UsuarioEntity(
            nombreUsuario = nombreNormalizado,
            contrasenaHash = PasswordHasher.hash(PasswordRules.normalize(contrasena)),
            nombreCompleto = nombreCompleto.trim(),
            email = email?.trim()?.ifBlank { null },
            aceptoAdultoEn = System.currentTimeMillis(),
            institucionSolicitada = institucion.ifBlank { null },
            solicitaValidacion = institucion.isNotBlank(),
            revisionCuenta = "nueva",
        )
        val nuevoId = usuarioDao.insert(usuario)
        if (nuevoId <= 0) return RegisterResult.UsernameTaken

        val usuarioCreado = usuario.copy(id = nuevoId)
        completeLogin(usuarioCreado)
        return RegisterResult.Success(usuarioCreado)
    }

    private var logoutNoticePending = false

    fun logout() {
        _currentUser.value = null
        logoutNoticePending = true
        scope.launch { context.sessionStore.edit { it.remove(KEY_REMEMBERED_USER_ID) } }
    }

    /** Borra la cuenta de la persona con sesión iniciada y todos sus datos, y luego cierra sesión. False cuando nadie tiene sesión iniciada. */
    suspend fun deleteCurrentAccount(): Boolean {
        val user = _currentUser.value ?: return false
        accountEraser.erase(user.id)
        logout()
        return true
    }

    sealed interface EditarPerfilResult {
        data object Exito : EditarPerfilResult
        data object ContrasenaActualIncorrecta : EditarPerfilResult
        data object SinSesion : EditarPerfilResult
    }

    /**
     * Cada persona edita su propio nombre y, opcionalmente, su propia contraseña — nunca la de otra persona
     * (Gestión solo cambia un rol, no esto). Confirma primero con la contraseña ACTUAL, igual que al borrar
     * la cuenta, para que un "cambia mi nombre" suelto de otra persona en la habitación no lo haga en silencio.
     */
    suspend fun actualizarPerfil(
        nombreCompleto: String,
        contrasenaActual: String,
        nuevaContrasena: String?,
        apodo: String? = null,
        usarApodoRanking: Boolean = false,
        email: String? = null,
    ): EditarPerfilResult {
        val user = _currentUser.value ?: return EditarPerfilResult.SinSesion
        if (!passwordMatches(user.contrasenaHash, contrasenaActual)) {
            return EditarPerfilResult.ContrasenaActualIncorrecta
        }
        val nombreLimpio = nombreCompleto.trim().ifBlank { user.nombreCompleto }
        usuarioDao.actualizarNombre(user.id, nombreLimpio)
        var nuevoHash = user.contrasenaHash
        if (!nuevaContrasena.isNullOrBlank()) {
            nuevoHash = PasswordHasher.hash(PasswordRules.normalize(nuevaContrasena))
            usuarioDao.actualizarContrasena(user.id, nuevoHash)
        }
        val apodoLimpio = apodo?.trim()?.ifBlank { null }
        usuarioDao.actualizarApodo(user.id, apodoLimpio)
        usuarioDao.actualizarUsarApodoRanking(user.id, usarApodoRanking)
        val correoLimpio = email?.trim()?.ifBlank { null }
        usuarioDao.actualizarEmail(user.id, correoLimpio)
        _currentUser.value = user.copy(
            nombreCompleto = nombreLimpio,
            contrasenaHash = nuevoHash,
            apodo = apodoLimpio,
            usarApodoRanking = usarApodoRanking,
            email = correoLimpio,
        )
        return EditarPerfilResult.Exito
    }

    /** Pide unirse a una institución; mientras no la valide, la persona sigue como usuario. */
    suspend fun pedirInstitucion(institucion: String) {
        val user = _currentUser.value ?: return
        // Un espacio de más no debe crear otra institución: se quitan los de las orillas y se junta lo repetido.
        val limpia = institucion.trim().replace(Regex("""\s+"""), " ")
        if (limpia.isEmpty()) return
        usuarioDao.solicitarInstitucion(user.id, limpia)
        _currentUser.value = user.copy(institucionSolicitada = limpia, solicitaValidacion = true)
    }

    /** Borra la cuenta de otra persona con la autorización de quien puede hacerlo (Gestión). */
    suspend fun borrarCuentaDe(usuarioId: Long) {
        accountEraser.erase(usuarioId)
    }

    /** Un alumno pide cambiar de educador; lo atiende un directivo, un educador o un administrador. */
    suspend fun pedirCambioEducador() {
        val user = _currentUser.value ?: return
        usuarioDao.actualizarSolicitaCambioEducador(user.id, true)
        _currentUser.value = user.copy(solicitaCambioEducador = true)
    }

    /** Lee y borra el aviso pendiente de la cuenta, para decirlo una sola vez al abrir la app. */
    suspend fun consumirAvisoPendiente(): String? {
        val user = _currentUser.value ?: return null
        val aviso = user.avisoPendiente ?: return null
        usuarioDao.actualizarAviso(user.id, null)
        _currentUser.value = user.copy(avisoPendiente = null)
        return aviso
    }

    /** True una sola vez después de cerrar sesión, para que la pantalla de inicio pueda decir "Sesión cerrada" al abrir. */
    fun consumeLogoutNotice(): Boolean = logoutNoticePending.also { logoutNoticePending = false }

    /** Compartido por iniciar sesión y registrarse: configura los valores por defecto para una cuenta nueva y es una
     * operación sin efecto para una que regresa, luego aplica los ajustes de voz guardados y abre la sesión. */
    private suspend fun completeLogin(usuario: UsuarioEntity) {
        ensureDefaultPermisos(usuario.id)
        // La velocidad de voz y los avisos ahora son por dispositivo (DeviceSettings); la fila por usuario se conserva solo para que
        // las cuentas antiguas y el esquema de la base de datos se mantengan intactos.
        ensureDefaultConfiguracion(usuario.id)
        _currentUser.value = usuario
        context.sessionStore.edit { it[KEY_REMEMBERED_USER_ID] = usuario.id }
    }

    /** Primer inicio de sesión de un usuario: otorga acceso a los seis módulos funcionales por defecto. */
    private suspend fun ensureDefaultPermisos(usuarioId: Long) {
        if (permisosModuloDao.countByUser(usuarioId) > 0) return
        val defaults = ModuloCecapi.entries.map { modulo ->
            PermisosModuloEntity(usuarioId = usuarioId, moduloCodigo = modulo.storageCode, habilitado = true)
        }
        permisosModuloDao.insertAll(defaults)
    }

    private suspend fun ensureDefaultConfiguracion(usuarioId: Long): ConfiguracionUsuarioEntity {
        val existente = configuracionUsuarioDao.findByUser(usuarioId)
        if (existente != null) return existente
        val nueva = ConfiguracionUsuarioEntity(usuarioId = usuarioId)
        configuracionUsuarioDao.upsert(nueva)
        return nueva
    }

    private companion object {
        val KEY_REMEMBERED_USER_ID = longPreferencesKey("remembered_user_id")
    }
}
