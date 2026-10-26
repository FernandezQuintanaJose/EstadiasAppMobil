"""
Panel de uso del asistente (Módulo 3): GET /panel (página) y GET /panel/datos (JSON).

Solo muestra números agregados, nunca textos de mensajes ni identificadores de
conversaciones. Todo lo que se imprime en la página pasa por html.escape.

Los días son los de la computadora del servidor, igual que en uso_diario.
"""

from datetime import date, datetime, time, timedelta
from html import escape

import bd

DIAS_POR_DEFECTO = 7
DIAS_MAXIMO = 90


def _desde(dias: int) -> tuple[str, int]:
    """Primer día del periodo, como texto (para uso_diario) y en milisegundos (para mensajes)."""
    primero = date.today() - timedelta(days=dias - 1)
    return primero.isoformat(), int(datetime.combine(primero, time.min).timestamp() * 1000)


def datos(dias: int = DIAS_POR_DEFECTO) -> dict:
    desde, desde_ms = _desde(dias)
    with bd.leer() as con:
        peticiones = [
            {"fecha": f["fecha"], "proveedor": f["proveedor"], "peticiones": f["peticiones"]}
            for f in con.execute(
                "SELECT fecha, proveedor, peticiones FROM uso_diario WHERE fecha >= ? "
                "ORDER BY fecha DESC, proveedor",
                (desde,),
            )
        ]
        tiempos = [
            {"proveedor": f["proveedor"], "respuestas": f["n"], "promedio_ms": round(f["promedio"])}
            for f in con.execute(
                "SELECT proveedor, COUNT(*) AS n, AVG(duracion_ms) AS promedio FROM mensajes "
                "WHERE rol = 'asistente' AND tipo = 'respuesta' AND proveedor IS NOT NULL "
                "AND duracion_ms IS NOT NULL AND creado_en >= ? GROUP BY proveedor ORDER BY proveedor",
                (desde_ms,),
            )
        ]
        por_dia: dict[str, dict] = {}
        for f in con.execute(
            "SELECT date(creado_en / 1000, 'unixepoch', 'localtime') AS fecha, rol, tipo, COUNT(*) AS n "
            "FROM mensajes WHERE creado_en >= ? AND ((rol = 'usuario') OR (rol = 'asistente' AND tipo = 'error')) "
            "GROUP BY fecha, rol, tipo",
            (desde_ms,),
        ):
            dia = por_dia.setdefault(f["fecha"], {"fecha": f["fecha"], "comandos": 0, "preguntas": 0,
                                                  "bloqueados": 0, "errores": 0})
            columna = {"comando": "comandos", "pregunta": "preguntas", "bloqueado": "bloqueados",
                       "error": "errores"}.get(f["tipo"])
            if columna:
                dia[columna] += f["n"]
    actividad = sorted(por_dia.values(), key=lambda d: d["fecha"], reverse=True)
    totales = {c: sum(d[c] for d in actividad) for c in ("comandos", "preguntas", "bloqueados", "errores")}
    return {
        "dias": dias,
        "desde": desde,
        "hasta": date.today().isoformat(),
        "totales": totales,
        "actividad": actividad,
        "peticiones": peticiones,
        "tiempos": tiempos,
    }


def _tabla(titulo: str, columnas: list[str], filas: list[list], vacia: str) -> str:
    if not filas:
        return f"<h2>{escape(titulo)}</h2>\n<p>{escape(vacia)}</p>"
    encabezado = "".join(f'<th scope="col">{escape(c)}</th>' for c in columnas)
    cuerpo = "\n".join(
        "<tr>" + "".join(f"<td>{escape(str(v))}</td>" for v in fila) + "</tr>" for fila in filas
    )
    return (f"<h2>{escape(titulo)}</h2>\n<table>\n<caption>{escape(titulo)}</caption>\n"
            f"<thead><tr>{encabezado}</tr></thead>\n<tbody>\n{cuerpo}\n</tbody>\n</table>")


def html(d: dict) -> str:
    t = {clave: escape(str(valor)) for clave, valor in d["totales"].items()}
    resumen = (
        f"<ul>\n<li>Preguntas: {t['preguntas']}</li>\n<li>Comandos: {t['comandos']}</li>\n"
        f"<li>Bloqueadas por privacidad: {t['bloqueados']}</li>\n<li>Errores de la IA: {t['errores']}</li>\n</ul>"
    )
    secciones = [
        _tabla("Actividad por día", ["Día", "Preguntas", "Comandos", "Bloqueadas por privacidad", "Errores"],
               [[a["fecha"], a["preguntas"], a["comandos"], a["bloqueados"], a["errores"]] for a in d["actividad"]],
               "Todavía no hay mensajes en este periodo."),
        _tabla("Peticiones a la IA por día y proveedor", ["Día", "Proveedor", "Peticiones"],
               [[p["fecha"], p["proveedor"], p["peticiones"]] for p in d["peticiones"]],
               "Todavía no hay peticiones en este periodo."),
        _tabla("Tiempo promedio de respuesta", ["Proveedor", "Respuestas", "Promedio (ms)"],
               [[x["proveedor"], x["respuestas"], x["promedio_ms"]] for x in d["tiempos"]],
               "Todavía no hay respuestas en este periodo."),
    ]
    periodo = f"Del {d['desde']} al {d['hasta']} ({d['dias']} días)."
    return f"""<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Panel de uso · Asistente CECAPI</title>
<style>
body {{ font-family: system-ui, sans-serif; font-size: 1.125rem; line-height: 1.5; margin: 1rem auto;
       max-width: 50rem; padding: 0 1rem; color: #111; background: #fff; }}
table {{ border-collapse: collapse; width: 100%; margin-bottom: 1.5rem; }}
caption {{ position: absolute; left: -9999px; }}
th, td {{ border: 1px solid #555; padding: .4rem .6rem; text-align: left; }}
th {{ background: #e8e8e8; }}
</style>
</head>
<body>
<main>
<h1>Panel de uso del asistente</h1>
<p>{escape(periodo)} Solo números: aquí no se muestran textos de mensajes.</p>
<h2>Resumen</h2>
{resumen}
{chr(10).join(secciones)}
</main>
</body>
</html>
"""
