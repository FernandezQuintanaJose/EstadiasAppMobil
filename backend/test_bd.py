"""Pruebas de la base de datos (bd.py): tablas, migraciones y reglas del diseño."""

import sqlite3

import pytest

import bd


def tablas(con) -> set[str]:
    return {f[0] for f in con.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}


def test_crea_las_tres_tablas_en_la_version_actual(base_temporal):
    with bd.leer() as con:
        assert {"conversaciones", "mensajes", "uso_diario"} <= tablas(con)
        assert "usuarios" not in tablas(con) and "proveedores" not in tablas(con)
        assert bd.version(con) == len(bd.MIGRACIONES) == 1
        assert con.execute("PRAGMA journal_mode").fetchone()[0] == "wal"
    assert base_temporal.exists()


def test_preparar_dos_veces_no_falla_ni_borra_datos(base_temporal):
    with bd.escribir() as con:
        con.execute("INSERT INTO uso_diario (fecha, proveedor, peticiones) VALUES ('2026-10-25', 'groq', 3)")
    bd.cerrar()  # como si se reiniciara el servidor
    bd.preparar()
    with bd.leer() as con:
        assert con.execute("SELECT peticiones FROM uso_diario").fetchone()[0] == 3


def test_si_borran_el_archivo_con_el_servidor_prendido_se_vuelve_a_crear(base_temporal):
    bd.preparar()
    try:
        base_temporal.unlink()
    except PermissionError:
        pytest.skip("Windows no deja borrar un archivo abierto; en Linux (donde se publicará) sí pasa.")
    with bd.escribir() as con:
        con.execute("INSERT INTO uso_diario (fecha, proveedor, peticiones) VALUES ('2026-10-25', 'groq', 1)")
    assert base_temporal.exists()


def test_una_migracion_nueva_se_aplica_sobre_una_base_vieja(base_temporal, monkeypatch):
    bd.preparar()
    with bd.escribir() as con:
        con.execute("INSERT INTO uso_diario (fecha, proveedor, peticiones) VALUES ('2026-10-25', 'groq', 7)")
    # Así se agregaría después la tabla proveedores: una migración más al final de la lista.
    monkeypatch.setattr(bd, "MIGRACIONES", [*bd.MIGRACIONES, """
        CREATE TABLE proveedores (id INTEGER PRIMARY KEY, nombre TEXT NOT NULL UNIQUE); -- futura
        INSERT INTO proveedores (nombre) SELECT DISTINCT proveedor FROM uso_diario;
    """])
    bd.cerrar()
    with bd.leer() as con:
        assert bd.version(con) == 2
        assert [f["nombre"] for f in con.execute("SELECT nombre FROM proveedores")] == ["groq"]
        assert con.execute("SELECT peticiones FROM uso_diario").fetchone()[0] == 7


def test_una_migracion_que_falla_no_deja_la_base_a_medias(base_temporal, monkeypatch):
    bd.preparar()
    monkeypatch.setattr(bd, "MIGRACIONES", [*bd.MIGRACIONES, "CREATE TABLE nueva (x); ESTO NO ES SQL"])
    bd.cerrar()
    with pytest.raises(sqlite3.OperationalError):
        bd.preparar()
    con = sqlite3.connect(base_temporal)
    try:
        assert con.execute("PRAGMA user_version").fetchone()[0] == 1
        assert "nueva" not in tablas(con)
    finally:
        con.close()


def _conversacion(con, id_="c1"):
    con.execute("INSERT INTO conversaciones (id, iniciada_en, ultima_actividad) VALUES (?, 0, 0)", (id_,))


def test_un_mensaje_bloqueado_no_puede_tener_texto():
    with pytest.raises(sqlite3.IntegrityError):
        with bd.escribir() as con:
            _conversacion(con)
            con.execute("INSERT INTO mensajes (conversacion_id, rol, tipo, texto, creado_en) "
                        "VALUES ('c1', 'usuario', 'bloqueado', 'mi CURP es...', 0)")


def test_un_comando_debe_decir_cual():
    with pytest.raises(sqlite3.IntegrityError):
        with bd.escribir() as con:
            _conversacion(con)
            con.execute("INSERT INTO mensajes (conversacion_id, rol, tipo, creado_en) VALUES ('c1', 'usuario', 'comando', 0)")


def test_mensaje_sin_conversacion_se_rechaza():
    with pytest.raises(sqlite3.IntegrityError):
        with bd.escribir() as con:
            con.execute("INSERT INTO mensajes (conversacion_id, rol, tipo, creado_en) "
                        "VALUES ('no-existe', 'usuario', 'pregunta', 0)")


def test_borrar_una_conversacion_borra_sus_mensajes():
    with bd.escribir() as con:
        _conversacion(con)
        con.execute("INSERT INTO mensajes (conversacion_id, rol, tipo, creado_en) VALUES ('c1', 'usuario', 'pregunta', 0)")
    with bd.escribir() as con:
        con.execute("DELETE FROM conversaciones WHERE id = 'c1'")
    with bd.leer() as con:
        assert con.execute("SELECT COUNT(*) FROM mensajes").fetchone()[0] == 0


def test_un_error_deshace_toda_la_transaccion():
    with pytest.raises(RuntimeError):
        with bd.escribir() as con:
            _conversacion(con)
            raise RuntimeError("falla a la mitad")
    with bd.leer() as con:
        assert con.execute("SELECT COUNT(*) FROM conversaciones").fetchone()[0] == 0
