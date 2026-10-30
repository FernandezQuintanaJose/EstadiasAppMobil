"""
Contador de peticiones por día y por proveedor (Módulo 3).

- Guarda los conteos en la tabla uso_diario de la base (db.py): SQLite en la computadora de
  cada quien, PostgreSQL en Neon cuando el servidor está publicado. Antes vivían en
  uso_diario.json; ese archivo ya no se usa y se puede borrar.
- Al llegar al 80% del límite diario de un proveedor escribe un AVISO en el log
  (una sola vez por día y proveedor) y lo marca en GET /uso.
- Al llegar al 100% ese proveedor se salta y se usa directamente el siguiente.

Los límites se configuran en .env (GEMINI_LIMITE_DIARIO, GROQ_LIMITE_DIARIO,
GROQ_VISION_LIMITE_DIARIO). El día es el de la computadora donde corre el backend
(en Render es la hora UTC).
"""

import logging
import os
import threading
from datetime import date

from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError

import db

log = logging.getLogger("cecapi.uso")

UMBRAL_AVISO = 0.8

# Evita que dos hilos del mismo servidor se pisen al crear la fila del día.
_candado = threading.Lock()


def hoy() -> str:
    return date.today().isoformat()


def limite(proveedor: str) -> int:
    """Límite diario del proveedor según .env; 0 significa 'sin límite conocido'."""
    try:
        return int(os.getenv(f"{proveedor.upper()}_LIMITE_DIARIO", "0"))
    except ValueError:
        return 0


def _conteo(conexion, fecha: str) -> dict[str, tuple[int, bool]]:
    filas = conexion.execute(
        select(db.uso_diario.c.proveedor, db.uso_diario.c.peticiones, db.uso_diario.c.aviso_enviado)
        .where(db.uso_diario.c.fecha == fecha)
    ).all()
    return {proveedor: (usadas, bool(avisado)) for proveedor, usadas, avisado in filas}


def usadas(proveedor: str) -> int:
    with db.motor().connect() as conexion:
        return _conteo(conexion, hoy()).get(proveedor, (0, False))[0]


def agotado(proveedor: str) -> bool:
    tope = limite(proveedor)
    return tope > 0 and usadas(proveedor) >= tope


def _sumar_uno(conexion, fecha: str, proveedor: str) -> tuple[int, bool]:
    """Suma 1 a la fila del día y regresa (usadas, ya_avisado). La crea si no existe."""
    t = db.uso_diario.c
    cambio = conexion.execute(
        update(db.uso_diario)
        .where(t.fecha == fecha, t.proveedor == proveedor)
        .values(peticiones=t.peticiones + 1)
    )
    if cambio.rowcount == 0:
        conexion.execute(db.uso_diario.insert().values(
            fecha=fecha, proveedor=proveedor, peticiones=1, aviso_enviado=False,
        ))
    usadas_, avisado = conexion.execute(
        select(t.peticiones, t.aviso_enviado).where(t.fecha == fecha, t.proveedor == proveedor)
    ).one()
    return usadas_, bool(avisado)


def registrar(proveedor: str) -> None:
    """Suma una petición al proveedor (cuenta aunque falle: la API también la cuenta)."""
    fecha = hoy()
    with _candado:
        try:
            with db.motor().begin() as conexion:
                usadas_, avisado = _sumar_uno(conexion, fecha, proveedor)
        except IntegrityError:
            # Otro proceso del servidor creó la fila del día al mismo tiempo: ahora sí existe.
            with db.motor().begin() as conexion:
                usadas_, avisado = _sumar_uno(conexion, fecha, proveedor)

        tope = limite(proveedor)
        if tope > 0 and usadas_ >= tope * UMBRAL_AVISO and not avisado:
            with db.motor().begin() as conexion:
                conexion.execute(
                    update(db.uso_diario)
                    .where(db.uso_diario.c.fecha == fecha, db.uso_diario.c.proveedor == proveedor)
                    .values(aviso_enviado=True)
                )
            log.warning(
                "AVISO: %s lleva %d de %d peticiones hoy (%d%%). Al llegar al 100%% se usará el respaldo.",
                proveedor, usadas_, tope, round(usadas_ * 100 / tope),
            )


def resumen(proveedores: list[str]) -> dict:
    fecha = hoy()
    with db.motor().connect() as conexion:
        conteo = _conteo(conexion, fecha)
    salida = {"fecha": fecha, "proveedores": {}}
    for nombre in proveedores:
        usadas_ = conteo.get(nombre, (0, False))[0]
        tope = limite(nombre)
        porcentaje = round(usadas_ * 100 / tope) if tope > 0 else None
        salida["proveedores"][nombre] = {
            "usadas": usadas_,
            "limite": tope or None,
            "porcentaje": porcentaje,
            "aviso": porcentaje is not None and porcentaje >= UMBRAL_AVISO * 100,
        }
    return salida
