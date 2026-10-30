"""
Contador de peticiones por día y por proveedor (Módulo 3).

- Guarda los conteos en la tabla uso_diario de la base (db.py): SQLite en la computadora de
  cada quien, PostgreSQL en Neon cuando el servidor está publicado. Antes iban en
  uso_diario.json: al arrancar, si ese archivo existe, se copia a la tabla y se renombra a
  uso_diario.json.migrado (importar_json).
- Al llegar al 80% del límite diario de un proveedor escribe un AVISO en el log
  (una sola vez por día y proveedor) y lo marca en GET /uso.
- Al llegar al 100% ese proveedor se salta y se usa directamente el siguiente.

Los límites se configuran en .env (GEMINI_LIMITE_DIARIO, GROQ_LIMITE_DIARIO,
GROQ_VISION_LIMITE_DIARIO). El día es el de la computadora donde corre el backend
(en Render es la hora UTC).
"""

import json
import logging
import os
from datetime import date
from pathlib import Path

from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError

import db

log = logging.getLogger("cecapi.uso")

UMBRAL_AVISO = 0.8
ARCHIVO_VIEJO = Path(__file__).with_name("uso_diario.json")


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
    with db.leer() as conexion:
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
    tope = limite(proveedor)
    try:
        with db.transaccion() as conexion:
            usadas_, avisado = _sumar_uno(conexion, fecha, proveedor)
    except IntegrityError:
        # Otro proceso del servidor creó la fila del día al mismo tiempo (PostgreSQL): ya existe.
        with db.transaccion() as conexion:
            usadas_, avisado = _sumar_uno(conexion, fecha, proveedor)

    if tope > 0 and usadas_ >= tope * UMBRAL_AVISO and not avisado:
        with db.transaccion() as conexion:
            # Solo la petición que cambia aviso_enviado de falso a verdadero escribe el aviso.
            cambio = conexion.execute(
                update(db.uso_diario)
                .where(db.uso_diario.c.fecha == fecha, db.uso_diario.c.proveedor == proveedor,
                       db.uso_diario.c.aviso_enviado.is_(False))
                .values(aviso_enviado=True)
            ).rowcount
        if cambio == 1:
            log.warning(
                "AVISO: %s lleva %d de %d peticiones hoy (%d%%). Al llegar al 100%% se usará el respaldo.",
                proveedor, usadas_, tope, round(usadas_ * 100 / tope),
            )


def resumen(proveedores: list[str]) -> dict:
    fecha = hoy()
    with db.leer() as conexion:
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
    t = db.uso_diario.c
    with db.transaccion() as conexion:
        for proveedor, peticiones in conteo.items():
            fila = conexion.execute(
                select(t.peticiones, t.aviso_enviado).where(t.fecha == fecha, t.proveedor == proveedor)
            ).first()
            avisado = proveedor in avisados
            if fila is None:
                conexion.execute(db.uso_diario.insert().values(
                    fecha=fecha, proveedor=proveedor, peticiones=peticiones, aviso_enviado=avisado,
                ))
            else:
                conexion.execute(
                    update(db.uso_diario).where(t.fecha == fecha, t.proveedor == proveedor).values(
                        peticiones=max(fila.peticiones, peticiones),
                        aviso_enviado=bool(fila.aviso_enviado) or avisado,
                    )
                )
    archivo.replace(archivo.with_name(archivo.name + ".migrado"))
    log.info("Uso del %s copiado de %s a la base de datos.", fecha, archivo.name)
    return True
