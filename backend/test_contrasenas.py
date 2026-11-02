from contrasenas import guardar, hay_que_actualizar, verificar


def test_la_contrasena_correcta_entra_y_la_otra_no():
    guardada = guardar("una frase larga de prueba")
    assert guardada.startswith("$argon2id$")
    assert verificar(guardada, "una frase larga de prueba")
    assert not verificar(guardada, "otra frase distinta de prueba")


def test_cada_guardado_lleva_su_propia_sal():
    assert guardar("la misma contrasena larga") != guardar("la misma contrasena larga")


def test_una_cadena_danada_o_vieja_nunca_entra():
    assert not verificar("no es un hash", "cualquiera")
    assert not verificar("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8", "password")
    assert not verificar("", "")


def test_avisa_cuando_hay_que_volver_a_guardar():
    assert not hay_que_actualizar(guardar("una frase larga de prueba"))
    assert hay_que_actualizar("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8")
