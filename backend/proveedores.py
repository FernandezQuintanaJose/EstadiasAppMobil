"""
Proveedores de IA del backend CECAPI (Módulo 3).

Cada proveedor sabe preguntarle a UNA API. El orden de la lista PROVEEDORES es el
orden del fallback: si el primero falla (sin cuota, caído, sin clave), se intenta
el siguiente. Las claves se leen SOLO de variables de entorno (archivo .env local),
nunca van escritas en el código.

Cada proveedor tiene dos métodos: preguntar() (texto) y ver() (una foto + un prompt).
Sus funciones de respaldo son preguntar_con_respaldo() y ver_con_respaldo().
"""

import logging
import os
import re
from base64 import b64encode

import uso

log = logging.getLogger("cecapi.proveedores")
_MARCA_MAS = "MAS_DISPONIBLE"

# Cómo debe responder la IA: la persona ESCUCHA la respuesta, no la lee.
INSTRUCCIONES = (
    "Eres el asistente de voz de CECAPI para personas con discapacidad visual. "
    "Responde siempre en español de México, en frases cortas y claras, "
    "como si hablaras en voz alta: entre una y tres oraciones. "
    "No uses markdown, viñetas, tablas, emojis ni enlaces. "
    "Si no sabes algo, dilo con honestidad. "
    f"Si tu respuesta completa sería notablemente más larga que eso y crees que "
    f"a la persona le serviría escucharla, agrega al final, en su propia línea, "
    f"exactamente: {_MARCA_MAS} (nada más en esa línea). Si tu respuesta ya cubre "
    f"todo, no agregues esa línea."
)


TIEMPO_LIMITE_S = float(os.getenv("IA_TIEMPO_LIMITE_S", "20"))

# --- Visión (fotos de billetes y escenas) ---
# ver() es aparte de preguntar(): recibe la foto ya comprimida (imagenes.py) y el prompt
# del modo (billete o escena). No usa INSTRUCCIONES ni contexto: el prompt trae todo.
# Respuesta corta a propósito: la persona la escucha, y el plan gratis de Groq limita
# los tokens de respuesta por minuto (1,000 en qwen/qwen3.8-27b; con 150 caben ~6 fotos/min).
VISION_MAX_TOKENS = int(os.getenv("VISION_MAX_TOKENS", "150"))


class ProveedorNoDisponible(Exception):
    """El proveedor no está configurado o falló; hay que probar el siguiente."""


def _armar_prompt(pregunta: str, contexto: list[str]) -> str:
    if not contexto:
        return pregunta
    historial = "\n".join(contexto)
    return f"Conversación anterior:\n{historial}\n\nPregunta actual: {pregunta}"

def _separar_mas(texto: str) -> tuple[str, bool]:
    """Si el texto termina en la línea MAS_DISPONIBLE, la quita y regresa hay_mas=True."""
    lineas = texto.strip().splitlines()
    if lineas and lineas[-1].strip() == _MARCA_MAS:
        return "\n".join(lineas[:-1]).strip(), True
    return texto, False

class Gemini:
    nombre = "gemini"

    def __init__(self) -> None:
        self.clave = os.getenv("GEMINI_API_KEY", "").strip()
        self.modelo = os.getenv("GEMINI_MODEL", "gemini-3.8-flash")
        self._cliente = None

    @property
    def configurado(self) -> bool:
        return bool(self.clave)

    def _cliente_listo(self):
        if self._cliente is None:
            from google import genai
            from google.genai import types

            self._cliente = genai.Client(
                api_key=self.clave,
                http_options=types.HttpOptions(timeout=int(TIEMPO_LIMITE_S * 1000)),
            )
        return self._cliente

    def preguntar(self, pregunta: str, contexto: list[str]) -> str:
        from google.genai import types

        respuesta = self._cliente_listo().models.generate_content(
            model=self.modelo,
            contents=_armar_prompt(pregunta, contexto),
            config=types.GenerateContentConfig(system_instruction=INSTRUCCIONES),
        )
        texto = (respuesta.text or "").strip()
        if not texto:
            raise ProveedorNoDisponible("Gemini respondió vacío")
        return texto

    # Mismo modelo que preguntar(): gemini-3.8-flash acepta imágenes. Por confirmar con una
    # clave que funcione (al 10/10/2026 Google responde "project denied access").
    nombre_vision = "gemini"  # cuenta en el mismo límite diario que las preguntas

    @property
    def vision_configurada(self) -> bool:
        return self.configurado

    def ver(self, imagen: bytes, tipo_mime: str, prompt: str) -> str:
        from google.genai import types

        respuesta = self._cliente_listo().models.generate_content(
            model=self.modelo,
            contents=[types.Part.from_bytes(data=imagen, mime_type=tipo_mime), prompt],
            config=types.GenerateContentConfig(max_output_tokens=VISION_MAX_TOKENS),
        )
        texto = (respuesta.text or "").strip()
        if not texto:
            raise ProveedorNoDisponible("Gemini (visión) respondió vacío")
        return texto


class Groq:
    nombre = "groq"

    def __init__(self) -> None:
        self.clave = os.getenv("GROQ_API_KEY", "").strip()
        self.modelo = os.getenv("GROQ_MODEL", "openai/gpt-oss-20b")
        self._cliente = None

    @property
    def configurado(self) -> bool:
        return bool(self.clave)

    def _cliente_listo(self):
        if self._cliente is None:
            from groq import Groq as ClienteGroq

            # max_retries=0: si falla, preferimos pasar al siguiente proveedor ya.
            self._cliente = ClienteGroq(api_key=self.clave, timeout=TIEMPO_LIMITE_S, max_retries=0)
        return self._cliente

    def preguntar(self, pregunta: str, contexto: list[str]) -> str:
        respuesta = self._cliente_listo().chat.completions.create(
            model=self.modelo,
            messages=[
                {"role": "system", "content": INSTRUCCIONES},
                {"role": "user", "content": _armar_prompt(pregunta, contexto)},
            ],
        )
        texto = (respuesta.choices[0].message.content or "").strip()
        if not texto:
            raise ProveedorNoDisponible("Groq respondió vacío")
        return texto

    # GROQ_MODEL (gpt-oss-20b) es solo texto; para fotos se usa otro modelo de Groq.
    # qwen/qwen3.8-27b se probó el 10/10/2026 con una imagen y contestó bien.
    # Tiene su propio límite diario, aparte del de GROQ_MODEL: GROQ_VISION_LIMITE_DIARIO.
    nombre_vision = "groq_vision"

    @property
    def modelo_vision(self) -> str:
        return os.getenv("GROQ_VISION_MODEL", "qwen/qwen3.8-27b").strip()

    @property
    def vision_configurada(self) -> bool:
        return self.configurado and bool(self.modelo_vision)

    def ver(self, imagen: bytes, tipo_mime: str, prompt: str) -> str:
        url = f"data:{tipo_mime};base64,{b64encode(imagen).decode('ascii')}"
        respuesta = self._cliente_listo().chat.completions.create(
            model=self.modelo_vision,
            max_tokens=VISION_MAX_TOKENS,
            messages=[{"role": "user", "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": url}},
            ]}],
        )
        texto = _quitar_razonamiento(respuesta.choices[0].message.content or "")
        if not texto:
            raise ProveedorNoDisponible("Groq (visión) respondió vacío")
        return texto


def _quitar_razonamiento(texto: str) -> str:
    """Algunos modelos (Qwen) pueden mandar su razonamiento entre <think></think>; no se lee en voz alta."""
    return re.sub(r"<think>.*?(</think>|$)", "", texto, flags=re.DOTALL).strip()


# Orden del fallback: primero Gemini, si falla Groq. Vale para preguntar() y para ver().
PROVEEDORES = [Gemini(), Groq()]


def configurados() -> list:
    return [p for p in PROVEEDORES if p.configurado]


def configurados_vision() -> list:
    return [p for p in PROVEEDORES if p.vision_configurada]


def ver_con_respaldo(imagen: bytes, tipo_mime: str, prompt: str) -> tuple[str, str]:
    """Manda la foto y el prompt a la primera IA con visión que responda.

    Devuelve (respuesta, nombre_del_proveedor). Lanza ProveedorNoDisponible si todas fallan;
    la ruta /ver debe convertir eso en un mensaje que se pueda decir en voz alta.
    """
    for proveedor in configurados_vision():
        if uso.agotado(proveedor.nombre_vision):
            log.info("%s ya usó su límite de hoy; se salta", proveedor.nombre_vision)
            continue
        uso.registrar(proveedor.nombre_vision)
        try:
            return proveedor.ver(imagen, tipo_mime, prompt), proveedor.nombre_vision
        except Exception as error:
            log.warning("Falló %s (%s); probando el siguiente", proveedor.nombre_vision, type(error).__name__)
    raise ProveedorNoDisponible("Ninguna IA con visión respondió")


def preguntar_con_respaldo(pregunta: str, contexto: list[str]) -> tuple[str, str, bool]:
    """Devuelve (respuesta, nombre_del_proveedor, hay_mas). Lanza ProveedorNoDisponible si todos fallan."""
    for proveedor in configurados():
        if uso.agotado(proveedor.nombre):
            log.info("%s ya usó su límite de hoy; se salta", proveedor.nombre)
            continue
        uso.registrar(proveedor.nombre)
        try:
            texto = proveedor.preguntar(pregunta, contexto)
            respuesta, hay_mas = _separar_mas(texto)
            return respuesta, proveedor.nombre, hay_mas
        except Exception as error:
            log.warning("Falló %s (%s); probando el siguiente", proveedor.nombre, type(error).__name__)
    raise ProveedorNoDisponible("Ningún proveedor de IA respondió")