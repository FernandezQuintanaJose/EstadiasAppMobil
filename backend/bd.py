"""
Base de datos SQLite del servidor del asistente (Módulo 3).

- Archivo: cecapi.db junto a este archivo (se cambia con CECAPI_BD en .env). No se sube al repo.
- Una sola conexión, compartida por los hilos de FastAPI (check_same_thread=False) y
  cuidada por un candado: cada operación espera su turno. Se probó abrir una conexión por
  operación y en Windows cuesta unos 8 ms cada vez, contra 0.1 ms con la conexión abierta.
  Las operaciones son cortas (nunca se llama a la IA con el candado tomado).
- Modo WAL, BEGIN IMMEDIATE y `timeout`: si otro proceso abre el mismo archivo (por
  ejemplo, alguien revisándolo con sqlite3), las escrituras esperan en vez de fallar
  con "database is locked".
- Las tablas se crean con migraciones numeradas (PRAGMA user_version). Hoy hay tres:
  conversaciones, mensajes y uso_diario. Para agregar usuarios y proveedores basta con
  añadir la migración 2 al final de MIGRACIONES; las bases que ya existen se actualizan
  solas al arrancar, sin perder datos.

Diferencias con el diseño de database/servidor.sql, mientras no existan usuarios ni proveedores:
- conversaciones.id es un UUID al azar (texto), no un entero consecutivo: es el
  conversacionId que ve la app, y así no se puede adivinar el de otra persona.
- conversaciones.usuario_id no tiene llave foránea y por ahora siempre es NULL.
- mensajes y uso_diario guardan el nombre del proveedor (proveedor TEXT) en vez de proveedor_id.
"""

import os
import sqlite3
import threading
import time
from contextlib import contextmanager
from pathlib import Path

RUTA_POR_DEFECTO = Path(__file__).with_name("cecapi.db")
ESPERA_S = 10  # cuánto espera una escritura a que termine otra

# Cada migración lleva la base de la versión N a la N+1. Nunca se cambia una que ya
# se publicó: los cambios nuevos van en una migración nueva al final.
MIGRACIONES = [
    # 1: conversaciones, mensajes y uso_diario
    """
    CREATE TABLE conversaciones (
        id                  TEXT PRIMARY KEY,                -- UUID al azar: el conversacionId de la API
        usuario_id          INTEGER,                         -- NULL: la app todavía no dice quién pregunta
        titulo              TEXT,                            -- primera pregunta, recortada; NULL si GUARDAR_TEXTOS=0
        iniciada_en         INTEGER NOT NULL,                -- milisegundos desde 1970
        ultima_actividad    INTEGER NOT NULL
    );
    CREATE INDEX idx_conversaciones_usuario ON conversaciones(usuario_id, ultima_actividad);

    CREATE TABLE mensajes (
        id                  INTEGER PRIMARY KEY AUTOINCREMENT,
        conversacion_id     TEXT NOT NULL,
        rol                 TEXT NOT NULL CHECK (rol IN ('usuario', 'asistente')),
        tipo                TEXT NOT NULL CHECK (tipo IN ('pregunta', 'respuesta', 'comando', 'bloqueado', 'error')),
        texto               TEXT,                            -- NULL si se bloqueó o si GUARDAR_TEXTOS=0
        comando             TEXT,                            -- storageCode (ENVIRONMENT...) si tipo = 'comando'
        proveedor           TEXT,                            -- qué IA contestó ('groq', 'gemini'); NULL si no hubo IA
        hay_mas             INTEGER NOT NULL DEFAULT 0,      -- boolean: la IA marcó MAS_DISPONIBLE
        duracion_ms         INTEGER,                         -- cuánto tardó la IA
        creado_en           INTEGER NOT NULL,
        FOREIGN KEY (conversacion_id) REFERENCES conversaciones(id) ON DELETE CASCADE,
        CHECK (tipo <> 'comando' OR comando IS NOT NULL),
        CHECK (tipo <> 'bloqueado' OR texto IS NULL)
    );
    CREATE INDEX idx_mensajes_conversacion ON mensajes(conversacion_id, creado_en);
    CREATE INDEX idx_mensajes_creado ON mensajes(creado_en);
    CREATE INDEX idx_mensajes_proveedor ON mensajes(proveedor);

    -- Peticiones por día y proveedor (antes, uso_diario.json).
    CREATE TABLE uso_diario (
        fecha               TEXT NOT NULL,                   -- 'AAAA-MM-DD', día de la computadora del servidor
        proveedor           TEXT NOT NULL,                   -- 'groq', 'groq_vision', 'gemini'
        peticiones          INTEGER NOT NULL DEFAULT 0,      -- cuentan aunque la IA falle
        aviso_enviado       INTEGER NOT NULL DEFAULT 0,      -- boolean: ya se avisó del 80% hoy
        PRIMARY KEY (fecha, proveedor)
    );
    """,
]

_conexiones: dict[str, sqlite3.Connection] = {}
_candado = threading.RLock()  # RLock: leer() dentro de escribir() en el mismo hilo no se traba


def ruta() -> Path:
    return Path(os.getenv("CECAPI_BD", "").strip() or RUTA_POR_DEFECTO)


def ahora_ms() -> int:
    return int(time.time() * 1000)


def version(con: sqlite3.Connection) -> int:
    return con.execute("PRAGMA user_version").fetchone()[0]


def _abrir(archivo: Path) -> sqlite3.Connection:
    # isolation_level=None: las transacciones se abren a mano (BEGIN IMMEDIATE en escribir()).
    con = sqlite3.connect(archivo, timeout=ESPERA_S, isolation_level=None, check_same_thread=False)
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA foreign_keys = ON")
    # Con WAL, NORMAL no corrompe la base; si se va la luz, a lo más se pierde la última escritura.
    con.execute("PRAGMA synchronous = NORMAL")
    return con


def _migrar(con: sqlite3.Connection) -> None:
    """Activa WAL y aplica, en una sola transacción, las migraciones que falten."""
    con.execute("PRAGMA journal_mode = WAL")  # se queda guardado en el archivo
    con.execute("BEGIN IMMEDIATE")
    try:
        for numero in range(version(con), len(MIGRACIONES)):
            for sentencia in _sentencias(MIGRACIONES[numero]):
                con.execute(sentencia)
            con.execute(f"PRAGMA user_version = {numero + 1}")
        con.execute("COMMIT")
    except BaseException:
        con.execute("ROLLBACK")
        raise


def _sentencias(script: str) -> list[str]:
    # executescript() haría COMMIT por su cuenta; así la migración entera queda en una transacción.
    sin_comentarios = "\n".join(linea.split("--")[0] for linea in script.splitlines())
    return [s.strip() for s in sin_comentarios.split(";") if s.strip()]


def _conexion() -> sqlite3.Connection:
    """La conexión abierta de ruta(); la abre y migra la primera vez. Llamar con _candado tomado."""
    archivo = ruta()
    con = _conexiones.get(str(archivo))
    if con is not None and archivo.exists():
        return con
    if con is not None:  # alguien borró el archivo: se vuelve a crear con sus tablas
        con.close()
        del _conexiones[str(archivo)]
    con = _abrir(archivo)
    try:
        _migrar(con)
    except BaseException:
        con.close()
        raise
    _conexiones[str(archivo)] = con
    return con


def preparar() -> None:
    """Crea el archivo si no existe, activa WAL y aplica las migraciones que falten."""
    with _candado:
        _conexion()


def cerrar() -> None:
    """Cierra las conexiones abiertas (al apagar el servidor y en las pruebas)."""
    with _candado:
        for con in _conexiones.values():
            con.close()
        _conexiones.clear()


@contextmanager
def leer():
    """Conexión para consultas, con el candado tomado mientras dura el with."""
    with _candado:
        yield _conexion()


@contextmanager
def escribir():
    """Conexión con transacción: COMMIT si todo sale bien, ROLLBACK si hay un error."""
    with _candado:
        con = _conexion()
        con.execute("BEGIN IMMEDIATE")
        try:
            yield con
            con.execute("COMMIT")
        except BaseException:
            con.execute("ROLLBACK")
            raise
