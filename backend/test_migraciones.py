"""Las migraciones de Alembic crean exactamente las tablas de db.py (lo que se usará en Neon)."""

from pathlib import Path

from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine, inspect

import db

AQUI = Path(__file__).resolve().parent


def configuracion(monkeypatch, tmp_path) -> Config:
    monkeypatch.setenv("DATABASE_URL", f"sqlite:///{tmp_path / 'migraciones.db'}")
    return Config(str(AQUI / "alembic.ini"))


def test_upgrade_crea_las_tablas_de_db(monkeypatch, tmp_path):
    command.upgrade(configuracion(monkeypatch, tmp_path), "head")
    motor = create_engine(db.url())
    tablas = set(inspect(motor).get_table_names()) - {"alembic_version"}
    motor.dispose()
    assert tablas == set(db.metadata.tables)


def test_las_migraciones_estan_al_dia_con_db(monkeypatch, tmp_path):
    # Si alguien cambia una tabla en db.py y olvida la migración, esto falla.
    cfg = configuracion(monkeypatch, tmp_path)
    command.upgrade(cfg, "head")
    command.check(cfg)


def test_downgrade_y_upgrade_de_nuevo(monkeypatch, tmp_path):
    cfg = configuracion(monkeypatch, tmp_path)
    command.upgrade(cfg, "head")
    command.downgrade(cfg, "base")
    command.upgrade(cfg, "head")
