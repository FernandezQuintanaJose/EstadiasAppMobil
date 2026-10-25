"""Backend del asistente inteligente de CECAPI (Módulo 3). Ver README.md."""

import base64
import binascii
import logging
from typing import Annotated

from dotenv import load_dotenv

# Carga el .env ANTES de importar proveedores, que leen las claves al crearse.
load_dotenv()

from fastapi import Depends, FastAPI, Header, HTTPException, Request  # noqa: E402
from pydantic import BaseModel, Field, StringConstraints  # noqa: E402

import comandos  # noqa: E402
import imagenes  # noqa: E402  (Owen: prepara la foto para /ver)
import privacidad  # noqa: E402
import proveedores  # noqa: E402
import seguridad  # noqa: E402
import uso  # noqa: E402
from prompts_vision import PROMPT_BILLETE  # noqa: E402

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

app = FastAPI(title="CECAPI backend")

# Cuánto historial se acepta de la app. La IA solo usa las últimas 6 líneas, pero se
# limita también el tamaño para que nadie mande textos enormes que gasten cuota.
MAX_LINEAS_CONTEXTO = 20
MAX_LARGO_LINEA = 1000
LINEAS_QUE_USA_LA_IA = 6


def proteger(request: Request, x_clave_app: str | None = Header(default=None)) -> None:
    """Se corre antes de cada ruta protegida: revisa la clave y el límite por minuto."""
    if not seguridad.clave_valida(x_clave_app):
        raise HTTPException(status_code=401, detail="La app no tiene permiso para usar el asistente.")
    quien = request.client.host if request.client else "desconocido"
    if not seguridad.limitador.permitir(quien):
        log.warning("Límite por minuto alcanzado para %s", quien)
        raise HTTPException(
            status_code=429,
            detail="Hiciste muchas preguntas seguidas. Espera un minuto e intenta de nuevo.",
        )


Protegida = [Depends(proteger)]


class Pregunta(BaseModel):
    pregunta: str = Field(min_length=1, max_length=2000)
    contexto: list[Annotated[str, StringConstraints(max_length=MAX_LARGO_LINEA)]] = Field(
        default_factory=list, max_length=MAX_LINEAS_CONTEXTO
    )


def _contexto_seguro(body: Pregunta) -> list[str]:
    """Últimas líneas del historial, sin las que traigan datos sensibles."""
    return privacidad.limpiar_contexto(body.contexto)[-LINEAS_QUE_USA_LA_IA:]


def _preguntar(body: Pregunta) -> tuple[str, str, bool]:
    try:
        return proveedores.preguntar_con_respaldo(body.pregunta, _contexto_seguro(body))
    except proveedores.ProveedorNoDisponible:
        # Mensaje genérico hacia la app; el detalle queda solo en el log del servidor.
        raise HTTPException(status_code=503, detail="El asistente no está disponible en este momento.")


@app.get("/")
def raiz():
    return {"status": "CECAPI backend activo", "proveedores": [p.nombre for p in proveedores.configurados()]}


@app.get("/uso", dependencies=Protegida)
def ver_uso():
    """Peticiones de hoy por proveedor; `aviso` se vuelve true al pasar el 80% del límite."""
    return uso.resumen([p.nombre for p in proveedores.PROVEEDORES])


# `def` y no `async def`: los SDK son bloqueantes y así FastAPI los corre en otro hilo
# sin congelar al servidor mientras espera a la IA.
@app.post("/preguntar", dependencies=Protegida)
def responder(body: Pregunta):
    if privacidad.contiene_dato_sensible(body.pregunta):
        return {"respuesta": privacidad.MENSAJE_BLOQUEO, "proveedor": None, "hayMas": False}
    respuesta, proveedor, hay_mas = _preguntar(body)
    return {"respuesta": respuesta, "proveedor": proveedor, "hayMas": hay_mas}


@app.post("/entender", dependencies=Protegida)
def entender(body: Pregunta):
    """
    Decide si lo que dijo la persona es un comando conocido de la app
    (ej. "abre la cámara") o una pregunta libre.

    Primero intenta el match de comando (rápido, gratis, sin llamar a ninguna
    IA). Si no reconoce ningún comando, cae al mismo flujo de /preguntar
    (mismo respaldo entre proveedores, según IA_ORDEN; mismas respuestas cortas).
    """
    comando = comandos.detectar(body.pregunta)
    if comando:
        return {"tipo": "comando", "comando": comando}

    if privacidad.contiene_dato_sensible(body.pregunta):
        return {"tipo": "respuesta", "respuesta": privacidad.MENSAJE_BLOQUEO, "proveedor": None, "hayMas": False}

    respuesta, proveedor, hay_mas = _preguntar(body)
    return {"tipo": "respuesta", "respuesta": respuesta, "proveedor": proveedor, "hayMas": hay_mas}


# Largo máximo del texto base64: el mismo límite de imagenes.py (IMAGEN_MAX_MB) pasado a
# base64, que ocupa 4 caracteres por cada 3 bytes.
MAX_BASE64 = (imagenes.MAX_BYTES_ENTRADA + 2) // 3 * 4


class Imagen(BaseModel):
    imagen: str = Field(min_length=1)  # foto en base64, sin prefijo data:...
    modo: str = Field(min_length=1)    # "billete" por ahora; "escena" responde pendiente


@app.post("/ver", dependencies=Protegida)
def ver(body: Imagen):
    if body.modo == "escena":
        raise HTTPException(status_code=400, detail="El modo escena todavía no está disponible.")
    if body.modo != "billete":
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
        respuesta, proveedor = proveedores.ver_con_respaldo(foto, tipo_mime, PROMPT_BILLETE)
    except proveedores.ProveedorNoDisponible:
        raise HTTPException(status_code=503, detail="El asistente no está disponible en este momento.")

    return {"respuesta": respuesta, "proveedor": proveedor}
