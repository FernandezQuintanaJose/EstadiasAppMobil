"""
Pruebas de imagenes.py. Corren con: python test_imagenes.py (o con pytest).
Usan fotos generadas en memoria, no fotos reales.
"""

import io

from PIL import Image

import imagenes


def _foto_de_celular() -> bytes:
    """4000x3000 "acostada" con EXIF de rotación (6 = girar 90°) y GPS, como las de un celular."""
    exif = Image.Exif()
    exif[0x0112] = 6  # Orientation
    exif[0x8825] = {1: "N", 2: (19.0, 26.0, 0.0)}  # GPSInfo
    salida = io.BytesIO()
    Image.new("RGB", (4000, 3000), (30, 120, 60)).save(salida, "JPEG", quality=95, exif=exif)
    return salida.getvalue()


def test_foto_grande_sale_chica_derecha_y_sin_metadatos():
    foto = _foto_de_celular()
    datos, tipo = imagenes.preparar(foto)
    resultado = Image.open(io.BytesIO(datos))
    assert tipo == "image/jpeg"
    assert resultado.format == "JPEG"
    assert len(datos) < len(foto)
    assert max(resultado.size) <= imagenes.LADO_MAX
    assert resultado.size == (768, 1024)  # se enderezó: ahora es vertical
    assert not resultado.getexif()  # ni rotación ni GPS


def test_png_transparente_sale_jpeg():
    salida = io.BytesIO()
    Image.new("RGBA", (500, 300), (255, 0, 0, 128)).save(salida, "PNG")
    datos, tipo = imagenes.preparar(salida.getvalue())
    assert tipo == "image/jpeg"
    assert Image.open(io.BytesIO(datos)).mode == "RGB"


def test_lo_que_no_es_foto_lanza_imagen_invalida():
    for dato in (b"", b"hola, no soy una foto", b"x" * (imagenes.MAX_BYTES_ENTRADA + 1)):
        try:
            imagenes.preparar(dato)
        except imagenes.ImagenInvalida:
            continue
        raise AssertionError(f"debió fallar con {len(dato)} bytes")


if __name__ == "__main__":
    for nombre, prueba in list(globals().items()):
        if nombre.startswith("test_"):
            prueba()
            print("OK", nombre)
