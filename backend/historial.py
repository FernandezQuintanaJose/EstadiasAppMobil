"""
Guarda en la base de datos lo que pasa en /preguntar, /entender, /ver y /documento (Módulo 3).

- Cada petición agrega sus mensajes a una conversación. Si la app manda un conversacionId
  que existe, se sigue esa; si no, se crea una nueva con un UUID al azar.
- GUARDAR_TEXTOS (en .env) decide si se guarda lo que dijo la persona y lo que contestó la IA.
  Por defecto es 0: decisión 0005, no se guardan datos reales hasta que Pp lo autorice.
  Con 0 el texto queda en NULL y la conversación sin título; lo demás (ruta, tipo, comando,
  proveedor, tiempo) sí se guarda, y con eso sale el panel.
- Un mensaje bloqueado por privacidad nunca guarda su texto, aunque GUARDAR_TEXTOS sea 1.
- El contexto que manda la app y las fotos de /ver nunca se guardan.
- Si la base de datos falla, se anota en el log y la persona recibe su respuesta igual.

Escrito primero sobre SQLite directo (rama modulo03/base-datos, 25/10) y pasado a
SQLAlchemy el 30/10 para que funcione igual con Neon.
"""

import logging
import os
import uuid
from dataclasses import dataclass

from sqlalchemy import update

import db

log = logging.getLogger("cecapi.historial")

LARGO_TITULO = 60


@dataclass
class Mensaje:
    rol: str  # 'usuario' o 'asistente'
    tipo: str  # 'pregunta', 'respuesta', 'comando', 'bloqueado' o 'error'
    texto: str | None = None
    comando: str | None = None
    proveedor: str | None = None
    hay_mas: bool = False
    duracion_ms: int | None = None


def guardar_textos() -> bool:
    return os.getenv("GUARDAR_TEXTOS", "0").strip() == "1"


def _uuid_valido(valor: str | None) -> str | None:
    try:
        return str(uuid.UUID(valor)) if valor else None
    except ValueError:
        return None


def guardar(
    ruta: str, conversacion_id: str | None, mensajes: list[Mensaje], titulo: str | None = None,
) -> str | None:
    """Guarda los mensajes y regresa el conversacionId (None si la base de datos falló)."""
    con_textos = guardar_textos()
    try:
        with db.transaccion() as con:
            momento = db.ahora()
            conversacion_id = _uuid_valido(conversacion_id)
            existe = bool(conversacion_id) and con.execute(
                update(db.conversaciones)
                .where(db.conversaciones.c.id == conversacion_id)
                .values(ultima_actividad=momento)
            ).rowcount == 1
            if not existe:
                conversacion_id = str(uuid.uuid4())
                con.execute(db.conversaciones.insert().values(
                    id=conversacion_id,
                    titulo=titulo[:LARGO_TITULO] if con_textos and titulo else None,
                    iniciada_en=momento,
                    ultima_actividad=momento,
                ))
            for m in mensajes:
                con.execute(db.mensajes.insert().values(
                    conversacion_id=conversacion_id, ruta=ruta, rol=m.rol, tipo=m.tipo,
                    texto=m.texto if con_textos and m.tipo != "bloqueado" else None,
                    comando=m.comando, proveedor=m.proveedor, hay_mas=bool(m.hay_mas),
                    duracion_ms=m.duracion_ms, creado_en=momento,
                ))
        return conversacion_id
    except Exception:
        log.exception("No se pudo guardar la conversación; la respuesta se manda igual.")
        return None
