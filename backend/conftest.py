"""
Configuración común de las pruebas automáticas (pytest) del servidor.

Las pruebas NUNCA llaman a una IA de verdad ni gastan cuota: antes de importar main.py
se ponen claves falsas, y cada prueba reemplaza a los proveedores por funciones de mentira.
Tampoco usan la clave real de la app ni el límite real por minuto del .env.
Tampoco tocan backend/cecapi.db ni uso_diario.json: cada prueba tiene su propia base de
datos vacía en una carpeta temporal.

Cómo correrlas (dentro de backend/, con el entorno virtual):  python -m pytest -v
"""

import os
import sys
import tempfile
from pathlib import Path

import pytest

# Antes de importar main: load_dotenv() no reemplaza variables que ya existen,
# así que estas ganan sobre lo que tenga el .env de la computadora.
os.environ["GROQ_API_KEY"] = "clave-de-prueba"
os.environ["GEMINI_API_KEY"] = ""
os.environ["IA_ORDEN"] = "groq,gemini"
os.environ["CECAPI_CLAVE_APP"] = ""
os.environ["LIMITE_POR_MINUTO"] = "1000"
os.environ["GUARDAR_TEXTOS"] = "0"
# Por si algo usa la base antes de la primera prueba: nunca la real.
os.environ["CECAPI_BD"] = str(Path(tempfile.mkdtemp(prefix="cecapi_pruebas_")) / "inicio.db")

sys.path.insert(0, str(Path(__file__).resolve().parent))


@pytest.fixture(autouse=True)
def base_temporal(tmp_path, monkeypatch):
    """Base de datos nueva y vacía para cada prueba. Regresa la ruta del archivo."""
    import uso

    archivo = tmp_path / "cecapi.db"
    monkeypatch.setenv("CECAPI_BD", str(archivo))
    monkeypatch.setenv("GUARDAR_TEXTOS", "0")
    monkeypatch.setattr(uso, "ARCHIVO_VIEJO", tmp_path / "uso_diario.json")  # nunca el real
    yield archivo
    import bd

    bd.cerrar()  # en Windows no se puede borrar la carpeta temporal con la base abierta
