"""Pruebas del panel de uso (/panel y /panel/datos)."""

import base64
from datetime import date, datetime, time, timedelta

import pytest
from fastapi.testclient import TestClient

import db
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


def mediodia(dia: date) -> datetime:
    """Mediodía local de ese día (el panel cuenta días locales), como momento con zona."""
    return datetime.combine(dia, time(12)).astimezone()


def sembrar():
    """Datos de dos días: hoy y ayer, y uno de hace 30 días que queda fuera del periodo."""
    hoy = date.today()
    ayer = hoy - timedelta(days=1)
    viejo = hoy - timedelta(days=30)
    with db.transaccion() as con:
        con.execute(db.conversaciones.insert().values(
            id="c1", titulo="<script>alert(1)</script>", iniciada_en=db.ahora(), ultima_actividad=db.ahora()))
        filas = [
            # (día, ruta, rol, tipo, texto, comando, proveedor, duración)
            (hoy, "preguntar", "usuario", "pregunta", "TEXTO-SECRETO", None, None, None),
            (hoy, "preguntar", "asistente", "respuesta", "RESPUESTA-SECRETA", None, "groq", 400),
            (hoy, "entender", "usuario", "pregunta", None, None, None, None),
            (hoy, "entender", "asistente", "respuesta", None, None, "groq", 600),
            (hoy, "entender", "usuario", "comando", None, "ENVIRONMENT", None, None),
            (hoy, "preguntar", "usuario", "bloqueado", None, None, None, None),
            (ayer, "ver", "usuario", "pregunta", None, None, None, None),
            (ayer, "ver", "asistente", "respuesta", None, None, "gemini", 1000),
            (ayer, "documento", "usuario", "pregunta", None, None, None, None),
            (ayer, "documento", "asistente", "error", None, None, None, 50),
            (viejo, "entender", "usuario", "comando", None, "LEARNING", None, None),
        ]
        for dia, ruta, rol, tipo, texto, comando, proveedor, duracion in filas:
            con.execute(db.mensajes.insert().values(
                conversacion_id="c1", ruta=ruta, rol=rol, tipo=tipo, texto=texto, comando=comando,
                proveedor=proveedor, duracion_ms=duracion, creado_en=mediodia(dia)))
        for dia, proveedor, n in [(hoy, "groq", 5), (hoy, "groq_vision", 2), (ayer, "gemini", 1),
                                  (viejo, "groq", 99)]:
            con.execute(db.uso_diario.insert().values(fecha=dia.isoformat(), proveedor=proveedor, peticiones=n))
    return hoy.isoformat(), ayer.isoformat()


def test_datos_agregados():
    hoy, ayer = sembrar()
    d = cliente.get("/panel/datos").json()
    assert d["dias"] == 7 and d["hasta"] == hoy
    assert d["totales"] == {"preguntas": 4, "comandos": 1, "bloqueados": 1, "errores": 1}
    assert d["por_ruta"] == {"preguntar": 2, "entender": 2, "ver": 1, "documento": 1}
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
    assert d["hoy"]["proveedores"]["groq"]["usadas"] == 5  # lo mismo que /uso


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
    assert d["actividad"] == [] and d["tiempos"] == []
    assert cliente.get("/panel").status_code == 200


def test_solo_numeros_nunca_textos():
    sembrar()
    datos = cliente.get("/panel/datos").text
    for secreto in ("TEXTO-SECRETO", "RESPUESTA-SECRETA", "script", "c1"):
        assert secreto not in datos
    r = cliente.get("/panel")
    assert r.headers["content-type"].startswith("text/html")
    assert r.headers["cache-control"] == "no-store"
    assert "default-src 'none'" in r.headers["content-security-policy"]
    assert "connect-src 'self'" in r.headers["content-security-policy"]
    # La página no trae datos: los pide a panel/datos y escapa lo que imprime.
    assert "TEXTO-SECRETO" not in r.text and "panel/datos" in r.text and "esc(" in r.text


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
