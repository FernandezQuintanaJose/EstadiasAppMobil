"""Cuentas propias con código por correo (decisión 0020): el contrato del README de PaginaAsistenteCECAPI.

Rutas (todas bajo /cuenta, con el límite por minuto por IP):
  POST   /registro       { usuario, correo, contrasena, mayorDeEdad, version } -> { requiereCodigo }
  POST   /verificar      { correo, codigo }                                    -> { token, usuario }
  POST   /acceso         { identificador, contrasena }                         -> { token, usuario }
  POST   /recuperar      { correo }                                            -> {} (siempre igual)
  POST   /restablecer    { correo, codigo, contrasena }                        -> {}
  POST   /aceptacion     { version, fecha, mayorDeEdad } + Bearer              -> {}
  DELETE /borrarChats    Bearer                                                -> { borradas }
  DELETE /eliminarCuenta { contrasena } + Bearer                               -> {}
  POST   /salir          Bearer                                                -> {}

Reglas (las mismas que la app y la web):
- Usuario: 3 a 30 caracteres, letras sin acento, números, punto, guion y guion bajo. Único sin importar mayúsculas.
- Correo: forma válida. Único sin importar mayúsculas. Se confirma con un código de 6 dígitos.
- Contraseña: al menos 15 caracteres después de normalizarla igual que el teléfono (minúsculas, sin acentos salvo
  la ñ y sin espacios), para que una frase dictada o escrita llegue igual. Se guarda con Argon2id.
- Mayor de edad: obligatorio; se guarda cuándo lo declaró.

Lo que no se guarda nunca: el token (solo su SHA-256) ni el código (solo su SHA-256 con sal).
"""

import hashlib
import hmac
import logging
import os
import re
import secrets
import unicodedata
from datetime import timedelta

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import delete, func, or_, select, update

import contrasenas
import correo as correo_mod
import db

log = logging.getLogger("cecapi.cuentas")

router = APIRouter(prefix="/cuenta", tags=["Cuentas"])

USUARIO = re.compile(r"^[A-Za-z0-9._-]{3,30}$")
CORREO = re.compile(r"^[^\s@]{1,64}@[^\s@]+\.[^\s@]{2,}$")
CONTRASENA_MINIMA = 15

MSJ_USUARIO = "El nombre de usuario debe tener entre 3 y 30 caracteres: letras sin acento, números, punto, guion o guion bajo."
MSJ_CORREO = "Escribe un correo válido, por ejemplo nombre@correo.com."
MSJ_CONTRASENA = "La contraseña debe tener al menos 15 caracteres. Puede ser una frase; los espacios y los acentos no cuentan."
MSJ_MAYOR = "Para crear una cuenta debes tener 18 años o más."
MSJ_OCUPADO = "Ese usuario o ese correo ya tienen una cuenta. Inicia sesión o recupera tu contraseña."
MSJ_CODIGO = "El código no es correcto o ya venció. Pide uno nuevo."
MSJ_CREDENCIALES = "Usuario, correo o contraseña incorrectos."
MSJ_SIN_VERIFICAR = "Falta confirmar tu correo con el código que te enviamos."
MSJ_SESION = "Tu sesión venció. Vuelve a iniciar sesión."
MSJ_CONTRASENA_ACTUAL = "La contraseña no es correcta."


def _entero(nombre: str, defecto: int, minimo: int = 1) -> int:
    try:
        return max(int(os.getenv(nombre, str(defecto))), minimo)
    except ValueError:
        return defecto


def verificacion_por_correo() -> bool:
    """VERIFICACION_POR_CORREO=0 crea la cuenta sin código (solo si no hay forma de mandar correos)."""
    return os.getenv("VERIFICACION_POR_CORREO", "1").strip() != "0"


def sesion_dias() -> int:
    return _entero("SESION_DIAS", 30)


def codigo_minutos() -> int:
    return _entero("CODIGO_MINUTOS", 15)


def codigo_intentos() -> int:
    return _entero("CODIGO_INTENTOS", 5)


def codigo_espera_s() -> int:
    return _entero("CODIGO_ESPERA_S", 60, minimo=0)


def acceso_fallos() -> int:
    return _entero("ACCESO_FALLOS", 5)


def acceso_bloqueo_min() -> int:
    return _entero("ACCESO_BLOQUEO_MIN", 15)


def cupo_diario() -> int:
    return _entero("CUPO_DIARIO_POR_CUENTA", 25)


# ---------------------------------------------------------------- Reglas
def normalizar_contrasena(texto: str) -> str:
    """Igual que PasswordRules.normalize de la app: minúsculas, sin acentos ni diéresis (la ñ se queda), sin espacios."""
    protegido = texto.lower().replace("ñ", "")
    sin_marcas = "".join(c for c in unicodedata.normalize("NFD", protegido) if unicodedata.category(c) != "Mn")
    return re.sub(r"\s+", "", unicodedata.normalize("NFC", sin_marcas).replace("", "ñ"))


def _revisar_usuario(usuario: str) -> str:
    usuario = usuario.strip()
    if not USUARIO.match(usuario):
        raise HTTPException(status_code=400, detail=MSJ_USUARIO)
    return usuario


def _revisar_correo(correo: str) -> str:
    correo = correo.strip()
    if len(correo) > 254 or not CORREO.match(correo) or ".." in correo:
        raise HTTPException(status_code=400, detail=MSJ_CORREO)
    return correo


def _revisar_contrasena(contrasena: str) -> str:
    normalizada = normalizar_contrasena(contrasena)
    if len(normalizada) < CONTRASENA_MINIMA:
        raise HTTPException(status_code=400, detail=MSJ_CONTRASENA)
    return normalizada


# ---------------------------------------------------------------- Tokens y códigos
def _sha256(texto: str) -> str:
    return hashlib.sha256(texto.encode()).hexdigest()


def _crear_sesion(con, cuenta_id: int) -> str:
    token = secrets.token_urlsafe(32)
    momento = db.ahora()
    con.execute(db.sesiones.insert().values(
        token_hash=_sha256(token), cuenta_id=cuenta_id, creada_en=momento,
        expira_en=momento + timedelta(days=sesion_dias()),
    ))
    return token


def cuenta_de_token(token: str | None) -> int | None:
    """La cuenta dueña de [token], o None si no existe o ya venció."""
    if not token:
        return None
    with db.leer() as con:
        fila = con.execute(
            select(db.sesiones.c.cuenta_id, db.sesiones.c.expira_en)
            .where(db.sesiones.c.token_hash == _sha256(token))
        ).first()
    if fila is None or db.como_utc(fila.expira_en) <= db.ahora():
        return None
    return fila.cuenta_id


def token_de_encabezado(authorization: str | None) -> str | None:
    """'Bearer abc' -> 'abc'. Cualquier otra forma -> None."""
    if not authorization:
        return None
    tipo, _, valor = authorization.strip().partition(" ")
    return valor.strip() or None if tipo.lower() == "bearer" else None


def _nuevo_codigo(con, cuenta_id: int, proposito: str) -> str | None:
    """Crea un código nuevo (borra el anterior). None si se pidió otro hace menos de CODIGO_ESPERA_S segundos."""
    momento = db.ahora()
    anterior = con.execute(
        select(db.codigos.c.creado_en)
        .where(db.codigos.c.cuenta_id == cuenta_id, db.codigos.c.proposito == proposito)
        .order_by(db.codigos.c.creado_en.desc())
    ).first()
    if anterior is not None and db.como_utc(anterior.creado_en) > momento - timedelta(seconds=codigo_espera_s()):
        return None
    con.execute(delete(db.codigos).where(db.codigos.c.cuenta_id == cuenta_id, db.codigos.c.proposito == proposito))
    codigo = f"{secrets.randbelow(10 ** 6):06d}"
    sal = secrets.token_hex(16)
    con.execute(db.codigos.insert().values(
        cuenta_id=cuenta_id, proposito=proposito, codigo_hash=_sha256(sal + codigo), sal=sal,
        creado_en=momento, expira_en=momento + timedelta(minutes=codigo_minutos()), intentos=0,
    ))
    return codigo


def _codigo_correcto(con, cuenta_id: int, proposito: str, codigo: str) -> bool:
    """Revisa el código y cuenta el intento. Uno correcto se borra para que no sirva dos veces."""
    fila = con.execute(
        select(db.codigos).where(db.codigos.c.cuenta_id == cuenta_id, db.codigos.c.proposito == proposito)
    ).first()
    if fila is None or db.como_utc(fila.expira_en) <= db.ahora() or fila.intentos >= codigo_intentos():
        return False
    limpio = re.sub(r"\s+", "", codigo or "")
    if hmac.compare_digest(_sha256(fila.sal + limpio), fila.codigo_hash):
        con.execute(delete(db.codigos).where(db.codigos.c.id == fila.id))
        return True
    con.execute(update(db.codigos).where(db.codigos.c.id == fila.id).values(intentos=fila.intentos + 1))
    return False


def _enviar(correo: str, codigo: str | None, proposito: str) -> None:
    if codigo is not None:
        correo_mod.enviar_codigo(correo, codigo, proposito, codigo_minutos())


# ---------------------------------------------------------------- Cupo diario por cuenta
def consumir_cupo(cuenta_id: int) -> bool:
    """Suma una consulta de hoy a la cuenta. False si ya llegó a CUPO_DIARIO_POR_CUENTA (no se suma)."""
    fecha = db.hoy().isoformat()
    with db.transaccion() as con:
        fila = con.execute(
            select(db.uso_cuentas.c.peticiones)
            .where(db.uso_cuentas.c.cuenta_id == cuenta_id, db.uso_cuentas.c.fecha == fecha)
        ).first()
        if fila is None:
            con.execute(db.uso_cuentas.insert().values(cuenta_id=cuenta_id, fecha=fecha, peticiones=1))
            return True
        if fila.peticiones >= cupo_diario():
            return False
        con.execute(
            update(db.uso_cuentas)
            .where(db.uso_cuentas.c.cuenta_id == cuenta_id, db.uso_cuentas.c.fecha == fecha)
            .values(peticiones=db.uso_cuentas.c.peticiones + 1)
        )
        return True


# ---------------------------------------------------------------- Respuestas y errores (para /docs)
class Error(BaseModel):
    detail: str = Field(description="Mensaje que la página o la app pueden decir en voz alta.")


def _error(descripcion: str, *ejemplos: str) -> dict:
    return {"model": Error, "description": descripcion, "content": {"application/json": {"examples": {
        f"ejemplo{i}": {"summary": t, "value": {"detail": t}} for i, t in enumerate(ejemplos, 1)}}}}


ERRORES = {
    400: _error("No cumple una regla, o el código no es correcto o venció.", MSJ_CONTRASENA, MSJ_CODIGO),
    401: _error("Credenciales incorrectas, o el token no existe o venció.", MSJ_CREDENCIALES, MSJ_SESION),
    429: _error("Límite por minuto de la IP, o demasiados intentos fallidos de la cuenta.",
                "Hiciste muchas preguntas seguidas. Espera un minuto e intenta de nuevo."),
}


class RespuestaRegistro(BaseModel):
    requiereCodigo: bool = Field(description="true: se mandó un código al correo y hay que usar /cuenta/verificar.")


class Sesion(BaseModel):
    token: str = Field(description="Para `Authorization: Bearer`. Guardarlo solo en memoria (web) o cifrado (app).")
    usuario: str


class Vacia(BaseModel):
    pass


class Borradas(BaseModel):
    borradas: int = Field(description="Cuántas conversaciones se borraron.")


# ---------------------------------------------------------------- Cuerpos
class Registro(BaseModel):
    usuario: str = Field(max_length=100)
    correo: str = Field(max_length=300)
    contrasena: str = Field(max_length=300)
    mayorDeEdad: bool = False
    version: str | None = Field(default=None, max_length=20)


class Verificar(BaseModel):
    correo: str = Field(max_length=300)
    codigo: str = Field(max_length=20)


class Acceso(BaseModel):
    identificador: str = Field(max_length=300)
    contrasena: str = Field(max_length=300)


class Recuperar(BaseModel):
    correo: str = Field(max_length=300)


class Restablecer(BaseModel):
    correo: str = Field(max_length=300)
    codigo: str = Field(max_length=20)
    contrasena: str = Field(max_length=300)


class Aceptacion(BaseModel):
    version: str = Field(max_length=20)
    fecha: str | None = Field(default=None, max_length=40)
    mayorDeEdad: bool = False


class ConContrasena(BaseModel):
    contrasena: str = Field(max_length=300)


def cuenta_actual(authorization: str | None = Header(default=None)) -> int:
    """Dependencia de las rutas con sesión: la cuenta del token, o 401."""
    cuenta = cuenta_de_token(token_de_encabezado(authorization))
    if cuenta is None:
        raise HTTPException(status_code=401, detail=MSJ_SESION)
    return cuenta


def _por_correo(con, correo: str):
    return con.execute(select(db.cuentas).where(db.cuentas.c.correo_clave == correo.strip().lower())).first()


# ---------------------------------------------------------------- Rutas
@router.post("/registro", summary="Crear una cuenta", response_model=RespuestaRegistro,
             responses={**ERRORES, 409: _error("El usuario o el correo ya tienen una cuenta confirmada.", MSJ_OCUPADO)})
def registro(body: Registro):
    """Crea la cuenta y manda un código de 6 dígitos al correo. Exige `mayorDeEdad: true`."""
    usuario = _revisar_usuario(body.usuario)
    correo = _revisar_correo(body.correo)
    normalizada = _revisar_contrasena(body.contrasena)
    if body.mayorDeEdad is not True:
        raise HTTPException(status_code=400, detail=MSJ_MAYOR)
    verificar = verificacion_por_correo()
    with db.transaccion() as con:
        choques = con.execute(select(db.cuentas.c.id, db.cuentas.c.verificada, db.cuentas.c.correo_clave).where(or_(
            db.cuentas.c.usuario_clave == usuario.lower(), db.cuentas.c.correo_clave == correo.lower(),
        ))).all()
        if any(c.verificada for c in choques):
            raise HTTPException(status_code=409, detail=MSJ_OCUPADO)
        # Un registro que nunca confirmó su código no aparta el usuario ni el correo para siempre. El del mismo
        # correo se actualiza (no se borra) para que la espera entre códigos siga contando: así nadie puede
        # llenar de correos el buzón de otra persona registrándose una y otra vez.
        mismo_correo = next((c.id for c in choques if c.correo_clave == correo.lower()), None)
        for c in choques:
            if c.id != mismo_correo:
                con.execute(delete(db.cuentas).where(db.cuentas.c.id == c.id))
        momento = db.ahora()
        valores = dict(
            usuario=usuario, usuario_clave=usuario.lower(), correo=correo, correo_clave=correo.lower(),
            contrasena_hash=contrasenas.guardar(normalizada), verificada=not verificar, creada_en=momento,
            mayor_de_edad_en=momento, aceptacion_version=body.version, aceptacion_en=momento if body.version else None,
            fallos=0, bloqueada_hasta=None,
        )
        if mismo_correo is not None:
            con.execute(update(db.cuentas).where(db.cuentas.c.id == mismo_correo).values(**valores))
            cuenta_id = mismo_correo
        else:
            cuenta_id = con.execute(db.cuentas.insert().values(**valores)).inserted_primary_key[0]
        codigo = _nuevo_codigo(con, cuenta_id, "registro") if verificar else None
    _enviar(correo, codigo, "registro")
    return {"requiereCodigo": verificar}


@router.post("/verificar", summary="Confirmar el correo con el código", response_model=Sesion, responses=ERRORES)
def verificar(body: Verificar):
    """Termina el registro. Cada código sirve una vez, vence en `CODIGO_MINUTOS` y aguanta `CODIGO_INTENTOS` intentos."""
    token = None
    with db.transaccion() as con:
        cuenta = _por_correo(con, body.correo)
        if cuenta is not None and not cuenta.verificada and _codigo_correcto(con, cuenta.id, "registro", body.codigo):
            con.execute(update(db.cuentas).where(db.cuentas.c.id == cuenta.id).values(verificada=True))
            token = _crear_sesion(con, cuenta.id)
    if token is None:  # fuera de la transacción: el intento fallido del código sí se cuenta
        raise HTTPException(status_code=400, detail=MSJ_CODIGO)
    return {"token": token, "usuario": cuenta.usuario}


# Para que una cuenta que no existe tarde lo mismo que una contraseña equivocada.
_HASH_SEÑUELO = contrasenas.guardar("contrasena-que-nadie-usa-para-medir-tiempos")


@router.post("/acceso", summary="Iniciar sesión con usuario o correo", response_model=Sesion,
             responses={**ERRORES, 403: _error("Falta confirmar el correo.", MSJ_SIN_VERIFICAR)})
def acceso(body: Acceso):
    """`identificador` es el usuario o el correo. El mismo error si falla cualquiera; se bloquea tras `ACCESO_FALLOS`."""
    clave = body.identificador.strip().lower()
    normalizada = normalizar_contrasena(body.contrasena)
    with db.transaccion() as con:
        cuenta = con.execute(select(db.cuentas).where(or_(
            db.cuentas.c.usuario_clave == clave, db.cuentas.c.correo_clave == clave,
        ))).first()
        if cuenta is None:
            contrasenas.verificar(_HASH_SEÑUELO, normalizada)
            raise HTTPException(status_code=401, detail=MSJ_CREDENCIALES)
        momento = db.ahora()
        if cuenta.bloqueada_hasta is not None and db.como_utc(cuenta.bloqueada_hasta) > momento:
            minutos = max(1, int((db.como_utc(cuenta.bloqueada_hasta) - momento).total_seconds() // 60) + 1)
            raise HTTPException(
                status_code=429,
                detail=f"Hay demasiados intentos fallidos. Espera {minutos} minutos e intenta de nuevo.",
            )
        if not contrasenas.verificar(cuenta.contrasena_hash, normalizada):
            fallos = cuenta.fallos + 1
            valores = {"fallos": fallos}
            if fallos >= acceso_fallos():
                valores = {"fallos": 0, "bloqueada_hasta": momento + timedelta(minutes=acceso_bloqueo_min())}
            con.execute(update(db.cuentas).where(db.cuentas.c.id == cuenta.id).values(**valores))
            correcta = False
        else:
            correcta = True
            if not cuenta.verificada and verificacion_por_correo():
                raise HTTPException(status_code=403, detail=MSJ_SIN_VERIFICAR)
            valores = {"fallos": 0, "bloqueada_hasta": None}
            if contrasenas.hay_que_actualizar(cuenta.contrasena_hash):
                valores["contrasena_hash"] = contrasenas.guardar(normalizada)
            con.execute(update(db.cuentas).where(db.cuentas.c.id == cuenta.id).values(**valores))
            token = _crear_sesion(con, cuenta.id)
    if not correcta:  # fuera de la transacción: el contador de fallos sí se guarda
        raise HTTPException(status_code=401, detail=MSJ_CREDENCIALES)
    return {"token": token, "usuario": cuenta.usuario}


@router.post("/recuperar", summary="Pedir un código para cambiar la contraseña", response_model=Vacia, responses=ERRORES)
def recuperar(body: Recuperar):
    """Responde siempre lo mismo, exista o no el correo, para no revelar quién tiene cuenta."""
    codigo, correo = None, body.correo.strip()
    if verificacion_por_correo():
        with db.transaccion() as con:
            cuenta = _por_correo(con, correo)
            if cuenta is not None and cuenta.verificada:
                codigo, correo = _nuevo_codigo(con, cuenta.id, "recuperar"), cuenta.correo
    _enviar(correo, codigo, "recuperar")
    return {}


@router.post("/restablecer", summary="Cambiar la contraseña con el código", response_model=Vacia, responses=ERRORES)
def restablecer(body: Restablecer):
    """Cambia la contraseña y cierra todas las sesiones abiertas de la cuenta."""
    normalizada = _revisar_contrasena(body.contrasena)
    with db.transaccion() as con:
        cuenta = _por_correo(con, body.correo)
        valido = cuenta is not None and cuenta.verificada and _codigo_correcto(con, cuenta.id, "recuperar", body.codigo)
        if valido:
            con.execute(update(db.cuentas).where(db.cuentas.c.id == cuenta.id).values(
                contrasena_hash=contrasenas.guardar(normalizada), fallos=0, bloqueada_hasta=None,
            ))
            # Quien tenía la contraseña vieja ya no sigue dentro.
            con.execute(delete(db.sesiones).where(db.sesiones.c.cuenta_id == cuenta.id))
    if not valido:  # fuera de la transacción: el intento fallido del código sí se cuenta
        raise HTTPException(status_code=400, detail=MSJ_CODIGO)
    return {}


@router.post("/aceptacion", summary="Guardar la aceptación de las políticas", response_model=Vacia, responses=ERRORES)
def aceptacion(body: Aceptacion, authorization: str | None = Header(default=None)):
    """Guarda la versión de las políticas aceptadas y la hora del servidor. Pide `Authorization: Bearer`."""
    cuenta = cuenta_actual(authorization)
    if body.mayorDeEdad is not True:
        raise HTTPException(status_code=400, detail=MSJ_MAYOR)
    with db.transaccion() as con:
        con.execute(update(db.cuentas).where(db.cuentas.c.id == cuenta).values(
            aceptacion_version=body.version, aceptacion_en=db.ahora(),
        ))
    return {}


@router.delete("/borrarChats", summary="Borrar todas mis conversaciones", response_model=Borradas, responses=ERRORES)
def borrar_chats(authorization: str | None = Header(default=None)):
    """Borra las conversaciones de la cuenta y sus mensajes. Pide `Authorization: Bearer`."""
    cuenta = cuenta_actual(authorization)
    with db.transaccion() as con:
        borradas = con.execute(delete(db.conversaciones).where(db.conversaciones.c.usuario_id == cuenta)).rowcount
    return {"borradas": borradas}


@router.delete("/eliminarCuenta", summary="Eliminar mi cuenta y todos sus datos", response_model=Vacia,
               responses={**ERRORES, 403: _error("La contraseña no es correcta.", MSJ_CONTRASENA_ACTUAL)})
def eliminar_cuenta(body: ConContrasena, authorization: str | None = Header(default=None)):
    """Pide la contraseña. Borra la cuenta, sus conversaciones, códigos, sesiones y uso."""
    cuenta = cuenta_actual(authorization)
    with db.transaccion() as con:
        fila = con.execute(select(db.cuentas.c.contrasena_hash).where(db.cuentas.c.id == cuenta)).first()
        if fila is None or not contrasenas.verificar(fila.contrasena_hash, normalizar_contrasena(body.contrasena)):
            raise HTTPException(status_code=403, detail=MSJ_CONTRASENA_ACTUAL)
        con.execute(delete(db.conversaciones).where(db.conversaciones.c.usuario_id == cuenta))
        # Códigos, sesiones y uso se van con la cuenta (ON DELETE CASCADE); se borran también por si la base
        # no aplica las llaves foráneas.
        for tabla in (db.codigos, db.sesiones, db.uso_cuentas):
            con.execute(delete(tabla).where(tabla.c.cuenta_id == cuenta))
        con.execute(delete(db.cuentas).where(db.cuentas.c.id == cuenta))
    return {}


@router.post("/salir", summary="Cerrar sesión (el token deja de servir)", response_model=Vacia, responses=ERRORES)
def salir(authorization: str | None = Header(default=None)):
    """Revoca el token de esta sesión. Responde igual aunque el token ya no exista."""
    token = token_de_encabezado(authorization)
    if token:
        with db.transaccion() as con:
            con.execute(delete(db.sesiones).where(db.sesiones.c.token_hash == _sha256(token)))
    return {}


def total_cuentas() -> int:
    """Cuántas cuentas confirmadas hay (para el panel; nunca quiénes son)."""
    with db.leer() as con:
        return con.execute(select(func.count()).select_from(db.cuentas).where(db.cuentas.c.verificada)).scalar_one()
