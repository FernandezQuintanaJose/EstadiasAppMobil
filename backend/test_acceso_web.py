"""Quién llega al servidor desde internet: IP del límite por minuto, CORS, documentación, zona y retención."""

from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import func, select

import db
import historial
import main
import proveedores
import seguridad
import uso
from historial import Mensaje

cliente = TestClient(main.app)
PAGINA = "https://fernandezquintanajose.github.io"


@pytest.fixture(autouse=True)
def ia(monkeypatch):
    monkeypatch.setattr(proveedores, "preguntar_con_respaldo", lambda p, c, instrucciones=None: ("Respuesta.", "groq", False))
    monkeypatch.setenv("CECAPI_CLAVE_APP", "")
    monkeypatch.setenv("LIMITE_POR_MINUTO", "1000")
    seguridad.limitador.reiniciar()


# --- IP real -------------------------------------------------------------------------


def test_sin_proxies_el_encabezado_no_cuenta(monkeypatch):
    monkeypatch.setenv("PROXIES_DE_CONFIANZA", "0")
    assert seguridad.ip_cliente(["1.2.3.4"], "9.9.9.9") == "9.9.9.9"


def test_con_un_proxy_se_toma_lo_que_el_agrego(monkeypatch):
    monkeypatch.setenv("PROXIES_DE_CONFIANZA", "1")
    assert seguridad.ip_cliente(["1.2.3.4, 5.5.5.5"], "9.9.9.9") == "5.5.5.5"


def test_con_dos_proxies_se_cuenta_desde_la_derecha(monkeypatch):
    monkeypatch.setenv("PROXIES_DE_CONFIANZA", "2")
    assert seguridad.ip_cliente(["falsa, 5.5.5.5, 6.6.6.6"], "9.9.9.9") == "5.5.5.5"


def test_varios_encabezados_cuentan_como_una_lista(monkeypatch):
    monkeypatch.setenv("PROXIES_DE_CONFIANZA", "1")
    assert seguridad.ip_cliente(["1.2.3.4", "5.5.5.5"], "9.9.9.9") == "5.5.5.5"


@pytest.mark.parametrize("reenviada", [[], [""], ["no-es-ip"], ["1.2.3.4, <script>"]])
def test_un_encabezado_raro_usa_la_conexion(monkeypatch, reenviada):
    monkeypatch.setenv("PROXIES_DE_CONFIANZA", "1")
    assert seguridad.ip_cliente(reenviada, "9.9.9.9") == "9.9.9.9"


def test_menos_entradas_que_proxies_usa_la_conexion(monkeypatch):
    monkeypatch.setenv("PROXIES_DE_CONFIANZA", "3")
    assert seguridad.ip_cliente(["1.2.3.4, 5.5.5.5"], "9.9.9.9") == "9.9.9.9"


@pytest.mark.parametrize("valor", ["x", "-1", ""])
def test_un_numero_de_proxies_malo_no_confia_en_nadie(monkeypatch, valor):
    monkeypatch.setenv("PROXIES_DE_CONFIANZA", valor)
    assert seguridad.ip_cliente(["1.2.3.4"], "9.9.9.9") == "9.9.9.9"


def test_sin_conexion_conocida_no_truena(monkeypatch):
    monkeypatch.setenv("PROXIES_DE_CONFIANZA", "0")
    assert seguridad.ip_cliente([], None) == "desconocido"


def _pedir_con_ip_falsa(falsa: str):
    # Lo que escribe el atacante queda a la izquierda; el proxy agrega la IP real al final.
    return cliente.post("/preguntar", json={"pregunta": "Hola"}, headers={"X-Forwarded-For": f"{falsa}, 5.5.5.5"})


def test_cambiar_la_ip_falsa_no_salta_el_limite(monkeypatch):
    monkeypatch.setenv("PROXIES_DE_CONFIANZA", "1")
    monkeypatch.setenv("LIMITE_POR_MINUTO", "2")
    assert _pedir_con_ip_falsa("1.1.1.1").status_code == 200
    assert _pedir_con_ip_falsa("2.2.2.2").status_code == 200
    assert _pedir_con_ip_falsa("3.3.3.3").status_code == 429


def test_dos_personas_tienen_limites_separados(monkeypatch):
    monkeypatch.setenv("PROXIES_DE_CONFIANZA", "1")
    monkeypatch.setenv("LIMITE_POR_MINUTO", "1")
    for ip in ("5.5.5.5", "6.6.6.6"):
        r = cliente.post("/preguntar", json={"pregunta": "Hola"}, headers={"X-Forwarded-For": ip})
        assert r.status_code == 200


def test_render_no_confia_en_uvicorn_para_la_ip():
    arranque = next(
        linea for linea in (Path(__file__).parent.parent / "render.yaml").read_text(encoding="utf-8").splitlines()
        if "startCommand" in linea
    )
    assert "--no-proxy-headers" in arranque and "--forwarded-allow-ips" not in arranque


# --- CORS ----------------------------------------------------------------------------


def _preflight(origen: str, metodo: str = "POST", encabezados: str = "content-type,x-clave-app"):
    return cliente.options("/preguntar", headers={
        "Origin": origen, "Access-Control-Request-Method": metodo, "Access-Control-Request-Headers": encabezados,
    })


def test_la_pagina_del_asistente_puede_preguntar():
    r = _preflight(PAGINA)
    assert r.status_code == 200 and r.headers["access-control-allow-origin"] == PAGINA


@pytest.mark.parametrize("origen", [
    "https://otra-pagina.example",
    "http://fernandezquintanajose.github.io",
    "https://fernandezquintanajose.github.io.evil.example",
    "null",
])
def test_otras_paginas_no(origen):
    r = _preflight(origen)
    assert r.status_code == 400 and "access-control-allow-origin" not in r.headers


def test_una_respuesta_a_otra_pagina_no_la_autoriza():
    r = cliente.post("/preguntar", json={"pregunta": "Hola"}, headers={"Origin": "https://otra-pagina.example"})
    assert "access-control-allow-origin" not in r.headers


def test_no_se_permiten_otros_metodos_ni_cookies():
    assert _preflight(PAGINA, metodo="DELETE").status_code == 400
    assert _preflight(PAGINA, encabezados="x-otro").status_code == 400
    assert "access-control-allow-credentials" not in _preflight(PAGINA).headers


def test_la_lista_de_origenes_ignora_el_comodin(monkeypatch):
    monkeypatch.setenv("ORIGENES_WEB", f"*, {PAGINA}/ ,https://otro.example,")
    assert seguridad.origenes_web() == [PAGINA, "https://otro.example"]


# --- Documentación -------------------------------------------------------------------


@pytest.mark.parametrize("ruta", ["/docs", "/redoc", "/openapi.json"])
def test_la_documentacion_no_esta_publicada(ruta):
    assert cliente.get(ruta).status_code == 404


def test_la_documentacion_se_prende_solo_con_1(monkeypatch):
    monkeypatch.setenv("MOSTRAR_DOCS", "1")
    assert seguridad.rutas_de_documentacion()["docs_url"] == "/docs"
    for valor in ("0", "", "true", "si"):
        monkeypatch.setenv("MOSTRAR_DOCS", valor)
        assert seguridad.rutas_de_documentacion() == {"docs_url": None, "redoc_url": None, "openapi_url": None}


# --- Zona horaria --------------------------------------------------------------------


def test_el_dia_es_el_de_cecapi_y_no_el_de_utc():
    # La 1 de la mañana en UTC todavía es el día anterior en Durango.
    assert db.hoy(datetime(2026, 11, 4, 1, 0, tzinfo=timezone.utc)).isoformat() == "2026-11-03"
    assert db.hoy(datetime(2026, 11, 4, 7, 0, tzinfo=timezone.utc)).isoformat() == "2026-11-04"


def test_el_contador_usa_el_dia_de_cecapi(monkeypatch):
    monkeypatch.setattr(db, "ahora", lambda: datetime(2026, 11, 4, 1, 0, tzinfo=timezone.utc))
    assert uso.hoy() == "2026-11-03"


# --- Retención -----------------------------------------------------------------------


AHORA = datetime(2026, 11, 4, 12, 0, tzinfo=timezone.utc)


def _guardar_en(momento: datetime, monkeypatch, conversacion: str | None = None) -> str:
    monkeypatch.setattr(db, "ahora", lambda: momento)
    monkeypatch.setattr(historial, "_ultima_purga", db.hoy(momento))  # que guardar no purgue por su cuenta
    return historial.guardar("preguntar", conversacion, [Mensaje(rol="usuario", tipo="pregunta")])


def _cuantos(tabla) -> int:
    with db.leer() as con:
        return con.execute(select(func.count()).select_from(tabla)).scalar_one()


def test_borra_lo_viejo_y_deja_lo_nuevo(monkeypatch):
    _guardar_en(AHORA - timedelta(days=91), monkeypatch)
    _guardar_en(AHORA - timedelta(days=89), monkeypatch)
    assert historial.purgar(AHORA) == 1
    assert _cuantos(db.mensajes) == 1 and _cuantos(db.conversaciones) == 1


def test_una_conversacion_activa_pierde_solo_sus_mensajes_viejos(monkeypatch):
    conversacion = _guardar_en(AHORA - timedelta(days=91), monkeypatch)
    _guardar_en(AHORA, monkeypatch, conversacion)
    assert historial.purgar(AHORA) == 1
    assert _cuantos(db.mensajes) == 1 and _cuantos(db.conversaciones) == 1


def test_tambien_borra_conteos_viejos():
    viejo = (AHORA - timedelta(days=100)).date().isoformat()
    with db.transaccion() as con:
        for fecha in (viejo, AHORA.date().isoformat()):
            con.execute(db.uso_diario.insert().values(fecha=fecha, proveedor="groq", peticiones=1, aviso_enviado=False))
    historial.purgar(AHORA)
    assert _cuantos(db.uso_diario) == 1


def test_con_cero_no_borra_nada(monkeypatch):
    _guardar_en(AHORA - timedelta(days=1000), monkeypatch)
    monkeypatch.setenv("RETENCION_DIAS", "0")
    assert historial.purgar(AHORA) == 0 and _cuantos(db.mensajes) == 1


@pytest.mark.parametrize("valor", ["x", "-5", ""])
def test_un_valor_malo_no_borra_todo(monkeypatch, valor):
    monkeypatch.setenv("RETENCION_DIAS", valor)
    assert historial.retencion_dias() == historial.RETENCION_POR_DEFECTO


def test_purga_una_sola_vez_al_dia(monkeypatch):
    llamadas = []
    monkeypatch.setattr(historial, "purgar", lambda: llamadas.append(1) or 0)
    monkeypatch.setattr(historial, "_ultima_purga", None)
    historial.purgar_si_toca()
    historial.purgar_si_toca()
    assert len(llamadas) == 1


def test_si_la_purga_falla_la_persona_recibe_su_respuesta(monkeypatch):
    def falla():
        raise RuntimeError("base caída")

    monkeypatch.setattr(historial, "purgar", falla)
    monkeypatch.setattr(historial, "_ultima_purga", None)
    assert historial.guardar("preguntar", None, [Mensaje(rol="usuario", tipo="pregunta")]) is not None
