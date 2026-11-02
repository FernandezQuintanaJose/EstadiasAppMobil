"""Backend del asistente inteligente de CECAPI (Módulo 3). Ver README.md."""

import base64
import binascii
import logging
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated, Literal

from dotenv import load_dotenv

# Carga el .env ANTES de importar proveedores, que leen las claves al crearse.
load_dotenv()

from fastapi import Body, Depends, FastAPI, Header, HTTPException, Query, Request  # noqa: E402
from fastapi.responses import HTMLResponse  # noqa: E402
from fastapi.security import HTTPBasic, HTTPBasicCredentials  # noqa: E402
from pydantic import BaseModel, Field, StringConstraints  # noqa: E402

import comandos  # noqa: E402
import db  # noqa: E402
import historial  # noqa: E402
import imagenes  # noqa: E402  (Owen: prepara la foto para /ver)
import panel  # noqa: E402
import privacidad  # noqa: E402
import proveedores  # noqa: E402
import seguridad  # noqa: E402
import uso  # noqa: E402
from historial import Mensaje  # noqa: E402
from prompts_vision import PROMPT_BILLETE, PROMPT_ESCENA  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("cecapi")

if not proveedores.configurados():
    raise SystemExit(
        "No hay ninguna clave de IA. Crea backend/.env con GEMINI_API_KEY y/o GROQ_API_KEY "
        "(copia .env.example)."
    )
log.info("Proveedores activos, en orden: %s", [p.nombre for p in proveedores.configurados()])
if not seguridad.clave_configurada():
    log.warning("CECAPI_CLAVE_APP está vacía: el servidor no pide clave. Ponla antes de publicarlo en internet.")


@asynccontextmanager
async def arranque(_app: FastAPI):
    """Al prender uvicorn: abre la base (con SQLite crea las tablas) y copia el uso_diario.json de antes."""
    db.preparar()
    uso.importar_json()
    log.info("Base de datos: %s · GUARDAR_TEXTOS=%s",
             "SQLite local" if db.es_sqlite() else "PostgreSQL", int(historial.guardar_textos()))
    yield
    db.reiniciar()

# --- Documentación de /docs ------------------------------------------------------
# Todo lo que sigue hasta las rutas es lo que FastAPI muestra en /docs y /redoc.
# Los textos de los errores son los mismos que lanzan las rutas, para que la app
# sepa qué esperar y pueda decirlos en voz alta.

DESCRIPCION = """
Servidor del **asistente inteligente** de la app del CECAPI (Centro de Capacitación
para Invidentes A. C., Durango). Módulo 3, Equipo 2.

La persona que usa la app es ciega o tiene baja visión: **escucha** las respuestas.
Por eso todas son cortas, en español de México, sin markdown, y los mensajes de error
(`detail`) se pueden leer en voz alta tal como llegan.

### Qué hace
- **/entender**: decide si lo que dijo la persona abre un módulo de la app o es una pregunta libre.
- **/preguntar**: responde una pregunta libre con la IA.
- **/ver**: describe una foto (billete mexicano o lo que hay enfrente).
- **/documento**: redacta un documento (carta, solicitud...) para la pantalla Consultas.
- **/uso**: cuántas peticiones lleva hoy cada proveedor de IA.
- **/panel**: página con el uso de los últimos días (solo números); **/panel/datos** lo da en JSON.

### Conversaciones
Cada respuesta de /preguntar y /entender trae `conversacionId`. Si la app lo manda en la
siguiente petición, los mensajes se guardan en la misma conversación. Por defecto
(`GUARDAR_TEXTOS=0`) **no se guarda lo que dijo la persona ni lo que contestó la IA**: solo
la ruta, el tipo de mensaje, el comando, el proveedor y el tiempo. El `contexto` y las fotos
nunca se guardan.

### Proveedores de IA
Se usan Groq y Gemini, en el orden de `IA_ORDEN`. Si el primero falla o ya gastó su
límite del día, se prueba el siguiente. El campo `proveedor` de la respuesta dice cuál contestó.

### Seguridad
- **Clave de la app**: si el servidor tiene `CECAPI_CLAVE_APP`, las rutas protegidas piden
  el encabezado `X-Clave-App` con esa clave (si falta o no coincide: **401**).
- **Límite por minuto**: cada dirección IP puede hacer `LIMITE_POR_MINUTO` peticiones por
  minuto, 20 por defecto (si se pasa: **429**).
- **Privacidad**: si la pregunta trae correo, teléfono, tarjeta, CURP, RFC o contraseña, no
  se manda a la IA y se responde con un aviso. Del `contexto` se quitan las líneas con esos datos.
- **Fotos**: se enderezan, se reducen y se les quita el EXIF (GPS, modelo del teléfono) antes de mandarlas a la IA.
"""

ETIQUETAS = [
    {"name": "Asistente", "description": "Preguntas por voz y comandos que abren un módulo de la app."},
    {"name": "Visión", "description": "Fotos tomadas con la cámara del teléfono."},
    {"name": "Servidor", "description": "Estado del servidor y uso de la IA."},
]

app = FastAPI(
    title="CECAPI · Asistente inteligente (Módulo 3)",
    description=DESCRIPCION,
    version="1.0.0",
    openapi_tags=ETIQUETAS,
    lifespan=arranque,
)

# Cuánto historial se acepta de la app. La IA solo usa las últimas 6 líneas, pero se
# limita también el tamaño para que nadie mande textos enormes que gasten cuota.
MAX_LINEAS_CONTEXTO = 20
MAX_LARGO_LINEA = 1000
LINEAS_QUE_USA_LA_IA = 6


class Error(BaseModel):
    detail: str = Field(description="Mensaje que la app puede decir en voz alta.")


def _error(descripcion: str, *ejemplos: str) -> dict:
    """Entrada de `responses=` para un código de error, con sus mensajes de ejemplo."""
    return {
        "model": Error,
        "description": descripcion,
        "content": {"application/json": {"examples": {
            f"ejemplo{i}": {"summary": texto, "value": {"detail": texto}} for i, texto in enumerate(ejemplos, 1)
        }}},
    }


ERROR_401 = _error(
    "Falta el encabezado `X-Clave-App` o no coincide con `CECAPI_CLAVE_APP`.",
    "La app no tiene permiso para usar el asistente.",
)
ERROR_429 = _error(
    "Esta IP pasó el límite de peticiones por minuto (`LIMITE_POR_MINUTO`).",
    "Hiciste muchas preguntas seguidas. Espera un minuto e intenta de nuevo.",
)
ERROR_503 = _error(
    "Ningún proveedor de IA respondió: todos fallaron o ya gastaron su límite del día.",
    "El asistente no está disponible en este momento.",
)
ERRORES_PROTEGIDA = {401: ERROR_401, 429: ERROR_429}


def proteger(
    request: Request,
    x_clave_app: str | None = Header(
        default=None,
        description="Clave de la app. Solo se pide si el servidor tiene `CECAPI_CLAVE_APP`.",
    ),
) -> None:
    """Se corre antes de cada ruta protegida: revisa la clave y el límite por minuto."""
    if not seguridad.clave_valida(x_clave_app):
        raise HTTPException(status_code=401, detail="La app no tiene permiso para usar el asistente.")
    _limitar(request)


def _limitar(request: Request) -> None:
    quien = request.client.host if request.client else "desconocido"
    if not seguridad.limitador.permitir(quien):
        log.warning("Límite por minuto alcanzado para %s", quien)
        raise HTTPException(
            status_code=429,
            detail="Hiciste muchas preguntas seguidas. Espera un minuto e intenta de nuevo.",
        )


Protegida = [Depends(proteger)]


class Pregunta(BaseModel):
    pregunta: str = Field(
        min_length=1, max_length=2000,
        description="Lo que dijo la persona, ya pasado a texto por el reconocimiento de voz.",
    )
    contexto: list[Annotated[str, StringConstraints(max_length=MAX_LARGO_LINEA)]] = Field(
        default_factory=list, max_length=MAX_LINEAS_CONTEXTO,
        description=(
            f"Historial de la conversación, de la más vieja a la más nueva (hasta {MAX_LINEAS_CONTEXTO} "
            f"líneas de {MAX_LARGO_LINEA} caracteres). La IA recibe las últimas {LINEAS_QUE_USA_LA_IA}, "
            "sin las que traigan datos personales."
        ),
    )
    conversacionId: str | None = Field(
        default=None, max_length=36,
        description="El `conversacionId` de la respuesta anterior, para seguir la misma conversación. "
                    "Si no se manda o no existe, se empieza una nueva.",
    )


EJEMPLOS_PREGUNTA = {
    "pregunta": {
        "summary": "Pregunta libre",
        "value": {"pregunta": "¿Quién fue Louis Braille?"},
    },
    "con_contexto": {
        "summary": "Pregunta con historial",
        "value": {
            "pregunta": "¿Y cómo se consigue uno?",
            "contexto": ["Persona: ¿Qué es un perro guía?",
                         "Asistente: Es un perro entrenado para guiar a una persona ciega."],
        },
    },
    "dato_personal": {
        "summary": "Trae un dato personal (no sale a la IA)",
        "value": {"pregunta": "Mi CURP es GODE561231HDFRRN09, ¿me ayudas con un trámite?"},
    },
}

EJEMPLOS_ENTENDER = {
    "comando": {
        "summary": "Abre un módulo de la app",
        "value": {"pregunta": "¿Qué hay enfrente de mí?"},
    },
    **EJEMPLOS_PREGUNTA,
}


DESCRIPCION_CONVERSACION = (
    "UUID de la conversación donde se guardó el mensaje. La app lo manda en la siguiente "
    "petición para seguir la misma. `null` si no se pudo guardar."
)


class Respuesta(BaseModel):
    respuesta: str = Field(description="Texto para decir en voz alta (de una a tres oraciones).")
    proveedor: str | None = Field(
        description="IA que contestó (`groq` o `gemini`). `null` si no se llamó a la IA "
                    "porque la pregunta traía datos personales.",
    )
    hayMas: bool = Field(
        description="`true` si la IA cree que una explicación más larga le serviría a la persona; "
                    "la app puede ofrecerle escucharla.",
    )
    conversacionId: str | None = Field(description=DESCRIPCION_CONVERSACION)

    model_config = {"json_schema_extra": {"examples": [{
        "respuesta": "Louis Braille fue un francés que inventó el sistema de lectura con puntos en relieve.",
        "proveedor": "groq",
        "hayMas": False,
        "conversacionId": "3f6c2a1e-8b4d-4c9a-9e2f-5a7b1d0c8e64",
    }]}}


class RespuestaEntender(Respuesta):
    tipo: Literal["respuesta"] = Field(description="Fue una pregunta libre: se contestó con la IA.")


class Comando(BaseModel):
    tipo: Literal["comando"] = Field(description="Lo que dijo la persona abre un módulo de la app.")
    comando: str = Field(
        description="`storageCode` del módulo que hay que abrir (de `core/model/ModuloCecapi.kt`): "
                    + ", ".join(f"`{c}`" for c in comandos.MODULOS) + ".",
        examples=["ENVIRONMENT"],
    )
    conversacionId: str | None = Field(description=DESCRIPCION_CONVERSACION)


NO_DISPONIBLE = "El asistente no está disponible en este momento."


def _ms(inicio: float) -> int:
    return round((time.perf_counter() - inicio) * 1000)


def _contexto_seguro(body: Pregunta) -> list[str]:
    """Últimas líneas del historial, sin las que traigan datos sensibles."""
    return privacidad.limpiar_contexto(body.contexto)[-LINEAS_QUE_USA_LA_IA:]


def _bloquear(body: Pregunta, ruta: str) -> dict:
    """Pregunta con datos personales: no sale a la IA y se guarda sin texto ni título."""
    conversacion = historial.guardar(ruta, body.conversacionId, [Mensaje("usuario", "bloqueado")])
    return {"respuesta": privacidad.MENSAJE_BLOQUEO, "proveedor": None, "hayMas": False,
            "conversacionId": conversacion}


def _preguntar(body: Pregunta, ruta: str) -> dict:
    """Manda la pregunta a la IA y guarda la pregunta y la respuesta (o el error)."""
    pregunta = Mensaje("usuario", "pregunta", texto=body.pregunta)
    inicio = time.perf_counter()
    try:
        respuesta, proveedor, hay_mas = proveedores.preguntar_con_respaldo(body.pregunta, _contexto_seguro(body))
    except proveedores.ProveedorNoDisponible:
        historial.guardar(ruta, body.conversacionId, [
            pregunta, Mensaje("asistente", "error", texto=NO_DISPONIBLE, duracion_ms=_ms(inicio)),
        ], titulo=body.pregunta)
        # Mensaje genérico hacia la app; el detalle queda solo en el log del servidor.
        raise HTTPException(status_code=503, detail=NO_DISPONIBLE)
    conversacion = historial.guardar(ruta, body.conversacionId, [
        pregunta,
        Mensaje("asistente", "respuesta", texto=respuesta, proveedor=proveedor, hay_mas=hay_mas,
                duracion_ms=_ms(inicio)),
    ], titulo=body.pregunta)
    return {"respuesta": respuesta, "proveedor": proveedor, "hayMas": hay_mas, "conversacionId": conversacion}


class Estado(BaseModel):
    status: str = Field(examples=["CECAPI backend activo"])
    proveedores: list[str] = Field(
        description="Proveedores de IA con clave, en el orden en que se prueban.",
        examples=[["groq", "gemini"]],
    )


@app.get("/", tags=["Servidor"], summary="¿Está vivo el servidor?", response_model=Estado)
def raiz():
    """Responde siempre, sin clave. Sirve para saber si el servidor está prendido y con qué IA."""
    return {"status": "CECAPI backend activo", "proveedores": [p.nombre for p in proveedores.configurados()]}


class UsoProveedor(BaseModel):
    usadas: int = Field(description="Peticiones de hoy.")
    limite: int | None = Field(description="Límite diario (`<PROVEEDOR>_LIMITE_DIARIO`). `null` = sin límite.")
    porcentaje: int | None = Field(description="Porcentaje del límite que ya se usó. `null` si no hay límite.")
    aviso: bool = Field(description="`true` al llegar al 80% del límite.")


class Uso(BaseModel):
    fecha: str = Field(description="Día del conteo (AAAA-MM-DD). El contador vuelve a cero cada día.")
    proveedores: dict[str, UsoProveedor]

    model_config = {"json_schema_extra": {"examples": [{
        "fecha": "2026-10-25",
        "proveedores": {
            "groq": {"usadas": 412, "limite": 500, "porcentaje": 82, "aviso": True},
            "gemini": {"usadas": 0, "limite": None, "porcentaje": None, "aviso": False},
        },
    }]}}


@app.get(
    "/uso", dependencies=Protegida, tags=["Servidor"], summary="Uso de la IA hoy",
    response_model=Uso, responses=ERRORES_PROTEGIDA,
)
def ver_uso():
    """Peticiones de hoy por proveedor (texto y visión); `aviso` se vuelve true al pasar el 80% del límite."""
    return _uso_hoy()


# `def` y no `async def`: los SDK son bloqueantes y así FastAPI los corre en otro hilo
# sin congelar al servidor mientras espera a la IA.
@app.post(
    "/preguntar", dependencies=Protegida, tags=["Asistente"], summary="Responder una pregunta",
    response_model=Respuesta, responses={**ERRORES_PROTEGIDA, 503: ERROR_503},
)
def responder(body: Annotated[Pregunta, Body(openapi_examples=EJEMPLOS_PREGUNTA)]):
    """
    Manda la pregunta a la IA y regresa una respuesta corta para decir en voz alta.

    Si la pregunta trae datos personales (correo, teléfono, tarjeta, CURP, RFC o
    contraseña) **no se manda a la IA**: se responde con un aviso y `proveedor` es `null`.
    """
    if privacidad.contiene_dato_sensible(body.pregunta):
        return _bloquear(body, "preguntar")
    return _preguntar(body, "preguntar")


@app.post(
    "/entender", dependencies=Protegida, tags=["Asistente"], summary="¿Comando o pregunta?",
    response_model=Comando | RespuestaEntender, responses={**ERRORES_PROTEGIDA, 503: ERROR_503},
)
def entender(body: Annotated[Pregunta, Body(openapi_examples=EJEMPLOS_ENTENDER)]):
    """
    Decide si lo que dijo la persona es un comando conocido de la app
    (ej. "abre la cámara") o una pregunta libre.

    Primero intenta el match de comando (rápido, gratis, sin llamar a ninguna
    IA). Si no reconoce ningún comando, cae al mismo flujo de /preguntar
    (mismo respaldo entre proveedores, según IA_ORDEN; mismas respuestas cortas).

    Hay dos respuestas posibles, según `tipo`:
    - `"comando"`: la app abre el módulo de `comando` (ej. `{"tipo": "comando", "comando": "ENVIRONMENT"}`).
    - `"respuesta"`: lo mismo que /preguntar, más `"tipo": "respuesta"`.
    """
    comando = comandos.detectar(body.pregunta)
    if comando:
        conversacion = historial.guardar("entender", body.conversacionId, [
            Mensaje("usuario", "comando", texto=body.pregunta, comando=comando),
        ], titulo=body.pregunta)
        return {"tipo": "comando", "comando": comando, "conversacionId": conversacion}

    if privacidad.contiene_dato_sensible(body.pregunta):
        return {"tipo": "respuesta", **_bloquear(body, "entender")}

    return {"tipo": "respuesta", **_preguntar(body, "entender")}


# Largo máximo del texto base64: el mismo límite de imagenes.py (IMAGEN_MAX_MB) pasado a
# base64, que ocupa 4 caracteres por cada 3 bytes.
MAX_BASE64 = (imagenes.MAX_BYTES_ENTRADA + 2) // 3 * 4

# Qué prompt de prompts_vision.py usa cada modo de /ver.
PROMPTS_VISION = {"billete": PROMPT_BILLETE, "escena": PROMPT_ESCENA}

# Foto gris de 8x8 píxeles en PNG: es pequeña y válida, para que "Try it out" de /docs funcione.
FOTO_EJEMPLO = (
    "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAFUlEQVR42mM8ceIEAzbAxIADDE4JANcnAmgro3IgAAAAAElFTkSuQmCC"
)


class Imagen(BaseModel):
    imagen: str = Field(
        min_length=1,
        description=f"Foto en base64, sin el prefijo `data:image/...;base64,`. JPEG, PNG u otro "
                    f"formato común, de hasta {imagenes.MAX_BYTES_ENTRADA // (1024 * 1024)} MB.",
    )
    modo: str = Field(
        min_length=1,
        description="`billete`: dice la denominación de un billete mexicano. "
                    "`escena`: describe hasta 3 objetos y si están a la izquierda, al centro o a la derecha.",
        examples=list(PROMPTS_VISION),
    )


EJEMPLOS_IMAGEN = {
    "billete": {"summary": "Billete", "value": {"imagen": FOTO_EJEMPLO, "modo": "billete"}},
    "escena": {"summary": "Lo que hay enfrente", "value": {"imagen": FOTO_EJEMPLO, "modo": "escena"}},
}


class RespuestaVision(BaseModel):
    respuesta: str = Field(description="Texto para decir en voz alta.")
    proveedor: str = Field(description="IA que vio la foto (`groq_vision` o `gemini`).")

    model_config = {"json_schema_extra": {"examples": [
        {"respuesta": "Billete de 50 pesos", "proveedor": "groq_vision"},
        {"respuesta": "Hay una silla a la izquierda y una mesa al centro.", "proveedor": "groq_vision"},
    ]}}


@app.post(
    "/ver", dependencies=Protegida, tags=["Visión"], summary="Describir una foto",
    response_model=RespuestaVision,
    responses={
        400: _error(
            "El modo no existe, o la foto no llegó bien: no es base64, no es una imagen o es demasiado grande.",
            "Modo no reconocido.",
            "La foto es demasiado grande.",
            "La imagen no llegó en un formato válido.",
            "La foto no es válida o es demasiado grande.",
        ),
        **ERRORES_PROTEGIDA,
        503: ERROR_503,
    },
)
def ver(body: Annotated[Imagen, Body(openapi_examples=EJEMPLOS_IMAGEN)]):
    """
    Manda la foto a la IA de visión según el `modo`.

    - **billete**: responde exactamente una frase, como "Billete de 50 pesos". Si no está
      segura, dice "No se ve claro, acerca el billete o busca más luz" o "No parece un billete".
    - **escena**: una o dos frases con hasta 3 objetos y su posición (izquierda, centro o
      derecha). Si no distingue nada con claridad, lo dice sin inventar.

    Antes de mandarla, la foto se endereza, se reduce (`IMAGEN_LADO_MAX`) y se le quita el EXIF.
    """
    inicio = time.perf_counter()
    foto = Mensaje("usuario", "pregunta")  # la foto nunca se guarda
    try:
        respuesta, proveedor = _ver(body)
    except HTTPException as error:
        if error.status_code == 503:
            historial.guardar("ver", None, [foto, Mensaje("asistente", "error", texto=NO_DISPONIBLE,
                                                           duracion_ms=_ms(inicio))])
        raise
    historial.guardar("ver", None, [foto, Mensaje("asistente", "respuesta", texto=respuesta, proveedor=proveedor,
                                                   duracion_ms=_ms(inicio))])
    return {"respuesta": respuesta, "proveedor": proveedor}


def _ver(body: Imagen) -> tuple[str, str]:
    prompt = PROMPTS_VISION.get(body.modo)
    if prompt is None:
        raise HTTPException(status_code=400, detail="Modo no reconocido.")

    # Se revisa el tamaño ANTES de decodificar, para no cargar en memoria un base64 enorme.
    # Se hace aquí y no con max_length en Imagen: así responde 400 con un mensaje que se
    # puede decir en voz alta, en vez del 422 técnico de FastAPI.
    if len(body.imagen) > MAX_BASE64:
        raise HTTPException(status_code=400, detail="La foto es demasiado grande.")

    try:
        datos = base64.b64decode(body.imagen, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=400, detail="La imagen no llegó en un formato válido.")

    try:
        foto, tipo_mime = imagenes.preparar(datos)
    except imagenes.ImagenInvalida:
        raise HTTPException(status_code=400, detail="La foto no es válida o es demasiado grande.")

    try:
        return proveedores.ver_con_respaldo(foto, tipo_mime, prompt)
    except proveedores.ProveedorNoDisponible:
        raise HTTPException(status_code=503, detail=NO_DISPONIBLE)


# --- Documentos (pantalla Consultas) ---------------------------------------------

class PedidoDocumento(BaseModel):
    pedido: str = Field(
        min_length=1, max_length=2000,
        description="Qué documento quiere la persona, como lo dijo o lo escribió "
                    "(ej. \"una carta para pedir permiso de faltar el viernes\").",
    )


class Documento(BaseModel):
    documento: str | None = Field(
        description="El documento en texto plano, con el título en la primera línea. Los datos "
                    "que la IA no sabe quedan entre corchetes, como `[tu nombre]`. `null` si se bloqueó.",
    )
    aviso: str | None = Field(
        description="Si el pedido traía datos personales no se manda a la IA y aquí viene el aviso "
                    "para decir en voz alta. `null` si todo salió bien.",
    )
    proveedor: str | None = Field(description="IA que lo redactó (`groq` o `gemini`); `null` si se bloqueó.")

    model_config = {"json_schema_extra": {"examples": [{
        "documento": "Solicitud de permiso\n\nDurango, Durango, a [fecha].\n\nPor medio de la presente...",
        "aviso": None,
        "proveedor": "groq",
    }]}}


EJEMPLOS_DOCUMENTO = {
    "carta": {
        "summary": "Carta",
        "value": {"pedido": "Una carta para pedir permiso de faltar el viernes al curso del CECAPI"},
    },
    "dato_personal": {
        "summary": "Trae un dato personal (no sale a la IA)",
        "value": {"pedido": "Una solicitud con mi CURP GODE561231HDFRRN09"},
    },
}


@app.post(
    "/documento", dependencies=Protegida, tags=["Asistente"], summary="Redactar un documento",
    response_model=Documento, responses={**ERRORES_PROTEGIDA, 503: ERROR_503},
)
def documento(body: Annotated[PedidoDocumento, Body(openapi_examples=EJEMPLOS_DOCUMENTO)]):
    """
    Redacta el documento que pidió la persona en la pantalla Consultas (carta, solicitud, oficio...).

    A diferencia de /preguntar, la respuesta puede ser larga: se muestra en pantalla y se lee
    con "lee el documento". Usa el mismo respaldo entre proveedores y el mismo filtro de
    privacidad: si el pedido trae CURP, RFC, teléfono, correo, tarjeta o contraseña, no se
    manda a la IA. La persona puede escribir esos datos después, a mano, en el documento.
    """
    if privacidad.contiene_dato_sensible(body.pedido):
        historial.guardar("documento", None, [Mensaje("usuario", "bloqueado")])
        return {"documento": None, "aviso": privacidad.MENSAJE_BLOQUEO, "proveedor": None}
    pedido = Mensaje("usuario", "pregunta", texto=body.pedido)
    inicio = time.perf_counter()
    try:
        texto, proveedor, _ = proveedores.preguntar_con_respaldo(
            body.pedido, [], proveedores.INSTRUCCIONES_DOCUMENTO,
        )
    except proveedores.ProveedorNoDisponible:
        historial.guardar("documento", None, [
            pedido, Mensaje("asistente", "error", texto=NO_DISPONIBLE, duracion_ms=_ms(inicio)),
        ], titulo=body.pedido)
        raise HTTPException(status_code=503, detail=NO_DISPONIBLE)
    historial.guardar("documento", None, [
        pedido, Mensaje("asistente", "respuesta", texto=texto, proveedor=proveedor, duracion_ms=_ms(inicio)),
    ], titulo=body.pedido)
    return {"documento": texto, "aviso": None, "proveedor": proveedor}


# --- Panel de uso ------------------------------------------------------------------
# El navegador no puede mandar X-Clave-App por su cuenta, así que el panel también acepta
# usuario y contraseña del navegador (HTTP Basic): el usuario puede ser cualquiera y la
# contraseña es CECAPI_CLAVE_APP. La clave nunca va en la liga (quedaría en el historial).

class _BasicOpcional(HTTPBasic):
    """HTTPBasic responde su propio 401 en inglés si el encabezado viene mal formado;
    así llega como "sin credenciales" y proteger_panel da el mensaje de siempre."""

    async def __call__(self, request: Request) -> HTTPBasicCredentials | None:
        try:
            return await super().__call__(request)
        except HTTPException:
            return None


_basic = _BasicOpcional(auto_error=False, description="Usuario: cualquiera. Contraseña: `CECAPI_CLAVE_APP`.")


def proteger_panel(
    request: Request,
    credenciales: HTTPBasicCredentials | None = Depends(_basic),
    x_clave_app: str | None = Header(
        default=None,
        description="Clave de la app. También se puede entrar con usuario y contraseña del navegador.",
    ),
) -> None:
    if seguridad.clave_configurada():
        por_navegador = credenciales is not None and seguridad.clave_valida(credenciales.password)
        if not (por_navegador or (x_clave_app is not None and seguridad.clave_valida(x_clave_app))):
            raise HTTPException(
                status_code=401, detail="La app no tiene permiso para usar el asistente.",
                headers={"WWW-Authenticate": 'Basic realm="Panel CECAPI", charset="UTF-8"'},
            )
    _limitar(request)


class TotalesPanel(BaseModel):
    preguntas: int = Field(description="Preguntas libres, fotos y pedidos de documento (fueron o iban a ir a la IA).")
    comandos: int = Field(description="Frases que abrieron un módulo de la app.")
    bloqueados: int = Field(description="Peticiones que no salieron a la IA por traer datos personales.")
    errores: int = Field(description="Veces que ninguna IA respondió (503).")


class ActividadDia(TotalesPanel):
    fecha: str = Field(description="Día (AAAA-MM-DD).")


class PeticionesDia(BaseModel):
    fecha: str
    proveedor: str = Field(description="`groq`, `groq_vision` o `gemini`.")
    peticiones: int = Field(description="Peticiones a esa IA ese día, incluidas las que fallaron.")


class TiempoProveedor(BaseModel):
    proveedor: str
    respuestas: int = Field(description="Respuestas que dio esa IA en el periodo.")
    promedio_ms: int = Field(description="Tiempo promedio de respuesta, en milisegundos.")


class Panel(BaseModel):
    dias: int
    desde: str = Field(description="Primer día del periodo (AAAA-MM-DD).")
    hasta: str = Field(description="Último día del periodo: hoy.")
    hoy: Uso = Field(description="Lo mismo que /uso: peticiones de hoy contra el límite diario.")
    totales: TotalesPanel
    por_ruta: dict[str, int] = Field(description="Peticiones de la persona por ruta: preguntar, entender, ver, documento.")
    actividad: list[ActividadDia] = Field(description="Un renglón por día con mensajes, del más nuevo al más viejo.")
    peticiones: list[PeticionesDia]
    tiempos: list[TiempoProveedor]


Dias = Annotated[int, Query(
    ge=1, le=panel.DIAS_MAXIMO,
    description=f"Cuántos días hacia atrás, contando hoy (1 a {panel.DIAS_MAXIMO}).",
)]
ERRORES_PANEL = {**ERRORES_PROTEGIDA, 401: _error(
    "Falta la clave: encabezado `X-Clave-App`, o usuario y contraseña del navegador con la clave como contraseña.",
    "La app no tiene permiso para usar el asistente.",
)}
PANEL_HTML = Path(__file__).with_name("panel.html")


def _uso_hoy() -> dict:
    nombres = [n for p in proveedores.PROVEEDORES for n in (p.nombre, p.nombre_vision)]
    return uso.resumen(list(dict.fromkeys(nombres)))


@app.get(
    "/panel", dependencies=[Depends(proteger_panel)], tags=["Servidor"], summary="Panel de uso (página)",
    response_class=HTMLResponse, responses=ERRORES_PANEL,
)
def ver_panel():
    """
    Página para abrir en el navegador: uso de hoy contra el límite, peticiones por día y
    proveedor, tiempo promedio, comandos contra preguntas y bloqueos por privacidad.
    Solo números: nunca muestra textos. Si el servidor tiene clave, el navegador la pide
    (usuario: cualquiera; contraseña: `CECAPI_CLAVE_APP`).
    """
    return HTMLResponse(PANEL_HTML.read_text(encoding="utf-8"), headers={
        "Cache-Control": "no-store",
        "Content-Security-Policy": (
            "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; "
            "connect-src 'self'; img-src data:"
        ),
    })


@app.get(
    "/panel/datos", dependencies=[Depends(proteger_panel)], tags=["Servidor"], summary="Panel de uso (JSON)",
    response_model=Panel, responses=ERRORES_PANEL,
)
def ver_panel_datos(dias: Dias = panel.DIAS_POR_DEFECTO):
    """Los números de /panel, en JSON."""
    return panel.datos(dias, _uso_hoy())
