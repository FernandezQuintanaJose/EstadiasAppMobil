"""
Pruebas de db.py y uso.py con un SQLite temporal (conftest.py pone DATABASE_URL).

Cada prueba usa un archivo nuevo, así los conteos de una no se mezclan con los de otra.
"""

import logging
from datetime import timedelta

import pytest
from sqlalchemy import select

import db
import uso


@pytest.fixture(autouse=True)
def base_nueva(monkeypatch, tmp_path):
    monkeypatch.setenv("DATABASE_URL", f"sqlite:///{tmp_path / 'prueba.db'}")
    db.reiniciar()
    yield
    db.reiniciar()


@pytest.mark.parametrize("valor, esperado", [
    ("postgres://u:p@ep-algo.neon.tech/cecapi?sslmode=require",
     "postgresql+psycopg://u:p@ep-algo.neon.tech/cecapi?sslmode=require"),
    ("postgresql://u:p@host/db", "postgresql+psycopg://u:p@host/db"),
    ("postgresql+psycopg://u:p@host/db", "postgresql+psycopg://u:p@host/db"),
])
def test_url_de_neon_usa_psycopg(monkeypatch, valor, esperado):
    monkeypatch.setenv("DATABASE_URL", valor)
    assert db.url() == esperado
    assert not db.es_sqlite()


def test_sin_database_url_usa_sqlite_local(monkeypatch):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    assert db.url().startswith("sqlite:///") and db.url().endswith("cecapi.db")


def test_cuenta_por_proveedor_y_por_dia(monkeypatch):
    monkeypatch.setenv("GROQ_LIMITE_DIARIO", "0")
    for _ in range(3):
        uso.registrar("groq")
    uso.registrar("gemini")
    assert uso.usadas("groq") == 3
    assert uso.usadas("gemini") == 1
    assert uso.usadas("groq_vision") == 0

    # Día nuevo: el contador empieza en cero, y el de ayer se queda en la tabla.
    monkeypatch.setattr(uso, "hoy", lambda: "2099-01-01")
    assert uso.usadas("groq") == 0
    uso.registrar("groq")
    assert uso.usadas("groq") == 1
    assert {f["fecha"] for f in db.uso_por_dia("2000-01-01")} == {uso.date.today().isoformat(), "2099-01-01"}


def test_avisa_una_vez_al_80_y_se_agota_al_100(monkeypatch, caplog):
    monkeypatch.setenv("GROQ_LIMITE_DIARIO", "5")
    with caplog.at_level(logging.WARNING, logger="cecapi.uso"):
        for _ in range(3):
            uso.registrar("groq")
        assert not uso.agotado("groq")
        assert "AVISO" not in caplog.text
        uso.registrar("groq")  # 4 de 5 = 80%
        assert caplog.text.count("AVISO") == 1
        uso.registrar("groq")  # 5 de 5
        assert caplog.text.count("AVISO") == 1  # no se repite
    assert uso.agotado("groq")

    resumen = uso.resumen(["groq", "gemini"])
    assert resumen["proveedores"]["groq"] == {"usadas": 5, "limite": 5, "porcentaje": 100, "aviso": True}
    assert resumen["proveedores"]["gemini"]["usadas"] == 0


def test_anota_peticiones_sin_contenido():
    db.anotar_peticion("preguntar", "respuesta", "groq", 800)
    db.anotar_peticion("entender", "comando", None, 0)
    db.anotar_peticion("preguntar", "bloqueado", None, 2)
    with db.motor().connect() as conexion:
        columnas = set(db.peticiones.c.keys())
        filas = conexion.execute(select(db.peticiones)).all()
    # Lo que NO debe existir: el texto, la respuesta, la foto o la IP.
    assert columnas == {"id", "creado_en", "ruta", "resultado", "proveedor", "duracion_ms"}
    assert len(filas) == 3

    est = db.estadisticas(db.ahora() - timedelta(days=1))
    assert est["total"] == 3
    assert est["por_ruta"] == {"preguntar": 2, "entender": 1}
    assert est["por_resultado"] == {"respuesta": 1, "comando": 1, "bloqueado": 1}
    assert est["por_proveedor"] == {"groq": 1, "ninguno": 2}
    assert est["promedio_ia_ms"] == 800  # los comandos (0 ms) no bajan el promedio de la IA


def test_rechaza_resultados_desconocidos():
    with pytest.raises(ValueError):
        db.anotar_peticion("preguntar", "otra cosa", None, 1)
