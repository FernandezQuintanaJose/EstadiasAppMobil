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
from prompts_vision import PROMPT_BILLETE, PROMPT_ESCENA


class IAFalsa:
    def __init__(self, respuesta="Respuesta de prueba.", falla=False):
        self.respuesta = respuesta
        self.falla = falla
        self.llamadas = []

    def preguntar(self, pregunta, contexto, instrucciones=None):
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
    datos = r.json()
    assert datos.pop("conversacionId")
    assert datos == {"tipo": "comando", "comando": "ENVIRONMENT"}
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
    datos = r.json()
    assert datos.pop("conversacionId")
    assert datos == {"respuesta": "Respuesta de prueba.", "proveedor": "groq", "hayMas": False}


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


@pytest.mark.parametrize("modo, prompt", [
    ("billete", PROMPT_BILLETE),
    ("escena", PROMPT_ESCENA),
])
def test_ver_usa_el_prompt_del_modo(ia, modo, prompt):
    r = cliente.post("/ver", json={"imagen": foto_png_base64(), "modo": modo})
    assert r.status_code == 200
    assert ia.llamadas == [("image/jpeg", prompt)]


def test_ver_escena_con_ia_caida_responde_503(monkeypatch):
    falsa = IAFalsa(falla=True)
    monkeypatch.setattr(proveedores, "ver_con_respaldo", falsa.ver)
    r = cliente.post("/ver", json={"imagen": foto_png_base64(), "modo": "escena"})
    assert r.status_code == 503
    assert r.json()["detail"] == "El asistente no está disponible en este momento."


@pytest.mark.parametrize("cuerpo", [
    {"imagen": "x", "modo": "otro"},            # modo que no existe
    {"imagen": "x", "modo": "Escena"},          # los modos van en minúsculas
    {"imagen": "esto no es base64!", "modo": "escena"},
    {"imagen": base64.b64encode(b"no soy una foto").decode(), "modo": "escena"},
    {"imagen": "esto no es base64!", "modo": "billete"},
    {"imagen": base64.b64encode(b"no soy una foto").decode(), "modo": "billete"},
])
def test_ver_rechaza_peticiones_invalidas(ia, cuerpo):
    r = cliente.post("/ver", json=cuerpo)
    assert r.status_code == 400
    assert ia.llamadas == []


# --- Documentación (/docs) -----------------------------------------------------

def test_cada_ruta_esta_documentada():
    rutas = cliente.get("/openapi.json").json()["paths"]
    assert set(rutas) == {"/", "/uso", "/panel", "/panel/datos", "/preguntar", "/entender", "/ver", "/documento"}
    for ruta, metodos in rutas.items():
        for metodo, doc in metodos.items():
            assert doc.get("summary") and doc.get("description"), f"{metodo} {ruta} sin texto"
            assert doc.get("tags"), f"{metodo} {ruta} sin etiqueta"
            tipo = "text/html" if ruta == "/panel" else "application/json"
            assert "schema" in doc["responses"]["200"]["content"][tipo]
            if ruta != "/":
                assert {"401", "429"} <= set(doc["responses"]), f"{metodo} {ruta} sin errores de seguridad"


@pytest.mark.parametrize("ruta, ejemplos", [
    ("/preguntar", main.EJEMPLOS_PREGUNTA),
    ("/entender", main.EJEMPLOS_ENTENDER),
    ("/ver", main.EJEMPLOS_IMAGEN),
    ("/documento", main.EJEMPLOS_DOCUMENTO),
])
def test_los_ejemplos_de_docs_funcionan(ia, ruta, ejemplos):
    # Lo que se ve en /docs con "Try it out" debe responder 200, no un error.
    for ejemplo in ejemplos.values():
        assert cliente.post(ruta, json=ejemplo["value"]).status_code == 200, ejemplo["summary"]


def test_ejemplo_con_dato_personal_no_sale_a_la_ia(ia):
    r = cliente.post("/preguntar", json=main.EJEMPLOS_PREGUNTA["dato_personal"]["value"])
    assert r.json()["proveedor"] is None
    assert ia.llamadas == []


# --- /documento, /estadisticas y /panel ---------------------------------------------

def test_documento_usa_las_instrucciones_de_documento(monkeypatch):
    llamadas = []

    def falsa(pregunta, contexto, instrucciones=None):
        llamadas.append((pregunta, contexto, instrucciones))
        return "Solicitud de permiso\n\nPor medio de la presente...", "groq", False

    monkeypatch.setattr(proveedores, "preguntar_con_respaldo", falsa)
    r = cliente.post("/documento", json={"pedido": "una carta para faltar el viernes"})
    assert r.status_code == 200
    assert r.json() == {
        "documento": "Solicitud de permiso\n\nPor medio de la presente...", "aviso": None, "proveedor": "groq",
    }
    assert llamadas == [("una carta para faltar el viernes", [], proveedores.INSTRUCCIONES_DOCUMENTO)]


def test_documento_con_dato_personal_no_sale_a_la_ia(ia):
    r = cliente.post("/documento", json={"pedido": "una solicitud con mi CURP GODE561231HDFRRN09"})
    assert r.json() == {"documento": None, "aviso": main.privacidad.MENSAJE_BLOQUEO, "proveedor": None}
    assert ia.llamadas == []


def test_documento_sin_ia_responde_503(monkeypatch):
    def falla(*args, **kwargs):
        raise proveedores.ProveedorNoDisponible("prueba")

    monkeypatch.setattr(proveedores, "preguntar_con_respaldo", falla)
    r = cliente.post("/documento", json={"pedido": "una carta"})
    assert r.status_code == 503
    assert r.json()["detail"] == "El asistente no está disponible en este momento."


def test_uso_incluye_al_proveedor_de_fotos():
    assert "groq_vision" in cliente.get("/uso").json()["proveedores"]
