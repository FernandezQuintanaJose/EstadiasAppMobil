"""Pruebas de db.py: a qué base se conecta y qué reglas cumplen las tablas."""

from datetime import timedelta

import pytest
from sqlalchemy import delete, func, select
from sqlalchemy.exc import IntegrityError

import db


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


def test_crea_las_tablas_solas_con_sqlite(base_temporal):
    db.preparar()
    assert base_temporal.exists()
    with db.leer() as con:
        assert con.execute(select(func.count()).select_from(db.mensajes)).scalar() == 0


def conversacion(con, id_="c1"):
    momento = db.ahora()
    con.execute(db.conversaciones.insert().values(id=id_, iniciada_en=momento, ultima_actividad=momento))


def mensaje(**campos):
    valores = {"conversacion_id": "c1", "ruta": "preguntar", "rol": "usuario", "tipo": "pregunta",
               "creado_en": db.ahora(), **campos}
    return db.mensajes.insert().values(**valores)


@pytest.mark.parametrize("campos", [
    {"rol": "otro"},
    {"tipo": "otro"},
    {"ruta": "otra"},
    {"tipo": "comando"},                         # comando sin código
    {"tipo": "bloqueado", "texto": "mi CURP"},   # lo bloqueado nunca guarda texto
    {"conversacion_id": "no-existe"},            # llave foránea
])
def test_reglas_de_mensajes(campos):
    with db.transaccion() as con:
        conversacion(con)
    with pytest.raises(IntegrityError), db.transaccion() as con:
        con.execute(mensaje(**campos))


def test_borrar_una_conversacion_borra_sus_mensajes():
    # Hace falta para "borra mi cuenta / borra mis datos" cuando haya usuarios.
    with db.transaccion() as con:
        conversacion(con)
        con.execute(mensaje())
        con.execute(mensaje(rol="asistente", tipo="respuesta", proveedor="groq"))
    with db.transaccion() as con:
        con.execute(delete(db.conversaciones).where(db.conversaciones.c.id == "c1"))
    with db.leer() as con:
        assert con.execute(select(func.count()).select_from(db.mensajes)).scalar() == 0


def test_fechas_regresan_en_utc():
    with db.transaccion() as con:
        conversacion(con)
    with db.leer() as con:
        guardada = con.execute(select(db.conversaciones.c.iniciada_en)).scalar()
    assert db.como_utc(guardada).utcoffset() == timedelta(0)
    assert abs(db.como_utc(guardada) - db.ahora()) < timedelta(minutes=1)
