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

Qué NO detecta todavía: datos dichos con palabras ("cinco cinco uno dos..."),
nombres, direcciones o datos de salud. Con datos reales, el aviso de privacidad
tiene que decirlo.
"""

import re
import unicodedata

MENSAJE_BLOQUEO = (
    "Por tu seguridad, no puedo procesar mensajes con contraseñas, números de "
    "tarjeta o datos personales. ¿Puedes repetirlo sin esa información?"
)

_CORREO = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")

# Cualquier tramo que empiece y termine en dígito y tenga solo dígitos y separadores en medio.
# Después se cuentan sus dígitos para decidir si es teléfono o tarjeta.
_TRAMO_NUMERICO = re.compile(r"\+?\d[\d\s().-]*\d")

# CURP: 4 letras, fecha (6 dígitos), H/M, 5 letras, 1 letra o dígito y 1 dígito.
_CURP = re.compile(r"\b[A-Z]{4}\d{6}[HMX][A-Z]{5}[A-Z\d]\d\b")
# RFC: 3 letras (empresa) o 4 (persona), fecha (6 dígitos) y homoclave de 3.
_RFC = re.compile(r"\b[A-Z&]{3,4}\d{6}[A-Z\d]{3}\b")

# Palabras de secreto seguidas de su valor: "es ...", ":", "=", o un valor con dígitos.
_SECRETO = re.compile(
    r"\b(?:contrasena|password|pin|nip)s?\b"
    r"(?:\s+(?:de\s+)?(?:mi|la|el|tu|su)?\s*(?:tarjeta|cuenta|banco|celular|correo)?)?"
    r"\s*(?:\bes\b|\bson\b|:|=)\s*\S+"
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


def contiene_dato_sensible(texto: str) -> bool:
    if not texto:
        return False
    if _CORREO.search(texto):
        return True
    if any(_es_telefono_o_tarjeta(m.group()) for m in _TRAMO_NUMERICO.finditer(texto)):
        return True
    mayusculas = _normalizar(texto).upper()
    if _CURP.search(mayusculas) or _RFC.search(mayusculas):
        return True
    return bool(_SECRETO.search(_normalizar(texto)))


def limpiar_contexto(contexto: list[str]) -> list[str]:
    """Quita del historial las líneas con datos sensibles, para que tampoco salgan a la IA.

    La pregunta actual se bloquea completa; el historial solo se filtra, así una línea
    vieja no le impide a la persona seguir preguntando.
    """
    return [linea for linea in contexto if not contiene_dato_sensible(linea)]
