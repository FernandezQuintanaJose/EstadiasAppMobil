"""
Pruebas de las cuentas propias con código por correo (cuentas.py, decisión 0020).

El correo nunca sale: se reemplaza el envío por una función que guarda el código, así la prueba lo "lee" como lo
leería la persona en su bandeja. Cada prueba tiene su propia base SQLite vacía (conftest.py).
"""

from datetime import timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

import correo
import cuentas
import db
import main
import proveedores

cliente = TestClient(main.app)

USUARIO = "maria.lopez"
CORREO = "Maria.Lopez@Example.com"
CONTRASENA = "Mi Perro Café Corre Rápido"  # 22 letras sin espacios ni acentos
CUERPO = {"usuario": USUARIO, "correo": CORREO, "contrasena": CONTRASENA, "mayorDeEdad": True, "version": "1.0"}


@pytest.fixture(autouse=True)
def buzon(monkeypatch):
    """Los correos que "se mandaron": lista de (correo, código, propósito)."""
    enviados = []

    def enviar(destino, codigo, proposito, minutos):
        enviados.append((destino, codigo, proposito))
        return True

    monkeypatch.setattr(correo, "enviar_codigo", enviar)
    monkeypatch.setenv("CODIGO_ESPERA_S", "0")
    monkeypatch.delenv("VERIFICACION_POR_CORREO", raising=False)
    return enviados


@pytest.fixture
def ia(monkeypatch):
    llamadas = []

    def preguntar(pregunta, contexto, instrucciones=None):
        llamadas.append(pregunta)
        return "Respuesta de prueba.", "groq", False

    monkeypatch.setattr(proveedores, "preguntar_con_respaldo", preguntar)
    return llamadas


def registrar(buzon, cuerpo=None):
    r = cliente.post("/cuenta/registro", json=cuerpo or CUERPO)
    assert r.status_code == 200, r.text
    return buzon[-1][1]


def cuenta_lista(buzon) -> str:
    """Registra y confirma; regresa el token."""
    codigo = registrar(buzon)
    r = cliente.post("/cuenta/verificar", json={"correo": CORREO, "codigo": codigo})
    assert r.status_code == 200, r.text
    return r.json()["token"]


def bearer(token):
    return {"Authorization": f"Bearer {token}"}


# --- Registro y verificación ---------------------------------------------------------------------


def test_registro_manda_un_codigo_de_6_digitos(buzon):
    r = cliente.post("/cuenta/registro", json=CUERPO)
    assert r.status_code == 200 and r.json() == {"requiereCodigo": True}
    destino, codigo, proposito = buzon[-1]
    assert destino == CORREO and proposito == "registro" and len(codigo) == 6 and codigo.isdigit()


def test_verificar_da_token_y_usuario(buzon):
    codigo = registrar(buzon)
    r = cliente.post("/cuenta/verificar", json={"correo": CORREO.upper(), "codigo": codigo})
    assert r.status_code == 200
    assert r.json()["usuario"] == USUARIO and len(r.json()["token"]) >= 40


def test_el_codigo_se_acepta_dictado_con_espacios(buzon):
    codigo = registrar(buzon)
    dictado = " ".join(codigo)
    assert cliente.post("/cuenta/verificar", json={"correo": CORREO, "codigo": dictado}).status_code == 200


def test_ni_el_codigo_ni_el_token_se_guardan(buzon):
    token = cuenta_lista(buzon)
    with db.leer() as con:
        sesiones = con.execute(select(db.sesiones)).all()
        cuenta = con.execute(select(db.cuentas)).first()
    assert all(token not in str(tuple(s)) for s in sesiones)
    assert cuenta.contrasena_hash.startswith("$argon2id$")
    assert "perro" not in cuenta.contrasena_hash


def test_codigo_equivocado_y_luego_agotado(buzon):
    codigo = registrar(buzon)
    malo = "000000" if codigo != "000000" else "111111"
    for _ in range(cuentas.codigo_intentos()):
        r = cliente.post("/cuenta/verificar", json={"correo": CORREO, "codigo": malo})
        assert r.status_code == 400 and r.json()["detail"] == cuentas.MSJ_CODIGO
    # Ya se gastaron los intentos: ni el código correcto sirve.
    assert cliente.post("/cuenta/verificar", json={"correo": CORREO, "codigo": codigo}).status_code == 400


def test_un_codigo_no_sirve_dos_veces(buzon):
    codigo = registrar(buzon)
    assert cliente.post("/cuenta/verificar", json={"correo": CORREO, "codigo": codigo}).status_code == 200
    assert cliente.post("/cuenta/verificar", json={"correo": CORREO, "codigo": codigo}).status_code == 400


def test_el_codigo_vence(buzon, monkeypatch):
    codigo = registrar(buzon)
    despues = db.ahora() + timedelta(minutes=cuentas.codigo_minutos() + 1)
    monkeypatch.setattr(db, "ahora", lambda: despues)
    assert cliente.post("/cuenta/verificar", json={"correo": CORREO, "codigo": codigo}).status_code == 400


def test_no_se_reenvia_codigo_antes_de_la_espera(buzon, monkeypatch):
    monkeypatch.setenv("CODIGO_ESPERA_S", "60")
    registrar(buzon)
    cliente.post("/cuenta/registro", json=CUERPO)
    assert len(buzon) == 1


@pytest.mark.parametrize("cambio, mensaje", [
    ({"usuario": "ab"}, cuentas.MSJ_USUARIO),
    ({"usuario": "maría"}, cuentas.MSJ_USUARIO),
    ({"usuario": "con espacio"}, cuentas.MSJ_USUARIO),
    ({"correo": "sin-arroba.com"}, cuentas.MSJ_CORREO),
    ({"correo": "a@b..com"}, cuentas.MSJ_CORREO),
    ({"contrasena": "corta"}, cuentas.MSJ_CONTRASENA),
    ({"contrasena": "a b c d e f g h i j k l m n"}, cuentas.MSJ_CONTRASENA),  # 14 letras con espacios
    ({"mayorDeEdad": False}, cuentas.MSJ_MAYOR),
])
def test_reglas_del_registro(buzon, cambio, mensaje):
    r = cliente.post("/cuenta/registro", json={**CUERPO, **cambio})
    assert r.status_code == 400 and r.json()["detail"] == mensaje
    assert buzon == []


def test_sin_mayor_de_edad_en_el_cuerpo_no_hay_cuenta(buzon):
    cuerpo = {k: v for k, v in CUERPO.items() if k != "mayorDeEdad"}
    assert cliente.post("/cuenta/registro", json=cuerpo).status_code == 400


def test_usuario_o_correo_ocupados_sin_importar_mayusculas(buzon):
    cuenta_lista(buzon)
    r = cliente.post("/cuenta/registro", json={**CUERPO, "usuario": USUARIO.upper(), "correo": "otro@example.com"})
    assert r.status_code == 409 and r.json()["detail"] == cuentas.MSJ_OCUPADO
    r = cliente.post("/cuenta/registro", json={**CUERPO, "usuario": "otro.usuario", "correo": CORREO.lower()})
    assert r.status_code == 409


def test_un_registro_sin_confirmar_no_aparta_el_usuario(buzon):
    registrar(buzon)
    nuevo = registrar(buzon, {**CUERPO, "correo": "otra@example.com"})
    r = cliente.post("/cuenta/verificar", json={"correo": "otra@example.com", "codigo": nuevo})
    assert r.status_code == 200


def test_sin_verificacion_por_correo_la_cuenta_queda_lista(buzon, monkeypatch):
    monkeypatch.setenv("VERIFICACION_POR_CORREO", "0")
    r = cliente.post("/cuenta/registro", json=CUERPO)
    assert r.json() == {"requiereCodigo": False} and buzon == []
    assert cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": CONTRASENA}).status_code == 200


# --- Acceso ----------------------------------------------------------------------------------------


@pytest.mark.parametrize("identificador", [USUARIO, USUARIO.upper(), CORREO, CORREO.lower()])
def test_acceso_con_usuario_o_correo(buzon, identificador):
    cuenta_lista(buzon)
    r = cliente.post("/cuenta/acceso", json={"identificador": identificador, "contrasena": CONTRASENA})
    assert r.status_code == 200 and r.json()["usuario"] == USUARIO


def test_la_contrasena_dictada_o_escrita_llega_igual(buzon):
    cuenta_lista(buzon)
    for variante in ("miperrocafecorrerapido", "MI PERRO CAFE CORRE RAPIDO", "Mi perro café corre rápido"):
        r = cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": variante})
        assert r.status_code == 200, variante


def test_mismo_error_si_falla_el_usuario_o_la_contrasena(buzon):
    cuenta_lista(buzon)
    a = cliente.post("/cuenta/acceso", json={"identificador": "nadie", "contrasena": CONTRASENA})
    b = cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": "otra frase muy larga de verdad"})
    assert a.status_code == b.status_code == 401
    assert a.json() == b.json() == {"detail": cuentas.MSJ_CREDENCIALES}


def test_sin_confirmar_el_correo_no_entra(buzon):
    registrar(buzon)
    r = cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": CONTRASENA})
    assert r.status_code == 403 and r.json()["detail"] == cuentas.MSJ_SIN_VERIFICAR


def test_bloqueo_tras_intentos_fallidos(buzon):
    cuenta_lista(buzon)
    for _ in range(cuentas.acceso_fallos()):
        assert cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": "x" * 20}).status_code == 401
    r = cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": CONTRASENA})
    assert r.status_code == 429 and "Espera" in r.json()["detail"]


def test_el_bloqueo_termina(buzon, monkeypatch):
    cuenta_lista(buzon)
    for _ in range(cuentas.acceso_fallos()):
        cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": "x" * 20})
    despues = db.ahora() + timedelta(minutes=cuentas.acceso_bloqueo_min() + 1)
    monkeypatch.setattr(db, "ahora", lambda: despues)
    assert cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": CONTRASENA}).status_code == 200


# --- Recuperar y restablecer -----------------------------------------------------------------------


def test_recuperar_responde_igual_exista_o_no(buzon):
    cuenta_lista(buzon)
    a = cliente.post("/cuenta/recuperar", json={"correo": CORREO})
    b = cliente.post("/cuenta/recuperar", json={"correo": "nadie@example.com"})
    assert a.status_code == b.status_code == 200 and a.json() == b.json() == {}
    assert [p for _, _, p in buzon] == ["registro", "recuperar"]


def test_restablecer_cambia_la_contrasena_y_cierra_sesiones(buzon):
    token = cuenta_lista(buzon)
    cliente.post("/cuenta/recuperar", json={"correo": CORREO})
    codigo = buzon[-1][1]
    nueva = "Una Frase Nueva Para Entrar"
    assert cliente.post("/cuenta/restablecer", json={"correo": CORREO, "codigo": codigo, "contrasena": nueva}).status_code == 200
    assert cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": CONTRASENA}).status_code == 401
    assert cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": nueva}).status_code == 200
    assert cliente.delete("/cuenta/borrarChats", headers=bearer(token)).status_code == 401


def _otro(codigo: str) -> str:
    return f"{(int(codigo) + 1) % 10 ** 6:06d}"


def test_pedir_codigos_nuevos_no_da_intentos_sin_fin(buzon):
    """Auditoría OWASP del 8 nov (A07-001): 10 intentos fallidos en 24 horas por cuenta, sumando todos los códigos."""
    cuenta_lista(buzon)
    nueva = "Una Frase Nueva Para Entrar"
    for _ in range(2):
        cliente.post("/cuenta/recuperar", json={"correo": CORREO})
        codigo = buzon[-1][1]
        for _ in range(5):
            r = cliente.post("/cuenta/restablecer", json={"correo": CORREO, "codigo": _otro(codigo), "contrasena": nueva})
            assert r.status_code == 400
    enviados = len(buzon)
    assert cliente.post("/cuenta/recuperar", json={"correo": CORREO}).status_code == 200
    assert len(buzon) == enviados  # ya no se manda otro código
    # Ni el último código correcto sirve hasta que pasen las 24 horas.
    assert cliente.post("/cuenta/restablecer", json={"correo": CORREO, "codigo": codigo, "contrasena": nueva}).status_code == 400


def test_solo_sirve_el_codigo_mas_nuevo(buzon):
    cuenta_lista(buzon)
    cliente.post("/cuenta/recuperar", json={"correo": CORREO})
    viejo = buzon[-1][1]
    cliente.post("/cuenta/restablecer", json={"correo": CORREO, "codigo": _otro(viejo), "contrasena": "Una Frase Nueva Para Entrar"})
    cliente.post("/cuenta/recuperar", json={"correo": CORREO})
    nuevo = buzon[-1][1]
    if viejo != nuevo:
        r = cliente.post("/cuenta/restablecer", json={"correo": CORREO, "codigo": viejo, "contrasena": "Una Frase Nueva Para Entrar"})
        assert r.status_code == 400
    assert cliente.post("/cuenta/restablecer", json={"correo": CORREO, "codigo": nuevo, "contrasena": "Una Frase Nueva Para Entrar"}).status_code == 200


def test_el_codigo_de_registro_no_sirve_para_restablecer(buzon):
    codigo = registrar(buzon)
    r = cliente.post("/cuenta/restablecer", json={"correo": CORREO, "codigo": codigo, "contrasena": "Una Frase Nueva Para Entrar"})
    assert r.status_code == 400


# --- Rutas con sesión ------------------------------------------------------------------------------


def test_aceptacion_guarda_la_version(buzon):
    token = cuenta_lista(buzon)
    r = cliente.post("/cuenta/aceptacion", json={"version": "2.0", "fecha": "2026-11-08", "mayorDeEdad": True},
                     headers=bearer(token))
    assert r.status_code == 200
    with db.leer() as con:
        assert con.execute(select(db.cuentas.c.aceptacion_version)).scalar_one() == "2.0"


@pytest.mark.parametrize("ruta, metodo", [
    ("/cuenta/aceptacion", "post"), ("/cuenta/borrarChats", "delete"), ("/cuenta/eliminarCuenta", "delete"),
])
def test_sin_token_o_con_token_falso_es_401(buzon, ruta, metodo):
    cuerpo = {"version": "1", "mayorDeEdad": True, "contrasena": CONTRASENA}
    for encabezados in ({}, bearer("token-inventado"), {"Authorization": "Basic abc"}):
        r = cliente.request(metodo.upper(), ruta, json=cuerpo, headers=encabezados)
        assert r.status_code == 401 and r.json()["detail"] == cuentas.MSJ_SESION


def test_el_token_vence(buzon, monkeypatch):
    token = cuenta_lista(buzon)
    despues = db.ahora() + timedelta(days=cuentas.sesion_dias() + 1)
    monkeypatch.setattr(db, "ahora", lambda: despues)
    assert cliente.delete("/cuenta/borrarChats", headers=bearer(token)).status_code == 401


def test_salir_revoca_el_token(buzon):
    token = cuenta_lista(buzon)
    assert cliente.post("/cuenta/salir", headers=bearer(token)).status_code == 200
    assert cliente.delete("/cuenta/borrarChats", headers=bearer(token)).status_code == 401


# --- Preguntar con cuenta ------------------------------------------------------------------------


def test_preguntar_con_token_sin_clave_de_la_app(buzon, ia, monkeypatch):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "clave-secreta-de-la-app")
    token = cuenta_lista(buzon)
    assert cliente.post("/preguntar", json={"pregunta": "Hola"}).status_code == 401  # la web sin sesión
    r = cliente.post("/preguntar", json={"pregunta": "¿Quién fue Louis Braille?"}, headers=bearer(token))
    assert r.status_code == 200 and r.json()["conversacionId"]
    assert ia == ["¿Quién fue Louis Braille?"]


def test_token_falso_en_preguntar_es_401_aunque_no_haya_clave(buzon, ia):
    r = cliente.post("/preguntar", json={"pregunta": "Hola"}, headers=bearer("inventado"))
    assert r.status_code == 401 and r.json()["detail"] == cuentas.MSJ_SESION and ia == []


def test_las_conversaciones_quedan_a_nombre_de_la_cuenta(buzon, ia):
    token = cuenta_lista(buzon)
    r = cliente.post("/preguntar", json={"pregunta": "Hola"}, headers=bearer(token))
    with db.leer() as con:
        dueno = con.execute(select(db.conversaciones.c.usuario_id)
                            .where(db.conversaciones.c.id == r.json()["conversacionId"])).scalar_one()
        cuenta_id = con.execute(select(db.cuentas.c.id)).scalar_one()
    assert dueno == cuenta_id


def test_nadie_sigue_la_conversacion_de_otra_cuenta(buzon, ia):
    token = cuenta_lista(buzon)
    propia = cliente.post("/preguntar", json={"pregunta": "Hola"}, headers=bearer(token)).json()["conversacionId"]
    otra = cliente.post("/preguntar", json={"pregunta": "Hola", "conversacionId": propia}).json()["conversacionId"]
    assert otra != propia  # sin cuenta (clave de la app) se abre una nueva


def test_cupo_diario_por_cuenta(buzon, ia, monkeypatch):
    monkeypatch.setenv("CUPO_DIARIO_POR_CUENTA", "3")
    token = cuenta_lista(buzon)
    for _ in range(3):
        assert cliente.post("/preguntar", json={"pregunta": "Hola"}, headers=bearer(token)).status_code == 200
    r = cliente.post("/preguntar", json={"pregunta": "Hola"}, headers=bearer(token))
    assert r.status_code == 429 and "consultas de hoy" in r.json()["detail"]
    assert len(ia) == 3


def test_borrar_chats_borra_solo_los_de_la_cuenta(buzon, ia):
    token = cuenta_lista(buzon)
    cliente.post("/preguntar", json={"pregunta": "Mía"}, headers=bearer(token))
    cliente.post("/preguntar", json={"pregunta": "De la app"})
    r = cliente.delete("/cuenta/borrarChats", headers=bearer(token))
    assert r.status_code == 200 and r.json() == {"borradas": 1}
    with db.leer() as con:
        assert len(con.execute(select(db.conversaciones)).all()) == 1
        assert len(con.execute(select(db.mensajes)).all()) == 2  # la pregunta y respuesta de la app


def test_eliminar_cuenta_pide_la_contrasena_y_borra_todo(buzon, ia):
    token = cuenta_lista(buzon)
    cliente.post("/preguntar", json={"pregunta": "Hola"}, headers=bearer(token))
    r = cliente.request("DELETE", "/cuenta/eliminarCuenta", json={"contrasena": "otra frase muy larga"}, headers=bearer(token))
    assert r.status_code == 403
    r = cliente.request("DELETE", "/cuenta/eliminarCuenta", json={"contrasena": CONTRASENA}, headers=bearer(token))
    assert r.status_code == 200
    with db.leer() as con:
        for tabla in (db.cuentas, db.sesiones, db.codigos, db.uso_cuentas, db.conversaciones, db.mensajes):
            assert con.execute(select(tabla)).all() == [], tabla.name
    assert cliente.post("/cuenta/acceso", json={"identificador": USUARIO, "contrasena": CONTRASENA}).status_code == 401


# --- CORS y correo ---------------------------------------------------------------------------------


def test_la_pagina_puede_mandar_el_token():
    r = cliente.options("/preguntar", headers={
        "Origin": "https://fernandezquintanajose.github.io", "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "authorization,content-type",
    })
    assert r.status_code == 200 and "authorization" in r.headers["access-control-allow-headers"].lower()


def test_correo_en_texto_simple_con_el_codigo_por_digitos(monkeypatch):
    monkeypatch.setenv("CORREO_REMITENTE", "asistente@example.com")
    m = correo.mensaje("ana@example.com", "123456", "registro", 15)
    texto = m.get_content()
    assert "123456" in texto and "1 2 3 4 5 6" in texto and "15 minutos" in texto and "http" not in texto
    assert m["To"] == "ana@example.com" and "asistente@example.com" in m["From"]


def test_smtp_sin_configurar_no_se_cae(monkeypatch, caplog):
    monkeypatch.setenv("CORREO_MODO", "smtp")
    for nombre in ("CORREO_SMTP_HOST", "CORREO_SMTP_USUARIO", "CORREO_SMTP_CONTRASENA", "CORREO_REMITENTE"):
        monkeypatch.delenv(nombre, raising=False)
    import importlib
    real = importlib.reload(correo)  # la versión sin el reemplazo de la prueba
    assert real.enviar_codigo("ana@example.com", "123456", "registro", 15) is False


def test_modo_consola_no_escribe_el_correo_completo(monkeypatch, caplog):
    monkeypatch.setenv("CORREO_MODO", "consola")
    import importlib
    real = importlib.reload(correo)
    with caplog.at_level("WARNING"):
        assert real.enviar_codigo("juan.perez@example.com", "654321", "registro", 15) is True
    assert "654321" in caplog.text and "juan.perez@" not in caplog.text and "ju***@example.com" in caplog.text
