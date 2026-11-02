"""
Números del panel de uso (Módulo 3): GET /panel (página) y GET /panel/datos (JSON).

Solo números agregados: nunca textos de mensajes ni identificadores de conversaciones.
Los días son los de la computadora del servidor, igual que en uso_diario (en Render se fija
TZ=America/Mexico_City para que el día cambie a la medianoche de México).

Primero se escribió con SQL de SQLite (rama modulo03/base-datos, 25/10); aquí se cuenta en
Python para que dé lo mismo en SQLite y en PostgreSQL.
"""

from collections import Counter, defaultdict
from datetime import date, datetime, time, timedelta

from sqlalchemy import select

import db

DIAS_POR_DEFECTO = 7
DIAS_MAXIMO = 90


def _desde(dias: int) -> tuple[date, datetime]:
    """Primer día del periodo (fecha local) y su medianoche local como momento con zona."""
    primero = date.today() - timedelta(days=dias - 1)
    medianoche = datetime.combine(primero, time.min).astimezone()  # zona de la computadora
    return primero, medianoche


def datos(dias: int, uso_hoy: dict) -> dict:
    primero, medianoche = _desde(dias)
    m = db.mensajes.c
    with db.leer() as con:
        filas = con.execute(
            select(m.creado_en, m.ruta, m.rol, m.tipo, m.proveedor, m.duracion_ms)
            .where(m.creado_en >= medianoche)
        ).all()
        peticiones = [
            {"fecha": f.fecha, "proveedor": f.proveedor, "peticiones": f.peticiones}
            for f in con.execute(
                select(db.uso_diario.c.fecha, db.uso_diario.c.proveedor, db.uso_diario.c.peticiones)
                .where(db.uso_diario.c.fecha >= primero.isoformat())
                .order_by(db.uso_diario.c.fecha.desc(), db.uso_diario.c.proveedor)
            )
        ]

    por_dia: dict[str, Counter] = defaultdict(Counter)
    por_ruta: Counter = Counter()
    tiempos: dict[str, list[int]] = defaultdict(list)
    columna = {"pregunta": "preguntas", "comando": "comandos", "bloqueado": "bloqueados"}
    for f in filas:
        dia = db.como_utc(f.creado_en).astimezone().date().isoformat()
        if f.rol == "usuario":
            por_ruta[f.ruta] += 1
            if f.tipo in columna:
                por_dia[dia][columna[f.tipo]] += 1
        elif f.tipo == "error":
            por_dia[dia]["errores"] += 1
        elif f.tipo == "respuesta" and f.proveedor and f.duracion_ms is not None:
            tiempos[f.proveedor].append(f.duracion_ms)

    campos = ("preguntas", "comandos", "bloqueados", "errores")
    actividad = [
        {"fecha": dia, **{c: conteo[c] for c in campos}}
        for dia, conteo in sorted(por_dia.items(), reverse=True)
    ]
    return {
        "dias": dias,
        "desde": primero.isoformat(),
        "hasta": date.today().isoformat(),
        "hoy": uso_hoy,
        "totales": {c: sum(a[c] for a in actividad) for c in campos},
        "por_ruta": {r: por_ruta[r] for r in db.RUTAS},
        "actividad": actividad,
        "peticiones": peticiones,
        "tiempos": [
            {"proveedor": p, "respuestas": len(ms), "promedio_ms": round(sum(ms) / len(ms))}
            for p, ms in sorted(tiempos.items())
        ],
    }
