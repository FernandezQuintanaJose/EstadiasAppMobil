"""
Configuración común de las pruebas automáticas (pytest) del servidor.

Las pruebas NUNCA llaman a una IA de verdad ni gastan cuota: antes de importar main.py
se ponen claves falsas, y cada prueba reemplaza a los proveedores por funciones de mentira.
Tampoco usan la clave real de la app ni el límite real por minuto del .env.

Cómo correrlas (dentro de backend/, con el entorno virtual):  python -m pytest -v
"""

import os
import sys
import tempfile
from pathlib import Path

# Antes de importar main: load_dotenv() no reemplaza variables que ya existen,
# así que estas ganan sobre lo que tenga el .env de la computadora.
os.environ["GROQ_API_KEY"] = "clave-de-prueba"
os.environ["GEMINI_API_KEY"] = ""
os.environ["IA_ORDEN"] = "groq,gemini"
os.environ["CECAPI_CLAVE_APP"] = ""
os.environ["LIMITE_POR_MINUTO"] = "1000"
# Base de datos de las pruebas: un SQLite temporal, nunca backend/cecapi.db ni la de Neon.
os.environ["DATABASE_URL"] = f"sqlite:///{Path(tempfile.mkdtemp(prefix='cecapi-pruebas-')) / 'pruebas.db'}"

sys.path.insert(0, str(Path(__file__).resolve().parent))
