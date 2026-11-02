"""Contraseñas de las cuentas con Argon2id: el mismo algoritmo que ya usa la app en el teléfono.

Todavía no hay rutas de cuentas (ver docs/cuentas-servicio.md); este módulo deja lista la parte de las contraseñas
para cuando se escriban. La cadena que se guarda ya trae su sal y sus parámetros, así que se pueden subir después.
"""
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError, VerifyMismatchError

# Perfil de RFC 9106 para memoria baja (el que recomienda OWASP como mínimo): 19 MiB, 2 pasadas, 1 hilo.
_hasher = PasswordHasher(time_cost=2, memory_cost=19 * 1024, parallelism=1)


def guardar(contrasena: str) -> str:
    """La cadena que se guarda en la base: Argon2id con una sal nueva cada vez."""
    return _hasher.hash(contrasena)


def verificar(guardada: str, contrasena: str) -> bool:
    """True solo si [contrasena] es la que produjo [guardada]. Una cadena dañada o de otro formato da False."""
    try:
        return _hasher.verify(guardada, contrasena)
    except (VerifyMismatchError, VerificationError, InvalidHashError):
        return False


def hay_que_actualizar(guardada: str) -> bool:
    """True si la cadena se hizo con parámetros más débiles que los de hoy y conviene volver a guardarla al entrar."""
    try:
        return _hasher.check_needs_rehash(guardada)
    except InvalidHashError:
        return True
