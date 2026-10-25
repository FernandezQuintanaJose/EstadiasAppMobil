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


def test_instrucciones_tienen_los_datos_del_cecapi():
    assert "Jacarandá 1603" in proveedores.INSTRUCCIONES
    assert "618 455 0146" in proveedores.INSTRUCCIONES
