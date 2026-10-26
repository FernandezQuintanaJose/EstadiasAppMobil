"""
Contador de peticiones por día y por proveedor (Módulo 3).

- Guarda los conteos en la tabla uso_diario de la base de datos (bd.py) para que no se
  pierdan si se reinicia el servidor. Antes iban en uso_diario.json: al arrancar, si ese
  archivo existe, se copia a la tabla y se renombra a uso_diario.json.migrado.
- Al llegar al 80% del límite diario de un proveedor escribe un AVISO en el log
  (una sola vez por día y proveedor) y lo marca en GET /uso.
- Al llegar al 100% ese proveedor se salta y se usa directamente el siguiente.

Los límites se configuran en .env (GEMINI_LIMITE_DIARIO, GROQ_LIMITE_DIARIO).
El día es el de la computadora donde corre el backend.
"""

import json
import logging
import os
from datetime import date
from pathlib import Path

import bd

log = logging.getLogger("cecapi.uso")

ARCHIVO_VIEJO = Path(__file__).with_name("uso_diario.json")
UMBRAL_AVISO = 0.8


def hoy() -> str:
    return date.today().isoformat()


def limite(proveedor: str) -> int:
    """Límite diario del proveedor según .env; 0 significa 'sin límite conocido'."""
    try:
        return int(os.getenv(f"{proveedor.upper()}_LIMITE_DIARIO", "0"))
    except ValueError:
        return 0


def _usadas(con, proveedor: str, fecha: str) -> int:
    fila = con.execute(
        "SELECT peticiones FROM uso_diario WHERE fecha = ? AND proveedor = ?", (fecha, proveedor)
    ).fetchone()
    return fila["peticiones"] if fila else 0


def agotado(proveedor: str) -> bool:
    tope = limite(proveedor)
    if tope <= 0:
        return False
    with bd.leer() as con:
        return _usadas(con, proveedor, hoy()) >= tope


def registrar(proveedor: str) -> None:
    """Suma una petición al proveedor (cuenta aunque falle: la API también la cuenta)."""
    fecha = hoy()
    tope = limite(proveedor)
    with bd.escribir() as con:
        # Una sola sentencia suma y lee: dos peticiones al mismo tiempo no pierden una cuenta.
        usadas = con.execute(
            "INSERT INTO uso_diario (fecha, proveedor, peticiones) VALUES (?, ?, 1) "
            "ON CONFLICT (fecha, proveedor) DO UPDATE SET peticiones = peticiones + 1 "
            "RETURNING peticiones",
            (fecha, proveedor),
        ).fetchone()["peticiones"]
        # Solo la petición que cambia aviso_enviado de 0 a 1 escribe el aviso.
        avisar = tope > 0 and usadas >= tope * UMBRAL_AVISO and con.execute(
            "UPDATE uso_diario SET aviso_enviado = 1 WHERE fecha = ? AND proveedor = ? AND aviso_enviado = 0",
            (fecha, proveedor),
        ).rowcount == 1
    if avisar:
        log.warning(
            "AVISO: %s lleva %d de %d peticiones hoy (%d%%). Al llegar al 100%% se usará el respaldo.",
            proveedor, usadas, tope, round(usadas * 100 / tope),
        )


def resumen(proveedores: list[str]) -> dict:
    fecha = hoy()
    salida = {"fecha": fecha, "proveedores": {}}
    with bd.leer() as con:
        for nombre in proveedores:
            usadas = _usadas(con, nombre, fecha)
            tope = limite(nombre)
            porcentaje = round(usadas * 100 / tope) if tope > 0 else None
            salida["proveedores"][nombre] = {
                "usadas": usadas,
                "limite": tope or None,
                "porcentaje": porcentaje,
                "aviso": porcentaje is not None and porcentaje >= UMBRAL_AVISO * 100,
            }
    return salida


def importar_json(archivo: Path | None = None) -> bool:
    """Copia el uso_diario.json de antes a la tabla y lo renombra. True si había algo que copiar.

    Si el día ya tiene cuenta en la tabla se queda la mayor de las dos, así correrlo
    dos veces no suma doble.
    """
    archivo = archivo or ARCHIVO_VIEJO
    if not archivo.exists():
        return False
    try:
        datos = json.loads(archivo.read_text(encoding="utf-8"))
        fecha = str(datos["fecha"])
        conteo = {str(p): int(n) for p, n in datos.get("conteo", {}).items()}
        avisados = set(datos.get("avisados", []))
    except (json.JSONDecodeError, KeyError, TypeError, ValueError, AttributeError):
        log.warning("%s no se pudo leer; se deja como está y no se copia.", archivo.name)
        return False
    with bd.escribir() as con:
        for proveedor, peticiones in conteo.items():
            con.execute(
                "INSERT INTO uso_diario (fecha, proveedor, peticiones, aviso_enviado) VALUES (?, ?, ?, ?) "
                "ON CONFLICT (fecha, proveedor) DO UPDATE SET "
                "peticiones = MAX(peticiones, excluded.peticiones), "
                "aviso_enviado = MAX(aviso_enviado, excluded.aviso_enviado)",
                (fecha, proveedor, peticiones, int(proveedor in avisados)),
            )
    archivo.replace(archivo.with_name(archivo.name + ".migrado"))
    log.info("Uso del %s copiado de %s a la base de datos.", fecha, archivo.name)
    return True
