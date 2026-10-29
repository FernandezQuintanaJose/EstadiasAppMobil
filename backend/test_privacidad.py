"""Pruebas del filtro de privacidad (privacidad.py): qué se bloquea y qué debe pasar."""

import pytest

import privacidad

SE_BLOQUEA = [
    "mi correo es ana@correo.com",
    "mi tel es 55 1234 5678",
    "llámame al (618) 455-0146",
    "+52 618 455 0146",
    "5512345678",
    "mi tarjeta es 4152 3134 5678 9012",
    "mi curp es GOMA850101HDFRRL09",
    "mi rfc es GOMA850101AB1",
    "mi contraseña es gato123",
    "la contraseña: perro",
    "el pin es 1234",
    "mi nip de la tarjeta es 4321",
    "contraseña gato123",
    "password=hola",
]

PASA = [
    "¿qué es un pin de corbata?",
    "¿qué es la CURP?",
    "¿cómo saco mi RFC?",
    "¿cómo hago una contraseña segura?",
    "olvidé mi contraseña",
    "la clave es practicar",
    "México tiene 126 millones de habitantes",
    "Benito Juárez nació en 1806",
    "el CECAPI abre de 2 a 7",
    "¿cuánto es 25 por 4?",
    "la ley de 2011 y la de 2025",
    "¿qué número de emergencia es el 911?",
    "compré 3 kilos a 45 pesos",
]


@pytest.mark.parametrize("texto", SE_BLOQUEA)
def test_se_bloquea(texto):
    assert privacidad.contiene_dato_sensible(texto)


@pytest.mark.parametrize("texto", PASA)
def test_pasa(texto):
    assert not privacidad.contiene_dato_sensible(texto)


def test_limpiar_contexto_quita_solo_las_lineas_sensibles():
    contexto = ["P: hola / R: hola", "P: mi tel es 55 1234 5678 / R: ok", "P: gracias / R: de nada"]
    assert privacidad.limpiar_contexto(contexto) == ["P: hola / R: hola", "P: gracias / R: de nada"]
