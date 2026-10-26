"""
Pruebas de lo que /preguntar y /entender guardan en la base de datos (historial.py).

La IA se reemplaza por una función de mentira: no se gasta cuota.
"""

import json
import sqlite3
import uuid
from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi.testclient import TestClient

import bd
import main
import proveedores
import seguridad

CURP = "GODE561231HDFRRN09"


@pytest.fixture(autouse=True)
def ia(monkeypatch):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "")
    monkeypatch.setenv("LIMITE_POR_MINUTO", "0")
    seguridad.limitador.reiniciar()
    llamadas = []

    def falsa(pregunta, contexto):
        llamadas.append(pregunta)
        return "Respuesta de prueba.", "groq", True

    monkeypatch.setattr(proveedores, "preguntar_con_respaldo", falsa)
    return llamadas


cliente = TestClient(main.app)


def mensajes() -> list[dict]:
    with bd.leer() as con:
        return [dict(f) for f in con.execute("SELECT * FROM mensajes ORDER BY id")]


def conversaciones() -> list[dict]:
    with bd.leer() as con:
        return [dict(f) for f in con.execute("SELECT * FROM conversaciones")]


def contenido_de_la_base(archivo) -> bytes:
    """Bytes del archivo y de su -wal: en modo WAL lo nuevo vive primero en el -wal."""
    return b"".join(p.read_bytes() for p in (archivo, archivo.with_name(archivo.name + "-wal")) if p.exists())


# --- Qué se guarda en cada caso ---------------------------------------------------

def test_pregunta_normal_guarda_pregunta_y_respuesta_sin_textos():
    r = cliente.post("/preguntar", json={"pregunta": "¿Quién fue Louis Braille?"}).json()
    uuid.UUID(r["conversacionId"])  # es un UUID válido
    filas = mensajes()
    assert [(m["rol"], m["tipo"]) for m in filas] == [("usuario", "pregunta"), ("asistente", "respuesta")]
    assert all(m["texto"] is None for m in filas)  # GUARDAR_TEXTOS=0 por defecto
    respuesta = filas[1]
    assert respuesta["proveedor"] == "groq" and respuesta["hay_mas"] == 1
    assert respuesta["duracion_ms"] is not None and respuesta["duracion_ms"] >= 0
    assert conversaciones()[0]["titulo"] is None


def test_con_guardar_textos_1_se_guardan_textos_y_titulo(monkeypatch):
    monkeypatch.setenv("GUARDAR_TEXTOS", "1")
    pregunta = "¿Quién fue Louis Braille? " + "x" * 100
    cliente.post("/preguntar", json={"pregunta": pregunta})
    filas = mensajes()
    assert filas[0]["texto"] == pregunta
    assert filas[1]["texto"] == "Respuesta de prueba."
    assert conversaciones()[0]["titulo"] == pregunta[:60]


@pytest.mark.parametrize("guardar", ["0", "1"])
def test_comando_guarda_el_codigo(monkeypatch, guardar):
    monkeypatch.setenv("GUARDAR_TEXTOS", guardar)
    r = cliente.post("/entender", json={"pregunta": "¿Qué hay enfrente de mí?"}).json()
    assert r["tipo"] == "comando" and r["conversacionId"]
    [fila] = mensajes()
    assert (fila["rol"], fila["tipo"], fila["comando"], fila["proveedor"]) == ("usuario", "comando", "ENVIRONMENT", None)
    assert fila["texto"] == ("¿Qué hay enfrente de mí?" if guardar == "1" else None)


@pytest.mark.parametrize("guardar", ["0", "1"])
@pytest.mark.parametrize("ruta", ["/preguntar", "/entender"])
def test_bloqueado_nunca_guarda_el_dato(monkeypatch, base_temporal, ia, guardar, ruta):
    monkeypatch.setenv("GUARDAR_TEXTOS", guardar)
    r = cliente.post(ruta, json={"pregunta": f"Mi CURP es {CURP}, ¿me ayudas?"}).json()
    assert r["proveedor"] is None and r["conversacionId"]
    assert ia == []
    [fila] = mensajes()
    assert (fila["tipo"], fila["texto"]) == ("bloqueado", None)
    assert conversaciones()[0]["titulo"] is None
    assert CURP.encode() not in contenido_de_la_base(base_temporal)


def test_el_contexto_nunca_se_guarda(monkeypatch, base_temporal):
    monkeypatch.setenv("GUARDAR_TEXTOS", "1")
    cliente.post("/preguntar", json={"pregunta": "¿y luego?", "contexto": ["Persona: frase-que-no-se-guarda"]})
    assert b"frase-que-no-se-guarda" not in contenido_de_la_base(base_temporal)


@pytest.mark.parametrize("guardar", ["0", "1"])
def test_ia_caida_guarda_un_error(monkeypatch, guardar):
    monkeypatch.setenv("GUARDAR_TEXTOS", guardar)

    def caida(pregunta, contexto):
        raise proveedores.ProveedorNoDisponible("prueba")

    monkeypatch.setattr(proveedores, "preguntar_con_respaldo", caida)
    assert cliente.post("/preguntar", json={"pregunta": "hola"}).status_code == 503
    filas = mensajes()
    assert [(m["rol"], m["tipo"]) for m in filas] == [("usuario", "pregunta"), ("asistente", "error")]
    assert filas[1]["proveedor"] is None
    assert filas[1]["texto"] == (main.NO_DISPONIBLE if guardar == "1" else None)


# --- conversacionId ---------------------------------------------------------------

def test_conversacion_id_sigue_la_misma_conversacion():
    primera = cliente.post("/entender", json={"pregunta": "¿Qué es un perro guía?"}).json()["conversacionId"]
    segunda = cliente.post("/entender", json={"pregunta": "¿Y cómo se consigue?",
                                              "conversacionId": primera}).json()["conversacionId"]
    assert segunda == primera
    assert len(conversaciones()) == 1
    assert len(mensajes()) == 4


@pytest.mark.parametrize("enviado", [None, str(uuid.uuid4()), "no-soy-un-uuid", "1"])
def test_conversacion_id_desconocido_empieza_otra(enviado):
    r = cliente.post("/preguntar", json={"pregunta": "hola", "conversacionId": enviado}).json()
    assert r["conversacionId"] != enviado
    uuid.UUID(r["conversacionId"])
    assert len(conversaciones()) == 1


def test_los_ids_son_al_azar_y_no_consecutivos():
    ids = {cliente.post("/preguntar", json={"pregunta": "hola"}).json()["conversacionId"] for _ in range(5)}
    assert len(ids) == 5
    assert all(uuid.UUID(i).version == 4 for i in ids)


def test_conversacion_id_demasiado_largo_se_rechaza():
    assert cliente.post("/preguntar", json={"pregunta": "hola", "conversacionId": "x" * 37}).status_code == 422


# --- Fallas y concurrencia --------------------------------------------------------

def test_si_la_base_falla_la_respuesta_llega_igual(monkeypatch):
    def rota():
        raise sqlite3.OperationalError("disk I/O error")

    monkeypatch.setattr(bd, "escribir", rota)
    r = cliente.post("/preguntar", json={"pregunta": "hola"})
    assert r.status_code == 200
    assert r.json()["respuesta"] == "Respuesta de prueba."
    assert r.json()["conversacionId"] is None


def test_varias_peticiones_al_mismo_tiempo(monkeypatch):
    monkeypatch.setenv("GUARDAR_TEXTOS", "1")
    frases = [f"pregunta número {n}" for n in range(40)]

    def mandar(frase):
        return cliente.post("/preguntar", json={"pregunta": frase})

    with ThreadPoolExecutor(max_workers=10) as hilos:
        respuestas = list(hilos.map(mandar, frases))
    assert all(r.status_code == 200 for r in respuestas)
    ids = {r.json()["conversacionId"] for r in respuestas}
    assert None not in ids and len(ids) == 40
    filas = mensajes()
    assert len(filas) == 80
    assert sorted(m["texto"] for m in filas if m["rol"] == "usuario") == sorted(frases)


# --- Arranque del servidor --------------------------------------------------------

def test_al_arrancar_crea_la_base_e_importa_el_json_viejo(base_temporal, tmp_path):
    (tmp_path / "uso_diario.json").write_text(
        json.dumps({"fecha": main.uso.hoy(), "conteo": {"groq": 9}}), encoding="utf-8")
    with TestClient(main.app) as c:  # el "with" corre el arranque (lifespan)
        assert c.get("/uso").json()["proveedores"]["groq"]["usadas"] == 9
    assert base_temporal.exists()
    assert (tmp_path / "uso_diario.json.migrado").exists()
