"""Alembic: toma la conexión de db.url() (DATABASE_URL) y las tablas de db.metadata."""

from logging.config import fileConfig

from alembic import context
from dotenv import load_dotenv
from sqlalchemy import create_engine

load_dotenv()

import db  # noqa: E402

if context.config.config_file_name is not None:
    fileConfig(context.config.config_file_name)

objetivo = db.metadata


def sin_conexion() -> None:
    """`alembic upgrade head --sql`: escribe el SQL sin conectarse (para revisarlo antes)."""
    context.configure(url=db.url(), target_metadata=objetivo, literal_binds=True,
                      render_as_batch=db.es_sqlite())
    with context.begin_transaction():
        context.run_migrations()


def con_conexion() -> None:
    motor = create_engine(db.url())
    with motor.connect() as conexion:
        # render_as_batch: SQLite no sabe cambiar columnas con ALTER; así Alembic rehace la tabla.
        context.configure(connection=conexion, target_metadata=objetivo, render_as_batch=db.es_sqlite())
        with context.begin_transaction():
            context.run_migrations()
    motor.dispose()


if context.is_offline_mode():
    sin_conexion()
else:
    con_conexion()
