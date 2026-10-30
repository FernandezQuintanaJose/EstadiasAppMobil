"""
Base de datos del servidor del asistente (Módulo 3).

- En la computadora de cada quien: SQLite, en backend/cecapi.db (no se sube al repo).
- Publicado en Render: PostgreSQL en Neon (decisión 0017). Basta con poner DATABASE_URL
  en las variables de entorno del servidor; la cadena de conexión nunca va en la app ni en el repo.

Tablas (diseño de docs/referencia/tablas-servidor.md, sin usuarios ni proveedores todavía):
- conversaciones: una por chat. Su id es un UUID al azar (el conversacionId de la API), para que
  nadie pueda adivinar el de otra persona. usuario_id queda en NULL hasta que haya cuentas.
- mensajes: lo que pasó en cada petición (pregunta, respuesta, comando, bloqueado, error), con la
  ruta, la IA que contestó y cuánto tardó. El texto solo se guarda con GUARDAR_TEXTOS=1
  (historial.py); por defecto queda en NULL (decisión 0005). Nunca se guarda la foto, el
  contexto ni la IP.
- uso_diario: cuántas peticiones lleva cada proveedor cada día (lo que antes era uso_diario.json).

Las tablas se crean con Alembic (`alembic upgrade head`, dentro de backend/). Con SQLite, si
no existen, también se crean solas al arrancar, para que las pruebas y el uso local no pidan
un paso más.

Escrituras: usar `transaccion()`. Con SQLite toma además un candado, porque SQLite solo deja
escribir a uno a la vez y, sin él, varias peticiones al mismo tiempo chocarían con
"database is locked". PostgreSQL no lo necesita.
"""

import os
import threading
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from sqlalchemy import (
    Boolean, CheckConstraint, Column, DateTime, ForeignKey, Index, Integer, MetaData, String, Table, Text,
    create_engine, event,
)
from sqlalchemy.engine import Engine

AQUI = Path(__file__).resolve().parent

metadata = MetaData()

conversaciones = Table(
    "conversaciones", metadata,
    Column("id", String(36), primary_key=True, comment="UUID al azar: el conversacionId de la API"),
    Column("usuario_id", Integer, nullable=True, comment="NULL hasta que el servidor tenga cuentas"),
    Column("titulo", String(60), nullable=True, comment="primera pregunta; NULL si GUARDAR_TEXTOS=0"),
    Column("iniciada_en", DateTime(timezone=True), nullable=False),
    Column("ultima_actividad", DateTime(timezone=True), nullable=False),
    Index("ix_conversaciones_usuario", "usuario_id", "ultima_actividad"),
)

ROLES = ("usuario", "asistente")
TIPOS = ("pregunta", "respuesta", "comando", "bloqueado", "error")
RUTAS = ("preguntar", "entender", "ver", "documento")

mensajes = Table(
    "mensajes", metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("conversacion_id", String(36),
           ForeignKey("conversaciones.id", ondelete="CASCADE"), nullable=False),
    Column("ruta", String(20), nullable=False, comment="preguntar, entender, ver o documento"),
    Column("rol", String(10), nullable=False),
    Column("tipo", String(10), nullable=False),
    Column("texto", Text, nullable=True, comment="NULL si se bloqueó o si GUARDAR_TEXTOS=0"),
    Column("comando", String(40), nullable=True, comment="storageCode si tipo = comando"),
    Column("proveedor", String(40), nullable=True, comment="IA que contestó; NULL si no hubo IA"),
    Column("hay_mas", Boolean, nullable=False, default=False),
    Column("duracion_ms", Integer, nullable=True, comment="cuánto tardó la IA"),
    Column("creado_en", DateTime(timezone=True), nullable=False),
    CheckConstraint("rol IN ('usuario', 'asistente')", name="ck_mensajes_rol"),
    CheckConstraint("tipo IN ('pregunta', 'respuesta', 'comando', 'bloqueado', 'error')", name="ck_mensajes_tipo"),
    CheckConstraint("ruta IN ('preguntar', 'entender', 'ver', 'documento')", name="ck_mensajes_ruta"),
    CheckConstraint("tipo <> 'comando' OR comando IS NOT NULL", name="ck_mensajes_comando"),
    CheckConstraint("tipo <> 'bloqueado' OR texto IS NULL", name="ck_mensajes_bloqueado_sin_texto"),
    Index("ix_mensajes_conversacion", "conversacion_id", "creado_en"),
    Index("ix_mensajes_creado", "creado_en"),
)

uso_diario = Table(
    "uso_diario", metadata,
    Column("fecha", String(10), primary_key=True, comment="AAAA-MM-DD"),
    Column("proveedor", String(40), primary_key=True, comment="groq, gemini, groq_vision"),
    Column("peticiones", Integer, nullable=False, default=0),
    Column("aviso_enviado", Boolean, nullable=False, default=False, comment="ya avisó del 80 por ciento"),
)

_motor: Engine | None = None
_candado_motor = threading.Lock()
_candado_sqlite = threading.RLock()


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


def _ajustar_sqlite(conexion, _registro) -> None:
    cursor = conexion.cursor()
    cursor.execute("PRAGMA foreign_keys = ON")  # para que borrar una conversación borre sus mensajes
    cursor.execute("PRAGMA journal_mode = WAL")  # leer mientras otro escribe
    cursor.execute("PRAGMA synchronous = NORMAL")
    cursor.close()


def motor() -> Engine:
    global _motor
    with _candado_motor:
        if _motor is None:
            if es_sqlite():
                _motor = create_engine(url(), connect_args={"check_same_thread": False, "timeout": 10})
                event.listen(_motor, "connect", _ajustar_sqlite)
                metadata.create_all(_motor)
            else:
                # pool_pre_ping: Neon se suspende tras 5 minutos sin uso y corta las conexiones
                # abiertas; así se detecta la conexión muerta y se abre otra en vez de fallar.
                _motor = create_engine(url(), pool_pre_ping=True, pool_size=5, max_overflow=5)
        return _motor


def preparar() -> None:
    """Abre la conexión (y con SQLite crea las tablas que falten). Se llama al arrancar."""
    motor()


def reiniciar() -> None:
    """Cierra la conexión (al apagar el servidor y en las pruebas, que cambian de archivo)."""
    global _motor
    with _candado_motor:
        if _motor is not None:
            _motor.dispose()
        _motor = None


@contextmanager
def transaccion():
    """Conexión con transacción: COMMIT si todo sale bien, ROLLBACK si hay un error."""
    if es_sqlite():
        with _candado_sqlite, motor().begin() as conexion:
            yield conexion
    else:
        with motor().begin() as conexion:
            yield conexion


@contextmanager
def leer():
    with motor().connect() as conexion:
        yield conexion


def ahora() -> datetime:
    return datetime.now(timezone.utc)


def como_utc(momento: datetime) -> datetime:
    """SQLite regresa las fechas sin zona; se guardaron en UTC."""
    return momento if momento.tzinfo else momento.replace(tzinfo=timezone.utc)
