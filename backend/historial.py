"""
Guarda en la base de datos lo que pasa en /preguntar y /entender (Módulo 3).

- Cada petición agrega sus mensajes a una conversación. Si la app manda un
  conversacionId que existe, se sigue esa; si no, se crea una nueva con un UUID al azar.
- GUARDAR_TEXTOS (en .env) decide si se guarda lo que dijo la persona y lo que contestó la IA.
  Por defecto es 0: decisión 0005, no se guardan datos reales hasta que Pp lo autorice.
  Con 0 el texto queda en NULL y la conversación sin título; lo demás (tipo, comando,
  proveedor, tiempo) sí se guarda, y con eso sale el panel.
- Un mensaje bloqueado por privacidad nunca guarda su texto, aunque GUARDAR_TEXTOS sea 1.
- El contexto que manda la app nunca se guarda.
- Si la base de datos falla, se anota en el log y la persona recibe su respuesta igual.
"""

import logging
import os
import uuid
from dataclasses import dataclass

import bd

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


def guardar(conversacion_id: str | None, mensajes: list[Mensaje], titulo: str | None = None) -> str | None:
    """Guarda los mensajes y regresa el conversacionId (None si la base de datos falló)."""
    con_textos = guardar_textos()
    try:
        with bd.escribir() as con:
            momento = bd.ahora_ms()
            conversacion_id = _uuid_valido(conversacion_id)
            existe = conversacion_id and con.execute(
                "UPDATE conversaciones SET ultima_actividad = ? WHERE id = ?", (momento, conversacion_id)
            ).rowcount == 1
            if not existe:
                conversacion_id = str(uuid.uuid4())
                con.execute(
                    "INSERT INTO conversaciones (id, titulo, iniciada_en, ultima_actividad) VALUES (?, ?, ?, ?)",
                    (conversacion_id, titulo[:LARGO_TITULO] if con_textos and titulo else None, momento, momento),
                )
            for m in mensajes:
                texto = m.texto if con_textos and m.tipo != "bloqueado" else None
                con.execute(
                    "INSERT INTO mensajes (conversacion_id, rol, tipo, texto, comando, proveedor, hay_mas, "
                    "duracion_ms, creado_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    (conversacion_id, m.rol, m.tipo, texto, m.comando, m.proveedor, int(m.hay_mas),
                     m.duracion_ms, momento),
                )
        return conversacion_id
    except Exception:
        log.exception("No se pudo guardar la conversación; la respuesta se manda igual.")
        return None
