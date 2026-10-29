"""Pruebas de comandos.py: frases que abren un módulo y frases que no deben abrir nada."""

import pytest

import comandos


@pytest.mark.parametrize("frase, modulo", [
    ("¿Qué hay enfrente de mí?", "ENVIRONMENT"),
    ("qué tengo delante", "ENVIRONMENT"),
    ("describe lo que veo", "ENVIRONMENT"),
    ("¿Me puedes leer este documento?", "DOCUMENT_READER"),
    ("¿Qué dice este letrero?", "DOCUMENT_READER"),
    ("Abre la cámara", "DOCUMENT_READER"),
    ("enciende el lector", "DOCUMENT_READER"),
    ("leer documentos", "DOCUMENT_READER"),     # gana el lector, no Solicitudes
    ("mis documentos", "REQUESTS"),
    ("mis trámites", "REQUESTS"),
    ("ver mis peticiones", "REQUESTS"),
    ("quiero practicar", "LEARNING"),
    ("¿Puedes abrir las actividades?", "LEARNING"),
])
def test_abre_el_modulo(frase, modulo):
    assert comandos.detectar(frase) == modulo


@pytest.mark.parametrize("frase", [
    "lee mis notificaciones",        # la resuelve el teléfono, no el lector
    "léeme mis notificaciones",
    "¿qué hora es?",
    "¿Quién fue Louis Braille?",
    "háblame del medio ambiente",
    "los entornos virtuales",        # "entornos" no es "entorno"
])
def test_no_abre_ningun_modulo(frase):
    assert comandos.detectar(frase) is None
