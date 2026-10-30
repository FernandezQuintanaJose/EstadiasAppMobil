"""
Proveedores de IA del backend CECAPI (Módulo 3).

Cada proveedor sabe preguntarle a UNA API. El orden de la lista PROVEEDORES es el
orden del fallback: si el primero falla (sin cuota, caído, sin clave), se intenta
el siguiente. Ese orden se elige en .env con IA_ORDEN (por defecto: groq,gemini).
Las claves se leen SOLO de variables de entorno (archivo .env local), nunca van
escritas en el código.

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
# Datos reales del CECAPI (Google Maps, revisado el 24/10/2026). Sin ellos la IA inventaba
# la dirección (pruebas del 10/10 y del 24/10: daba calles de la Ciudad de México).
DATOS_CECAPI = (
    "Datos del CECAPI, el Centro de Capacitación para Invidentes A. C.: "
    "está en la calle Jacarandá 1603, colonia Valle Florido, código postal 34226, "
    "en Durango, Durango. Su teléfono es 618 455 0146. "
    "Abre de lunes a viernes, de dos a siete de la tarde. "
)

# Nombres oficiales. Sin ellos la IA no daba el nombre del programa (pregunta 29, 24/10) e
# inventaba leyes (pregunta 27, 25/10). Montos y edades cambian cada año: no van aquí.
# - Pensión: DOF del 13/02/2026 (Reglas de Operación 2026, Secretaría de Bienestar).
# - Ley: diputados.gob.mx (DOF 30/05/2011, última reforma 14/06/2024), revisado el 25/10/2026.
DATOS_APOYOS = (
    "Datos oficiales: la pensión federal para personas con discapacidad se llama Pensión para "
    "el Bienestar de las Personas con Discapacidad Permanente y la da la Secretaría de "
    "Bienestar; sus montos, edades y requisitos cambian cada año, así que para eso sugiere "
    "preguntar ahí. La ley vigente es la Ley General para la Inclusión de las Personas con "
    "Discapacidad. No nombres ninguna otra ley ni otro programa: si te preguntan por otro, "
    "di que no estás seguro. "
)

# Una idea por regla y sin repetir (el 25/10 el texto creció de 1,652 a 2,564 caracteres
# y la respuesta promedio pasó de 630 a 1,128 ms).
INSTRUCCIONES = (
    "Eres el asistente de voz de la app del CECAPI para personas ciegas o con baja visión. "
    "Responde en español de México, en una a tres oraciones cortas, como si hablaras en voz "
    "alta, sin markdown, viñetas, tablas, emojis ni enlaces. "
    "La persona no ve: no uses palabras de vista (ver, mirar, revisar, verificar, pantalla); "
    "da indicaciones para el oído, el tacto, el bastón o pedir ayuda. "
    "No tienes acceso al teléfono (batería, volumen, wifi, notificaciones, cámara, fecha, hora, "
    "lo último que dijo la app): sugiere preguntarle a la app, por ejemplo 'cuánta batería "
    "tengo' o 'qué hora es'. "
    "No inventes funciones, menús, botones ni comandos de la app; para saber cómo usarla, que "
    "diga 'ayuda' o 'qué comandos hay'. "
    + DATOS_CECAPI +
    "Si te preguntan algo del CECAPI que no esté ahí, di que no lo sabes y sugiere llamar; "
    "ese teléfono solo se sugiere en preguntas sobre el CECAPI. "
    "Cultura general, ciencia, historia y cifras conocidas contéstalas normalmente, diciendo "
    "'alrededor de' si la cifra es aproximada. "
    + DATOS_APOYOS +
    "No inventes direcciones ni nombres; si de verdad no sabes algo, dilo. "
    "Para cruzar la calle, di una vez el término 'guía podotáctil' y explica que son franjas "
    "con relieve en el piso que se sienten con el bastón o los pies y llevan al cruce; luego "
    "habla de escuchar el tráfico, cruzar en esquinas o semáforos con sonido y pedir ayuda. "
    f"Si una respuesta más larga le serviría a la persona, agrega al final, en su propia "
    f"línea, solo: {_MARCA_MAS}"
)


TIEMPO_LIMITE_S = float(os.getenv("IA_TIEMPO_LIMITE_S", "20"))

# Temperatura de Groq para las preguntas. Su valor por defecto es 1; con gpt-oss-20b eso
# producía datos inventados (pregunta 17, 25/10: "comunidad daltónica" al hablar de Braille).
# Más baja = respuestas más apegadas a lo seguro. Vacía = la del proveedor.
GROQ_TEMPERATURA = os.getenv("GROQ_TEMPERATURA", "0.3").strip()

# Palabras de vista que la persona no puede seguir (preguntas 9 y 13, 25/10: "revisa la
# aplicación", "verifica si la app ofrece... en su pantalla", "si no la ves"). Si la
# respuesta trae alguna, se le pide a la misma IA una sola vez que la diga de nuevo.
# "ve" no está: también es el imperativo de "ir" ("ve a la esquina").
_LENGUAJE_VISUAL = re.compile(
    r"\b(mira|mirar|mires|observa|observar|f[ií]jate|visualmente|ves|veas|verla|verlo|"
    r"verifica|verificar|revisa|revisar|pantalla)\b",
    re.IGNORECASE,
)
# "lector de pantalla" (TalkBack, NVDA) sí es para personas ciegas: no cuenta.
_PERMITIDO = re.compile(r"\blector(es)? de pantalla\b", re.IGNORECASE)
CORRECCION_VISUAL = (
    "Tu respuesta anterior usaba palabras de vista y la persona no ve. Responde de nuevo la "
    "misma pregunta sin ver, mirar, revisar, verificar ni pantalla."
)


def tiene_lenguaje_visual(texto: str) -> bool:
    return bool(_LENGUAJE_VISUAL.search(_PERMITIDO.sub("", texto)))

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
    """Si el texto trae la marca MAS_DISPONIBLE, la quita y regresa hay_mas=True."""
    # La IA no siempre la pone en su propia línea (prueba del 25/10: quedó pegada al final
    # de la última oración). Se quita donde esté: la persona nunca debe escucharla.
    if _MARCA_MAS not in texto:
        return texto, False
    return re.sub(rf"[ \t]*{_MARCA_MAS}[ \t]*", " ", texto).strip(), True

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
        extra = {"temperature": float(GROQ_TEMPERATURA)} if GROQ_TEMPERATURA else {}
        respuesta = self._cliente_listo().chat.completions.create(
            model=self.modelo,
            messages=[
                {"role": "system", "content": INSTRUCCIONES},
                {"role": "user", "content": _armar_prompt(pregunta, contexto)},
            ],
            **extra,
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


# Orden del fallback, tomado de IA_ORDEN en .env. Vale para preguntar() y para ver().
# Por defecto Groq va primero: desde el 10/10/2026 Google responde 403 "project denied
# access" a la clave de Gemini. Cuando haya una clave que funcione: IA_ORDEN=gemini,groq
_DISPONIBLES = {"gemini": Gemini, "groq": Groq}


def _armar_lista() -> list:
    pedidos = [n.strip().lower() for n in os.getenv("IA_ORDEN", "groq,gemini").split(",") if n.strip()]
    desconocidos = [n for n in pedidos if n not in _DISPONIBLES]
    if desconocidos:
        log.warning("IA_ORDEN tiene nombres que no existen y se ignoran: %s", desconocidos)
    orden = [n for n in dict.fromkeys(pedidos) if n in _DISPONIBLES]
    # Los que no se nombraron van al final, para que nunca se pierda un respaldo.
    orden += [n for n in _DISPONIBLES if n not in orden]
    return [_DISPONIBLES[n]() for n in orden]


PROVEEDORES = _armar_lista()


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
        except Exception as error:
            log.warning("Falló %s (%s); probando el siguiente", proveedor.nombre, type(error).__name__)
            continue
        if tiene_lenguaje_visual(texto) and not uso.agotado(proveedor.nombre):
            texto = _corregir_visual(proveedor, pregunta, contexto, texto)
        respuesta, hay_mas = _separar_mas(texto)
        return respuesta, proveedor.nombre, hay_mas
    raise ProveedorNoDisponible("Ningún proveedor de IA respondió")


def _corregir_visual(proveedor, pregunta: str, contexto: list[str], texto: str) -> str:
    """Pide una vez la misma respuesta sin palabras de vista; si falla, se queda la primera."""
    log.info("%s usó palabras de vista; se le pide corregir", proveedor.nombre)
    uso.registrar(proveedor.nombre)
    try:
        nuevo = proveedor.preguntar(
            f"{pregunta}\n\n{CORRECCION_VISUAL}", [*contexto, f"Asistente: {texto}"]
        )
    except Exception as error:
        log.warning("No se pudo corregir con %s (%s)", proveedor.nombre, type(error).__name__)
        return texto
    return nuevo if nuevo.strip() else texto

