"""Pruebas del panel de uso (/panel y /panel/datos)."""

import base64
from datetime import date, datetime, time, timedelta

import pytest
from fastapi.testclient import TestClient

import bd
import main
import seguridad

cliente = TestClient(main.app)


@pytest.fixture(autouse=True)
def limpio(monkeypatch):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "")
    monkeypatch.setenv("LIMITE_POR_MINUTO", "1000")
    seguridad.limitador.reiniciar()


def basic(contrasena: str, usuario: str = "admin") -> dict:
    return {"Authorization": "Basic " + base64.b64encode(f"{usuario}:{contrasena}".encode()).decode()}


def ms(dia: date, hora: int = 12) -> int:
    return int(datetime.combine(dia, time(hora)).timestamp() * 1000)


def sembrar():
    """Datos de dos días: hoy y ayer, y uno de hace 30 días que queda fuera del periodo."""
    hoy = date.today()
    ayer = hoy - timedelta(days=1)
    viejo = hoy - timedelta(days=30)
    with bd.escribir() as con:
        con.execute("INSERT INTO conversaciones (id, titulo, iniciada_en, ultima_actividad) "
                    "VALUES ('c1', '<script>alert(1)</script>', 0, 0)")
        filas = [
            # (día, rol, tipo, texto, comando, proveedor, duración)
            (hoy, "usuario", "pregunta", "TEXTO-SECRETO", None, None, None),
            (hoy, "asistente", "respuesta", "RESPUESTA-SECRETA", None, "groq", 400),
            (hoy, "usuario", "pregunta", None, None, None, None),
            (hoy, "asistente", "respuesta", None, None, "groq", 600),
            (hoy, "usuario", "comando", None, "ENVIRONMENT", None, None),
            (hoy, "usuario", "bloqueado", None, None, None, None),
            (ayer, "usuario", "pregunta", None, None, None, None),
            (ayer, "asistente", "respuesta", None, None, "gemini", 1000),
            (ayer, "usuario", "pregunta", None, None, None, None),
            (ayer, "asistente", "error", None, None, None, 50),
            (viejo, "usuario", "comando", None, "LEARNING", None, None),
        ]
        for dia, rol, tipo, texto, comando, proveedor, duracion in filas:
            con.execute(
                "INSERT INTO mensajes (conversacion_id, rol, tipo, texto, comando, proveedor, duracion_ms, creado_en) "
                "VALUES ('c1', ?, ?, ?, ?, ?, ?, ?)",
                (rol, tipo, texto, comando, proveedor, duracion, ms(dia)),
            )
        for dia, proveedor, n in [(hoy, "groq", 5), (hoy, "groq_vision", 2), (ayer, "gemini", 1),
                                  (viejo, "groq", 99)]:
            con.execute("INSERT INTO uso_diario (fecha, proveedor, peticiones) VALUES (?, ?, ?)",
                        (dia.isoformat(), proveedor, n))
    return hoy.isoformat(), ayer.isoformat()


def test_datos_agregados():
    hoy, ayer = sembrar()
    d = cliente.get("/panel/datos").json()
    assert d["dias"] == 7 and d["hasta"] == hoy
    assert d["totales"] == {"preguntas": 4, "comandos": 1, "bloqueados": 1, "errores": 1}
    assert d["actividad"] == [
        {"fecha": hoy, "preguntas": 2, "comandos": 1, "bloqueados": 1, "errores": 0},
        {"fecha": ayer, "preguntas": 2, "comandos": 0, "bloqueados": 0, "errores": 1},
    ]
    assert d["peticiones"] == [
        {"fecha": hoy, "proveedor": "groq", "peticiones": 5},
        {"fecha": hoy, "proveedor": "groq_vision", "peticiones": 2},
        {"fecha": ayer, "proveedor": "gemini", "peticiones": 1},
    ]
    assert d["tiempos"] == [
        {"proveedor": "gemini", "respuestas": 1, "promedio_ms": 1000},
        {"proveedor": "groq", "respuestas": 2, "promedio_ms": 500},
    ]


def test_dias_cambia_el_periodo():
    sembrar()
    d = cliente.get("/panel/datos", params={"dias": 1}).json()
    assert d["totales"] == {"preguntas": 2, "comandos": 1, "bloqueados": 1, "errores": 0}
    assert cliente.get("/panel/datos", params={"dias": 31}).json()["totales"]["comandos"] == 2


@pytest.mark.parametrize("dias", [0, 91, "muchos"])
def test_dias_fuera_de_rango(dias):
    assert cliente.get("/panel/datos", params={"dias": dias}).status_code == 422


def test_base_vacia():
    d = cliente.get("/panel/datos").json()
    assert d["totales"] == {"preguntas": 0, "comandos": 0, "bloqueados": 0, "errores": 0}
    r = cliente.get("/panel")
    assert r.status_code == 200
    assert "Todavía no hay mensajes" in r.text


def test_pagina_solo_numeros_nunca_textos():
    sembrar()
    r = cliente.get("/panel")
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("text/html")
    assert r.headers["cache-control"] == "no-store"
    assert "default-src 'none'" in r.headers["content-security-policy"]
    pagina = r.text
    assert '<html lang="es">' in pagina and "<caption>" in pagina and 'scope="col"' in pagina
    assert "<td>groq_vision</td>" in pagina and "<td>500</td>" in pagina
    for secreto in ("TEXTO-SECRETO", "RESPUESTA-SECRETA", "script", "c1"):
        assert secreto not in pagina
    assert "TEXTO-SECRETO" not in cliente.get("/panel/datos").text


def test_la_pagina_escapa_lo_que_imprime():
    with bd.escribir() as con:
        con.execute("INSERT INTO uso_diario (fecha, proveedor, peticiones) VALUES (?, '<b>x</b>&', 1)",
                    (date.today().isoformat(),))
    pagina = cliente.get("/panel").text
    assert "<b>x</b>" not in pagina
    assert "&lt;b&gt;x&lt;/b&gt;&amp;" in pagina


# --- Clave ------------------------------------------------------------------------

@pytest.mark.parametrize("ruta", ["/panel", "/panel/datos"])
@pytest.mark.parametrize("encabezados, esperado", [
    ({}, 401),
    ({"X-Clave-App": "equivocada"}, 401),
    (basic("equivocada"), 401),
    ({"Authorization": "Basic esto-no-es-base64"}, 401),
    ({"X-Clave-App": "secreta"}, 200),
    (basic("secreta"), 200),
    (basic("secreta", usuario="cualquiera"), 200),
])
def test_clave_del_panel(monkeypatch, ruta, encabezados, esperado):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "secreta")
    r = cliente.get(ruta, headers=encabezados)
    assert r.status_code == esperado
    if esperado == 401:
        assert r.headers["www-authenticate"].startswith("Basic")  # el navegador pide la contraseña
        assert r.json()["detail"] == "La app no tiene permiso para usar el asistente."


def test_sin_clave_configurada_el_panel_queda_abierto():
    assert cliente.get("/panel").status_code == 200
    assert cliente.get("/panel", headers={"Authorization": "Basic esto-no-es-base64"}).status_code == 200


def test_la_clave_no_se_acepta_en_la_liga(monkeypatch):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "secreta")
    assert cliente.get("/panel", params={"clave": "secreta"}).status_code == 401
    assert cliente.get("/panel", params={"x_clave_app": "secreta"}).status_code == 401


def test_el_panel_cuenta_en_el_limite_por_minuto(monkeypatch):
    monkeypatch.setenv("LIMITE_POR_MINUTO", "2")
    assert [cliente.get("/panel/datos").status_code for _ in range(3)] == [200, 200, 429]
