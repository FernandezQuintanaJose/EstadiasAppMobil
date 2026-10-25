"""
Pruebas de las rutas del servidor (main.py) con el cliente de pruebas de FastAPI.

La IA se reemplaza por una función de mentira (IAFalsa) que anota con qué la llamaron,
así se puede revisar qué le llegaría a Groq o Gemini sin gastar cuota.
"""

import base64
import io

import pytest
from fastapi.testclient import TestClient
from PIL import Image

import main
import proveedores
import seguridad


class IAFalsa:
    def __init__(self, respuesta="Respuesta de prueba.", falla=False):
        self.respuesta = respuesta
        self.falla = falla
        self.llamadas = []

    def preguntar(self, pregunta, contexto):
        self.llamadas.append((pregunta, list(contexto)))
        if self.falla:
            raise proveedores.ProveedorNoDisponible("prueba")
        return self.respuesta, "groq", False

    def ver(self, imagen, tipo_mime, prompt):
        self.llamadas.append((tipo_mime, prompt))
        if self.falla:
            raise proveedores.ProveedorNoDisponible("prueba")
        return "Billete de 50 pesos", "groq_vision"


@pytest.fixture
def ia(monkeypatch):
    falsa = IAFalsa()
    monkeypatch.setattr(proveedores, "preguntar_con_respaldo", falsa.preguntar)
    monkeypatch.setattr(proveedores, "ver_con_respaldo", falsa.ver)
    return falsa


@pytest.fixture(autouse=True)
def limpio(monkeypatch):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "")
    monkeypatch.setenv("LIMITE_POR_MINUTO", "1000")
    seguridad.limitador.reiniciar()
    yield
    seguridad.limitador.reiniciar()


cliente = TestClient(main.app)


def foto_png_base64() -> str:
    salida = io.BytesIO()
    Image.new("RGB", (40, 20), "green").save(salida, format="PNG")
    return base64.b64encode(salida.getvalue()).decode()


# --- Rutas básicas -------------------------------------------------------------

def test_raiz_responde():
    r = cliente.get("/")
    assert r.status_code == 200
    assert r.json()["proveedores"][0] == "groq"


def test_uso_responde(ia):
    r = cliente.get("/uso")
    assert r.status_code == 200
    assert "proveedores" in r.json()


# --- /entender y /preguntar ----------------------------------------------------

def test_entender_reconoce_comando_sin_llamar_a_la_ia(ia):
    r = cliente.post("/entender", json={"pregunta": "¿Qué hay enfrente de mí?"})
    assert r.json() == {"tipo": "comando", "comando": "ENVIRONMENT"}
    assert ia.llamadas == []


def test_entender_pregunta_libre_va_a_la_ia(ia):
    r = cliente.post("/entender", json={"pregunta": "¿Quién fue Louis Braille?"})
    datos = r.json()
    assert r.status_code == 200
    assert datos["tipo"] == "respuesta"
    assert datos["respuesta"] == "Respuesta de prueba."
    assert datos["proveedor"] == "groq"
    assert ia.llamadas[0][0] == "¿Quién fue Louis Braille?"


def test_preguntar_devuelve_respuesta(ia):
    r = cliente.post("/preguntar", json={"pregunta": "¿Por qué el cielo es azul?"})
    assert r.status_code == 200
    assert r.json() == {"respuesta": "Respuesta de prueba.", "proveedor": "groq", "hayMas": False}


@pytest.mark.parametrize("ruta", ["/preguntar", "/entender"])
def test_dato_sensible_se_bloquea_y_no_sale_a_la_ia(ia, ruta):
    r = cliente.post(ruta, json={"pregunta": "mi teléfono es 55 1234 5678"})
    assert r.status_code == 200
    assert r.json()["respuesta"] == main.privacidad.MENSAJE_BLOQUEO
    assert ia.llamadas == []


def test_contexto_sin_datos_sensibles_y_solo_las_ultimas_6(ia):
    contexto = [f"P: pregunta {n} / R: respuesta {n}" for n in range(8)]
    contexto.insert(7, "P: mi correo es ana@correo.com / R: ...")
    cliente.post("/preguntar", json={"pregunta": "¿y luego?", "contexto": contexto})
    enviado = ia.llamadas[0][1]
    assert len(enviado) == 6
    assert not any("@" in linea for linea in enviado)
    assert enviado[-1] == "P: pregunta 7 / R: respuesta 7"


def test_ia_caida_responde_503_con_mensaje_para_decir(monkeypatch):
    falsa = IAFalsa(falla=True)
    monkeypatch.setattr(proveedores, "preguntar_con_respaldo", falsa.preguntar)
    r = cliente.post("/preguntar", json={"pregunta": "hola"})
    assert r.status_code == 503
    assert r.json()["detail"] == "El asistente no está disponible en este momento."


# --- Límites de tamaño ---------------------------------------------------------

@pytest.mark.parametrize("cuerpo", [
    {"pregunta": ""},                                        # vacía
    {"pregunta": "a" * 2001},                                # pregunta demasiado larga
    {"pregunta": "hola", "contexto": ["x"] * 21},            # demasiadas líneas
    {"pregunta": "hola", "contexto": ["x" * 1001]},          # línea demasiado larga
    {"contexto": []},                                        # sin pregunta
])
def test_peticiones_invalidas_se_rechazan_sin_llamar_a_la_ia(ia, cuerpo):
    r = cliente.post("/preguntar", json=cuerpo)
    assert r.status_code == 422
    assert ia.llamadas == []


# --- Clave de la app -----------------------------------------------------------

def test_sin_clave_configurada_no_se_pide(ia):
    assert cliente.post("/preguntar", json={"pregunta": "hola"}).status_code == 200


@pytest.mark.parametrize("encabezados, esperado", [
    ({}, 401),
    ({"X-Clave-App": "equivocada"}, 401),
    ({"X-Clave-App": "secreta"}, 200),
])
def test_con_clave_configurada(ia, monkeypatch, encabezados, esperado):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "secreta")
    r = cliente.post("/preguntar", json={"pregunta": "hola"}, headers=encabezados)
    assert r.status_code == esperado


def test_clave_protege_uso_y_ver_pero_no_la_raiz(ia, monkeypatch):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "secreta")
    assert cliente.get("/").status_code == 200
    assert cliente.get("/uso").status_code == 401
    assert cliente.post("/ver", json={"imagen": "x", "modo": "billete"}).status_code == 401


# --- Límite por minuto ---------------------------------------------------------

def test_limite_por_minuto(ia, monkeypatch):
    monkeypatch.setenv("LIMITE_POR_MINUTO", "3")
    codigos = [cliente.post("/preguntar", json={"pregunta": "hola"}).status_code for _ in range(4)]
    assert codigos == [200, 200, 200, 429]
    assert len(ia.llamadas) == 3


# --- /ver ----------------------------------------------------------------------

def test_ver_billete(ia):
    r = cliente.post("/ver", json={"imagen": foto_png_base64(), "modo": "billete"})
    assert r.status_code == 200
    assert r.json() == {"respuesta": "Billete de 50 pesos", "proveedor": "groq_vision"}
    assert ia.llamadas[0][0] == "image/jpeg"  # imagenes.py la convirtió a JPEG


@pytest.mark.parametrize("cuerpo", [
    {"imagen": "x", "modo": "escena"},          # todavía no disponible
    {"imagen": "x", "modo": "otro"},            # modo que no existe
    {"imagen": "esto no es base64!", "modo": "billete"},
    {"imagen": base64.b64encode(b"no soy una foto").decode(), "modo": "billete"},
])
def test_ver_rechaza_peticiones_invalidas(ia, cuerpo):
    r = cliente.post("/ver", json=cuerpo)
    assert r.status_code == 400
    assert ia.llamadas == []
