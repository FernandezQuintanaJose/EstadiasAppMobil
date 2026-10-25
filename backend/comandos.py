"""
Catalogo de comandos de navegacion por voz (Modulo 3).

Esta lista esta copiada de core/model/ModuloCecapi.kt, que es la fuente real
de los modulos de la app (storageCode + title de cada uno). Si ese archivo
cambia del lado de la app, hay que actualizar esto tambien.

La comparacion ignora mayusculas, acentos y signos, y solo cuenta palabras
completas (de la version de Owen, IA_recuerda.py, que ya se unio aqui).
"""

import re
import unicodedata

# storageCode de ModuloCecapi -> frases que, si aparecen en lo que dijo
# la persona, significan "abre este modulo". Se escriben sin acentos.
# El orden importa: gana el primer modulo que coincida. Por eso "leer documentos"
# (DOCUMENT_READER) se revisa antes que "documentos" (REQUESTS).
# Sinonimos agregados el 24/10/2026 a partir de las fallas de docs/Pruebas/resultados.md;
# salen de la lista de sinonimos de la app (core/voice/CommandAlternatives.kt).
MODULOS = {
    "VOICE_ASSISTANT": ["asistente de voz"],
    "AI_ASSISTANT": ["asistente inteligente"],
    "DOCUMENT_READER": [
        "camara inteligente", "camara", "leer documentos", "abre la camara", "abrir camara",
        "leer este documento", "leer el documento", "lee el documento", "lee esto", "leeme esto",
        "lee el texto", "leeme el texto", "lee la hoja", "que dice aqui", "que dice este",
        "que dice esta", "lector",
    ],
    "REQUESTS": ["documentos", "solicitudes", "tramites", "peticiones"],
    "LEARNING": [
        "actividades", "centro de aprendizaje", "ejercicios", "quiero practicar", "quiero entrenar",
    ],
    "ENVIRONMENT": [
        "asistente del entorno", "entorno", "que hay enfrente", "que tengo delante",
        "que tengo enfrente", "describe lo que veo", "que hay a mi alrededor", "que hay alrededor",
    ],
}


def normalizar(texto: str) -> str:
    """Minusculas, sin acentos ni signos: "¿Abre la Cámara?" -> "abre la camara"."""
    sin_acentos = unicodedata.normalize("NFD", texto.lower())
    sin_acentos = "".join(c for c in sin_acentos if unicodedata.category(c) != "Mn")
    return " ".join(re.sub(r"[^a-z0-9 ]", " ", sin_acentos).split())


def detectar(texto: str) -> str | None:
    """Regresa el storageCode del modulo si el texto lo menciona, si no None."""
    # Espacios alrededor para comparar palabras completas ("entornos" no es "entorno").
    texto = f" {normalizar(texto)} "
    for codigo, variantes in MODULOS.items():
        if any(f" {variante} " in texto for variante in variantes):
            return codigo
    return None
