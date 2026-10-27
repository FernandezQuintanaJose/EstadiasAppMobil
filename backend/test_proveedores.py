"""Pruebas de proveedores.py que no llaman a ninguna IA: orden del respaldo y marca de "hay más"."""

import importlib

import pytest

import proveedores


@pytest.fixture(autouse=True)
def restaurar(monkeypatch):
    """Cada prueba recarga proveedores.py; al final se deja como lo cargó conftest.py."""
    yield
    monkeypatch.undo()
    importlib.reload(proveedores)


@pytest.mark.parametrize("orden, esperado", [
    (None, ["groq", "gemini"]),               # sin IA_ORDEN: Groq primero
    ("gemini,groq", ["gemini", "groq"]),
    ("groq", ["groq", "gemini"]),             # el que no se nombra queda de respaldo
    ("GROQ, gemini ,foo", ["groq", "gemini"]),  # mayúsculas, espacios y nombres que no existen
])
def test_orden_de_proveedores(monkeypatch, orden, esperado):
    if orden is None:
        monkeypatch.delenv("IA_ORDEN", raising=False)
    else:
        monkeypatch.setenv("IA_ORDEN", orden)
    modulo = importlib.reload(proveedores)
    assert [p.nombre for p in modulo.PROVEEDORES] == esperado


def test_separar_marca_de_hay_mas():
    assert proveedores._separar_mas("Respuesta corta.\nMAS_DISPONIBLE") == ("Respuesta corta.", True)
    assert proveedores._separar_mas("Respuesta completa.") == ("Respuesta completa.", False)


@pytest.mark.parametrize("texto", [
    "Pide ayuda a quien esté cerca. MAS_DISPONIBLE",     # pegada al final (pregunta 28, 25/10)
    "Pide ayuda a quien esté cerca.MAS_DISPONIBLE",
    "Pide ayuda a quien esté cerca.\n  MAS_DISPONIBLE  \n",
    "Pide ayuda MAS_DISPONIBLE a quien esté cerca.",      # en medio: tampoco se debe decir
])
def test_separar_marca_aunque_no_este_en_su_linea(texto):
    respuesta, hay_mas = proveedores._separar_mas(texto)
    assert hay_mas is True
    assert "MAS_DISPONIBLE" not in respuesta
    assert respuesta.startswith("Pide ayuda") and respuesta.endswith("cerca.")


def test_instrucciones_tienen_los_datos_del_cecapi():
    assert "Jacarandá 1603" in proveedores.INSTRUCCIONES
    assert "618 455 0146" in proveedores.INSTRUCCIONES


def test_instrucciones_tienen_los_nombres_oficiales():
    assert "Pensión para el Bienestar de las Personas con Discapacidad Permanente" in proveedores.INSTRUCCIONES
    assert "Ley General para la Inclusión de las Personas con Discapacidad" in proveedores.INSTRUCCIONES
    assert "guía podotáctil" in proveedores.INSTRUCCIONES


def test_instrucciones_no_crecen_de_mas():
    # Más texto = respuestas más lentas (25/10: 2,564 caracteres, 1,128 ms en promedio).
    assert len(proveedores.INSTRUCCIONES) < 2200
