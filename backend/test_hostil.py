"""
Pruebas de quien no confía en nada: entradas raras, claves, límite por minuto, fotos hostiles
y datos personales escritos de formas que el filtro de privacidad todavía no reconoce.

No cubre (porque no es código de la app) que --forwarded-allow-ips '*' deja falsear la IP del límite por
minuto: ver docs/estado/2026-11-04-revision-qa-y-entrega.md.

Las que están marcadas xfail(strict=True) son huecos conocidos (revisión del 4 nov 2026). Cuando
alguien los arregle, la prueba pasará y pytest avisará con un error para que se quite la marca.
"""

import base64
import io

import pytest
from fastapi.testclient import TestClient
from PIL import Image

import main
import proveedores
import seguridad

cliente = TestClient(main.app)


@pytest.fixture(autouse=True)
def ia(monkeypatch):
    llamadas = []

    def preguntar(pregunta, contexto, instrucciones=None):
        llamadas.append((pregunta, list(contexto)))
        return "Respuesta de prueba.", "groq", False

    monkeypatch.setattr(proveedores, "preguntar_con_respaldo", preguntar)
    monkeypatch.setattr(proveedores, "ver_con_respaldo", lambda imagen, tipo, prompt: ("Billete de 50 pesos", "groq_vision"))
    monkeypatch.setenv("CECAPI_CLAVE_APP", "")
    monkeypatch.setenv("LIMITE_POR_MINUTO", "1000")
    seguridad.limitador.reiniciar()
    yield llamadas
    seguridad.limitador.reiniciar()


def preguntar(cuerpo, **kw):
    return cliente.post("/preguntar", json=cuerpo, **kw)


@pytest.mark.parametrize("cuerpo,esperado", [
    ({"pregunta": ""}, 422),
    ({"pregunta": "a" * 2001}, 422),
    ({"pregunta": "a" * 2000}, 200),
    ({"pregunta": "x", "contexto": ["a"] * 21}, 422),
    ({"pregunta": "x", "contexto": ["a" * 1001]}, 422),
    ({"pregunta": "x", "contexto": "hola"}, 422),
    ({"pregunta": "x", "contexto": [1, 2]}, 422),
    ({"pregunta": 5}, 422),
    ({"pregunta": None}, 422),
    ({"pregunta": "x", "conversacionId": "a" * 37}, 422),
    ({"pregunta": "x", "conversacionId": 5}, 422),
    ({"pregunta": "x", "conversacionId": "' OR 1=1;--"}, 200),
    ({"pregunta": "x", "admin": True}, 200),
    ({"pregunta": "hola\u0000mundo"}, 200),
    ({"pregunta": "‮حال 😀" * 10}, 200),
])
def test_entradas_raras(cuerpo, esperado):
    assert preguntar(cuerpo).status_code == esperado


def test_cuerpos_que_no_son_json_valido():
    assert cliente.post("/preguntar", content=b"{no es json", headers={"Content-Type": "application/json"}).status_code == 422
    assert cliente.post("/preguntar", content=b"x" * 5_000_000, headers={"Content-Type": "application/json"}).status_code == 422
    assert cliente.post("/preguntar", content="pregunta=hola", headers={"Content-Type": "application/x-www-form-urlencoded"}).status_code == 422
    assert cliente.get("/preguntar").status_code == 405


@pytest.mark.parametrize("encabezados,esperado", [
    ({}, 401),
    ({"X-Clave-App": "mala"}, 401),
    ({"X-Clave-App": "CLAVE-SECRETA-LARGA"}, 401),
    ({"X-Clave-App": " clave-secreta-larga"}, 401),
    ({"X-Clave-App": "clave-secreta-larga"}, 200),
])
def test_clave_de_la_app(monkeypatch, encabezados, esperado):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "clave-secreta-larga")
    assert preguntar({"pregunta": "hola"}, headers=encabezados).status_code == esperado
    assert cliente.get("/uso").status_code == 401


def test_limite_por_minuto(monkeypatch):
    monkeypatch.setenv("LIMITE_POR_MINUTO", "5")
    seguridad.limitador.reiniciar()
    codigos = [preguntar({"pregunta": "hola"}).status_code for _ in range(8)]
    assert codigos[:5] == [200] * 5 and codigos[5:] == [429] * 3


def test_datos_personales_en_el_contexto_no_llegan_a_la_ia(ia):
    preguntar({"pregunta": "hola", "contexto": ["Persona: mi CURP es GODE561231HDFRRN09", "Asistente: ok"]})
    assert ia and "GODE561231HDFRRN09" not in " ".join(ia[-1][1])


@pytest.mark.parametrize("texto", [
    "mi correo es juan@example.com", "618-123-45-67", "6 1 8 1 2 3 4 5 6 7", "4111 1111 1111 1111",
    "4111-1111-1111-1111", "4111111111111111", "godz561231hdfrrn09", "GODE561231AB1", "mi contraseña es hunter2",
])
def test_privacidad_bloquea(ia, texto):
    assert preguntar({"pregunta": texto}).status_code == 200 and not ia


@pytest.mark.parametrize("texto", ["¿Quién fue Louis Braille?", "la ley 1234 del 2006"])
def test_privacidad_no_bloquea_preguntas_normales(ia, texto):
    preguntar({"pregunta": texto})
    assert ia


@pytest.mark.xfail(strict=True, reason="Hueco conocido: el filtro no ve datos escritos con espacios, palabras u otro idioma.")
@pytest.mark.parametrize("texto", [
    "juan [arroba] example punto com", "juan(at)example.com", "juan @ example . com",
    "seis uno ocho uno dos tres cuatro cinco seis siete", "GODE 561231 HDFRRN 09", "my password is hunter2",
])
def test_privacidad_variantes_que_hoy_pasan(ia, texto):
    assert preguntar({"pregunta": texto}).status_code == 200 and not ia


def _png(ancho, alto, formato="PNG"):
    salida = io.BytesIO()
    Image.new("RGB", (ancho, alto), "red").save(salida, formato)
    return salida.getvalue()


@pytest.mark.parametrize("nombre,imagen,esperado", [
    ("base64 inválido", "%%%%", 400),
    ("vacío", "", 422),
    ("no es foto", base64.b64encode(b"MZ\x90\x00 programa").decode(), 400),
    ("PNG truncado", base64.b64encode(_png(50, 50)[:40]).decode(), 400),
    ("bomba de descompresión", base64.b64encode(_png(30000, 30000)).decode(), 400),
    ("10 MB de ceros", base64.b64encode(b"\0" * (10 * 1024 * 1024 + 5)).decode(), 400),
    ("con prefijo data:", "data:image/png;base64," + base64.b64encode(_png(60, 40)).decode(), 400),
    ("foto normal", base64.b64encode(_png(60, 40)).decode(), 200),
], ids=["base64 inválido", "vacío", "no es foto", "PNG truncado", "bomba de descompresión",
        "10 MB de ceros", "con prefijo data:", "foto normal"])  # sin ids, Windows no acepta el nombre
def test_fotos_hostiles(nombre, imagen, esperado):
    assert cliente.post("/ver", json={"imagen": imagen, "modo": "escena"}).status_code == esperado, nombre
