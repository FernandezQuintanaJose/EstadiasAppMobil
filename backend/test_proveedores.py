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


@pytest.mark.parametrize("texto, visual", [
    ("No tengo forma de saberlo. Revisa la aplicación.", True),        # pregunta 9, 25/10
    ("Si no la ves, di ayuda.", True),                                  # pregunta 13, 25/10
    ("Verifica si la app lo ofrece en su pantalla principal.", True),
    ("Ve a la esquina y escucha el tráfico.", False),                   # "ve" de ir
    ("Activa tu lector de pantalla con tres dedos.", False),            # TalkBack sí se vale
    ("Louis Braille fue un francés que inventó la lectura con puntos.", False),
])
def test_detecta_lenguaje_visual(texto, visual):
    assert proveedores.tiene_lenguaje_visual(texto) is visual


class ProveedorFalso:
    nombre = "groq"
    configurado = True

    def __init__(self, respuestas):
        self.respuestas = list(respuestas)
        self.llamadas = []

    def preguntar(self, pregunta, contexto, instrucciones=None):
        self.llamadas.append((pregunta, list(contexto)))
        respuesta = self.respuestas.pop(0)
        if isinstance(respuesta, Exception):
            raise respuesta
        return respuesta


@pytest.fixture
def sin_conteo(monkeypatch):
    monkeypatch.setattr(proveedores.uso, "registrar", lambda nombre: None)
    monkeypatch.setattr(proveedores.uso, "agotado", lambda nombre: False)


def test_pide_corregir_una_vez_si_usa_palabras_de_vista(monkeypatch, sin_conteo):
    falso = ProveedorFalso(["Revisa la aplicación.", "Pregúntale a la app: di notificaciones."])
    monkeypatch.setattr(proveedores, "configurados", lambda: [falso])
    respuesta, nombre, _ = proveedores.preguntar_con_respaldo("¿Tengo notificaciones?", [])
    assert respuesta == "Pregúntale a la app: di notificaciones."
    assert nombre == "groq"
    assert len(falso.llamadas) == 2
    pregunta, contexto = falso.llamadas[1]
    assert proveedores.CORRECCION_VISUAL in pregunta
    assert contexto[-1] == "Asistente: Revisa la aplicación."


def test_si_la_correccion_falla_se_queda_la_primera(monkeypatch, sin_conteo):
    falso = ProveedorFalso(["Revisa la aplicación.", RuntimeError("caído")])
    monkeypatch.setattr(proveedores, "configurados", lambda: [falso])
    respuesta, _, _ = proveedores.preguntar_con_respaldo("¿Tengo notificaciones?", [])
    assert respuesta == "Revisa la aplicación."


def test_sin_palabras_de_vista_no_se_corrige(monkeypatch, sin_conteo):
    falso = ProveedorFalso(["Louis Braille inventó la lectura con puntos."])
    monkeypatch.setattr(proveedores, "configurados", lambda: [falso])
    proveedores.preguntar_con_respaldo("¿Quién fue Louis Braille?", [])
    assert len(falso.llamadas) == 1


def test_documento_no_se_corrige_por_palabras_de_vista(monkeypatch, sin_conteo):
    falso = ProveedorFalso(["Solicitud\n\nPor medio de la presente pido revisar mi expediente."])
    monkeypatch.setattr(proveedores, "configurados", lambda: [falso])
    proveedores.preguntar_con_respaldo("Una solicitud", [], proveedores.INSTRUCCIONES_DOCUMENTO)
    assert len(falso.llamadas) == 1


def test_instrucciones_de_documento_piden_texto_plano_y_no_inventar_datos():
    texto = proveedores.INSTRUCCIONES_DOCUMENTO
    assert "sin markdown" in texto
    assert "[tu nombre]" in texto
    assert "Jacarandá 1603" in texto
