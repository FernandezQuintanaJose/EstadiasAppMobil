"""Pruebas de seguridad.py: clave de la app y límite de peticiones por minuto."""

import seguridad


def test_sin_clave_configurada_todo_pasa(monkeypatch):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "")
    assert seguridad.clave_valida(None)
    assert seguridad.clave_valida("cualquiera")


def test_con_clave_configurada(monkeypatch):
    monkeypatch.setenv("CECAPI_CLAVE_APP", "secreta")
    assert seguridad.clave_valida("secreta")
    assert not seguridad.clave_valida("Secreta")
    assert not seguridad.clave_valida("")
    assert not seguridad.clave_valida(None)


def test_limite_por_minuto_y_ventana(monkeypatch):
    monkeypatch.setenv("LIMITE_POR_MINUTO", "2")
    limitador = seguridad.LimitadorPorMinuto()
    assert limitador.permitir("1.1.1.1", ahora=0)
    assert limitador.permitir("1.1.1.1", ahora=10)
    assert not limitador.permitir("1.1.1.1", ahora=20)    # tercera en el mismo minuto
    assert limitador.permitir("2.2.2.2", ahora=20)        # otra IP tiene su propio límite
    assert limitador.permitir("1.1.1.1", ahora=60.5)      # la de t=0 ya salió de la ventana


def test_limite_cero_es_sin_limite(monkeypatch):
    monkeypatch.setenv("LIMITE_POR_MINUTO", "0")
    limitador = seguridad.LimitadorPorMinuto()
    assert all(limitador.permitir("1.1.1.1", ahora=0) for _ in range(100))
