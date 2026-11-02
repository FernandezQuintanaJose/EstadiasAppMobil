"""Pruebas del contador diario (uso.py), sobre la tabla uso_diario."""

import json
import logging
from concurrent.futures import ThreadPoolExecutor

from sqlalchemy import func, select

import db
import uso


def test_cuenta_por_proveedor(monkeypatch):
    monkeypatch.setenv("GROQ_LIMITE_DIARIO", "10")
    for _ in range(3):
        uso.registrar("groq")
    uso.registrar("gemini")
    r = uso.resumen(["groq", "gemini"])
    assert r["fecha"] == uso.hoy()
    assert r["proveedores"]["groq"] == {"usadas": 3, "limite": 10, "porcentaje": 30, "aviso": False}
    assert r["proveedores"]["gemini"]["usadas"] == 1


def test_sin_peticiones_cuenta_cero():
    assert uso.resumen(["groq"])["proveedores"]["groq"]["usadas"] == 0


def test_aviso_del_80_por_ciento_una_sola_vez(monkeypatch, caplog):
    monkeypatch.setenv("GROQ_LIMITE_DIARIO", "5")
    with caplog.at_level(logging.WARNING, logger="cecapi.uso"):
        for _ in range(5):
            uso.registrar("groq")
    avisos = [r for r in caplog.records if "AVISO" in r.getMessage()]
    assert len(avisos) == 1
    assert "4 de 5" in avisos[0].getMessage()
    assert uso.resumen(["groq"])["proveedores"]["groq"] == {"usadas": 5, "limite": 5, "porcentaje": 100, "aviso": True}


def test_agotado_al_llegar_al_limite(monkeypatch):
    monkeypatch.setenv("GROQ_LIMITE_DIARIO", "2")
    assert not uso.agotado("groq")
    uso.registrar("groq")
    uso.registrar("groq")
    assert uso.agotado("groq")


def test_sin_limite_nunca_se_agota(monkeypatch):
    monkeypatch.setenv("GROQ_LIMITE_DIARIO", "0")
    for _ in range(3):
        uso.registrar("groq")
    assert not uso.agotado("groq")
    assert uso.resumen(["groq"])["proveedores"]["groq"]["limite"] is None


def test_dia_nuevo_empieza_en_cero(monkeypatch):
    monkeypatch.setenv("GROQ_LIMITE_DIARIO", "2")
    monkeypatch.setattr(uso, "hoy", lambda: "2026-10-24")
    uso.registrar("groq")
    uso.registrar("groq")
    assert uso.agotado("groq")
    monkeypatch.setattr(uso, "hoy", lambda: "2026-10-25")
    assert not uso.agotado("groq")
    with db.leer() as con:  # el día anterior se queda guardado para el panel
        assert con.execute(select(func.count()).select_from(db.uso_diario)).scalar() == 1


def test_varias_peticiones_al_mismo_tiempo_no_pierden_cuentas(monkeypatch, caplog):
    monkeypatch.setenv("GROQ_LIMITE_DIARIO", "300")
    with caplog.at_level(logging.WARNING, logger="cecapi.uso"), ThreadPoolExecutor(max_workers=16) as hilos:
        list(hilos.map(lambda _: uso.registrar("groq"), range(300)))
    assert uso.resumen(["groq"])["proveedores"]["groq"]["usadas"] == 300
    assert len([r for r in caplog.records if "AVISO" in r.getMessage()]) == 1


# --- uso_diario.json de antes -----------------------------------------------------

def test_importa_el_json_viejo_y_lo_renombra(tmp_path):
    viejo = tmp_path / "uso_diario.json"
    viejo.write_text(json.dumps({"fecha": uso.hoy(), "conteo": {"groq": 12, "gemini": 3},
                                 "avisados": ["groq"]}), encoding="utf-8")
    assert uso.importar_json() is True
    assert not viejo.exists()
    assert (tmp_path / "uso_diario.json.migrado").exists()
    r = uso.resumen(["groq", "gemini"])["proveedores"]
    assert r["groq"]["usadas"] == 12 and r["gemini"]["usadas"] == 3
    with db.leer() as con:
        avisado = con.execute(
            select(db.uso_diario.c.aviso_enviado).where(db.uso_diario.c.proveedor == "groq")
        ).scalar()
    assert avisado is True


def test_importar_no_suma_doble(tmp_path):
    uso.registrar("groq")
    viejo = tmp_path / "uso_diario.json"
    viejo.write_text(json.dumps({"fecha": uso.hoy(), "conteo": {"groq": 5}}), encoding="utf-8")
    uso.importar_json()
    assert uso.resumen(["groq"])["proveedores"]["groq"]["usadas"] == 5


def test_sin_json_viejo_no_hace_nada():
    assert uso.importar_json() is False


def test_json_viejo_danado_se_deja_como_esta(tmp_path):
    viejo = tmp_path / "uso_diario.json"
    viejo.write_text("{esto no es json", encoding="utf-8")
    assert uso.importar_json() is False
    assert viejo.exists()
