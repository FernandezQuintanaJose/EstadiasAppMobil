"""
Revisa el texto del usuario ANTES de mandarlo a cualquier proveedor de IA,
para no enviar contraseñas ni datos personales.

Es un filtro por patrones, no perfecto, pero evita los casos más claros sin
tener que mandar nada a una IA externa para decidir.

Qué bloquea (24/10/2026):
- Correos electrónicos.
- Teléfonos de 10 dígitos, aunque vengan con espacios, guiones, puntos,
  paréntesis o con +52 adelante ("55 1234 5678", "(618) 455-0146").
- Números de tarjeta (13 a 19 dígitos, con o sin espacios).
- Una CURP o un RFC escritos (el dato, no la palabra: "¿qué es la CURP?" sí pasa).
- Una contraseña, PIN o NIP cuando se dice su valor ("mi contraseña es gato123",
  "el pin es 1234"). Preguntar por ellos sí pasa ("¿cómo hago una contraseña segura?").

También ve las formas disfrazadas más comunes (8/11/2026):
- Correos con "arroba", "[at]", "(at)" o espacios ("juan @ example . com"), si terminan en un dominio
  conocido (.com, .mx, .net, .org, .edu, .gob, .io), para no confundir "nos vemos at home".
- Teléfonos y tarjetas dichos con palabras ("seis uno ocho uno dos...", también en inglés).
- Una CURP con espacios, puntos o guiones entre sus partes ("GODE 561231 HDFRRN 09").
- La contraseña en inglés ("my password is hunter2").

Qué NO detecta todavía: nombres, direcciones o datos de salud. Con datos reales, el aviso de privacidad
tiene que decirlo.
"""

import re
import unicodedata

MENSAJE_BLOQUEO = (
    "Por tu seguridad, no puedo procesar mensajes con contraseñas, números de "
    "tarjeta o datos personales. ¿Puedes repetirlo sin esa información?"
)

_CORREO = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")

# Correo disfrazado: se reemplazan "arroba", "(at)", "[punto]", etc. y se juntan los espacios. Como "punto"
# y "at" son palabras normales, el resultado solo cuenta si termina en un dominio conocido.
_ARROBA = re.compile(r"\s*(?:[\[(<{]\s*(?:arroba|at)\s*[\])>}]|\barroba\b|\bat\b|@)\s*")
_PUNTO = re.compile(r"\s*(?:[\[(<{]\s*(?:punto|dot)\s*[\])>}]|\bpunto\b|\bdot\b|\.)\s*")
_CORREO_DISFRAZADO = re.compile(r"[\w+-]+@[\w-]+(?:\.[\w-]+)*\.(?:com|mx|net|org|edu|gob|io)\b")

# Números dichos con palabras, para leer "seis uno ocho..." como "6 1 8...".
_NUMEROS = {
    "cero": "0", "uno": "1", "una": "1", "dos": "2", "tres": "3", "cuatro": "4", "cinco": "5", "seis": "6",
    "siete": "7", "ocho": "8", "nueve": "9",
    "zero": "0", "one": "1", "two": "2", "three": "3", "four": "4", "five": "5", "six": "6", "seven": "7",
    "eight": "8", "nine": "9",
}
_PALABRA_NUMERO = re.compile(r"\b(?:" + "|".join(_NUMEROS) + r")\b")

# Cualquier tramo que empiece y termine en dígito y tenga solo dígitos y separadores en medio.
# Después se cuentan sus dígitos para decidir si es teléfono o tarjeta.
_TRAMO_NUMERICO = re.compile(r"\+?\d[\d\s().-]*\d")

# CURP: 4 letras, fecha (6 dígitos), H/M, 5 letras, 1 letra o dígito y 1 dígito.
# Entre sus partes puede haber espacios, puntos o guiones ("GODE 561231 HDFRRN 09").
_CURP = re.compile(r"\b[A-Z]{4}[\s.-]*\d{6}[\s.-]*[HMX][\s.-]*[A-Z]{5}[\s.-]*[A-Z\d][\s.-]*\d\b")
# RFC: 3 letras (empresa) o 4 (persona), fecha (6 dígitos) y homoclave de 3.
_RFC = re.compile(r"\b[A-Z&]{3,4}\d{6}[A-Z\d]{3}\b")

# Palabras de secreto seguidas de su valor: "es ...", ":", "=", o un valor con dígitos.
_SECRETO = re.compile(
    r"\b(?:contrasena|password|pin|nip)s?\b"
    r"(?:\s+(?:de\s+)?(?:mi|la|el|tu|su)?\s*(?:tarjeta|cuenta|banco|celular|correo)?)?"
    r"\s*(?:\bes\b|\bson\b|\bis\b|:|=)\s*\S+"
    r"|\b(?:contrasena|password|pin|nip)\b\s+\S*\d\S*"
)


def _normalizar(texto: str) -> str:
    """Minúsculas y sin acentos: "Contraseña" -> "contrasena"."""
    sin_acentos = unicodedata.normalize("NFD", texto.lower())
    return "".join(c for c in sin_acentos if unicodedata.category(c) != "Mn")


def _es_telefono_o_tarjeta(tramo: str) -> bool:
    digitos = re.sub(r"\D", "", tramo)
    if len(digitos) == 10:
        return True  # teléfono de México
    if len(digitos) == 12 and digitos.startswith("52"):
        return True  # teléfono con lada del país
    return 13 <= len(digitos) <= 19  # tarjeta


def _hay_correo(normalizado: str) -> bool:
    if _CORREO.search(normalizado):
        return True
    return bool(_CORREO_DISFRAZADO.search(_PUNTO.sub(".", _ARROBA.sub("@", normalizado))))


def _hay_numero(texto: str) -> bool:
    return any(_es_telefono_o_tarjeta(m.group()) for m in _TRAMO_NUMERICO.finditer(texto))


def contiene_dato_sensible(texto: str) -> bool:
    if not texto:
        return False
    normalizado = _normalizar(texto)
    if _hay_correo(normalizado):
        return True
    if _hay_numero(texto) or _hay_numero(_PALABRA_NUMERO.sub(lambda m: _NUMEROS[m.group()], normalizado)):
        return True
    mayusculas = normalizado.upper()
    if _CURP.search(mayusculas) or _RFC.search(mayusculas):
        return True
    return bool(_SECRETO.search(normalizado))


def limpiar_contexto(contexto: list[str]) -> list[str]:
    """Quita del historial las líneas con datos sensibles, para que tampoco salgan a la IA.

    La pregunta actual se bloquea completa; el historial solo se filtra, así una línea
    vieja no le impide a la persona seguir preguntando.
    """
    return [linea for linea in contexto if not contiene_dato_sensible(linea)]
