"""Corre las 30 preguntas (preguntas.py) y los 20 sinónimos (sinonimos.py) contra el servidor.

Usa la misma función que la ruta POST /entender de backend/main.py, sin necesidad de
arrancar uvicorn, y anota el resultado en docs/Pruebas/resultados.md.

Cómo correrlo (desde la carpeta del repo, con el entorno virtual activado):

    python docs/Pruebas/correr_pruebas.py            # las preguntas libres sí van a la IA (usa backend/.env)
    python docs/Pruebas/correr_pruebas.py --sin-ia   # solo revisa qué detecta como comando; no gasta cuota

Los sinónimos nunca se mandan a la IA: solo se revisa si el servidor los reconoce como
comando. Con IA se hacen unas 25 peticiones (las preguntas que no son comando), con una
pausa de 3 segundos entre cada una porque el plan gratis de Groq limita las peticiones por minuto.

Qué se espera de cada frase:
- Un código de módulo (ENVIRONMENT, DOCUMENT_READER...): el servidor debe regresar ese comando.
- "IA": no es comando; el servidor debe contestar con la IA.
- "teléfono": la resuelve la app antes de llegar al servidor (batería, hora, volumen,
  notificaciones, menú, sesión, repetir...). Aquí solo se revisa que el servidor no abra un
  módulo equivocado; si llegara, la contestaría la IA.
"""

import argparse
import os
import re
import sys
import time
from datetime import datetime
from pathlib import Path

AQUI = Path(__file__).resolve().parent
BACKEND = AQUI.parent.parent / "backend"

# Lo que debería pasar con cada pregunta (número -> esperado).
ESPERADO_PREGUNTAS = {
    1: "ENVIRONMENT", 2: "DOCUMENT_READER", 3: "DOCUMENT_READER",
    4: "teléfono", 5: "teléfono", 6: "teléfono", 7: "teléfono", 8: "teléfono", 9: "teléfono",
    10: "teléfono", 11: "REQUESTS", 12: "LEARNING", 13: "teléfono", 14: "teléfono", 15: "teléfono",
    **{n: "IA" for n in range(16, 31)},
}
# Lo que debería pasar con cada acción de sinonimos.py (número -> esperado).
ESPERADO_SINONIMOS = {
    1: "ENVIRONMENT", 2: "DOCUMENT_READER", 3: "DOCUMENT_READER",
    **{n: "teléfono" for n in range(4, 12)},
    12: "REQUESTS", 13: "LEARNING",
    **{n: "teléfono" for n in range(14, 20)},
    20: "IA",
}


def leer_preguntas() -> list[tuple[int, str]]:
    texto = (AQUI / "preguntas.py").read_text(encoding="utf-8")
    return [(int(n), p.strip()) for n, p in re.findall(r"^#\s*(\d+)\.\s+(.+)$", texto, re.MULTILINE)]


def leer_sinonimos() -> list[tuple[int, str, list[str]]]:
    texto = (AQUI / "sinonimos.py").read_text(encoding="utf-8")
    salida = []
    for n, accion, frases in re.findall(r"^#\s*(\d+)\.\s+([^:]+):\s*(.+)$", texto, re.MULTILINE):
        salida.append((int(n), accion.strip(), [f.strip() for f in frases.split(",") if f.strip()]))
    return salida


def cargar_servidor(sin_ia: bool):
    """Importa backend/main.py tal cual. Con --sin-ia, la IA se reemplaza por un aviso."""
    os.chdir(BACKEND)
    sys.path.insert(0, str(BACKEND))
    if sin_ia and not (os.getenv("GEMINI_API_KEY") or os.getenv("GROQ_API_KEY")):
        os.environ["GEMINI_API_KEY"] = "sin-ia"  # solo para que main.py arranque; nunca se usa
    import main
    import proveedores

    if sin_ia:
        def sin_llamar(pregunta, contexto):
            return "(no se llamó a la IA)", None, False
        proveedores.preguntar_con_respaldo = sin_llamar
    return main


def preguntar(main, frase: str) -> dict:
    inicio = time.perf_counter()
    try:
        r = main.entender(main.Pregunta(pregunta=frase))
    except Exception as error:  # HTTPException 503 u otro fallo
        r = {"tipo": "error", "respuesta": getattr(error, "detail", None) or type(error).__name__}
    r["ms"] = round((time.perf_counter() - inicio) * 1000)
    return r


def veredicto(esperado: str, r: dict, sin_ia: bool) -> str:
    comando = r.get("comando")
    if esperado not in ("IA", "teléfono"):
        return "✅" if comando == esperado else "❌"
    if comando:
        return "❌"  # abrió un módulo que no era
    if r.get("tipo") == "error":
        return "⚠️"
    if esperado == "teléfono":
        return "➖"
    if r.get("respuesta") == main_bloqueo():
        return "⚠️"
    return "✅" if sin_ia or r.get("respuesta") else "❌"


def main_bloqueo() -> str:
    import privacidad
    return privacidad.MENSAJE_BLOQUEO


def resultado_corto(r: dict) -> str:
    if r.get("tipo") == "comando":
        return f"comando `{r['comando']}`"
    if r.get("tipo") == "error":
        return f"error: {r.get('respuesta')}"
    if r.get("respuesta") == main_bloqueo():
        return "bloqueado por privacidad"
    proveedor = r.get("proveedor")
    texto = (r.get("respuesta") or "").replace("\n", " ").replace("|", "/")
    if proveedor is None and texto.startswith("(no se llamó"):
        return "iría a la IA"
    extra = " (hay más)" if r.get("hayMas") else ""
    return f"IA ({proveedor}): {texto}{extra}"


def main_cli() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--sin-ia", action="store_true", help="no llama a la IA; solo revisa los comandos")
    parser.add_argument("--salida", default=str(AQUI / "resultados.md"))
    parser.add_argument("--pausa", type=float, default=3.0,
                        help="segundos de espera después de cada respuesta de la IA (el plan gratis limita por minuto)")
    args = parser.parse_args()

    servidor = cargar_servidor(args.sin_ia)
    preguntas = leer_preguntas()
    sinonimos = leer_sinonimos()
    assert len(preguntas) == 30, f"Se esperaban 30 preguntas y hay {len(preguntas)}"
    assert len(sinonimos) == 20, f"Se esperaban 20 sinónimos y hay {len(sinonimos)}"

    filas_p, cuenta_p = [], {}
    for n, frase in preguntas:
        r = preguntar(servidor, frase)
        esperado = ESPERADO_PREGUNTAS[n]
        v = veredicto(esperado, r, args.sin_ia)
        cuenta_p[v] = cuenta_p.get(v, 0) + 1
        filas_p.append(f"| {n} | {frase} | {esperado} | {resultado_corto(r)} | {r['ms']} | {v} |")
        print(f"P{n:>2} {v} {frase} -> {resultado_corto(r)[:90]}")
        if not args.sin_ia and r.get("tipo") != "comando":
            time.sleep(args.pausa)

    # Los sinónimos nunca van a la IA: solo importa si se reconocen como comando.
    import proveedores
    original = proveedores.preguntar_con_respaldo
    proveedores.preguntar_con_respaldo = lambda pregunta, contexto: ("(no se llamó a la IA)", None, False)
    filas_s, cuenta_s, reconocidas = [], {}, 0
    total_frases = 0
    for n, accion, frases in sinonimos:
        esperado = ESPERADO_SINONIMOS[n]
        for frase in frases:
            total_frases += 1
            r = preguntar(servidor, frase)
            v = veredicto(esperado, r, True)
            cuenta_s[v] = cuenta_s.get(v, 0) + 1
            if v == "✅" and esperado not in ("IA", "teléfono"):
                reconocidas += 1
            filas_s.append(f"| {n} | {accion} | {frase} | {esperado} | {resultado_corto(r)} | {v} |")
            print(f"S{n:>2} {v} {frase} -> {resultado_corto(r)}")
    proveedores.preguntar_con_respaldo = original

    def resumen(cuenta: dict) -> str:
        nombres = {"✅": "correctas", "❌": "fallas", "➖": "las resuelve el teléfono", "⚠️": "avisos"}
        return " · ".join(f"{v} {cuenta[v]} {nombres[v]}" for v in ("✅", "❌", "➖", "⚠️") if v in cuenta)

    esperadas_modulo = sum(
        len(f) for n, _, f in sinonimos if ESPERADO_SINONIMOS[n] not in ("IA", "teléfono")
    )
    modo = "sin IA (solo detección de comandos)" if args.sin_ia else "con IA (backend/.env)"
    md = [
        "# Resultados: 30 preguntas y 20 sinónimos contra el servidor",
        "",
        f"> Generado por `docs/Pruebas/correr_pruebas.py` el {datetime.now():%d/%m/%Y %H:%M} · modo: {modo}",
        "",
        "Se usa la misma función que `POST /entender` (backend/main.py). Leyenda: ✅ correcto · ❌ falla · "
        "➖ la resuelve el teléfono antes de llegar al servidor (aquí solo se revisa que no abra un módulo equivocado) · "
        "⚠️ error o bloqueo.",
        "",
        "## Resumen",
        "",
        f"- **Preguntas (30):** {resumen(cuenta_p)}",
        f"- **Sinónimos ({total_frases} frases de 20 acciones):** {resumen(cuenta_s)}",
        f"- **Frases que deben abrir un módulo y el servidor reconoce:** {reconocidas} de {esperadas_modulo}",
        "",
        "## Preguntas",
        "",
        "| # | Pregunta | Esperado | Resultado del servidor | ms | |",
        "|---|---|---|---|---|---|",
        *filas_p,
        "",
        "## Sinónimos",
        "",
        "Los sinónimos no se mandan a la IA: solo se revisa si el servidor los reconoce como comando.",
        "",
        "| # | Acción | Frase | Esperado | Resultado del servidor | |",
        "|---|---|---|---|---|---|",
        *filas_s,
        "",
    ]
    Path(args.salida).write_text("\n".join(md), encoding="utf-8")
    print(f"\nPreguntas: {resumen(cuenta_p)}\nSinónimos: {resumen(cuenta_s)}\nGuardado en {args.salida}")


if __name__ == "__main__":
    main_cli()
