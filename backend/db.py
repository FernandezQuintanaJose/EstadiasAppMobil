"""
Base de datos del servidor del asistente (Módulo 3).

- En la computadora de cada quien: SQLite, en backend/cecapi.db (no se sube al repo).
- Publicado en Render: PostgreSQL en Neon (decisión 0017). Basta con poner DATABASE_URL
  en las variables de entorno del servidor; la cadena de conexión nunca va en la app ni en el repo.

Qué se guarda (y qué NO):
- uso_diario: cuántas peticiones lleva cada proveedor cada día (lo que antes era uso_diario.json).
- peticiones: una fila por petición a /preguntar, /entender, /ver o /documento con la ruta,
  el resultado, la IA que contestó y cuánto tardó. NO se guarda el texto de la pregunta, ni la
  respuesta, ni la foto, ni la IP: no hay datos reales autorizados todavía (decisión 0005).

Las tablas de conversaciones, mensajes y usuarios de docs/referencia/tablas-servidor.md
esperan a que existan las cuentas del servidor.

Las tablas se crean con Alembic (`alembic upgrade head`, dentro de backend/). Con SQLite, si
no existen, también se crean solas al arrancar, para que las pruebas y el uso local no pidan
un paso más.
"""

import os
import threading
from datetime import datetime, timezone
from pathlib import Path

from sqlalchemy import (
    Boolean, Column, DateTime, Integer, MetaData, String, Table, create_engine, func, select,
)
from sqlalchemy.engine import Engine

AQUI = Path(__file__).resolve().parent

metadata = MetaData()

uso_diario = Table(
    "uso_diario", metadata,
    Column("fecha", String(10), primary_key=True, comment="AAAA-MM-DD"),
    Column("proveedor", String(40), primary_key=True, comment="groq, gemini, groq_vision"),
    Column("peticiones", Integer, nullable=False, default=0),
    Column("aviso_enviado", Boolean, nullable=False, default=False, comment="ya avisó del 80 por ciento"),
)

peticiones = Table(
    "peticiones", metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("creado_en", DateTime(timezone=True), nullable=False, index=True),
    Column("ruta", String(20), nullable=False, comment="preguntar, entender, ver, documento"),
    Column("resultado", String(20), nullable=False,
           comment="respuesta, comando, bloqueado, error, no_disponible"),
    Column("proveedor", String(40), nullable=True, comment="NULL si no se llamó a la IA"),
    Column("duracion_ms", Integer, nullable=False),
)

RESULTADOS = {"respuesta", "comando", "bloqueado", "error", "no_disponible"}

_motor: Engine | None = None
_candado = threading.Lock()


def url() -> str:
    """DATABASE_URL de .env, ya lista para SQLAlchemy con el controlador psycopg."""
    valor = os.getenv("DATABASE_URL", "").strip()
    if not valor:
        return f"sqlite:///{AQUI / 'cecapi.db'}"
    # Neon y Render dan la cadena como postgres:// o postgresql://; SQLAlchemy necesita el controlador.
    for prefijo in ("postgres://", "postgresql://"):
        if valor.startswith(prefijo):
            return "postgresql+psycopg://" + valor[len(prefijo):]
    return valor


def es_sqlite() -> bool:
    return url().startswith("sqlite")


def motor() -> Engine:
    global _motor
    with _candado:
        if _motor is None:
            if es_sqlite():
                _motor = create_engine(url(), connect_args={"check_same_thread": False})
                metadata.create_all(_motor)
            else:
                # pool_pre_ping: Neon se suspende tras 5 minutos sin uso y corta las conexiones
                # abiertas; así se detecta la conexión muerta y se abre otra en vez de fallar.
                _motor = create_engine(url(), pool_pre_ping=True, pool_size=5, max_overflow=5)
        return _motor


def reiniciar() -> None:
    """Olvida la conexión (las pruebas la cambian de un archivo a otro)."""
    global _motor
    with _candado:
        if _motor is not None:
            _motor.dispose()
        _motor = None


def ahora() -> datetime:
    return datetime.now(timezone.utc)


def anotar_peticion(ruta: str, resultado: str, proveedor: str | None, duracion_ms: int) -> None:
    """Guarda una petición sin su contenido. Si la base falla, el asistente sigue contestando."""
    if resultado not in RESULTADOS:
        raise ValueError(f"Resultado desconocido: {resultado}")
    with motor().begin() as conexion:
        conexion.execute(peticiones.insert().values(
            creado_en=ahora(), ruta=ruta, resultado=resultado,
            proveedor=proveedor, duracion_ms=max(0, int(duracion_ms)),
        ))


def estadisticas(desde: datetime) -> dict:
    """Totales desde una fecha: por ruta, por resultado, por proveedor y tiempo promedio."""
    filtro = peticiones.c.creado_en >= desde
    with motor().connect() as conexion:
        def contar(columna):
            filas = conexion.execute(
                select(columna, func.count()).where(filtro).group_by(columna)
            ).all()
            return {(clave if clave is not None else "ninguno"): total for clave, total in filas}

        total, promedio = conexion.execute(
            select(func.count(), func.avg(peticiones.c.duracion_ms)).where(filtro)
        ).one()
        # Tiempo promedio solo de las que sí llamaron a la IA: los comandos tardan 0 ms y lo esconderían.
        promedio_ia = conexion.execute(
            select(func.avg(peticiones.c.duracion_ms))
            .where(filtro, peticiones.c.proveedor.is_not(None))
        ).scalar()
        return {
            "total": total,
            "por_ruta": contar(peticiones.c.ruta),
            "por_resultado": contar(peticiones.c.resultado),
            "por_proveedor": contar(peticiones.c.proveedor),
            "promedio_ms": round(promedio) if promedio is not None else None,
            "promedio_ia_ms": round(promedio_ia) if promedio_ia is not None else None,
        }


def uso_por_dia(desde: str) -> list[dict]:
    """Filas de uso_diario desde una fecha (AAAA-MM-DD), de la más vieja a la más nueva."""
    with motor().connect() as conexion:
        filas = conexion.execute(
            select(uso_diario.c.fecha, uso_diario.c.proveedor, uso_diario.c.peticiones)
            .where(uso_diario.c.fecha >= desde)
            .order_by(uso_diario.c.fecha, uso_diario.c.proveedor)
        ).all()
    return [{"fecha": f, "proveedor": p, "peticiones": n} for f, p, n in filas]
